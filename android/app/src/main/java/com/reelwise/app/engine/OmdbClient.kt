package com.reelwise.app.engine

import com.reelwise.app.data.model.ImdbScore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * IMDb ratings through OMDb (omdbapi.com). TMDB has no IMDb scores, only IMDb ids, so this needs
 * the person's own free OMDb key; without one it returns null and the app falls back to TMDB's
 * rating. Answers are cached for the session: the free tier allows 1,000 lookups a day.
 */
class OmdbClient(
    private val http: OkHttpClient,
    private val keys: KeyProvider,
    private val json: Json,
    private val baseUrl: String = "https://www.omdbapi.com/",
) {
    private val cache = HashMap<String, ImdbScore?>()
    private val limit = Semaphore(6)

    suspend fun hasKey(): Boolean = !keys.omdbKey().isNullOrBlank()

    suspend fun score(imdbId: String): ImdbScore? {
        val key = keys.omdbKey()?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        synchronized(cache) { if (cache.containsKey(imdbId)) return cache[imdbId] }
        val url = baseUrl.toHttpUrl().newBuilder().addQueryParameter("i", imdbId).addQueryParameter("apikey", key).build()
        val result = try {
            limit.withPermit {
                withContext(Dispatchers.IO) {
                    http.newCall(Request.Builder().url(url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) return@use null
                        parse(json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject)
                    }
                }
            }
        } catch (e: java.io.IOException) {
            return null // don't cache network failures
        }
        synchronized(cache) { cache[imdbId] = result }
        return result
    }

    companion object {
        fun parse(o: JsonObject): ImdbScore? {
            if (o.str("Response") != "True") return null
            val rating = o.str("imdbRating")?.toDoubleOrNull() ?: return null
            val tomatoes = o.arr("Ratings").orEmpty().mapNotNull { it as? JsonObject }
                .firstOrNull { it.str("Source") == "Rotten Tomatoes" }?.str("Value")
            return ImdbScore(
                rating = rating,
                votes = o.str("imdbVotes")?.replace(",", "")?.toIntOrNull(),
                rottenTomatoes = tomatoes,
                metascore = o.str("Metascore")?.toIntOrNull(),
            )
        }
    }
}
