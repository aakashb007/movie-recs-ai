package com.reelwise.app.data.remote

import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.OnboardingResponse
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.TitleDetail
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** The Reelwise backend. It holds the TMDB and Groq keys; the app never sees them. */
interface ReelwiseApi {
    @GET("v1/onboarding")
    suspend fun onboarding(): OnboardingResponse

    @POST("v1/recommendations")
    suspend fun recommendations(@Body body: RecommendationRequest): RecommendationResponse

    @POST("v1/ask")
    suspend fun ask(@Body body: AskRequest): AskResponse

    @GET("v1/titles/{type}/{id}")
    suspend fun title(
        @Path("type") type: String,
        @Path("id") id: Int,
        @Query("region") region: String,
    ): TitleDetail
}
