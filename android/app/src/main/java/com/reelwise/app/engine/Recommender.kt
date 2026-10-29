package com.reelwise.app.engine

import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.Interaction
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Mix
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleRef
import com.reelwise.app.data.model.TitleSummary
import java.util.Calendar
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Turns a person's ratings, taste profile or typed request into a ranked, explained list of
 * real TMDB titles. Port of backend/app/service.py, running on the phone.
 *
 * Scores blend: content similarity (genres + plot) to the titles you loved, TMDB's own
 * "people who liked X liked Y" lists for each liked title, a match with your loved genres,
 * a Bayesian rating, and recency. The MovieLens model the backend can add is not used here.
 * The blend weights are hand-set: on the phone there is no held-out data to tune them on.
 */
class Recommender(
    private val tmdb: TmdbClient,
    private val currentYear: () -> Int = { Calendar.getInstance().get(Calendar.YEAR) },
) {
    class Candidate(
        val title: TitleSummary,
        val seedVotes: MutableMap<TitleKey, Double> = mutableMapOf(),
        var facets: List<String> = emptyList(),
    )

    /** Feed recommendations; returns the items and the strategy used ("personalized" or "cold_start"). */
    suspend fun recommend(
        interactions: List<Interaction>,
        taste: TasteProfile,
        filter: String,
        limit: Int,
    ): Pair<List<RecItem>, String> = coroutineScope {
        val latest = LinkedHashMap<TitleKey, Interaction>()
        for (i in interactions) { // a later signal on the same title wins
            val key = TitleKey(i.id, i.mediaType)
            latest.remove(key)
            latest[key] = i
        }
        val excluded = latest.keys.toSet()
        val likes = latest.values.filter { it.signal == Signal.LIKE }
        val seen = latest.values.filter { it.signal == Signal.SEEN }
        val dislikes = latest.values.filter { it.signal == Signal.DISLIKE }
        val kinds = kindsFor(filter)
        // Loved titles lead; with none, titles merely watched still say a lot about taste.
        val seeds = likes.takeLast(SEEDS).ifEmpty { seen.takeLast(SEEDS - 2) }
        val watchedOnly = likes.isEmpty()

        if (seeds.isEmpty() && taste.lovedGenres.isEmpty()) {
            return@coroutineScope coldStart(excluded, kinds, taste, limit) to "cold_start"
        }

        val seedTitles = async { summaries((seeds + dislikes.takeLast(SEEDS)).map { TitleKey(it.id, it.mediaType) }) }
        val recLists = seeds.map { s -> async { s to tmdb.recommendations(s.mediaType, s.id) } }
        val genreLists = kinds.map { k -> async { tasteDiscover(k, taste) } }

        val candidates = LinkedHashMap<TitleKey, Candidate>()
        for ((seed, recs) in recLists.awaitAll()) {
            recs.forEachIndexed { pos, t ->
                val c = candidates.getOrPut(t.key) { Candidate(t) }
                c.seedVotes[TitleKey(seed.id, seed.mediaType)] = 1.0 - pos.toDouble() / maxOf(recs.size, 1)
            }
        }
        for (titles in genreLists.awaitAll()) {
            for (t in titles) candidates.getOrPut(t.key) { Candidate(t) }
        }

        val seedKeys = seeds.map { TitleKey(it.id, it.mediaType) }.toSet()
        val fetched = seedTitles.await()
        val liked = fetched.filter { it.key in seedKeys }
        val disliked = fetched.filterNot { it.key in seedKeys }
        val pool = candidates.filter { (k, c) -> k !in excluded && k.mediaType in kinds && passes(c.title, taste) }.values.toList()
        rank(pool, liked, disliked, taste, limit, watchedOnly = watchedOnly) to "personalized"
    }

    private suspend fun coldStart(excluded: Set<TitleKey>, kinds: List<MediaType>, taste: TasteProfile, limit: Int): List<RecItem> =
        coroutineScope {
            val trending = kinds.map { k -> async { tmdb.trending(k) } }
            val top = kinds.map { k -> async { tmdb.topRated(k) } }
            val pool = LinkedHashMap<TitleKey, Candidate>()
            trending.awaitAll().flatten().forEach { pool.getOrPut(it.key) { Candidate(it, facets = listOf("Trending this week")) } }
            top.awaitAll().flatten().forEach { pool.getOrPut(it.key) { Candidate(it, facets = listOf("All-time favourite")) } }
            val usable = pool.filter { (k, c) -> k !in excluded && passes(c.title, taste) }.values.toList()
            rank(usable, emptyList(), emptyList(), taste, limit)
        }

    /** Titles from the person's loved genres, inside their freshness and language choices. */
    private suspend fun tasteDiscover(type: MediaType, taste: TasteProfile): List<TitleSummary> {
        val loved = Genres.idsFor(taste.lovedGenres, type)
        if (loved.isEmpty()) return emptyList()
        val dateField = if (type == MediaType.MOVIE) "primary_release_date" else "first_air_date"
        val base = mapOf(
            "sort_by" to "popularity.desc",
            "vote_count.gte" to "50",
            "with_genres" to loved.joinToString("|"),
            "without_genres" to Genres.idsFor(taste.avoidedGenres, type).joinToString(",").ifEmpty { null },
            "$dateField.gte" to minYear(taste)?.let { "$it-01-01" },
        )
        val languages = taste.languages.take(3).ifEmpty { listOf(null) }
        return coroutineScope {
            languages.map { lang -> async { tmdb.discover(type, base + ("with_original_language" to lang)) } }.awaitAll().flatten()
        }
    }

    private fun minYear(taste: TasteProfile): Int? = when (taste.freshness) {
        Freshness.LATEST -> currentYear() - 2
        Freshness.RECENT -> currentYear() - 10
        Freshness.ANY -> null
    }

    /** Hard constraints from the taste quiz: avoided genres, freshness and languages. */
    fun passes(t: TitleSummary, taste: TasteProfile): Boolean {
        if (t.genres.any { it in Genres.bannedNames(taste.avoidedGenres, t.mediaType) }) return false
        minYear(taste)?.let { min -> if ((t.year ?: 0) < min) return false }
        if (taste.languages.isNotEmpty() && t.originalLanguage != null && t.originalLanguage !in taste.languages) return false
        return true
    }

    private fun kindsFor(filter: String) = when (filter) {
        "movie" -> listOf(MediaType.MOVIE)
        "tv" -> listOf(MediaType.TV)
        else -> listOf(MediaType.MOVIE, MediaType.TV)
    }

    fun rank(
        pool: List<Candidate>,
        liked: List<TitleSummary>,
        disliked: List<TitleSummary>,
        taste: TasteProfile,
        limit: Int,
        queryText: String? = null,
        queryWeight: Double = 0.0,
        watchedOnly: Boolean = false,
    ): List<RecItem> {
        if (pool.isEmpty()) return emptyList()
        val texts = pool.map { textOf(it.title) } + liked.map(::textOf) + disliked.map(::textOf) + listOfNotNull(queryText)
        val vecs = TextVectors.embed(texts)
        val cand = vecs.copyOfRange(0, pool.size)
        val likedV = vecs.copyOfRange(pool.size, pool.size + liked.size)
        val dislikedV = vecs.copyOfRange(pool.size + liked.size, pool.size + liked.size + disliked.size)

        val dim = cand.first().size
        val profile = FloatArray(dim)
        likedV.forEach { v -> for (i in 0 until dim) profile[i] += v[i] }
        dislikedV.forEach { v -> for (i in 0 until dim) profile[i] -= 0.5f * v[i] }
        val hasProfile = likedV.isNotEmpty()
        if (hasProfile) TextVectors.normalize(profile)
        val nSeeds = maxOf(liked.size, 1)
        val year = currentYear()

        val score = DoubleArray(pool.size) { i ->
            val c = pool[i]
            val t = c.title
            val tasteSim = if (hasProfile) TextVectors.dot(cand[i], profile) else 0.0
            val votes = minOf(c.seedVotes.values.sum() / nSeeds, 1.0)
            val genre = if (t.genres.any { it in Genres.displayNames(taste.lovedGenres, t.mediaType) }) 1.0 else 0.0
            val recency = when {
                (t.year ?: 0) >= year - 1 -> 1.0
                (t.year ?: 0) >= year - 3 -> 0.6
                (t.year ?: 0) >= year - 10 -> 0.25
                else -> 0.0
            }
            val mix = when {
                taste.mix == Mix.MOVIES && t.mediaType == MediaType.MOVIE -> 0.05
                taste.mix == Mix.SERIES && t.mediaType == MediaType.TV -> 0.05
                else -> 0.0
            }
            var s = W_CONTENT * tasteSim + W_VOTES * votes + W_GENRE * genre + W_QUALITY * quality(t) + W_RECENCY * recency + mix
            if (queryText != null) s = (1 - queryWeight) * s + queryWeight * TextVectors.dot(cand[i], vecs.last())
            s
        }
        val order = score.indices.sortedByDescending { score[it] }.take(maxOf(limit * 3, 30))
        val picked = mmr(order, DoubleArray(order.size) { score[order[it]] }, cand, limit, lam = 0.75)
        return picked.map { explain(pool[it], score[it], cand[it], liked, likedV, taste, watchedOnly) }
    }

    /** Every reason points at a real title the person rated, or at the genre or request facets it matched. */
    private fun explain(
        c: Candidate,
        score: Double,
        vec: FloatArray,
        liked: List<TitleSummary>,
        likedV: Array<FloatArray>,
        taste: TasteProfile,
        watchedOnly: Boolean,
    ): RecItem {
        val sims = DoubleArray(liked.size) { TextVectors.dot(likedV[it], vec) }
        val byKey = liked.withIndex().associate { (i, t) -> t.key to i }
        val voters = c.seedVotes.keys.mapNotNull { byKey[it] }
        var because: TitleSummary? = null
        var reason: String
        val lovedGenre = c.title.genres.firstOrNull { it in Genres.displayNames(taste.lovedGenres, c.title.mediaType) }
        if (voters.isNotEmpty()) {
            because = liked[voters.maxBy { sims[it] }]
            reason = if (watchedOnly) "Because you watched ${because.title}" else "Fans of ${because.title} also loved this"
        } else if (liked.isNotEmpty() && sims.max() > 0.05) {
            because = liked[sims.indices.maxBy { sims[it] }]
            val shared = c.title.genres.filter { it in because.genres }.take(2)
            reason = (if (watchedOnly) "Because you watched " else "Because you liked ") + because.title +
                (if (shared.isNotEmpty()) " · " + shared.joinToString(" & ") else "")
        } else if (lovedGenre != null && c.facets.isEmpty()) {
            reason = "Matches your love of $lovedGenre"
        } else {
            reason = c.facets.joinToString(" · ").ifEmpty { "Highly rated and trending" }
        }
        if (c.facets.isNotEmpty() && because != null) reason = c.facets.take(2).joinToString(" · ") + " · like ${because.title}"
        val ref = because?.let { TitleRef(it.id, it.mediaType, it.title) }
        return RecItem(title = c.title, score = Math.round(score * 10000) / 10000.0, reason = reason, because = ref)
    }

    /** Recognisable titles for the "what have you watched" grid, including popular Hindi titles. */
    suspend fun onboarding(): List<TitleSummary> = coroutineScope {
        val calls = listOf(
            async { tmdb.popular(MediaType.MOVIE, 1) }, async { tmdb.popular(MediaType.MOVIE, 2) },
            async { tmdb.popular(MediaType.TV, 1) }, async { tmdb.popular(MediaType.TV, 2) },
            async { tmdb.topRated(MediaType.MOVIE) }, async { tmdb.topRated(MediaType.TV) },
            async { tmdb.trending(MediaType.MOVIE) }, async { tmdb.trending(MediaType.TV) },
            async { tmdb.discover(MediaType.MOVIE, mapOf("with_original_language" to "hi", "sort_by" to "vote_count.desc")) },
            async { tmdb.discover(MediaType.TV, mapOf("with_original_language" to "hi", "sort_by" to "vote_count.desc")) },
        )
        val pool = LinkedHashMap<TitleKey, TitleSummary>()
        for (t in calls.awaitAll().flatten()) {
            val minVotes = if (t.originalLanguage == "en") 300 else 60
            if (t.posterPath != null && t.voteCount >= minVotes) pool.putIfAbsent(t.key, t)
        }
        // Best-known first, alternating films and series so both show up near the top.
        val movies = pool.values.filter { it.mediaType == MediaType.MOVIE }.sortedByDescending { it.voteCount }
        val shows = pool.values.filter { it.mediaType == MediaType.TV }.sortedByDescending { it.voteCount }
        val out = ArrayList<TitleSummary>()
        for (i in 0 until maxOf(movies.size, shows.size)) {
            movies.getOrNull(i)?.let(out::add)
            shows.getOrNull(i)?.let(out::add)
        }
        out.take(90)
    }

    suspend fun search(query: String): List<TitleSummary> =
        tmdb.search(query).filter { it.posterPath != null }.take(30)

    suspend fun ask(
        intent: QueryIntent,
        query: String,
        interactions: List<Interaction>,
        taste: TasteProfile,
        limit: Int,
    ): Pair<List<RecItem>, List<TitleRef>> = coroutineScope {
        val kinds = when (intent.mediaType) {
            "movie" -> listOf(MediaType.MOVIE)
            "tv" -> listOf(MediaType.TV)
            else -> listOf(MediaType.MOVIE, MediaType.TV)
        }
        val refs = intent.referenceTitles.map { name -> async { tmdb.search(name).firstOrNull() } }.awaitAll().filterNotNull()
        val keywordIds = intent.keywords.map { w -> async { tmdb.keywordId(w) } }.awaitAll().filterNotNull()

        val candidates = LinkedHashMap<TitleKey, Candidate>()
        val refLists = refs.map { r -> async { r to tmdb.recommendations(r.mediaType, r.id) } }
        val discoverLists = kinds.map { k -> async { discover(k, intent, keywordIds) } }
        for ((ref, recs) in refLists.awaitAll()) {
            recs.forEachIndexed { pos, t ->
                val c = candidates.getOrPut(t.key) { Candidate(t) }
                c.seedVotes[ref.key] = 1.0 - pos.toDouble() / maxOf(recs.size, 1)
            }
        }
        for (titles in discoverLists.awaitAll()) for (t in titles) candidates.getOrPut(t.key) { Candidate(t) }

        val excluded = interactions.filter { it.signal != Signal.LIKE }.map { TitleKey(it.id, it.mediaType) }.toSet() + refs.map { it.key }
        // An explicit request overrides freshness and language, but avoided genres stay avoided.
        val askTaste = TasteProfile(avoidedGenres = taste.avoidedGenres)
        val pool = candidates.filter { (k, c) ->
            k !in excluded && k.mediaType in kinds && matches(c.title, intent) && passes(c.title, askTaste)
        }.values.toList()
        pool.forEach { it.facets = facets(it.title, intent) }

        val likedKeys = interactions.filter { it.signal == Signal.LIKE }.takeLast(SEEDS).map { TitleKey(it.id, it.mediaType) }
        val liked = refs + summaries(likedKeys)
        val queryText = (listOf(query) + intent.keywords + intent.genres + refs.map(::textOf)).joinToString(" ")
        val items = rank(pool, liked, emptyList(), TasteProfile(lovedGenres = intent.genres), limit, queryText = queryText, queryWeight = 0.5)
        items to refs.map { TitleRef(it.id, it.mediaType, it.title) }
    }

    private suspend fun discover(type: MediaType, intent: QueryIntent, keywordIds: List<Int>): List<TitleSummary> {
        if (intent.genres.isNotEmpty() && Genres.idsFor(intent.genres, type).isEmpty()) return emptyList()
        val dateField = if (type == MediaType.MOVIE) "primary_release_date" else "first_air_date"
        val genres = Genres.idsFor(intent.genres, type).joinToString(",").ifEmpty { null }
        val params = mapOf(
            "sort_by" to "popularity.desc",
            "vote_count.gte" to "80",
            "with_genres" to genres,
            "without_genres" to Genres.idsFor(intent.excludeGenres, type).joinToString(",").ifEmpty { null },
            "with_keywords" to keywordIds.joinToString("|").ifEmpty { null },
            "vote_average.gte" to intent.minRating?.toString(),
            "with_original_language" to intent.language,
            "$dateField.gte" to intent.yearMin?.let { "$it-01-01" },
            "$dateField.lte" to intent.yearMax?.let { "$it-12-31" },
        )
        var results = tmdb.discover(type, params)
        if (results.size < 10 && params["with_keywords"] != null) results = results + tmdb.discover(type, params + ("with_keywords" to null))
        if (results.size < 10 && genres?.contains(",") == true) results = results + tmdb.discover(type, params + ("with_genres" to genres.replace(",", "|")))
        return results
    }

    private suspend fun summaries(keys: List<TitleKey>): List<TitleSummary> = coroutineScope {
        keys.map { k -> async { tmdb.summary(k.mediaType, k.id) } }.awaitAll().filterNotNull()
    }

    companion object {
        const val SEEDS = 8
        const val W_CONTENT = 0.35
        const val W_VOTES = 0.30
        const val W_GENRE = 0.15
        const val W_QUALITY = 0.10
        const val W_RECENCY = 0.10

        /** Bayesian-averaged rating in [0, 1] so a 9.0 from 12 votes does not beat an 8.4 from 20,000. */
        fun quality(t: TitleSummary): Double {
            val prior = 6.5
            val weight = 200
            return (t.voteAverage * t.voteCount + prior * weight) / (t.voteCount + weight) / 10.0
        }

        fun textOf(t: TitleSummary): String {
            val g = t.genres.joinToString(" ") { it.replace(" ", "") }
            return "$g $g ${t.overview}"
        }

        /** Hard constraints of a typed request, enforced on every candidate, including "like X" expansions. */
        fun matches(t: TitleSummary, intent: QueryIntent): Boolean {
            intent.yearMin?.let { if (t.year == null || t.year < it) return false }
            intent.yearMax?.let { if (t.year == null || t.year > it) return false }
            return t.genres.none { it in Genres.bannedNames(intent.excludeGenres, t.mediaType) }
        }

        fun facets(t: TitleSummary, intent: QueryIntent): List<String> {
            val wanted = Genres.displayNames(intent.genres, t.mediaType)
            val out = t.genres.filter { it in wanted }.take(2).toMutableList()
            val min = intent.yearMin
            val max = intent.yearMax
            if (min != null && max != null && max - min == 9 && t.year != null) out += "${min}s"
            return out
        }
    }
}
