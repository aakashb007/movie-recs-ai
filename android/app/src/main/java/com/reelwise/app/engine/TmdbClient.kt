package com.reelwise.app.engine

import com.reelwise.app.data.model.CastMember
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Provider
import com.reelwise.app.data.model.Providers
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/** Keys live on the phone; the engine asks for them at call time so a change applies at once. */
interface KeyProvider {
    suspend fun tmdbKey(): String?
    suspend fun groqKey(): String?
}

/** No TMDB key has been saved yet. */
class MissingKeyException : IOException("No TMDB key saved")

/** TMDB answered, but not with what we asked for. */
open class TmdbException(message: String, val status: Int) : IOException(message)

/** TMDB rejected the key (401). */
class TmdbAuthException : TmdbException("TMDB rejected the key", 401)

/**
 * Thin TMDB client with an in-memory TTL cache. Accepts a v3 API key or a v4 read-access
 * token (the long one starting "eyJ"). Port of backend/app/tmdb.py.
 */
class TmdbClient(
    private val http: OkHttpClient,
    private val keys: KeyProvider,
    private val json: Json,
    private val baseUrl: String = "https://api.themoviedb.org/3",
    private val ttlMs: Long = 6 * 3600 * 1000L,
    private val maxEntries: Int = 800,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val limit = Semaphore(8)
    private val cache = object : LinkedHashMap<String, Pair<Long, JsonObject>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, JsonObject>>) = size > maxEntries
    }

    suspend fun get(path: String, params: Map<String, String?> = emptyMap(), key: String? = null): JsonObject {
        val apiKey = key ?: keys.tmdbKey()?.takeIf { it.isNotBlank() } ?: throw MissingKeyException()
        val clean = params.filterValues { it != null }.mapValues { it.value!! }.toSortedMap()
        val cacheKey = apiKey.hashCode().toString() + path + "?" + clean.entries.joinToString("&") { "${it.key}=${it.value}" }
        synchronized(cache) {
            cache[cacheKey]?.let { (expires, body) -> if (expires > now()) return body }
        }
        val url = (baseUrl + path).toHttpUrl().newBuilder().apply {
            clean.forEach { (k, v) -> addQueryParameter(k, v) }
            if (!apiKey.startsWith("eyJ")) addQueryParameter("api_key", apiKey)
        }.build()
        val request = Request.Builder().url(url).apply {
            if (apiKey.startsWith("eyJ")) header("Authorization", "Bearer $apiKey")
            header("Accept", "application/json")
        }.build()
        val body = limit.withPermit {
            withContext(Dispatchers.IO) {
                http.newCall(request).execute().use { resp ->
                    when {
                        resp.code == 401 -> throw TmdbAuthException()
                        resp.code == 404 -> throw TmdbException("Not found: $path", 404)
                        !resp.isSuccessful -> throw TmdbException("TMDB ${resp.code} for $path", resp.code)
                    }
                    json.parseToJsonElement(resp.body?.string().orEmpty()).jsonObject
                }
            }
        }
        synchronized(cache) { cache[cacheKey] = now() + ttlMs to body }
        return body
    }

    /** Throws TmdbAuthException or IOException when the key doesn't work. */
    suspend fun checkKey(key: String) {
        get("/configuration", key = key.trim())
    }

    suspend fun trending(type: MediaType, window: String = "week"): List<TitleSummary> =
        results(get("/trending/${type.wire}/$window"), type)

    suspend fun topRated(type: MediaType, page: Int = 1): List<TitleSummary> =
        results(get("/${type.wire}/top_rated", mapOf("page" to page.toString())), type)

    suspend fun popular(type: MediaType, page: Int = 1): List<TitleSummary> =
        results(get("/${type.wire}/popular", mapOf("page" to page.toString())), type)

    suspend fun recommendations(type: MediaType, id: Int): List<TitleSummary> = try {
        results(get("/${type.wire}/$id/recommendations"), type)
    } catch (e: TmdbAuthException) {
        throw e
    } catch (e: TmdbException) {
        emptyList()
    }

    suspend fun summary(type: MediaType, id: Int): TitleSummary? = try {
        summary(details(type, id), type)
    } catch (e: TmdbAuthException) {
        throw e
    } catch (e: TmdbException) {
        null
    }

    private suspend fun details(type: MediaType, id: Int): JsonObject =
        get("/${type.wire}/$id", mapOf("append_to_response" to "watch/providers,recommendations,credits,videos,keywords"))

    suspend fun details(type: MediaType, id: Int, region: String): TitleDetail {
        val raw = details(type, id)
        val base = summary(raw, type) ?: throw TmdbException("Unusable title", 500)
        val regional = raw.obj("watch/providers")?.obj("results")?.obj(region.uppercase())
        fun providers(kind: String) = regional?.arr(kind).orEmpty().mapNotNull { p ->
            val o = p as? JsonObject ?: return@mapNotNull null
            Provider(o.str("provider_name") ?: return@mapNotNull null, o.str("logo_path"))
        }
        val trailer = raw.obj("videos")?.arr("results").orEmpty().map { it as JsonObject }
            .firstOrNull { it.str("site") == "YouTube" && it.str("type") == "Trailer" }?.str("key")
        val runtime = raw.int("runtime") ?: raw.arr("episode_run_time")?.firstOrNull()?.let { (it as? JsonPrimitive)?.intOrNull }
        return TitleDetail(
            summary = base,
            tagline = raw.str("tagline").orEmpty(),
            runtime = runtime?.takeIf { it > 0 },
            seasons = raw.int("number_of_seasons"),
            status = raw.str("status").orEmpty(),
            cast = raw.obj("credits")?.arr("cast").orEmpty().take(10).mapNotNull { c ->
                val o = c as? JsonObject ?: return@mapNotNull null
                CastMember(o.str("name") ?: return@mapNotNull null, o.str("character").orEmpty(), o.str("profile_path"))
            },
            trailerYoutubeKey = trailer,
            providers = Providers(
                link = regional?.str("link"),
                stream = providers("flatrate"),
                free = providers("free") + providers("ads"),
                rent = providers("rent"),
                buy = providers("buy"),
            ),
            similar = raw.obj("recommendations")?.arr("results").orEmpty().take(12)
                .mapNotNull { (it as? JsonObject)?.let { o -> summary(o, type) } },
        )
    }

    suspend fun search(query: String): List<TitleSummary> {
        val body = get("/search/multi", mapOf("query" to query, "include_adult" to "false"))
        return body.arr("results").orEmpty().mapNotNull { r ->
            val o = r as? JsonObject ?: return@mapNotNull null
            when (o.str("media_type")) {
                "movie" -> summary(o, MediaType.MOVIE)
                "tv" -> summary(o, MediaType.TV)
                else -> null
            }
        }
    }

    suspend fun keywordId(keyword: String): Int? =
        get("/search/keyword", mapOf("query" to keyword)).arr("results")?.firstOrNull()
            ?.let { (it as JsonObject).int("id") }

    suspend fun discover(type: MediaType, params: Map<String, String?>): List<TitleSummary> =
        results(get("/discover/${type.wire}", params + ("include_adult" to "false")), type)

    private fun results(body: JsonObject, type: MediaType) =
        body.arr("results").orEmpty().mapNotNull { (it as? JsonObject)?.let { o -> summary(o, type) } }

    companion object {
        /** Normalise a TMDB movie/tv object (list item or details) into a TitleSummary. */
        fun summary(raw: JsonObject, type: MediaType): TitleSummary? {
            val id = raw.int("id") ?: return null
            val title = raw.str("title") ?: raw.str("name") ?: return null
            if (raw["adult"]?.let { (it as? JsonPrimitive)?.booleanOrNull } == true) return null
            val date = raw.str("release_date") ?: raw.str("first_air_date") ?: ""
            val genres = raw.arr("genre_ids")?.let { ids -> Genres.names(ids.mapNotNull { (it as? JsonPrimitive)?.intOrNull }, type) }
                ?: raw.arr("genres").orEmpty().mapNotNull { (it as? JsonObject)?.str("name") }
            return TitleSummary(
                id = id,
                mediaType = type,
                title = title,
                year = date.take(4).toIntOrNull(),
                overview = raw.str("overview").orEmpty(),
                posterPath = raw.str("poster_path"),
                backdropPath = raw.str("backdrop_path"),
                voteAverage = raw.double("vote_average") ?: 0.0,
                voteCount = raw.int("vote_count") ?: 0,
                genres = genres,
                originalLanguage = raw.str("original_language"),
            )
        }
    }
}

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
internal fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
internal fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull
