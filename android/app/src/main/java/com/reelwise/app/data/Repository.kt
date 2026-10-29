package com.reelwise.app.data

import com.reelwise.app.data.local.FeedCacheEntity
import com.reelwise.app.data.local.InteractionEntity
import com.reelwise.app.data.local.ReelwiseDatabase
import com.reelwise.app.data.local.Settings
import com.reelwise.app.data.local.SettingsStore
import com.reelwise.app.data.local.WatchlistEntity
import com.reelwise.app.data.model.AskRequest
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.Interaction
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.RecommendationRequest
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.remote.ReelwiseApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A cached feed and when it was fetched. */
data class CachedFeed(val response: RecommendationResponse, val fetchedAt: Long)

interface Repository {
    val settings: Flow<Settings>
    val interactions: Flow<List<InteractionEntity>>
    val watchlist: Flow<List<WatchlistEntity>>

    fun feed(filter: FeedFilter): Flow<CachedFeed?>
    suspend fun refreshFeed(filter: FeedFilter): Result<Unit>
    suspend fun onboarding(): Result<List<TitleSummary>>
    suspend fun ask(query: String): Result<AskResponse>
    suspend fun detail(mediaType: MediaType, id: Int): Result<TitleDetail>

    /** Records a signal for a title; null clears it. */
    suspend fun rate(title: TitleSummary, signal: Signal?)
    fun signalFor(mediaType: MediaType, id: Int): Flow<Signal?>
    suspend fun toggleWatchlist(title: TitleSummary)
    fun isInWatchlist(mediaType: MediaType, id: Int): Flow<Boolean>

    suspend fun setOnboardingDone(done: Boolean)
    suspend fun setRegion(region: String)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun resetTaste()
}

@Singleton
class DefaultRepository @Inject constructor(
    private val api: ReelwiseApi,
    private val db: ReelwiseDatabase,
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val clock: Clock,
) : Repository {
    override val settings: Flow<Settings> = settingsStore.settings
    override val interactions: Flow<List<InteractionEntity>> = db.interactions().observeAll()
    override val watchlist: Flow<List<WatchlistEntity>> = db.watchlist().observeAll()

    override fun feed(filter: FeedFilter): Flow<CachedFeed?> =
        db.feed().observe(filter.wire).map { cached ->
            cached?.let {
                runCatching { CachedFeed(json.decodeFromString<RecommendationResponse>(it.json), it.fetchedAt) }
                    .getOrNull()
            }
        }

    override suspend fun refreshFeed(filter: FeedFilter): Result<Unit> = safely {
        val response = api.recommendations(
            RecommendationRequest(
                interactions = currentInteractions(),
                mediaType = filter.wire,
                region = settings.first().region,
            ),
        )
        db.feed().upsert(FeedCacheEntity(filter.wire, json.encodeToString(response), clock.now()))
    }

    override suspend fun onboarding(): Result<List<TitleSummary>> = safely { api.onboarding().items }

    override suspend fun ask(query: String): Result<AskResponse> = safely {
        api.ask(AskRequest(query.trim(), currentInteractions(), settings.first().region))
    }

    override suspend fun detail(mediaType: MediaType, id: Int): Result<TitleDetail> = safely {
        api.title(mediaType.wire, id, settings.first().region)
    }

    override suspend fun rate(title: TitleSummary, signal: Signal?) {
        if (signal == null) {
            db.interactions().delete(title.id, title.mediaType)
        } else {
            db.interactions().upsert(
                InteractionEntity(title.id, title.mediaType, signal, title.title, title.posterPath, clock.now()),
            )
        }
    }

    override fun signalFor(mediaType: MediaType, id: Int): Flow<Signal?> =
        interactions.map { list -> list.firstOrNull { it.id == id && it.mediaType == mediaType }?.signal }

    override suspend fun toggleWatchlist(title: TitleSummary) {
        val dao = db.watchlist()
        if (dao.contains(title.id, title.mediaType)) {
            dao.delete(title.id, title.mediaType)
        } else {
            dao.upsert(
                WatchlistEntity(
                    title.id, title.mediaType, title.title, title.year, title.posterPath, title.voteAverage, clock.now(),
                ),
            )
        }
    }

    override fun isInWatchlist(mediaType: MediaType, id: Int): Flow<Boolean> =
        db.watchlist().observeContains(id, mediaType)

    override suspend fun setOnboardingDone(done: Boolean) {
        settingsStore.setOnboardingDone(done)
    }

    override suspend fun setRegion(region: String) {
        settingsStore.setRegion(region)
        db.feed().clear()
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        settingsStore.setDynamicColor(enabled)
    }

    override suspend fun resetTaste() {
        db.interactions().clear()
        db.feed().clear()
        settingsStore.setOnboardingDone(false)
    }

    private suspend fun currentInteractions(): List<Interaction> =
        db.interactions().getAll().takeLast(MAX_INTERACTIONS).map { Interaction(it.id, it.mediaType, it.signal) }

    private inline fun <T> safely(block: () -> T): Result<T> =
        try {
            Result.success(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }

    private companion object {
        const val MAX_INTERACTIONS = 500
    }
}

fun interface Clock {
    fun now(): Long
}
