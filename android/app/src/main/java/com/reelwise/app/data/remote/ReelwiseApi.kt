package com.reelwise.app.data.remote

import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.OnboardingResponse
import com.reelwise.app.data.model.PersonDetail
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary

/**
 * Everything the app asks of its recommendation engine. This used to be a Retrofit client for
 * the backend; LocalReelwiseApi now answers it on the phone, calling TMDB and Groq directly.
 */
interface ReelwiseApi {
    suspend fun onboarding(): OnboardingResponse

    suspend fun recommendations(body: RecommendationRequest): RecommendationResponse

    suspend fun ask(body: AskRequest): AskResponse

    suspend fun title(type: String, id: Int, region: String): TitleDetail

    suspend fun search(query: String): List<TitleSummary>

    suspend fun person(id: Int): PersonDetail
}
