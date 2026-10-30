package com.reelwise.app.engine

import com.reelwise.app.data.model.LearnedTaste
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.TitleSummary
import kotlin.math.abs
import kotlin.math.pow

/** What the model knows about one title: enough to learn from without calling TMDB again. */
data class TitleFeatures(
    val id: Int,
    val mediaType: MediaType,
    val genres: List<String>,
    val language: String?,
    val year: Int?,
) {
    companion object {
        fun of(t: TitleSummary) = TitleFeatures(t.id, t.mediaType, t.genres, t.originalLanguage, t.year)
    }
}

/**
 * One thing the person did with a title, as a signed weight (see [TasteLearner] for the scale).
 * [genreOnly] evidence is about the kind of title (its genres and film vs series), so it barely
 * touches the language and decade.
 */
data class Evidence(val title: TitleFeatures, val weight: Double, val at: Long, val genreOnly: Boolean = false)

/**
 * Learns the person's taste from what they do, not only from the quiz. Each action is evidence
 * for or against the title's genres, language, decade and type; recent evidence counts more
 * (half-life [HALF_LIFE_DAYS]), and a feature seen only once or twice is shrunk towards neutral
 * so one rating doesn't swing the feed.
 */
object TasteLearner {
    /** Seen it, and wants more titles like it. */
    const val MORE = 1.5

    /** Loved it. */
    const val LIKE = 1.0

    /** My list: not watched yet, wants to. */
    const val WATCHLIST = 0.6

    /** Already watched; says little about liking it, mostly "don't suggest it again". */
    const val SEEN = 0.1
    const val OPENED = 0.15
    const val SKIPPED = -0.1

    /** "Not interested": may be good, but not this genre or type of title ([Evidence.genreOnly]). */
    const val NOT_INTERESTED = -1.0

    /** "Not for me": twice as strong as not interested. */
    const val DISLIKE = -2.0

    /** How much genre-only evidence counts for language and decade. */
    private const val SIDE_FEATURES = 0.25

    const val HALF_LIFE_DAYS = 120.0
    private const val PRIOR = 2.0
    private const val KEEP = 12
    private const val DAY_MS = 86_400_000.0

    fun key(mediaType: MediaType, id: Int) = "${mediaType.name.lowercase()}:$id"

    /**
     * @param skips how often each title (by [key]) was swiped past without a reaction.
     * @param acted titles with an explicit reaction, which are never "tired".
     */
    fun learn(evidence: List<Evidence>, skips: Map<String, Int>, acted: Set<String>, now: Long): LearnedTaste {
        val genres = Tally<String>()
        val languages = Tally<String>()
        val decades = Tally<Int>()
        val types = Tally<MediaType>()
        for (e in evidence) {
            val ageDays = ((now - e.at).coerceAtLeast(0)) / DAY_MS
            val w = e.weight * 0.5.pow(ageDays / HALF_LIFE_DAYS)
            // Spread a title's weight over its genres, so a five-genre title doesn't count five times.
            val perGenre = w / maxOf(e.title.genres.size, 1).toDouble().pow(0.5)
            e.title.genres.forEach { genres.add(it, perGenre) }
            val side = if (e.genreOnly) w * SIDE_FEATURES else w
            e.title.language?.let { languages.add(it, side) }
            e.title.year?.let { decades.add(it / 10 * 10, side) }
            types.add(e.title.mediaType, w)
        }
        val tired = skips.filter { (k, n) -> n >= TIRED_AFTER && k !in acted }.keys
        return LearnedTaste(
            genres = genres.affinities(),
            languages = languages.affinities(),
            decades = decades.affinities(),
            types = types.affinities(),
            tired = tired,
            evidence = evidence.map { key(it.title.mediaType, it.title.id) }.distinct().size,
        )
    }

    /** How well a title fits the learned taste, in [-1, 1]; 0 when nothing relevant was learned. */
    fun affinity(t: TitleSummary, learned: LearnedTaste): Double {
        val parts = mutableListOf<Pair<Double, Double>>() // (value, weight)
        t.genres.mapNotNull { learned.genres[it] }.takeIf { it.isNotEmpty() }?.let { parts += it.average() to 0.5 }
        t.originalLanguage?.let { learned.languages[it] }?.let { parts += it to 0.2 }
        t.year?.let { learned.decades[it / 10 * 10] }?.let { parts += it to 0.2 }
        learned.types[t.mediaType]?.let { parts += it to 0.1 }
        if (parts.isEmpty()) return 0.0
        return parts.sumOf { it.first * it.second } / parts.sumOf { it.second }
    }

    const val TIRED_AFTER = 2

    private class Tally<K> {
        private val sum = HashMap<K, Double>()
        private val mass = HashMap<K, Double>()

        fun add(k: K, w: Double) {
            sum[k] = (sum[k] ?: 0.0) + w
            mass[k] = (mass[k] ?: 0.0) + abs(w)
        }

        /** Signed mean, shrunk towards 0 by a prior of [PRIOR] neutral observations; strongest [KEEP] kept. */
        fun affinities(): Map<K, Double> = sum.keys
            .associateWith { k -> sum.getValue(k) / (mass.getValue(k) + PRIOR) }
            .entries.sortedByDescending { abs(it.value) }.take(KEEP)
            .associate { (k, v) -> k to Math.round(v * 1000) / 1000.0 }
    }
}
