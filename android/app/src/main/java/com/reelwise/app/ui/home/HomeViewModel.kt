package com.reelwise.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Clock
import com.reelwise.app.data.Repository
import com.reelwise.app.data.local.ThemeMode
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Calendar
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
    val saved: Set<TitleKey> = emptySet(),
    val currentYear: Int = 2026,
)

fun RecItem.isNew(currentYear: Int): Boolean = (title.year ?: 0) >= currentYear - 1

/**
 * Lays a ranked list out as a feed. The top five become the hero stack. Then, in order:
 * fresh releases, rows grouped by the title that explains them ("Because you loved Arrival"),
 * series not started yet, and whatever is left. Every item appears exactly once.
 */
fun buildSections(
    items: List<RecItem>,
    currentYear: Int,
    heroSize: Int = 5,
    minGroup: Int = 3,
    maxGroups: Int = 3,
): Pair<List<RecItem>, List<FeedSection>> {
    val hero = items.take(heroSize)
    var rest = items.drop(heroSize)
    val sections = mutableListOf<FeedSection>()

    val fresh = rest.filter { it.isNew(currentYear) }.take(12)
    if (fresh.size >= minGroup) {
        sections += FeedSection("NEW · ${currentYear - 1}–${currentYear % 100}", "Fresh releases for you", fresh)
        rest = rest - fresh.toSet()
    }

    val groups = rest
        .filter { it.because != null }
        .groupBy { it.because!!.title }
        .filterValues { it.size >= minGroup }
        .entries
        .sortedByDescending { it.value.size }
        .take(maxGroups)
    for ((title, group) in groups) {
        val watched = group.first().reason.startsWith("Because you watched")
        sections += FeedSection(if (watched) "BECAUSE YOU WATCHED" else "BECAUSE YOU LOVED", title, group)
        rest = rest - group.toSet()
    }

    val series = rest.filter { it.title.mediaType == MediaType.TV }
    if (series.size >= minGroup) {
        sections += FeedSection("NOT STARTED YET", "Series you'd binge", series)
        rest = rest - series.toSet()
    }
    if (rest.isNotEmpty()) sections += FeedSection(null, "More picks for you", rest)
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
        combine(hiddenKeys(), savedKeys()) { hidden, saved -> hidden to saved },
        refreshing,
        error,
    ) { f, feed, (hidden, saved), isRefreshing, err ->
        // Rated or dismissed titles vanish at once; the re-ranked list follows a moment later.
        val items = feed?.response?.items.orEmpty().filterNot { it.title.key in hidden }
        val year = currentYear()
        val (hero, sections) = buildSections(items, year)
        HomeUiState(
            filter = f,
            hero = hero,
            sections = sections,
            coldStart = feed?.response?.strategy == "cold_start",
            refreshing = isRefreshing,
            firstLoad = feed == null && err == null,
            error = err,
            hasContent = items.isNotEmpty(),
            saved = saved,
            currentYear = year,
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

    private fun savedKeys(): Flow<Set<TitleKey>> =
        repository.watchlist.map { list -> list.map { TitleKey(it.id, it.mediaType) }.toSet() }

    private fun currentYear(): Int = Calendar.getInstance().apply { timeInMillis = clock.now() }.get(Calendar.YEAR)

    fun toggleTheme(currentlyDark: Boolean) {
        viewModelScope.launch { repository.setThemeMode(if (currentlyDark) ThemeMode.LIGHT else ThemeMode.DARK) }
    }

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
