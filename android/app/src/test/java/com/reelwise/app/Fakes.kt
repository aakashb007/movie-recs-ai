package com.reelwise.app

import com.reelwise.app.data.CachedFeed
import com.reelwise.app.data.Repository
import com.reelwise.app.data.SavedKeys
import com.reelwise.app.data.local.InteractionEntity
import com.reelwise.app.data.local.Settings
import com.reelwise.app.data.local.ThemeMode
import com.reelwise.app.data.local.WatchlistEntity
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.RecommendationResponse
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleRef
import com.reelwise.app.data.model.TitleSummary
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

fun title(id: Int, type: MediaType = MediaType.MOVIE, genre: String = "Drama") = TitleSummary(
    id = id, mediaType = type, title = "Title $id", year = 2000 + id % 20, overview = "Overview $id",
    voteAverage = 7.0 + (id % 3) / 10.0, voteCount = 1000, genres = listOf(genre),
)

fun rec(id: Int, because: Int? = null, reason: String = "Because you liked Title $because") = RecItem(
    title = title(id), score = 1.0 / id, reason = reason,
    because = because?.let { TitleRef(it, MediaType.MOVIE, "Title $it") },
)

class FakeRepository : Repository {
    val settingsFlow = MutableStateFlow(Settings("US", onboardingDone = true, dynamicColor = false))
    val interactionsFlow = MutableStateFlow<List<InteractionEntity>>(emptyList())
    val watchlistFlow = MutableStateFlow<List<WatchlistEntity>>(emptyList())
    val feeds = FeedFilter.entries.associateWith { MutableStateFlow<CachedFeed?>(null) }
    var nextFeed: RecommendationResponse = RecommendationResponse(emptyList(), "personalized")
    var failRefresh = false
    val refreshCalls = mutableListOf<FeedFilter>()
    var onboardingTitles: List<TitleSummary> = emptyList()
    var searchResults: List<TitleSummary> = emptyList()
    val searches = mutableListOf<String>()
    val keys = MutableStateFlow(SavedKeys(tmdb = "tmdb-key"))
    var rejectKeys = false

    /** 2026-09-22, so "new" (this year or last) means 2025 or later. */
    var now = 1_790_000_000_000L

    override val settings: Flow<Settings> = settingsFlow
    override val interactions: Flow<List<InteractionEntity>> = interactionsFlow
    override val watchlist: Flow<List<WatchlistEntity>> = watchlistFlow
    override val hasTmdbKey: Flow<Boolean> = keys.map { it.tmdb.isNotBlank() }

    override suspend fun saveKeys(keys: SavedKeys): Result<Unit> {
        if (rejectKeys) return Result.failure(com.reelwise.app.engine.TmdbAuthException())
        this.keys.value = keys
        return Result.success(Unit)
    }

    override suspend fun savedKeys(): SavedKeys = keys.value

    override suspend fun search(query: String): Result<List<TitleSummary>> {
        searches += query
        return Result.success(searchResults)
    }

    override suspend fun saveTaste(taste: TasteProfile, watched: List<TitleSummary>, loved: Set<TitleKey>) {
        watched.forEach { rate(it, if (it.key in loved) Signal.LIKE else Signal.SEEN) }
        settingsFlow.value = settingsFlow.value.copy(taste = taste, onboardingDone = true)
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        settingsFlow.value = settingsFlow.value.copy(themeMode = mode)
    }

    override fun feed(filter: FeedFilter): Flow<CachedFeed?> = feeds.getValue(filter)

    override suspend fun refreshFeed(filter: FeedFilter): Result<Unit> {
        refreshCalls += filter
        if (failRefresh) return Result.failure(IOException("offline"))
        feeds.getValue(filter).value = CachedFeed(nextFeed, now)
        return Result.success(Unit)
    }

    override suspend fun onboarding(): Result<List<TitleSummary>> = Result.success(onboardingTitles)

    override suspend fun ask(query: String): Result<AskResponse> =
        Result.success(AskResponse(QueryIntent(summary = query), emptyList(), emptyList(), "rules"))

    override suspend fun detail(mediaType: MediaType, id: Int): Result<TitleDetail> =
        Result.success(TitleDetail(summary = title(id, mediaType)))

    override suspend fun rate(title: TitleSummary, signal: Signal?) {
        val others = interactionsFlow.value.filterNot { it.id == title.id && it.mediaType == title.mediaType }
        interactionsFlow.value = if (signal == null) others else others + InteractionEntity(
            title.id, title.mediaType, signal, title.title, title.posterPath, now++,
        )
    }

    override fun signalFor(mediaType: MediaType, id: Int): Flow<Signal?> =
        interactionsFlow.map { l -> l.firstOrNull { it.id == id && it.mediaType == mediaType }?.signal }

    override suspend fun toggleWatchlist(title: TitleSummary) {
        val existing = watchlistFlow.value.any { it.id == title.id && it.mediaType == title.mediaType }
        watchlistFlow.value = if (existing) {
            watchlistFlow.value.filterNot { it.id == title.id && it.mediaType == title.mediaType }
        } else {
            watchlistFlow.value + WatchlistEntity(title.id, title.mediaType, title.title, title.year, null, 0.0, now++)
        }
    }

    override fun isInWatchlist(mediaType: MediaType, id: Int): Flow<Boolean> =
        watchlistFlow.map { l -> l.any { it.id == id && it.mediaType == mediaType } }

    override suspend fun setOnboardingDone(done: Boolean) {
        settingsFlow.value = settingsFlow.value.copy(onboardingDone = done)
    }

    override suspend fun setRegion(region: String) {
        settingsFlow.value = settingsFlow.value.copy(region = region)
    }

    override suspend fun setDynamicColor(enabled: Boolean) {
        settingsFlow.value = settingsFlow.value.copy(dynamicColor = enabled)
    }

    override suspend fun resetTaste() {
        interactionsFlow.value = emptyList()
    }
}
