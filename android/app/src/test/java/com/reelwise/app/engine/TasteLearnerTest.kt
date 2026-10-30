package com.reelwise.app.engine

import com.reelwise.app.data.model.LearnedTaste
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.model.TitleSummary
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteLearnerTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun f(id: Int, genre: String, lang: String = "en", year: Int = 2015, type: MediaType = MediaType.MOVIE) =
        TitleFeatures(id, type, listOf(genre), lang, year)

    private fun t(id: Int, genre: String, lang: String = "en", year: Int = 2015, votes: Int = 1000) = TitleSummary(
        id = id, mediaType = MediaType.MOVIE, title = "T$id", year = year, overview = "same words for every title",
        voteAverage = 7.0, voteCount = votes, genres = listOf(genre), originalLanguage = lang,
    )

    /** rank() needs no network; the client is never called. */
    private fun recommender() = Recommender(
        TmdbClient(
            OkHttpClient(),
            object : KeyProvider {
                override suspend fun tmdbKey() = "unused"
                override suspend fun groqKey(): String? = null
            },
            FakeServices.json,
        ),
        currentYear = { 2024 },
    )

    @Test
    fun likesAndDislikesBecomeSignedGenreAffinities() {
        val learned = TasteLearner.learn(
            listOf(
                Evidence(f(1, "Crime"), TasteLearner.LIKE, now),
                Evidence(f(2, "Crime"), TasteLearner.LIKE, now),
                Evidence(f(3, "Crime"), TasteLearner.LIKE, now),
                Evidence(f(4, "Horror"), TasteLearner.DISLIKE, now),
                Evidence(f(5, "Horror"), TasteLearner.DISLIKE, now),
            ),
            emptyMap(), emptySet(), now,
        )
        assertEquals(0.6, learned.genres.getValue("Crime"), 1e-9) // 3 / (3 + prior 2)
        assertEquals(-0.5, learned.genres.getValue("Horror"), 1e-9)
        assertEquals(5, learned.evidence)
    }

    @Test
    fun oneRatingIsShrunkAndOldActivityFades() {
        val single = TasteLearner.learn(listOf(Evidence(f(1, "Drama"), TasteLearner.LIKE, now)), emptyMap(), emptySet(), now)
        assertTrue("one like is a hint, not a verdict", single.genres.getValue("Drama") < 0.4)

        val old = TasteLearner.learn(
            listOf(Evidence(f(1, "Drama"), TasteLearner.LIKE, now - 240 * day)), // two half-lives ago
            emptyMap(), emptySet(), now,
        )
        assertEquals(0.25 / 2.25, old.genres.getValue("Drama"), 1e-9)
    }

    @Test
    fun aRecentChangeOfHeartOutweighsOldLikes() {
        val learned = TasteLearner.learn(
            listOf(
                Evidence(f(1, "Romance"), TasteLearner.LIKE, now - 365 * day),
                Evidence(f(2, "Romance"), TasteLearner.LIKE, now - 365 * day),
                Evidence(f(3, "Romance"), TasteLearner.DISLIKE, now),
                Evidence(f(4, "Romance"), TasteLearner.DISLIKE, now),
            ),
            emptyMap(), emptySet(), now,
        )
        assertTrue(learned.genres.getValue("Romance") < 0)
    }

    @Test
    fun titlesSwipedPastRepeatedlyAreTiredUnlessActedOn() {
        val learned = TasteLearner.learn(emptyList(), mapOf("movie:1" to 2, "movie:2" to 1, "movie:3" to 3), setOf("movie:3"), now)
        assertEquals(setOf("movie:1"), learned.tired)
    }

    @Test
    fun affinityBlendsGenreLanguageAndDecade() {
        val learned = LearnedTaste(
            genres = mapOf("Crime" to 0.6),
            languages = mapOf("ko" to 0.5),
            decades = mapOf(2010 to -0.2),
        )
        // (0.6 * 0.5 + 0.5 * 0.2 - 0.2 * 0.2) / 0.9
        assertEquals(0.36 / 0.9, TasteLearner.affinity(t(1, "Crime", "ko", 2016), learned), 1e-9)
        assertEquals(0.0, TasteLearner.affinity(t(2, "Western", "en", 1950), learned), 1e-9)
    }

    @Test
    fun learnedTasteReordersOtherwiseEqualTitlesAndExplainsWhy() {
        val recommender = recommender()
        val pool = listOf(Recommender.Candidate(t(1, "Comedy")), Recommender.Candidate(t(2, "Crime")))
        val plain = recommender.rank(pool, emptyList(), emptyList(), TasteProfile(), 2)
        val learned = LearnedTaste(genres = mapOf("Crime" to 0.6, "Comedy" to -0.4), evidence = 10)
        val ranked = recommender.rank(pool, emptyList(), emptyList(), TasteProfile(), 2, learned = learned)

        assertEquals(listOf(2, 1), ranked.map { it.title.id })
        assertEquals("You keep going for Crime", ranked.first().reason)
        assertTrue(ranked.first().score > plain.first { it.title.id == 2 }.score)
    }

    @Test
    fun tiredTitlesSinkBelowFreshOnes() {
        val recommender = recommender()
        val pool = listOf(Recommender.Candidate(t(1, "Drama", votes = 5000)), Recommender.Candidate(t(2, "Drama", votes = 4000)))
        val ranked = recommender.rank(pool, emptyList(), emptyList(), TasteProfile(), 2, learned = LearnedTaste(tired = setOf("movie:1"), evidence = 1))
        assertEquals(listOf(2, 1), ranked.map { it.title.id })
    }
}
