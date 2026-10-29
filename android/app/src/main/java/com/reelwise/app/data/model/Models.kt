package com.reelwise.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Wire models mirroring backend/app/schemas.py. */

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
data class RecommendationRequest(
    val interactions: List<Interaction>,
    @SerialName("media_type") val mediaType: String,
    val region: String,
    val limit: Int = 40,
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
)

@Serializable
data class TitleDetail(
    val summary: TitleSummary,
    val tagline: String = "",
    val runtime: Int? = null,
    val seasons: Int? = null,
    val status: String = "",
    val cast: List<CastMember> = emptyList(),
    @SerialName("trailer_youtube_key") val trailerYoutubeKey: String? = null,
    val providers: Providers = Providers(),
    val similar: List<TitleSummary> = emptyList(),
)

@Serializable
data class OnboardingResponse(val items: List<TitleSummary>)

enum class FeedFilter(val wire: String) { ALL("all"), MOVIES("movie"), SERIES("tv") }
