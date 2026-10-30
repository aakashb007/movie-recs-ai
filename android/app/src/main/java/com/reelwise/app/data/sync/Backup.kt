package com.reelwise.app.data.sync

import com.reelwise.app.data.local.InteractionEntity
import com.reelwise.app.data.local.ThemeMode
import com.reelwise.app.data.local.WatchlistEntity
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import kotlinx.serialization.Serializable

/**
 * Everything Reelwise backs up to the person's Google Drive: ratings, watchlist and taste.
 * API keys are deliberately not in here; they never leave the phone.
 */
@Serializable
data class BackupData(
    val version: Int = 1,
    val exportedAt: Long,
    val settings: BackupSettings,
    val interactions: List<BackupInteraction> = emptyList(),
    val watchlist: List<BackupWatch> = emptyList(),
)

@Serializable
data class BackupSettings(
    val region: String,
    val onboardingDone: Boolean,
    val dynamicColor: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.DARK,
    val taste: TasteProfile = TasteProfile(),
)

@Serializable
data class BackupInteraction(
    val id: Int,
    val mediaType: MediaType,
    val signal: Signal,
    val title: String,
    val posterPath: String? = null,
    val updatedAt: Long,
) {
    fun toEntity() = InteractionEntity(id, mediaType, signal, title, posterPath, updatedAt)

    companion object {
        fun of(e: InteractionEntity) = BackupInteraction(e.id, e.mediaType, e.signal, e.title, e.posterPath, e.updatedAt)
    }
}

@Serializable
data class BackupWatch(
    val id: Int,
    val mediaType: MediaType,
    val title: String,
    val year: Int? = null,
    val posterPath: String? = null,
    val voteAverage: Double = 0.0,
    val addedAt: Long,
) {
    fun toEntity() = WatchlistEntity(id, mediaType, title, year, posterPath, voteAverage, addedAt)

    companion object {
        fun of(e: WatchlistEntity) = BackupWatch(e.id, e.mediaType, e.title, e.year, e.posterPath, e.voteAverage, e.addedAt)
    }
}

/**
 * Combines this phone's data with the copy in Drive when an account is connected.
 * Per title the most recent rating wins; watchlists are united; a finished taste quiz in Drive
 * replaces this phone's settings only if this phone hasn't finished the quiz yet (a new phone).
 */
fun mergeBackups(local: BackupData, remote: BackupData?): BackupData {
    if (remote == null) return local
    val interactions = (remote.interactions + local.interactions)
        .groupBy { it.id to it.mediaType }
        .map { (_, versions) -> versions.maxBy { it.updatedAt } }
        .sortedBy { it.updatedAt }
    val watchlist = (remote.watchlist + local.watchlist)
        .groupBy { it.id to it.mediaType }
        .map { (_, versions) -> versions.maxBy { it.addedAt } }
        .sortedBy { it.addedAt }
    val settings = if (!local.settings.onboardingDone && remote.settings.onboardingDone) remote.settings else local.settings
    return BackupData(exportedAt = maxOf(local.exportedAt, remote.exportedAt), settings = settings, interactions = interactions, watchlist = watchlist)
}
