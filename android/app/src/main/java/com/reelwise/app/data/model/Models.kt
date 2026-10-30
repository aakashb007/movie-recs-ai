package com.reelwise.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * App models. They began as the backend's wire format (backend/app/schemas.py) and keep its
 * snake_case names, which is also the shape cached feeds are stored in on the phone.
 */

@Serializable
enum class MediaType(val wire: String) {
    @SerialName("movie") MOVIE("movie"),
    @SerialName("tv") TV("tv"),
}

@Serializable
enum class Signal {
    @SerialName("like") LIKE,
    @SerialName("dislike") DISLIKE,
    @SerialName("seen") SEEN,
    @SerialName("dismiss") DISMISS,
}

/** A title is identified by its TMDB id *and* type: movie 1399 and show 1399 are different things. */
data class TitleKey(val id: Int, val mediaType: MediaType)

@Serializable
data class TitleSummary(
    val id: Int,
    @SerialName("media_type") val mediaType: MediaType,
    val title: String,
    val year: Int? = null,
    val overview: String = "",
    @SerialName("poster_path") val posterPath: String? = null,
    @SerialName("backdrop_path") val backdropPath: String? = null,
    @SerialName("vote_average") val voteAverage: Double = 0.0,
    @SerialName("vote_count") val voteCount: Int = 0,
    val genres: List<String> = emptyList(),
    @SerialName("original_language") val originalLanguage: String? = null,
    /** IMDb rating via OMDb, filled in only when an OMDb key is saved. */
    @SerialName("imdb_rating") val imdbRating: Double? = null,
) {
    val key: TitleKey get() = TitleKey(id, mediaType)
}

@Serializable
data class TitleRef(
    val id: Int,
    @SerialName("media_type") val mediaType: MediaType,
    val title: String,
)

@Serializable
data class RecItem(
    val title: TitleSummary,
    val score: Double = 0.0,
    val reason: String = "",
    val because: TitleRef? = null,
)

@Serializable
data class Interaction(
    val id: Int,
    @SerialName("media_type") val mediaType: MediaType,
    val signal: Signal,
)

@Serializable
enum class Freshness {
    /** Released in the last 2 years. */
    @SerialName("latest") LATEST,
    /** Released in the last 10 years. */
    @SerialName("recent") RECENT,
    @SerialName("any") ANY,
    /** Old classics and cult films: released before 2000. */
    @SerialName("classics") CLASSICS,
}

@Serializable
enum class Mix {
    @SerialName("both") BOTH,
    @SerialName("movies") MOVIES,
    @SerialName("series") SERIES,
}

/** What the taste quiz learned, beyond the titles themselves (those are interactions). */
@Serializable
data class TasteProfile(
    /** Canonical genre names, as in engine.Genres ("science fiction", "comedy"…). */
    @SerialName("loved_genres") val lovedGenres: List<String> = emptyList(),
    @SerialName("avoided_genres") val avoidedGenres: List<String> = emptyList(),
    val freshness: Freshness = Freshness.ANY,
    val mix: Mix = Mix.BOTH,
    /** ISO 639-1 codes; empty means any language. */
    val languages: List<String> = emptyList(),
    /** Minimum score out of 10: IMDb when an OMDb key is saved, TMDB otherwise. Null means any. */
    @SerialName("min_rating") val minRating: Double? = null,
)

@Serializable
data class RecommendationRequest(
    val interactions: List<Interaction>,
    @SerialName("media_type") val mediaType: String,
    val region: String,
    val limit: Int = 60,
    val taste: TasteProfile = TasteProfile(),
)

@Serializable
data class RecommendationResponse(
    val items: List<RecItem>,
    val strategy: String,
)

@Serializable
data class AskRequest(
    val query: String,
    val interactions: List<Interaction>,
    val region: String,
    val limit: Int = 24,
    val taste: TasteProfile = TasteProfile(),
)

@Serializable
data class QueryIntent(
    @SerialName("media_type") val mediaType: String = "any",
    val genres: List<String> = emptyList(),
    @SerialName("exclude_genres") val excludeGenres: List<String> = emptyList(),
    val keywords: List<String> = emptyList(),
    @SerialName("reference_titles") val referenceTitles: List<String> = emptyList(),
    @SerialName("year_min") val yearMin: Int? = null,
    @SerialName("year_max") val yearMax: Int? = null,
    @SerialName("min_rating") val minRating: Double? = null,
    val language: String? = null,
    val summary: String = "",
)

@Serializable
data class AskResponse(
    val interpretation: QueryIntent,
    val references: List<TitleRef> = emptyList(),
    val items: List<RecItem>,
    val parser: String,
)

@Serializable
data class Provider(
    val name: String,
    @SerialName("logo_path") val logoPath: String? = null,
)

@Serializable
data class Providers(
    val link: String? = null,
    val stream: List<Provider> = emptyList(),
    val free: List<Provider> = emptyList(),
    val rent: List<Provider> = emptyList(),
    val buy: List<Provider> = emptyList(),
) {
    val isEmpty: Boolean get() = stream.isEmpty() && free.isEmpty() && rent.isEmpty() && buy.isEmpty()
}

@Serializable
data class CastMember(
    val name: String,
    val character: String = "",
    @SerialName("profile_path") val profilePath: String? = null,
    /** TMDB person id; opens their filmography. */
    val id: Int? = null,
)

/** A director (films) or creator (series). */
@Serializable
data class CrewMember(
    val id: Int,
    val name: String,
    val job: String,
    @SerialName("profile_path") val profilePath: String? = null,
)

/** One line of a filmography: the title and what the person did on it. */
@Serializable
data class PersonCredit(
    val title: TitleSummary,
    /** "Director", "Creator", or the character played; empty when unknown. */
    val role: String,
    val kind: CreditKind,
)

@Serializable
enum class CreditKind { @SerialName("directed") DIRECTED, @SerialName("created") CREATED, @SerialName("acted") ACTED }

@Serializable
data class PersonDetail(
    val id: Int,
    val name: String,
    val biography: String = "",
    val birthday: String? = null,
    val deathday: String? = null,
    @SerialName("place_of_birth") val placeOfBirth: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    /** "Acting", "Directing", "Writing"… */
    val department: String = "",
    /** Their most widely seen titles, best known first. */
    @SerialName("known_for") val knownFor: List<TitleSummary> = emptyList(),
    /** Every credit, newest first; a title can appear once per kind. */
    val credits: List<PersonCredit> = emptyList(),
)

@Serializable
data class TitleDetail(
    val summary: TitleSummary,
    val tagline: String = "",
    val runtime: Int? = null,
    val seasons: Int? = null,
    val status: String = "",
    val cast: List<CastMember> = emptyList(),
    /** Directors for films, creators (and any episode directors) for series. */
    val crew: List<CrewMember> = emptyList(),
    @SerialName("trailer_youtube_key") val trailerYoutubeKey: String? = null,
    val providers: Providers = Providers(),
    val similar: List<TitleSummary> = emptyList(),
    @SerialName("imdb_id") val imdbId: String? = null,
    val imdb: ImdbScore? = null,
)

/** Scores OMDb reports for an IMDb title. */
@Serializable
data class ImdbScore(
    val rating: Double,
    val votes: Int? = null,
    @SerialName("rotten_tomatoes") val rottenTomatoes: String? = null,
    val metascore: Int? = null,
)

@Serializable
data class OnboardingResponse(val items: List<TitleSummary>)

enum class FeedFilter(val wire: String) {
    ALL("all"),
    MOVIES("movie"),
    SERIES("tv"),
    /** Uplifting comedy, family, animation and romance; never horror, thrillers, war or crime. */
    FEEL_GOOD("feelgood"),
    /** Old classics and cult films, released before 2000. */
    CLASSICS("classics"),
}
