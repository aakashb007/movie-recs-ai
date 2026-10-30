package com.reelwise.app.engine

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/** An in-memory TMDB + Groq, served through an OkHttp interceptor (no network). Mirrors backend/tests/fake_tmdb.py. */
class FakeServices(
    var groqReply: String = "{}",
    var groqStatus: Int = 200,
    var tmdbStatus: Int = 200,
) {
    val requests = mutableListOf<Request>()
    private val movieGenres = mapOf("scifi" to 878, "comedy" to 35, "horror" to 27, "drama" to 18, "crime" to 80)
    private val tvGenres = mapOf("scifi" to 10765, "comedy" to 35, "drama" to 18, "crime" to 80)
    val catalog: Map<Pair<String, Int>, JsonObject> = buildMap {
        val themes = listOf("scifi", "comedy", "horror", "drama", "crime")
        for (i in 1..40) {
            val theme = themes[i % 5]
            put("movie" to i, buildJsonObject {
                put("id", i); put("title", "Film $i"); put("release_date", "${1985 + i}-05-01")
                putJsonArray("genre_ids") { add(movieGenres.getValue(theme)) }
                put("overview", "A $theme story about $theme things number $i")
                put("poster_path", "/m$i.jpg"); put("vote_average", 6 + i % 4); put("vote_count", 500 + i * 10)
                put("popularity", 100 - i); put("original_language", if (i == 33) "hi" else "en")
            })
        }
        for (i in 101..120) {
            val theme = listOf("scifi", "comedy", "drama", "crime")[i % 4]
            put("tv" to i, buildJsonObject {
                put("id", i); put("name", "Show $i"); put("first_air_date", "${1990 + i - 100}-01-01")
                putJsonArray("genre_ids") { add(tvGenres.getValue(theme)) }
                put("overview", "A $theme series about $theme people $i")
                put("poster_path", "/t$i.jpg"); put("vote_average", 7.5); put("vote_count", 900); put("popularity", 50)
                put("original_language", if (i == 105) "de" else "en")
            })
        }
    }

    fun ids(): Set<Pair<String, Int>> = catalog.keys

    fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { chain -> handle(chain.request()) }).build()

    private fun respond(req: Request, code: Int, body: String) = Response.Builder()
        .request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    private fun list(req: Request, rows: List<JsonObject>) =
        respond(req, 200, buildJsonObject { put("results", buildJsonArray { rows.forEach { add(it) } }) }.toString())

    private fun genre(o: JsonObject) = o["genre_ids"]!!.toString().trim('[', ']').toInt()

    private fun handle(req: Request): Response {
        requests += req
        val url = req.url
        if (url.host == "www.omdbapi.com") {
            val id = url.queryParameter("i").orEmpty().removePrefix("tt").toIntOrNull() ?: return respond(req, 200, """{"Response":"False"}""")
            // Even ids rate 8.4 on IMDb, odd ones 6.1, whatever TMDB says.
            val rating = if (id % 2 == 0) "8.4" else "6.1"
            return respond(req, 200, """{"Response":"True","imdbRating":"$rating","imdbVotes":"12,345","Metascore":"77",
                "Ratings":[{"Source":"Rotten Tomatoes","Value":"88%"}]}""")
        }
        if (url.host == "api.groq.com") {
            if (groqStatus != 200) return respond(req, groqStatus, """{"error":"boom"}""")
            val body = buildJsonObject {
                putJsonArray("choices") { add(buildJsonObject { putJsonObject("message") { put("content", groqReply) } }) }
            }
            return respond(req, 200, body.toString())
        }
        if (tmdbStatus != 200) return respond(req, tmdbStatus, "{}")
        val path = url.encodedPath.removePrefix("/3")
        val p = { k: String -> url.queryParameter(k) }
        Regex("/trending/(movie|tv)/week").matchEntire(path)?.let { m ->
            return list(req, catalog.filterKeys { it.first == m.groupValues[1] }.values.take(20))
        }
        Regex("/(movie|tv)/(top_rated|popular)").matchEntire(path)?.let { m ->
            return list(req, catalog.filterKeys { it.first == m.groupValues[1] }.values.sortedBy { -(it["vote_count"].toString().toDouble()) }.take(20))
        }
        Regex("/(movie|tv)/(\\d+)/recommendations").matchEntire(path)?.let { m ->
            val base = catalog[m.groupValues[1] to m.groupValues[2].toInt()] ?: return respond(req, 404, "{}")
            return list(req, catalog.filter { (k, v) -> k.first == m.groupValues[1] && k.second != m.groupValues[2].toInt() && genre(v) == genre(base) }.values.toList())
        }
        Regex("/(movie|tv)/(\\d+)").matchEntire(path)?.let { m ->
            val row = catalog[m.groupValues[1] to m.groupValues[2].toInt()] ?: return respond(req, 404, "{}")
            val names = mapOf(878 to "Science Fiction", 35 to "Comedy", 27 to "Horror", 18 to "Drama", 80 to "Crime", 10765 to "Sci-Fi & Fantasy")
            val detail = buildJsonObject {
                row.filterKeys { it != "genre_ids" }.forEach { (k, v) -> put(k, v) }
                putJsonArray("genres") { add(buildJsonObject { put("id", genre(row)); put("name", names.getValue(genre(row))) }) }
                putJsonObject("watch/providers") { putJsonObject("results") { putJsonObject("GB") {
                    put("link", "https://example.test/watch")
                    putJsonArray("flatrate") { add(buildJsonObject { put("provider_name", "StreamCo"); put("logo_path", "/s.png") }) }
                } } }
                putJsonObject("videos") { putJsonArray("results") { add(buildJsonObject { put("site", "YouTube"); put("type", "Trailer"); put("key", "abc123") }) } }
                putJsonObject("credits") {
                    putJsonArray("cast") { add(buildJsonObject { put("id", 600); put("name", "Ada Actor"); put("character", "Lead") }) }
                    putJsonArray("crew") {
                        add(buildJsonObject { put("id", 500); put("name", "Dee Rector"); put("job", "Director") })
                        add(buildJsonObject { put("id", 501); put("name", "Pro Ducer"); put("job", "Producer") })
                    }
                }
                if (m.groupValues[1] == "tv") putJsonArray("created_by") { add(buildJsonObject { put("id", 502); put("name", "Cree Ator") }) }
                putJsonObject("recommendations") { putJsonArray("results") {} }
                if (m.groupValues[1] == "movie") put("runtime", 120)
                val imdb = "tt" + m.groupValues[2].padStart(7, '0')
                if (m.groupValues[1] == "movie") put("imdb_id", imdb) else putJsonObject("external_ids") { put("imdb_id", imdb) }
            }
            return respond(req, 200, detail.toString())
        }
        Regex("/person/(\\d+)").matchEntire(path)?.let { m ->
            if (m.groupValues[1] != "500") return respond(req, 404, "{}")
            fun credit(type: String, id: Int, extra: Pair<String, String>) = buildJsonObject {
                catalog.getValue(type to id).forEach { (k, v) -> put(k, v) }
                put("media_type", type); put(extra.first, extra.second)
            }
            val body = buildJsonObject {
                put("id", 500); put("name", "Dee Rector"); put("biography", "Makes films."); put("known_for_department", "Directing")
                putJsonObject("combined_credits") {
                    putJsonArray("crew") {
                        add(credit("movie", 3, "job" to "Director")); add(credit("movie", 28, "job" to "Director"))
                        add(credit("movie", 9, "job" to "Producer"))
                    }
                    putJsonArray("cast") {
                        add(credit("movie", 13, "character" to "Hero")); add(credit("tv", 101, "character" to "Self - Guest"))
                    }
                }
            }
            return respond(req, 200, body.toString())
        }
        if (path == "/configuration") return respond(req, 200, """{"images":{}}""")
        if (path == "/search/multi") {
            val q = p("query").orEmpty().lowercase()
            return list(req, catalog.filter { (_, v) -> q.isNotEmpty() && (v["title"] ?: v["name"]).toString().trim('"').lowercase().contains(q) }
                .map { (k, v) -> buildJsonObject { v.forEach { (a, b) -> put(a, b) }; put("media_type", k.first) } })
        }
        if (path == "/search/keyword") {
            val ids = mapOf("time travel" to 999, "feel-good" to 1001, "cult film" to 1002)
            return list(req, ids[p("query")]?.let { listOf(buildJsonObject { put("id", it); put("name", p("query")) }) } ?: emptyList())
        }
        Regex("/discover/(movie|tv)").matchEntire(path)?.let { m ->
            val type = m.groupValues[1]
            var rows = catalog.filterKeys { it.first == type }.values.toList()
            p("with_genres")?.let { g -> val want = g.split(',', '|').map(String::toInt).toSet(); rows = rows.filter { genre(it) in want } }
            p("without_genres")?.let { g -> val ban = g.split(',').map(String::toInt).toSet(); rows = rows.filter { genre(it) !in ban } }
            val dateKey = if (type == "movie") "release_date" else "first_air_date"
            val field = if (type == "movie") "primary_release_date" else "first_air_date"
            p("$field.gte")?.let { lo -> rows = rows.filter { it[dateKey].toString().trim('"') >= lo } }
            p("$field.lte")?.let { hi -> rows = rows.filter { it[dateKey].toString().trim('"') <= hi } }
            p("with_original_language")?.let { l -> rows = rows.filter { it["original_language"].toString().trim('"') == l } }
            p("vote_average.gte")?.let { v -> rows = rows.filter { it["vote_average"].toString().toDouble() >= v.toDouble() } }
            return list(req, rows.take(20))
        }
        return respond(req, 404, "{}")
    }

    companion object {
        val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; encodeDefaults = true }
    }
}
