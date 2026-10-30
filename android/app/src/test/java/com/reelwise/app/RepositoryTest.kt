package com.reelwise.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reelwise.app.data.DefaultRepository
import com.reelwise.app.data.SavedKeys
import com.reelwise.app.data.local.KeyStore
import com.reelwise.app.data.local.ReelwiseDatabase
import com.reelwise.app.data.local.SettingsStore
import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.OnboardingResponse
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.remote.ReelwiseApi
import com.reelwise.app.data.userMessage
import com.reelwise.app.engine.FakeServices
import com.reelwise.app.engine.TmdbClient
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The data layer end to end on a real (in-memory) Room database and real DataStore files. */
@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, ReelwiseDatabase::class.java).allowMainThreadQueries().build()
    private val stamp = System.nanoTime()
    private val settingsFile = File(context.filesDir, "settings-$stamp.preferences_pb")
    private val secretsFile = File(context.filesDir, "secrets-$stamp.preferences_pb")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val api = RecordingApi()
    private val services = FakeServices()
    private val keyStore = KeyStore(PreferenceDataStoreFactory.create { secretsFile })
    private var now = 42L
    private val repo = DefaultRepository(
        api,
        db,
        SettingsStore(PreferenceDataStoreFactory.create { settingsFile }, json),
        keyStore,
        TmdbClient(services.client(), keyStore, json),
        json,
    ) { now++ }

    @After
    fun tearDown() {
        db.close()
        settingsFile.delete()
        secretsFile.delete()
    }

    @Test
    fun ratingsRegionAndTasteAreSentAndTheFeedIsCached() = runTest {
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
    }

    @Test
    fun keysAreCheckedWithTmdbBeforeSavingAndStayOutOfSettings() = runTest {
        services.tmdbStatus = 401
        val rejected = repo.saveKeys(SavedKeys("bad-key"))
        assertTrue(rejected.isFailure)
        assertEquals("Your TMDB key was rejected. Check it in Settings.", rejected.exceptionOrNull()!!.userMessage())
        assertFalse(repo.hasTmdbKey.first())

        services.tmdbStatus = 200
        assertTrue(repo.saveKeys(SavedKeys("  good-key ", "gsk_123", "omdb_1")).isSuccess)
        assertTrue(repo.hasTmdbKey.first())
        assertEquals(SavedKeys("good-key", "gsk_123", "omdb_1"), repo.savedKeys())
        assertEquals("good-key", services.requests.last().url.queryParameter("api_key"))
        // The keys live in their own file, which the backup rules leave out.
        assertFalse(settingsFile.exists() && settingsFile.readText(Charsets.ISO_8859_1).contains("good-key"))
        assertTrue(secretsFile.readText(Charsets.ISO_8859_1).contains("good-key"))
    }

    @Test
    fun theTasteQuizBecomesInteractionsAndAProfile() = runTest {
        val watched = listOf(title(1), title(2), title(3), title(4, MediaType.TV))
        val taste = TasteProfile(lovedGenres = listOf("comedy"), freshness = Freshness.LATEST, languages = listOf("hi"))
        repo.saveTaste(taste, watched, loved = setOf(title(2).key))

        val stored = db.interactions().getAll()
        assertEquals(listOf(1, 3, 4, 2), stored.map { it.id }) // the loved one is most recent
        assertEquals(Signal.LIKE, stored.last().signal)
        assertTrue(stored.dropLast(1).all { it.signal == Signal.SEEN })
        assertTrue(repo.settings.first().onboardingDone)

        repo.refreshFeed(FeedFilter.ALL)
        assertEquals(taste, api.lastRequest!!.taste)
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

        override suspend fun search(query: String): List<TitleSummary> = emptyList()
    }
}
