package com.reelwise.app.data.remote

import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.OnboardingResponse
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.engine.QueryParser
import com.reelwise.app.engine.Recommender
import com.reelwise.app.engine.TmdbClient

/** The recommendation engine, on the phone: no server, just TMDB and (optionally) Groq. */
class LocalReelwiseApi(
    private val tmdb: TmdbClient,
    private val recommender: Recommender,
    private val parser: QueryParser,
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

    override suspend fun title(type: String, id: Int, region: String): TitleDetail =
        tmdb.details(if (type == MediaType.TV.wire) MediaType.TV else MediaType.MOVIE, id, region)

    override suspend fun search(query: String): List<TitleSummary> = recommender.search(query)
}
