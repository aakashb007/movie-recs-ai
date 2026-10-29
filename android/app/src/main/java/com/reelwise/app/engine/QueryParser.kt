package com.reelwise.app.engine

import com.reelwise.app.data.model.QueryIntent
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Turns a free-text request into a structured QueryIntent. Port of backend/app/llm.py.
 *
 * The LLM (Groq, OpenAI-compatible API) only fills in filters. It never chooses result
 * titles: candidates always come from TMDB, so it cannot recommend something that does not
 * exist. Without a Groq key, or when the call fails, a rule-based parser does the same job.
 */
class QueryParser(
    private val http: OkHttpClient,
    private val keys: KeyProvider,
    private val json: Json,
    private val groqUrl: String = "https://api.groq.com/openai/v1/chat/completions",
    private val model: String = "llama-3.3-70b-versatile",
) {
    /** The intent and which parser produced it ("llm" or "rules"). */
    suspend fun parse(query: String): Pair<QueryIntent, String> {
        val key = keys.groqKey()?.trim()?.takeIf { it.isNotEmpty() }
        if (key != null) {
            try {
                return parseWithGroq(query, key) to "llm"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Network error, bad JSON or an unexpected shape: the rules below still work.
            }
        }
        return parseWithRules(query) to "rules"
    }

    private suspend fun parseWithGroq(query: String, key: String): QueryIntent {
        val body = buildJsonObject {
            put("model", model)
            put("temperature", 0)
            putJsonObject("response_format") { put("type", "json_object") }
            putJsonArray("messages") {
                addJsonObject { put("role", "system"); put("content", SYSTEM_PROMPT) }
                addJsonObject { put("role", "user"); put("content", query) }
            }
        }
        val request = Request.Builder()
            .url(groqUrl)
            .header("Authorization", "Bearer $key")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val content = withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) throw IOException("Groq ${resp.code}")
                val root = json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                root.getValue("choices").jsonArray[0].jsonObject.getValue("message").jsonObject.getValue("content").jsonPrimitive.content
            }
        }
        return sanitize(json.decodeFromString(QueryIntent.serializer(), content), query)
    }

    companion object {
        val SYSTEM_PROMPT = """You convert a person's request for something to watch into JSON filters.
Return only a JSON object with these keys:
  media_type: "movie", "tv" or "any"
  genres: list from ${Genres.CANONICAL.keys.sorted()}
  exclude_genres: same vocabulary
  keywords: up to 4 short themes or subjects, e.g. "time travel", "heist", "small town"
  reference_titles: titles the person compares to ("like Dark" -> ["Dark"]); never invent titles they did not name
  year_min, year_max: integers or null ("90s" -> 1990, 1999)
  min_rating: 0-10 or null (only when they ask for acclaimed/highly rated: 7.0)
  language: ISO 639-1 code or null (only if they ask for a language or country)
  summary: one short sentence restating the request
Map moods to genres and keywords ("something cosy" -> comedy, family, keywords ["feel good"]).
"""

        /** Clamp everything the model said to the vocabulary and ranges the rest of the system understands. */
        fun sanitize(intent: QueryIntent, query: String): QueryIntent {
            fun canon(values: List<String>): List<String> =
                values.mapNotNull { Genres.canonicalize(it) }.distinct().take(5)

            fun year(y: Int?) = y?.takeIf { it in 1900..2035 }
            val excluded = canon(intent.excludeGenres)
            val lang = intent.language?.takeIf { Regex("[a-zA-Z]{2}").matches(it) }?.lowercase()
            return QueryIntent(
                mediaType = intent.mediaType.takeIf { it in setOf("movie", "tv", "any") } ?: "any",
                genres = canon(intent.genres).filterNot { it in excluded },
                excludeGenres = excluded,
                keywords = intent.keywords.map { it.trim().take(40) }.filter { it.isNotEmpty() }.take(4),
                referenceTitles = intent.referenceTitles.map { it.trim().take(80) }.filter { it.isNotEmpty() }.take(3),
                yearMin = year(intent.yearMin),
                yearMax = year(intent.yearMax),
                minRating = intent.minRating?.takeIf { it in 0.0..10.0 },
                language = lang,
                summary = intent.summary.ifBlank { query }.take(200),
            )
        }

        private val DECADE = Regex("""\b(?:(19|20)?(\d)0)'?s\b""")
        private val LIKE = Regex("""\b(?:like|similar to|such as)\s+(.+?)(?:\s+(?:but|and|with|from|in|that|which)\b|[,.!?]|$)""", RegexOption.IGNORE_CASE)
        private val NEGATED = Regex("""\b(?:no|not|without|nothing)\s+([a-z\- ]+?)(?:\b(?:and|but|or)\b|[,.!?]|$)""")

        fun parseWithRules(query: String): QueryIntent {
            val text = query.lowercase()
            val media = when {
                Regex("""\b(show|shows|series|tv|season|binge)\b""").containsMatchIn(text) -> "tv"
                Regex("""\b(movie|movies|film|films)\b""").containsMatchIn(text) -> "movie"
                else -> "any"
            }
            val excluded = mutableListOf<String>()
            for (m in NEGATED.findAll(text)) {
                for (word in m.groupValues[1].trim().split(Regex("\\s+"))) {
                    val c = Genres.canonicalize(word)
                    if (c != null && c !in excluded) excluded += c
                }
            }
            val found = mutableListOf<String>()
            for (term in Genres.SYNONYMS.keys + Genres.CANONICAL.keys) {
                if (Regex("""\b${Regex.escape(term)}\b""").containsMatchIn(text)) {
                    val c = Genres.canonicalize(term)
                    if (c != null && c !in found && c !in excluded) found += c
                }
            }
            var yearMin: Int? = null
            var yearMax: Int? = null
            DECADE.find(text)?.let { m ->
                val digit = m.groupValues[2]
                val century = m.groupValues[1].ifEmpty { if (digit in setOf("0", "1", "2")) "20" else "19" }
                yearMin = "$century${digit}0".toInt()
                yearMax = yearMin!! + 9
            }
            if (Regex("""\b(recent|new|latest)\b""").containsMatchIn(text)) yearMin = 2020
            val minRating = if (Regex("""\b(acclaimed|best|great|top rated|highly rated|masterpiece)\b""").containsMatchIn(text)) 7.0 else null
            val refs = LIKE.findAll(query).map { it.groupValues[1].trim(' ', '\'', '"') }.toList()
            return sanitize(
                QueryIntent(
                    mediaType = media, genres = found, excludeGenres = excluded, referenceTitles = refs,
                    yearMin = yearMin, yearMax = yearMax, minRating = minRating, summary = query,
                ),
                query,
            )
        }
    }
}
