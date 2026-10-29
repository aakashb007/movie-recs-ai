package com.reelwise.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reelwise.app.data.DefaultRepository
import com.reelwise.app.data.local.ReelwiseDatabase
import com.reelwise.app.data.local.SettingsStore
import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.OnboardingResponse
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.remote.ReelwiseApi
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The data layer end to end on a real (in-memory) Room database: ratings flow into the next request. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, ReelwiseDatabase::class.java).allowMainThreadQueries().build()
    private val storeFile = File(context.filesDir, "test-${System.nanoTime()}.preferences_pb")
    private val api = RecordingApi()
    private val repo = DefaultRepository(
        api,
        db,
        SettingsStore(PreferenceDataStoreFactory.create { storeFile }),
        Json { ignoreUnknownKeys = true; encodeDefaults = true },
    ) { 42L }

    @After
    fun tearDown() {
        db.close()
        storeFile.delete()
    }

    @Test
    fun ratingsAndRegionAreSentAndTheFeedIsCached() = runTest {
        repo.setRegion("GB")
        repo.rate(title(1), Signal.LIKE)
        repo.rate(title(2, MediaType.TV), Signal.DISMISS)
        repo.rate(title(3), Signal.SEEN)
        repo.rate(title(3), null) // cleared

        assertTrue(repo.refreshFeed(FeedFilter.SERIES).isSuccess)

        val sent = api.lastRequest!!
        assertEquals("GB", sent.region)
        assertEquals("tv", sent.mediaType)
        assertEquals(listOf(1 to Signal.LIKE, 2 to Signal.DISMISS), sent.interactions.map { it.id to it.signal })
        val cached = repo.feed(FeedFilter.SERIES).first()
        assertNotNull(cached)
        assertEquals(listOf(7), cached!!.response.items.map { it.title.id })
        assertEquals(42L, cached.fetchedAt)
    }

    @Test
    fun networkFailureIsAResultNotACrash() = runTest {
        api.fail = true
        assertTrue(repo.refreshFeed(FeedFilter.ALL).isFailure)
        assertEquals(null, repo.feed(FeedFilter.ALL).first())
    }

    @Test
    fun watchlistToggles() = runTest {
        repo.toggleWatchlist(title(5))
        assertTrue(repo.isInWatchlist(MediaType.MOVIE, 5).first())
        assertEquals(false, repo.isInWatchlist(MediaType.TV, 5).first())
        repo.toggleWatchlist(title(5))
        assertEquals(false, repo.isInWatchlist(MediaType.MOVIE, 5).first())
    }

    private class RecordingApi : ReelwiseApi {
        var lastRequest: RecommendationRequest? = null
        var fail = false

        override suspend fun onboarding() = OnboardingResponse(emptyList())

        override suspend fun recommendations(body: RecommendationRequest): RecommendationResponse {
            if (fail) throw IOException("offline")
            lastRequest = body
            return RecommendationResponse(listOf(rec(7, because = 1)), "personalized")
        }

        override suspend fun ask(body: AskRequest) = AskResponse(QueryIntent(), emptyList(), emptyList(), "rules")

        override suspend fun title(type: String, id: Int, region: String) = TitleDetail(summary = title(id))
    }
}
