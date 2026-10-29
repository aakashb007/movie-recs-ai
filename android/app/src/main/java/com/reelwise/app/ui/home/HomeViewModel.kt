package com.reelwise.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Clock
import com.reelwise.app.data.Repository
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FeedSection(val eyebrow: String?, val title: String, val items: List<RecItem>)

data class HomeUiState(
    val filter: FeedFilter = FeedFilter.ALL,
    val hero: List<RecItem> = emptyList(),
    val sections: List<FeedSection> = emptyList(),
    val coldStart: Boolean = false,
    val refreshing: Boolean = false,
    val firstLoad: Boolean = true,
    val error: String? = null,
    val hasContent: Boolean = false,
)

/**
 * Lays a ranked list out as a feed: the top five become the hero carousel, then items that
 * share a "because" title are grouped into rows, so the explanation is the row heading.
 */
fun buildSections(items: List<RecItem>, heroSize: Int = 5, minGroup: Int = 3, maxGroups: Int = 3): Pair<List<RecItem>, List<FeedSection>> {
    val hero = items.take(heroSize)
    val rest = items.drop(heroSize)
    val groups = rest
        .groupBy { item -> item.because?.let { "because" to it.title } ?: ("reason" to item.reason) }
        .filter { (key, value) -> value.size >= minGroup && key.second.isNotBlank() }
        .entries
        .sortedByDescending { it.value.size }
        .take(maxGroups)
    val used = groups.flatMap { it.value }.toSet()
    val sections = groups.map { (key, value) ->
        if (key.first == "because") FeedSection("Because you liked", key.second, value) else FeedSection(null, key.second, value)
    }.toMutableList()
    val leftovers = rest.filterNot { it in used }
    if (leftovers.isNotEmpty()) sections += FeedSection(null, "More picks for you", leftovers)
    return hero to sections
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: Repository,
    private val clock: Clock,
) : ViewModel() {
    private val filter = MutableStateFlow(FeedFilter.ALL)
    private val refreshing = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)
    private var refreshJob: Job? = null

    val state: StateFlow<HomeUiState> = combine(
        filter,
        filter.flatMapLatest { repository.feed(it) },
        hiddenKeys(),
        refreshing,
        error,
    ) { f, feed, hidden, isRefreshing, err ->
        // Rated or dismissed titles vanish at once; the re-ranked list follows a moment later.
        val items = feed?.response?.items.orEmpty().filterNot { it.title.key in hidden }
        val (hero, sections) = buildSections(items)
        HomeUiState(
            filter = f,
            hero = hero,
            sections = sections,
            coldStart = feed?.response?.strategy == "cold_start",
            refreshing = isRefreshing,
            firstLoad = feed == null && err == null,
            error = err,
            hasContent = items.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    init {
        viewModelScope.launch { filter.collect { refresh(force = false) } }
        viewModelScope.launch {
            repository.interactions
                .map { list -> list.map { Triple(it.id, it.mediaType, it.signal) }.toSet() }
                .distinctUntilChanged()
                .drop(1)
                .debounce(REFRESH_DEBOUNCE_MS)
                .collect { refresh(force = true) }
        }
    }

    private fun hiddenKeys(): Flow<Set<TitleKey>> =
        repository.interactions.map { list -> list.map { TitleKey(it.id, it.mediaType) }.toSet() }

    fun setFilter(value: FeedFilter) {
        filter.value = value
    }

    fun refresh(force: Boolean = true) {
        val current = filter.value
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (!force) {
                val cached = repository.feed(current).first()
                if (cached != null && clock.now() - cached.fetchedAt < STALE_AFTER_MS) return@launch
            }
            refreshing.value = true
            error.value = null
            repository.refreshFeed(current).onFailure { error.value = it.userMessage() }
            refreshing.value = false
        }
    }

    fun rate(title: TitleSummary, signal: Signal?) {
        viewModelScope.launch { repository.rate(title, signal) }
    }

    fun toggleWatchlist(title: TitleSummary) {
        viewModelScope.launch { repository.toggleWatchlist(title) }
    }

    fun isInWatchlist(title: TitleSummary): Flow<Boolean> = repository.isInWatchlist(title.mediaType, title.id)

    companion object {
        const val STALE_AFTER_MS = 30 * 60 * 1000L
        const val REFRESH_DEBOUNCE_MS = 800L
    }
}
