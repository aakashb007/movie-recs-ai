package com.reelwise.app.engine

import kotlin.math.ln
import kotlin.math.sqrt

/**
 * TF-IDF with sublinear term frequency and smoothed idf, rows L2-normalised; the same recipe
 * as scikit-learn's TfidfVectorizer(stop_words="english", sublinear_tf=True) used by the backend.
 * Fitted per request on a few hundred short texts, so dense rows are fine.
 */
object TextVectors {
    private val TOKEN = Regex("""\b\w\w+\b""")

    fun embed(texts: List<String>): Array<FloatArray> {
        val docs = texts.map { text -> TOKEN.findAll(text.lowercase()).map { it.value }.filter { it !in STOP_WORDS }.toList() }
        val vocab = HashMap<String, Int>()
        val df = ArrayList<Int>()
        for (doc in docs) {
            for (term in doc.toSet()) {
                val idx = vocab.getOrPut(term) { df.add(0); df.size - 1 }
                df[idx] = df[idx] + 1
            }
        }
        val n = docs.size
        if (vocab.isEmpty()) return Array(n) { FloatArray(1) }
        val idf = DoubleArray(df.size) { ln((1.0 + n) / (1.0 + df[it])) + 1.0 }
        return Array(n) { row ->
            val v = FloatArray(vocab.size)
            for ((term, count) in docs[row].groupingBy { it }.eachCount()) {
                val idx = vocab.getValue(term)
                v[idx] = ((1.0 + ln(count.toDouble())) * idf[idx]).toFloat()
            }
            normalize(v)
        }
    }

    fun normalize(v: FloatArray): FloatArray {
        var sum = 0.0
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm > 0) for (i in v.indices) v[i] = (v[i] / norm).toFloat()
        return v
    }

    fun dot(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i] * b[i]
        return s
    }

    private val STOP_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "also", "am", "an", "and", "any", "are", "as", "at",
        "be", "because", "been", "before", "being", "below", "between", "both", "but", "by", "can", "could", "did",
        "do", "does", "doing", "down", "during", "each", "even", "ever", "every", "few", "for", "from", "further",
        "get", "gets", "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him", "himself",
        "his", "how", "however", "if", "in", "into", "is", "it", "its", "itself", "just", "last", "least", "less",
        "made", "many", "may", "me", "might", "more", "most", "much", "must", "my", "myself", "never", "new", "no",
        "nor", "not", "now", "of", "off", "often", "on", "once", "one", "only", "or", "other", "our", "ours",
        "ourselves", "out", "over", "own", "per", "rather", "same", "she", "should", "since", "so", "some", "still",
        "such", "than", "that", "the", "their", "theirs", "them", "themselves", "then", "there", "these", "they",
        "this", "those", "though", "through", "to", "too", "two", "under", "until", "up", "upon", "us", "very",
        "was", "we", "well", "were", "what", "when", "where", "whether", "which", "while", "who", "whom", "whose",
        "why", "will", "with", "within", "without", "would", "yet", "you", "your", "yours", "yourself", "yourselves",
    )
}

/**
 * Maximal Marginal Relevance: greedily trade relevance against similarity to items already
 * picked, so a list isn't ten near-identical titles. Port of recsys/models.py mmr().
 * candidates index into vectors; relevance is aligned with candidates.
 */
fun mmr(candidates: List<Int>, relevance: DoubleArray, vectors: Array<FloatArray>, k: Int, lam: Double = 0.8): List<Int> {
    if (candidates.isEmpty()) return emptyList()
    val min = relevance.min()
    val span = relevance.max() - min
    val rel = DoubleArray(relevance.size) { if (span > 0) (relevance[it] - min) / span else 1.0 }
    val maxSim = DoubleArray(candidates.size)
    val available = BooleanArray(candidates.size) { true }
    val picked = ArrayList<Int>()
    repeat(minOf(k, candidates.size)) {
        var best = -1
        var bestGain = Double.NEGATIVE_INFINITY
        for (i in candidates.indices) {
            if (!available[i]) continue
            val gain = lam * rel[i] - (1 - lam) * maxSim[i]
            if (gain > bestGain) { bestGain = gain; best = i }
        }
        picked += best
        available[best] = false
        val chosen = vectors[candidates[best]]
        for (i in candidates.indices) maxSim[i] = maxOf(maxSim[i], TextVectors.dot(vectors[candidates[i]], chosen))
    }
    return picked.map { candidates[it] }
}
