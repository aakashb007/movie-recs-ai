package com.reelwise.app

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reelwise.app.data.DefaultRepository
import com.reelwise.app.data.local.InteractionEntity
import com.reelwise.app.data.local.KeyStore
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
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.remote.ReelwiseApi
import com.reelwise.app.engine.FakeServices
import com.reelwise.app.engine.TmdbClient
import java.io.File
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The taste model fed by the real repository and Room tables. */
@RunWith(AndroidJUnit4::class)
class LearningTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, ReelwiseDatabase::class.java).allowMainThreadQueries().build()
    private val stamp = System.nanoTime()
    private val settingsFile = File(context.filesDir, "learn-settings-$stamp.preferences_pb")
    private val secretsFile = File(context.filesDir, "learn-secrets-$stamp.preferences_pb")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val api = Api()
    private val keyStore = KeyStore(PreferenceDataStoreFactory.create { secretsFile })
    private val day = 86_400_000L
    private var now = 1_000 * day
    private val repo = DefaultRepository(
        api, db, SettingsStore(PreferenceDataStoreFactory.create { settingsFile }, json), keyStore,
        TmdbClient(FakeServices().client(), keyStore, json), json,
    ) { now++ }

    @After
    fun tearDown() {
        db.close()
        settingsFile.delete()
        secretsFile.delete()
    }

    private fun t(id: Int, genre: String) = title(id, genre = genre)

    @Test
    fun ratingsAndMyListTeachGenresThatAreSentWithTheFeedRequest() = runTest {
        repo.rate(t(1, "Crime"), Signal.LIKE)
        repo.rate(t(2, "Crime"), Signal.LIKE)
        repo.toggleWatchlist(t(3, "Crime"))
        repo.rate(t(4, "Horror"), Signal.DISLIKE)
        repo.rate(t(5, "Horror"), Signal.DISMISS)

        val learned = repo.learnedTaste()
        assertTrue(learned.genres.getValue("Crime") > 0.4)
        assertTrue(learned.genres.getValue("Horror") < -0.3)
        assertEquals(5, learned.evidence)

        repo.refreshFeed(FeedFilter.ALL)
        assertEquals(learned.genres, api.lastRequest!!.learned!!.genres)
    }

    @Test
    fun noEvidenceSendsNoModel() = runTest {
        repo.refreshFeed(FeedFilter.ALL)
        assertNull(api.lastRequest!!.learned)
    }

    @Test
    fun openingAndSwipingAreQuietSignals() = runTest {
        repo.noteOpened(t(1, "Animation"))
        repo.noteSkipped(t(2, "Western"))
        repo.noteSkipped(t(2, "Western"))
        repo.noteSkipped(t(3, "Western"))

        val learned = repo.learnedTaste()
        assertTrue(learned.genres.getValue("Animation") > 0)
        assertTrue(learned.genres.getValue("Western") < 0)
        assertEquals(setOf("movie:2"), learned.tired)

        repo.forgetQuietSignals()
        assertEquals(0, repo.learnedTaste().evidence)
    }

    @Test
    fun ratingsFromBeforeTheModelAreBackfilledFromTmdb() = runTest {
        // An old rating: no features were stored with it.
        db.interactions().upsert(InteractionEntity(9, MediaType.MOVIE, Signal.LIKE, "Old", null, now))
        assertEquals(0, repo.learnedTaste().evidence)

        repo.refreshFeed(FeedFilter.ALL)
        assertEquals(1, repo.learnedTaste().evidence)
        assertEquals(1, api.lastRequest!!.learned!!.evidence)
    }

    @Test
    fun upgradingFromVersionOneKeepsRatingsAndAddsTheLearningTables() {
        val name = "upgrade-$stamp.db"
        context.deleteDatabase(name)
        // Build a current database, then turn it back into version 1: no learning tables.
        Room.databaseBuilder(context, ReelwiseDatabase::class.java, name).allowMainThreadQueries().build().apply {
            runBlocking { interactions().upsert(InteractionEntity(1, MediaType.MOVIE, Signal.LIKE, "Kept", null, 5)) }
            close()
        }
        SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READWRITE).apply {
            execSQL("DROP TABLE title_features")
            execSQL("DROP TABLE events")
            version = 1
            close()
        }

        val upgraded = Room.databaseBuilder(context, ReelwiseDatabase::class.java, name)
            .addMigrations(ReelwiseDatabase.MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        runBlocking {
            assertEquals(listOf("Kept"), upgraded.interactions().getAll().map { it.title })
            assertTrue(upgraded.learning().allFeatures().isEmpty()) // Room validated the new tables
        }
        upgraded.close()
        context.deleteDatabase(name)
    }

    private class Api : ReelwiseApi {
        var lastRequest: RecommendationRequest? = null

        override suspend fun onboarding() = OnboardingResponse(emptyList())

        override suspend fun recommendations(body: RecommendationRequest): RecommendationResponse {
            lastRequest = body
            return RecommendationResponse(emptyList(), "personalized")
        }

        override suspend fun ask(body: AskRequest) = AskResponse(QueryIntent(), emptyList(), emptyList(), "rules")

        override suspend fun title(type: String, id: Int, region: String) = TitleDetail(summary = title(id, genre = "Mystery"))

        override suspend fun search(query: String): List<TitleSummary> = emptyList()

        override suspend fun person(id: Int) = com.reelwise.app.data.model.PersonDetail(id, "Person $id")
    }
}
