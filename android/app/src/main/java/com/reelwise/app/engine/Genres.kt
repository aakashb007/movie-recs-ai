package com.reelwise.app.engine

import com.reelwise.app.data.model.MediaType

/**
 * TMDB genre ids. Movies and TV use different ids for some genres, so the LLM, the quiz and
 * the ranking speak in canonical names and this object translates. Port of backend/app/genres.py.
 */
object Genres {
    val MOVIE = mapOf(
        28 to "Action", 12 to "Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 14 to "Fantasy", 36 to "History", 27 to "Horror", 10402 to "Music",
        9648 to "Mystery", 10749 to "Romance", 878 to "Science Fiction", 10770 to "TV Movie", 53 to "Thriller",
        10752 to "War", 37 to "Western",
    )
    val TV = mapOf(
        10759 to "Action & Adventure", 16 to "Animation", 35 to "Comedy", 80 to "Crime", 99 to "Documentary",
        18 to "Drama", 10751 to "Family", 10762 to "Kids", 9648 to "Mystery", 10763 to "News", 10764 to "Reality",
        10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk", 10768 to "War & Politics", 37 to "Western",
    )

    /** canonical name -> (movie ids, tv ids) */
    val CANONICAL: Map<String, Pair<List<Int>, List<Int>>> = linkedMapOf(
        "action" to (listOf(28) to listOf(10759)),
        "adventure" to (listOf(12) to listOf(10759)),
        "animation" to (listOf(16) to listOf(16)),
        "comedy" to (listOf(35) to listOf(35)),
        "crime" to (listOf(80) to listOf(80)),
        "documentary" to (listOf(99) to listOf(99)),
        "drama" to (listOf(18) to listOf(18)),
        "family" to (listOf(10751) to listOf(10751)),
        "fantasy" to (listOf(14) to listOf(10765)),
        "history" to (listOf(36) to emptyList()),
        "horror" to (listOf(27) to emptyList()),
        "music" to (listOf(10402) to emptyList()),
        "mystery" to (listOf(9648) to listOf(9648)),
        "romance" to (listOf(10749) to emptyList()),
        "science fiction" to (listOf(878) to listOf(10765)),
        "thriller" to (listOf(53) to emptyList()),
        "war" to (listOf(10752) to listOf(10768)),
        "western" to (listOf(37) to listOf(37)),
        "kids" to (emptyList<Int>() to listOf(10762)),
        "reality" to (emptyList<Int>() to listOf(10764)),
    )

    val SYNONYMS = mapOf(
        "sci-fi" to "science fiction", "scifi" to "science fiction", "sci fi" to "science fiction",
        "space" to "science fiction", "funny" to "comedy", "comedies" to "comedy", "laugh" to "comedy",
        "scary" to "horror", "spooky" to "horror", "romantic" to "romance", "rom-com" to "romance",
        "love story" to "romance", "animated" to "animation", "anime" to "animation", "cartoon" to "animation",
        "documentaries" to "documentary", "docs" to "documentary", "thrillers" to "thriller",
        "suspense" to "thriller", "detective" to "mystery", "whodunit" to "mystery", "heist" to "crime",
        "gangster" to "crime", "magic" to "fantasy", "historical" to "history", "musical" to "music",
        "cowboy" to "western", "children" to "kids",
    )

    fun table(mediaType: MediaType): Map<Int, String> = if (mediaType == MediaType.MOVIE) MOVIE else TV

    fun names(ids: List<Int>, mediaType: MediaType): List<String> = ids.mapNotNull { table(mediaType)[it] }

    fun idsFor(canonical: List<String>, mediaType: MediaType): List<Int> {
        val out = mutableListOf<Int>()
        for (name in canonical) {
            val pair = CANONICAL[name] ?: continue
            for (id in if (mediaType == MediaType.MOVIE) pair.first else pair.second) if (id !in out) out += id
        }
        return out
    }

    fun canonicalize(name: String): String? {
        val key = name.trim().lowercase().let { SYNONYMS[it] ?: it }
        return key.takeIf { it in CANONICAL }
    }

    /** TMDB display names a title must not carry when these canonical genres are avoided. */
    fun bannedNames(avoided: List<String>, mediaType: MediaType): Set<String> {
        val set = avoided.toSet()
        // TV's combined genres cover two canonical ones; only ban them when both are avoided.
        return idsFor(avoided, mediaType)
            .filterNot { it == 10765 && !set.containsAll(listOf("science fiction", "fantasy")) }
            .filterNot { it == 10759 && !set.containsAll(listOf("action", "adventure")) }
            .mapNotNull { table(mediaType)[it] }
            .toSet()
    }

    /** TMDB display names that count as a match for these canonical genres. */
    fun displayNames(canonical: List<String>, mediaType: MediaType): Set<String> =
        idsFor(canonical, mediaType).mapNotNull { table(mediaType)[it] }.toSet()
}
