package com.reelwise.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.reelwise.app.data.local.InteractionEntity
import com.reelwise.app.data.local.KeyStore
import com.reelwise.app.data.local.WatchlistEntity
import com.reelwise.app.data.local.ReelwiseDatabase
import com.reelwise.app.data.local.SettingsStore
import com.reelwise.app.data.local.ThemeMode
import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.sync.BackupData
import com.reelwise.app.data.sync.BackupInteraction
import com.reelwise.app.data.sync.BackupKeys
import com.reelwise.app.data.sync.BackupSettings
import com.reelwise.app.data.sync.BackupWatch
import com.reelwise.app.data.sync.DriveClient
import com.reelwise.app.data.sync.DriveTokens
import com.reelwise.app.data.sync.GoogleGrant
import com.reelwise.app.data.sync.SyncManager
import com.reelwise.app.data.sync.mergeBackups
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** A Google Drive appDataFolder in memory, accepting only the token "good". */
class FakeDrive {
    val files = linkedMapOf<String, String>()
    val requests = mutableListOf<Request>()

    fun client(): OkHttpClient = OkHttpClient.Builder().addInterceptor(Interceptor { handle(it.request()) }).build()

    private fun respond(req: Request, code: Int, body: String) = Response.Builder()
        .request(req).protocol(Protocol.HTTP_1_1).code(code).message("x")
        .body(body.toResponseBody("application/json".toMediaType())).build()

    private fun handle(req: Request): Response {
        requests += req
        if (req.header("Authorization") != "Bearer good") return respond(req, 401, "{}")
        val path = req.url.encodedPath
        return when {
            req.method == "GET" && path == "/drive/v3/files" ->
                respond(req, 200, """{"files":[${files.keys.joinToString(",") { """{"id":"$it"}""" }}]}""")
            req.method == "GET" && path.startsWith("/drive/v3/files/") ->
                files[path.removePrefix("/drive/v3/files/")]?.let { respond(req, 200, it) } ?: respond(req, 404, "{}")
            req.method == "POST" && path == "/upload/drive/v3/files" -> {
                val content = Buffer().also { (req.body as MultipartBody).part(1).body.writeTo(it) }.readUtf8()
                val id = "file${files.size + 1}"
                files[id] = content
                respond(req, 200, """{"id":"$id"}""")
            }
            req.method == "PATCH" && path.startsWith("/upload/drive/v3/files/") -> {
                val id = path.removePrefix("/upload/drive/v3/files/")
                files[id] = Buffer().also { req.body!!.writeTo(it) }.readUtf8()
                respond(req, 200, """{"id":"$id"}""")
            }
            else -> respond(req, 404, "{}")
        }
    }
}

@RunWith(AndroidJUnit4::class)
class SyncTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val db = Room.inMemoryDatabaseBuilder(context, ReelwiseDatabase::class.java).allowMainThreadQueries().build()
    private val settingsFile = File(context.filesDir, "sync-${System.nanoTime()}.preferences_pb")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val settings = SettingsStore(PreferenceDataStoreFactory.create { settingsFile }, json)
    private val keysFile = File(context.filesDir, "keys-${System.nanoTime()}.preferences_pb")
    private val keys = KeyStore(PreferenceDataStoreFactory.create { keysFile })
    private val drive = FakeDrive()
    private var grant: GoogleGrant? = GoogleGrant("good", "aakash@example.com", "Aakash")
    private var now = 1_000L
    private val sync = SyncManager(
        db, settings, keys, DriveClient(drive.client(), json),
        object : DriveTokens { override suspend fun silentGrant() = grant },
        json,
    ) { now++ }

    @After
    fun tearDown() {
        db.close()
        settingsFile.delete()
        keysFile.delete()
    }

    private fun backupIn(drive: FakeDrive, data: BackupData) {
        drive.files["existing"] = json.encodeToString(BackupData.serializer(), data)
    }

    private fun stored(): BackupData = json.decodeFromString(BackupData.serializer(), drive.files.values.single())

    @Test
    fun firstConnectBacksUpThisPhone() = runTest {
        db.interactions().upsert(InteractionEntity(1, MediaType.MOVIE, Signal.LIKE, "Film 1", null, 10))
        settings.setTaste(TasteProfile(lovedGenres = listOf("comedy")))
        assertTrue(sync.connect(grant!!).isSuccess)

        val backup = stored()
        assertEquals(listOf(1), backup.interactions.map { it.id })
        assertEquals(listOf("comedy"), backup.settings.taste.lovedGenres)
        assertEquals("aakash@example.com", settings.account.first()!!.email)
        assertTrue(settings.account.first()!!.lastSyncedAt != null)
        assertFalse(sync.state.value.restoredProfile)
    }

    @Test
    fun aNewPhoneIsRestoredFromDrive() = runTest {
        backupIn(drive, BackupData(
            exportedAt = 500,
            settings = BackupSettings("GB", onboardingDone = true, themeMode = ThemeMode.LIGHT, taste = TasteProfile(freshness = Freshness.CLASSICS)),
            interactions = listOf(BackupInteraction(7, MediaType.TV, Signal.SEEN, "Show 7", null, 5)),
            watchlist = listOf(BackupWatch(9, MediaType.MOVIE, "Film 9", addedAt = 6)),
        ))
        sync.connect(grant!!)

        assertEquals(listOf(7), db.interactions().getAll().map { it.id })
        assertEquals(listOf(9), db.watchlist().getAll().map { it.id })
        val s = settings.settings.first()
        assertTrue(s.onboardingDone)
        assertEquals("GB", s.region)
        assertEquals(ThemeMode.LIGHT, s.themeMode)
        assertEquals(Freshness.CLASSICS, s.taste.freshness)
        assertTrue("the quiz can be skipped", sync.state.value.restoredProfile)
        assertEquals(1, drive.files.size) // updated in place, not duplicated
    }

    @Test
    fun theNewerRatingWinsAndThisPhonesFinishedQuizIsKept() {
        val local = BackupData(
            exportedAt = 10, settings = BackupSettings("US", onboardingDone = true, taste = TasteProfile(lovedGenres = listOf("drama"))),
            interactions = listOf(BackupInteraction(1, MediaType.MOVIE, Signal.DISLIKE, "Film 1", null, updatedAt = 20)),
        )
        val remote = BackupData(
            exportedAt = 30, settings = BackupSettings("GB", onboardingDone = true, taste = TasteProfile(lovedGenres = listOf("crime"))),
            interactions = listOf(
                BackupInteraction(1, MediaType.MOVIE, Signal.LIKE, "Film 1", null, updatedAt = 15),
                BackupInteraction(2, MediaType.MOVIE, Signal.SEEN, "Film 2", null, updatedAt = 16),
            ),
        )
        val merged = mergeBackups(local, remote)
        assertEquals(listOf(2 to Signal.SEEN, 1 to Signal.DISLIKE), merged.interactions.map { it.id to it.signal })
        assertEquals(listOf("drama"), merged.settings.taste.lovedGenres)
    }

    @Test
    fun withoutAGrantNothingIsSentAndThePersonIsAskedToSignIn() = runTest {
        settings.setAccount("aakash@example.com", null)
        grant = null
        assertTrue(sync.upload().isSuccess)
        assertTrue(drive.requests.isEmpty())
    }

    @Test
    fun aRevokedTokenIsReported() = runTest {
        grant = GoogleGrant("expired", "aakash@example.com", null)
        assertTrue(sync.connect(grant!!).isFailure)
        assertNull(drive.files.values.firstOrNull())
    }

    @Test
    fun apiKeysAreLeftOutUnlessThePersonOptsIn() = runTest {
        keys.save("tmdb-secret", "groq-secret", null)
        sync.connect(grant!!)
        assertNull(stored().keys)
        assertFalse(drive.files.values.single().contains("tmdb-secret"))

        sync.setIncludeKeys(true)
        sync.upload()
        assertEquals(BackupKeys("tmdb-secret", "groq-secret", ""), stored().keys)
    }

    @Test
    fun restoreReplacesThisPhonesDataWithDrive() = runTest {
        db.interactions().upsert(InteractionEntity(1, MediaType.MOVIE, Signal.LIKE, "Only on phone", null, 10))
        db.watchlist().upsert(WatchlistEntity(2, MediaType.MOVIE, "Phone list", null, null, 0.0, 11))
        settings.setAccount("aakash@example.com", null)
        backupIn(drive, BackupData(
            exportedAt = 500,
            settings = BackupSettings("IN", onboardingDone = true),
            interactions = listOf(BackupInteraction(7, MediaType.TV, Signal.SEEN, "Show 7", null, 5)),
            keys = BackupKeys("tmdb-from-drive"),
        ))

        assertTrue(sync.restore().isSuccess)
        assertEquals(listOf(7), db.interactions().getAll().map { it.id })
        assertTrue(db.watchlist().getAll().isEmpty())
        assertEquals("IN", settings.settings.first().region)
        assertEquals("tmdb-from-drive", keys.tmdbKey())
        assertTrue(settings.settings.first().onboardingDone)
        assertEquals("Restored from Google Drive", sync.state.value.message)
    }

    @Test
    fun restoreWithNoBackupChangesNothing() = runTest {
        db.interactions().upsert(InteractionEntity(1, MediaType.MOVIE, Signal.LIKE, "Film 1", null, 10))
        sync.restore()
        assertEquals(listOf(1), db.interactions().getAll().map { it.id })
        assertEquals("There's no Reelwise backup in this Google account yet.", sync.state.value.message)
    }

    @Test
    fun anExpiredGrantAsksToSignInAgain() = runTest {
        grant = null
        sync.restore()
        assertTrue(sync.state.value.needsSignIn)
        assertTrue(drive.requests.isEmpty())
    }
}
