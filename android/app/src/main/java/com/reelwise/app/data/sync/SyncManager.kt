package com.reelwise.app.data.sync

import com.reelwise.app.data.Clock
import com.reelwise.app.data.local.GoogleAccount
import com.reelwise.app.data.local.KeyStore
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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

data class SyncState(
    val account: GoogleAccount? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /** Set after connecting when Drive held a finished taste quiz: the quiz can be skipped. */
    val restoredProfile: Boolean = false,
    /** Google refused the saved access (expired or revoked): show "Sign in again". */
    val needsSignIn: Boolean = false,
    /** The person opted in to backing up their API keys too. */
    val includeKeys: Boolean = false,
)

/**
 * Keeps the person's data in their Google Drive. Connecting merges Drive and phone once; after
 * that this phone is the source of truth and every change is uploaded a few seconds later.
 * "Restore from Drive" replaces this phone's data with the Drive copy.
 */
@Singleton
class SyncManager @Inject constructor(
    private val db: ReelwiseDatabase,
    private val settings: SettingsStore,
    private val keys: KeyStore,
    private val drive: DriveClient,
    private val tokens: DriveTokens,
    private val json: Json,
    private val clock: Clock,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Starts mirroring the account and uploading changes; call once from the Application. */
    @OptIn(FlowPreview::class)
    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(settings.account, settings.backupKeys) { a, k -> a to k }
                .collect { (a, k) -> _state.update { it.copy(account = a, includeKeys = k) } }
        }
        scope.launch {
            combine(
                db.interactions().observeAll(),
                db.watchlist().observeAll(),
                settings.settings.map { s -> listOf(s.region, s.onboardingDone, s.dynamicColor, s.themeMode, s.taste) },
                settings.backupKeys,
            ) { a, b, c, d -> listOf(a, b, c, d) }
                .distinctUntilChanged()
                .drop(1)
                .debounce(UPLOAD_DEBOUNCE_MS)
                .collect { if (settings.account.first() != null && !_state.value.needsSignIn) upload() }
        }
    }

    /** After the person signs in: merge Drive into the phone and the result back into Drive. */
    suspend fun connect(grant: GoogleGrant): Result<Unit> = guarded {
        settings.setAccount(grant.email ?: "Google account", grant.name)
        _state.update { it.copy(account = settings.account.first(), needsSignIn = false) }
        val id = drive.find(grant.token)
        val remote = id?.let { read(grant.token, it) }
        val local = export()
        val merged = mergeBackups(local, remote)
        import(merged, replace = false)
        restoreKeysIfMissing(remote?.keys)
        drive.upload(grant.token, id, json.encodeToString(BackupData.serializer(), export()))
        settings.setLastSynced(clock.now())
        setRestored(remote?.settings?.onboardingDone == true && merged.settings == remote.settings)
        say(if (remote == null) "Backed up to Google Drive" else "Restored and synced with Google Drive")
    }

    /** Uploads this phone's data now; needs a previously granted account. */
    suspend fun upload(): Result<Unit> = guarded {
        val grant = grantOrAsk() ?: return@guarded
        val content = json.encodeToString(BackupData.serializer(), export())
        drive.upload(grant.token, drive.find(grant.token), content)
        settings.setLastSynced(clock.now())
        say("Backed up to Google Drive")
    }

    /** Replaces this phone's ratings, list and taste with the copy in Drive. */
    suspend fun restore(): Result<Unit> = guarded {
        val grant = grantOrAsk() ?: return@guarded
        val remote = drive.find(grant.token)?.let { read(grant.token, it) }
        if (remote == null) {
            say("There's no Reelwise backup in this Google account yet.")
            return@guarded
        }
        import(remote, replace = true)
        remote.keys?.takeIf { it.tmdb.isNotBlank() }?.let { keys.save(it.tmdb, it.groq, it.omdb) }
        settings.setLastSynced(clock.now())
        say("Restored from Google Drive")
    }

    suspend fun setIncludeKeys(enabled: Boolean) {
        settings.setBackupKeys(enabled)
        _state.update { it.copy(includeKeys = enabled) }
    }

    suspend fun signOut() {
        settings.clearAccount()
        _state.update { it.copy(account = null, restoredProfile = false, needsSignIn = false) }
        say("Signed out. Your data stays on this phone and in your Drive.")
    }

    /** The restore has been acted on (the quiz was skipped); don't act on it again. */
    fun acknowledgeRestore() = setRestored(false)

    fun report(error: String) = say(error)

    private fun say(message: String?) = _state.update { it.copy(message = message) }

    private fun setRestored(value: Boolean) = _state.update { it.copy(restoredProfile = value) }

    private suspend fun grantOrAsk(): GoogleGrant? = tokens.silentGrant().also {
        if (it == null) {
            _state.update { s -> s.copy(needsSignIn = true) }
            say("Google sign-in expired. Tap \"Sign in again\" to keep backing up.")
        }
    }

    private suspend fun read(token: String, id: String): BackupData? =
        runCatching { json.decodeFromString(BackupData.serializer(), drive.download(token, id)) }
            .onFailure { if (it is IOException) throw it }
            .getOrNull()

    private suspend fun restoreKeysIfMissing(remote: BackupKeys?) {
        if (remote == null || remote.tmdb.isBlank() || keys.all().first.isNotBlank()) return
        keys.save(remote.tmdb, remote.groq, remote.omdb)
    }

    private suspend fun guarded(block: suspend () -> Unit): Result<Unit> = mutex.withLock {
        _state.update { it.copy(busy = true) }
        try {
            block()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: DriveAuthException) {
            _state.update { it.copy(needsSignIn = true) }
            say("Google sign-in expired. Tap \"Sign in again\" to keep backing up.")
            Result.failure(e)
        } catch (e: IOException) {
            val offline = e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException
            say(
                if (offline) "No internet connection. Your data is safe on this phone; it will back up with your next change."
                else "Google Drive had a problem (${e.message}). Try again in a minute.",
            )
            Result.failure(e)
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    suspend fun export(): BackupData {
        val s = settings.settings.first()
        val withKeys = settings.backupKeys.first()
        return BackupData(
            exportedAt = clock.now(),
            settings = BackupSettings(s.region, s.onboardingDone, s.dynamicColor, s.themeMode, s.taste),
            interactions = db.interactions().getAll().map(BackupInteraction::of),
            watchlist = db.watchlist().getAll().map(BackupWatch::of),
            keys = if (withKeys) keys.all().let { (t, g, o) -> BackupKeys(t, g, o) } else null,
        )
    }

    private suspend fun import(data: BackupData, replace: Boolean) {
        if (replace) {
            db.interactions().clear()
            db.watchlist().clear()
        }
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
