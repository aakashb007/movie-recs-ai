package com.reelwise.app.data.sync

import com.reelwise.app.data.Clock
import com.reelwise.app.data.local.GoogleAccount
import com.reelwise.app.data.local.ReelwiseDatabase
import com.reelwise.app.data.local.SettingsStore
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

data class SyncState(
    val account: GoogleAccount? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** Set after connecting when Drive held a finished taste quiz: a restored phone can skip it. */
    val restoredProfile: Boolean = false,
)

/**
 * Keeps the person's data in their Google Drive. Connecting merges Drive and phone once; after
 * that this phone is the source of truth and every change is uploaded a few seconds later.
 */
@Singleton
class SyncManager @Inject constructor(
    private val db: ReelwiseDatabase,
    private val settings: SettingsStore,
    private val drive: DriveClient,
    private val tokens: DriveTokens,
    private val json: Json,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val busy = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val restored = MutableStateFlow(false)
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Starts mirroring state and uploading changes; call once from the Application. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settings.account, busy, message, restored) { a, b, m, r -> SyncState(a, b, m, r) }.collect { _state.value = it }
        }
        scope.launch {
            combine(
                db.interactions().observeAll(),
                db.watchlist().observeAll(),
                settings.settings.map { s -> listOf(s.region, s.onboardingDone, s.dynamicColor, s.themeMode, s.taste) },
            ) { a, b, c -> Triple(a, b, c) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(UPLOAD_DEBOUNCE_MS)
                .collect { if (settings.account.first() != null) upload() }
        }
    }

    /** After the person signs in: merge Drive into the phone and the result back into Drive. */
    suspend fun connect(grant: GoogleGrant): Result<Unit> = guarded {
        settings.setAccount(grant.email ?: "Google account", grant.name)
        val id = drive.find(grant.token)
        val remote = id?.let { runCatching { json.decodeFromString(BackupData.serializer(), drive.download(grant.token, it)) }.getOrNull() }
        val local = export()
        val merged = mergeBackups(local, remote)
        import(merged)
        drive.upload(grant.token, id, json.encodeToString(BackupData.serializer(), merged.copy(exportedAt = clock.now())))
        settings.setLastSynced(clock.now())
        restored.value = remote?.settings?.onboardingDone == true && merged.settings == remote.settings
        message.value = if (remote == null) "Backed up to Google Drive" else "Restored and synced with Google Drive"
    }

    /** Uploads this phone's data now; needs a previously granted account. */
    suspend fun upload(): Result<Unit> = guarded {
        val grant = tokens.silentGrant()
        if (grant == null) {
            message.value = "Sign in to Google again to keep backing up"
            return@guarded
        }
        val content = json.encodeToString(BackupData.serializer(), export())
        drive.upload(grant.token, drive.find(grant.token), content)
        settings.setLastSynced(clock.now())
        message.value = null
    }

    suspend fun signOut() {
        settings.clearAccount()
        restored.value = false
        message.value = "Signed out. Your data stays on this phone and in your Drive."
    }

    /** The restore has been acted on (the quiz was skipped); don't act on it again. */
    fun acknowledgeRestore() {
        restored.value = false
    }

    fun report(error: String) {
        message.value = error
    }

    private suspend fun guarded(block: suspend () -> Unit): Result<Unit> = mutex.withLock {
        busy.value = true
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveAuthException) {
            message.value = "Google said no. Sign in again."
            Result.failure(e)
        } catch (e: IOException) {
            message.value = "Couldn't reach Google Drive. It will retry with your next change."
            Result.failure(e)
        } finally {
            busy.value = false
        }
    }

    suspend fun export(): BackupData {
        val s = settings.settings.first()
        return BackupData(
            exportedAt = clock.now(),
            settings = BackupSettings(s.region, s.onboardingDone, s.dynamicColor, s.themeMode, s.taste),
            interactions = db.interactions().getAll().map(BackupInteraction::of),
            watchlist = db.watchlist().getAll().map(BackupWatch::of),
        )
    }

    private suspend fun import(data: BackupData) {
        data.interactions.forEach { db.interactions().upsert(it.toEntity()) }
        data.watchlist.forEach { db.watchlist().upsert(it.toEntity()) }
        val s = data.settings
        settings.setRegion(s.region)
        settings.setThemeMode(s.themeMode)
        settings.setDynamicColor(s.dynamicColor)
        settings.setTaste(s.taste)
        settings.setOnboardingDone(s.onboardingDone)
        db.feed().clear()
    }

    companion object {
        const val UPLOAD_DEBOUNCE_MS = 5_000L
    }
}
