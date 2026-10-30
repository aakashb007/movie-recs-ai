package com.reelwise.app.engine

import com.reelwise.app.data.model.QueryIntent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ParserAndMathTest {
    @Test
    fun `rules parse reference, genres and decade`() {
        val intent = QueryParser.parseWithRules("a funny sci-fi show like Dark but lighter from the 90s")
        assertEquals("tv", intent.mediaType)
        assertEquals(listOf("Dark"), intent.referenceTitles)
        assertTrue(intent.genres.containsAll(listOf("comedy", "science fiction")))
        assertEquals(1990, intent.yearMin)
        assertEquals(1999, intent.yearMax)
    }

    @Test
    fun `rules parse negation and acclaim`() {
        val intent = QueryParser.parseWithRules("acclaimed movies, no horror")
        assertEquals("movie", intent.mediaType)
        assertEquals(listOf("horror"), intent.excludeGenres)
        assertTrue("horror" !in intent.genres)
        assertEquals(7.0, intent.minRating!!, 0.0)
    }

    @Test
    fun `sanitize drops what the system cannot use`() {
        val raw = QueryIntent(genres = listOf("Sci-Fi", "space opera", "comedy"), excludeGenres = listOf("comedy"), yearMin = 1066,
            yearMax = 2005, minRating = 42.0, language = "english", referenceTitles = listOf(" ", "Dark"), keywords = listOf("a", "b", "c", "d", "e"))
        val clean = QueryParser.sanitize(raw, "q")
        assertEquals(listOf("science fiction"), clean.genres)
        assertNull(clean.yearMin)
        assertEquals(2005, clean.yearMax)
        assertNull(clean.minRating)
        assertNull(clean.language)
        assertEquals(listOf("Dark"), clean.referenceTitles)
        assertEquals(4, clean.keywords.size)
    }

    @Test
    fun `tf-idf ranks shared rare words above shared common ones`() {
        val v = TextVectors.embed(listOf("space station mystery", "space station drama", "kitchen drama comedy", "the and of"))
        assertTrue(TextVectors.dot(v[0], v[1]) > TextVectors.dot(v[0], v[2]))
        assertEquals(1.0, TextVectors.dot(v[0], v[0]), 1e-5)
        assertEquals(0.0, TextVectors.dot(v[3], v[3]), 1e-9) // only stop words
    }

    @Test
    fun `mmr keeps the best first and spreads out the rest`() {
        val vectors = Array(10) { i -> if (i < 5) floatArrayOf(1f, 0f) else floatArrayOf(0f, 1f) }
        val candidates = (0 until 10).toList()
        val relevance = DoubleArray(10) { 1.0 - it * 0.05 }
        val diverse = mmr(candidates, relevance, vectors, 4, lam = 0.5)
        assertEquals(0, diverse[0])
        assertTrue(diverse.any { it >= 5 })
        assertEquals(listOf(0, 1, 2, 3), mmr(candidates, relevance, vectors, 4, lam = 1.0))
    }

    @Test
    fun `avoiding sci-fi alone does not ban TV's combined Sci-Fi and Fantasy`() {
        assertEquals(setOf("Science Fiction"), Genres.bannedNames(listOf("science fiction"), com.reelwise.app.data.model.MediaType.MOVIE))
        assertTrue(Genres.bannedNames(listOf("science fiction"), com.reelwise.app.data.model.MediaType.TV).isEmpty())
        assertEquals(setOf("Sci-Fi & Fantasy"), Genres.bannedNames(listOf("science fiction", "fantasy"), com.reelwise.app.data.model.MediaType.TV))
    }

    @Test
    fun `rules understand feel-good and old cult classics`() {
        val feel = QueryParser.parseWithRules("feel good movies for tonight")
        assertTrue("comedy" in feel.genres)
        assertTrue(feel.excludeGenres.containsAll(listOf("horror", "thriller")))
        val cult = QueryParser.parseWithRules("old cult classics")
        assertEquals(1999, cult.yearMax)
        assertEquals(listOf("cult film"), cult.keywords)
    }
}
