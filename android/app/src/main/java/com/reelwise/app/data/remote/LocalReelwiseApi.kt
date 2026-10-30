package com.reelwise.app.data.remote

import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.OnboardingResponse
import com.reelwise.app.data.model.PersonDetail
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.engine.ImdbLookup
import com.reelwise.app.engine.QueryParser
import com.reelwise.app.engine.Recommender
import com.reelwise.app.engine.TmdbClient

/** The recommendation engine, on the phone: no server, just TMDB and (optionally) Groq. */
class LocalReelwiseApi(
    private val tmdb: TmdbClient,
    private val recommender: Recommender,
    private val parser: QueryParser,
    private val imdb: ImdbLookup? = null,
) : ReelwiseApi {
    override suspend fun onboarding() = OnboardingResponse(recommender.onboarding())

    override suspend fun recommendations(body: RecommendationRequest): RecommendationResponse {
        val (items, strategy) = recommender.recommend(body.interactions, body.taste, body.mediaType, body.limit)
        return RecommendationResponse(items, strategy)
    }

    override suspend fun ask(body: AskRequest): AskResponse {
        val (intent, parsedBy) = parser.parse(body.query)
        val (items, refs) = recommender.ask(intent, body.query, body.interactions, body.taste, body.limit)
        return AskResponse(intent, refs, items, parsedBy)
    }

    override suspend fun title(type: String, id: Int, region: String): TitleDetail {
        val mediaType = if (type == MediaType.TV.wire) MediaType.TV else MediaType.MOVIE
        val detail = tmdb.details(mediaType, id, region)
        val score = if (imdb != null && imdb.available()) imdb.score(mediaType, id) else null
        return if (score == null) detail else detail.copy(imdb = score, summary = detail.summary.copy(imdbRating = score.rating))
    }

    override suspend fun search(query: String): List<TitleSummary> = recommender.search(query)

    override suspend fun person(id: Int): PersonDetail = tmdb.person(id)
}
