package com.reelwise.app.engine

import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.CreditKind
import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.Interaction
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.remote.LocalReelwiseApi
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The on-device engine end to end against a fake TMDB and Groq: the Kotlin twin of backend/tests/test_api.py. */
class EngineTest {
    private class Keys(var tmdb: String? = "test-key", var groq: String? = null, var omdb: String? = null) : KeyProvider {
        override suspend fun tmdbKey() = tmdb
        override suspend fun groqKey() = groq
        override suspend fun omdbKey() = omdb
    }

    private fun api(services: FakeServices, keys: Keys = Keys(), year: Int = 2026): LocalReelwiseApi {
        val http = services.client()
        val tmdb = TmdbClient(http, keys, FakeServices.json)
        val imdb = OmdbImdbLookup(tmdb, OmdbClient(http, keys, FakeServices.json))
        return LocalReelwiseApi(tmdb, Recommender(tmdb, imdb) { year }, QueryParser(http, keys, FakeServices.json), imdb)
    }

    private fun ids(items: List<RecItem>) = items.map { it.title.mediaType.wire to it.title.id }
    private fun like(id: Int, type: MediaType = MediaType.MOVIE, signal: Signal = Signal.LIKE) = Interaction(id, type, signal)
    private fun recs(api: LocalReelwiseApi, interactions: List<Interaction>, taste: TasteProfile = TasteProfile(), media: String = "all", limit: Int = 12) =
        runBlocking { api.recommendations(RecommendationRequest(interactions, media, "US", limit, taste)) }

    @Test
    fun `v3 key goes in the query, v4 token in a bearer header`() = runBlocking {
        val services = FakeServices()
        api(services, Keys(tmdb = "abc123")).title("movie", 7, "US")
        assertEquals("abc123", services.requests.last().url.queryParameter("api_key"))
        api(services, Keys(tmdb = "eyJtoken")).title("movie", 8, "US")
        assertEquals("Bearer eyJtoken", services.requests.last().header("Authorization"))
        assertNull(services.requests.last().url.queryParameter("api_key"))
    }

    @Test
    fun `missing and rejected keys are distinct errors`() = runBlocking {
        try { api(FakeServices(), Keys(tmdb = null)).onboarding(); fail("expected MissingKeyException") } catch (e: MissingKeyException) {}
        try { api(FakeServices(tmdbStatus = 401)).onboarding(); fail("expected TmdbAuthException") } catch (e: TmdbAuthException) {}
    }

    @Test
    fun `cold start without likes or taste`() {
        val body = recs(api(FakeServices()), emptyList())
        assertEquals("cold_start", body.strategy)
        assertTrue(body.items.isNotEmpty())
    }

    @Test
    fun `personalised picks are explained by real likes and skip everything rated`() {
        val services = FakeServices()
        val interactions = listOf(like(1), like(6), like(11, signal = Signal.SEEN), like(16, signal = Signal.DISMISS))
        val body = recs(api(services), interactions, limit = 10)
        assertEquals("personalized", body.strategy)
        val got = ids(body.items)
        assertTrue(got.isNotEmpty())
        assertTrue(got.none { it in setOf("movie" to 1, "movie" to 6, "movie" to 11, "movie" to 16) })
        assertTrue(got.all { it in services.ids() })
        body.items.forEach { assertTrue(it.because!!.id in setOf(1, 6)); assertTrue(it.reason.isNotBlank()) }
        assertEquals(listOf("Comedy"), body.items.first().title.genres)
    }

    @Test
    fun `watched-only history still personalises, and says so`() {
        val body = recs(api(FakeServices()), listOf(like(2, signal = Signal.SEEN), like(7, signal = Signal.SEEN)))
        assertEquals("personalized", body.strategy)
        assertTrue(body.items.first().reason.startsWith("Because you watched"))
    }

    @Test
    fun `media filter keeps one type`() {
        val body = recs(api(FakeServices()), listOf(like(102, MediaType.TV), like(3)), media = "tv")
        assertTrue(body.items.isNotEmpty())
        assertTrue(body.items.all { it.title.mediaType == MediaType.TV })
    }

    @Test
    fun `taste quiz answers are hard filters`() {
        // Liked a comedy (Film 36, 2021), a horror film (37, 2022) and a drama (38, 2023), whose
        // neighbours include older titles, horror titles and Film 33, the one Hindi film.
        val liked = listOf(like(36), like(37), like(38))
        val api = api(FakeServices())
        val unfiltered = recs(api, liked, limit = 30).items
        assertTrue(unfiltered.any { "Horror" in it.title.genres })
        assertTrue(unfiltered.any { it.title.originalLanguage == "hi" })
        assertTrue(unfiltered.any { (it.title.year ?: 0) < 2016 })

        val taste = TasteProfile(avoidedGenres = listOf("horror"), freshness = Freshness.RECENT, languages = listOf("en"))
        val filtered = recs(api, liked, taste, limit = 30).items
        assertTrue(filtered.isNotEmpty())
        filtered.forEach {
            assertFalse("Horror" in it.title.genres)
            assertTrue("${it.title.title} is ${it.title.year}", (it.title.year ?: 0) >= 2016)
            assertEquals("en", it.title.originalLanguage)
        }
    }

    @Test
    fun `loved genres alone are enough to personalise`() {
        val body = recs(api(FakeServices()), emptyList(), TasteProfile(lovedGenres = listOf("crime")))
        assertEquals("personalized", body.strategy)
        assertTrue(body.items.first().title.genres.single() == "Crime")
        assertEquals("Matches your love of Crime", body.items.first().reason)
    }

    @Test
    fun `newer titles rank higher when everything else is equal`() {
        // Films 38 (2023) and 33 (2018) beat higher-rated older dramas such as Film 23 (2008, rated 9).
        val body = recs(api(FakeServices()), emptyList(), TasteProfile(lovedGenres = listOf("drama")), media = "movie", limit = 3)
        assertEquals(listOf(2023, 2018), body.items.take(2).map { it.title.year })
    }

    @Test
    fun `the LLM cannot inject titles that do not exist`() = runBlocking {
        val services = FakeServices(groqReply = """{"media_type":"movie","genres":["sci-fi","not-a-genre"],
            "reference_titles":["Zorblax 9: The Invented Sequel"],"keywords":["time travel"],"year_min":1990,"year_max":2020,
            "summary":"sci-fi films","results":[{"id":999999,"title":"Hallucinated Movie"}]}""")
        val body = api(services, Keys(groq = "gsk_test")).ask(AskRequest("time travel sci-fi movies", emptyList(), "US"))
        assertEquals("llm", body.parser)
        assertEquals(listOf("science fiction"), body.interpretation.genres)
        assertTrue(body.references.isEmpty())
        assertTrue(body.items.isNotEmpty())
        body.items.forEach {
            assertTrue((it.title.mediaType.wire to it.title.id) in services.ids())
            assertEquals(MediaType.MOVIE, it.title.mediaType)
            assertTrue(it.title.year!! in 1990..2020)
            assertTrue("Science Fiction" in it.title.genres)
        }
    }

    @Test
    fun `ask falls back to rules when the LLM fails`() = runBlocking {
        val body = api(FakeServices(groqStatus = 500), Keys(groq = "gsk_test")).ask(AskRequest("shows like Show 102, no comedy", emptyList(), "US"))
        assertEquals("rules", body.parser)
        assertEquals(listOf("Show 102"), body.references.map { it.title })
        assertTrue(body.items.isNotEmpty())
        body.items.forEach {
            assertEquals(MediaType.TV, it.title.mediaType)
            assertFalse("Comedy" in it.title.genres)
            assertTrue(it.title.id != 102)
        }
    }

    @Test
    fun `ask skips what you have seen`() = runBlocking {
        val body = api(FakeServices()).ask(AskRequest("crime series", listOf(like(103, MediaType.TV, Signal.SEEN)), "US"))
        assertTrue(body.items.none { it.title.id == 103 })
    }

    @Test
    fun `title detail uses the region`() = runBlocking {
        val api = api(FakeServices())
        assertEquals(listOf("StreamCo"), api.title("movie", 7, "GB").providers.stream.map { it.name })
        assertTrue(api.title("movie", 7, "US").providers.stream.isEmpty())
        assertEquals("abc123", api.title("movie", 7, "GB").trailerYoutubeKey)
        try { api.title("movie", 424242, "GB"); fail() } catch (e: TmdbException) { assertEquals(404, e.status) }
    }

    @Test
    fun `onboarding and search return real titles with posters`() = runBlocking {
        val api = api(FakeServices())
        val grid = api.onboarding().items
        assertTrue(grid.size >= 20)
        assertEquals(MediaType.MOVIE, grid[0].mediaType)
        assertEquals(MediaType.TV, grid[1].mediaType)
        assertEquals(listOf("Show 112"), api.search("show 112").map { it.title })
    }

    @Test
    fun `feel-good shelf is uplifting genres only, never dark ones`() {
        val body = recs(api(FakeServices()), listOf(like(2), like(3)), media = "feelgood", limit = 20) // liked horror and drama
        assertTrue(body.items.isNotEmpty())
        body.items.forEach {
            assertTrue("${it.title.title}: ${it.title.genres}", it.title.genres.any { g -> g in setOf("Comedy", "Family", "Animation", "Romance", "Music", "Kids") })
            assertTrue(it.title.genres.none { g -> g in setOf("Horror", "Thriller", "Crime", "War") })
        }
    }

    @Test
    fun `classics shelf only has titles from before 2000, and quiz classics does the same`() {
        val api = api(FakeServices())
        val shelf = recs(api, listOf(like(36)), media = "classics", limit = 20).items
        assertTrue(shelf.isNotEmpty())
        shelf.forEach { assertTrue("${it.title.title} is ${it.title.year}", it.title.year!! <= 1999) }
        assertTrue(shelf.any { it.reason.contains("Classic") })

        val quiz = recs(api, emptyList(), TasteProfile(lovedGenres = listOf("comedy"), freshness = Freshness.CLASSICS), limit = 20).items
        assertTrue(quiz.isNotEmpty())
        quiz.forEach { assertTrue(it.title.year!! <= 1999) }
    }

    @Test
    fun `minimum rating uses TMDB without an OMDb key`() {
        val body = recs(api(FakeServices()), listOf(like(1), like(6)), TasteProfile(minRating = 8.0), limit = 20)
        assertTrue(body.items.isNotEmpty())
        body.items.forEach { assertTrue(it.title.voteAverage >= 8.0) }
        assertTrue(body.items.all { it.title.imdbRating == null })
    }

    @Test
    fun `minimum rating uses IMDb when an OMDb key is saved`() {
        // The fake IMDb rates even ids 8.4 and odd ids 6.1, regardless of TMDB.
        val body = recs(api(FakeServices(), Keys(omdb = "omdb-key")), listOf(like(1), like(6)), TasteProfile(minRating = 8.0), limit = 20)
        assertTrue(body.items.isNotEmpty())
        body.items.forEach {
            assertEquals(8.4, it.title.imdbRating!!, 0.0)
            assertEquals(0, it.title.id % 2)
        }
    }

    @Test
    fun `details carry IMDb and Rotten Tomatoes when an OMDb key is saved`() = runBlocking {
        val withKey = api(FakeServices(), Keys(omdb = "omdb-key")).title("movie", 8, "US")
        assertEquals(8.4, withKey.imdb!!.rating, 0.0)
        assertEquals(12345, withKey.imdb!!.votes)
        assertEquals("88%", withKey.imdb!!.rottenTomatoes)
        assertEquals("tt0000008", withKey.imdbId)
        assertNull(api(FakeServices()).title("movie", 8, "US").imdb)
    }

    @Test
    fun `titles name their director or creator, and a person page lists their work`() = runBlocking {
        val api = api(FakeServices())
        val film = api.title("movie", 7, "US")
        assertEquals(listOf("Dee Rector" to "Director"), film.crew.map { it.name to it.job }) // the producer is left out
        assertEquals(600, film.cast.single().id)
        assertEquals("Creator", api.title("tv", 102, "US").crew.first().job)

        val person = api.person(500)
        assertEquals("Dee Rector", person.name)
        assertEquals("Directing", person.department)
        // Directed films and acting roles, newest first; producing credits and "Self" appearances are dropped.
        assertEquals(listOf(28 to CreditKind.DIRECTED, 13 to CreditKind.ACTED, 3 to CreditKind.DIRECTED), person.credits.map { it.title.id to it.kind })
        assertEquals("Hero", person.credits[1].role)
        assertEquals(listOf(28, 13, 3), person.knownFor.map { it.id }) // most voted first
    }
}
