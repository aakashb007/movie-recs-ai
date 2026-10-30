package com.reelwise.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Repository
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Mix
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TasteProfile
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizStep { WATCHED, GENRES, PREFS, REVIEW }

enum class GenreChoice { LOVE, AVOID }

/** A quiz genre: what the chip says and the canonical name the engine uses. */
data class GenreOption(val label: String, val canonical: String)

data class LanguageOption(val label: String, val code: String)

data class QuizUiState(
    val step: QuizStep = QuizStep.WATCHED,
    val loadingPool: Boolean = true,
    val error: String? = null,
    val pool: List<TitleSummary> = emptyList(),
    val query: String = "",
    val searching: Boolean = false,
    val searchResults: List<TitleSummary>? = null,
    val kind: FeedFilter = FeedFilter.ALL,
    val watched: Map<TitleKey, TitleSummary> = emptyMap(),
    val loved: Set<TitleKey> = emptySet(),
    val genres: Map<String, GenreChoice> = emptyMap(),
    val freshness: Freshness = Freshness.RECENT,
    val mix: Mix = Mix.BOTH,
    val languages: Set<String> = emptySet(),
    val minRating: Double? = null,
    val saving: Boolean = false,
    val done: Boolean = false,
) {
    val visible: List<TitleSummary>
        get() = (searchResults ?: pool).filter {
            kind == FeedFilter.ALL ||
                (kind == FeedFilter.MOVIES && it.mediaType == MediaType.MOVIE) ||
                (kind == FeedFilter.SERIES && it.mediaType == MediaType.TV)
        }
    val lovedGenres: List<String> get() = GENRES.filter { genres[it.canonical] == GenreChoice.LOVE }.map { it.canonical }
    val avoidedGenres: List<String> get() = GENRES.filter { genres[it.canonical] == GenreChoice.AVOID }.map { it.canonical }

    val canNext: Boolean
        get() = when (step) {
            QuizStep.WATCHED -> watched.size >= MIN_WATCHED
            QuizStep.GENRES -> lovedGenres.isNotEmpty()
            QuizStep.PREFS -> true
            QuizStep.REVIEW -> !saving
        }

    val taste: TasteProfile
        get() = TasteProfile(lovedGenres, avoidedGenres, freshness, mix, LANGUAGES.filter { it.code in languages }.map { it.code }, minRating)

    companion object {
        const val MIN_WATCHED = 3
        val RATINGS = listOf(null, 6.0, 7.0, 7.5, 8.0)
        val GENRES = listOf(
            GenreOption("Sci-Fi", "science fiction"), GenreOption("Thriller", "thriller"), GenreOption("Mystery", "mystery"),
            GenreOption("Crime", "crime"), GenreOption("Drama", "drama"), GenreOption("Comedy", "comedy"),
            GenreOption("Action", "action"), GenreOption("Romance", "romance"), GenreOption("Fantasy", "fantasy"),
            GenreOption("Animation", "animation"), GenreOption("Horror", "horror"), GenreOption("Documentary", "documentary"),
            GenreOption("Family", "family"), GenreOption("History", "history"), GenreOption("War", "war"),
        )
        val LANGUAGES = listOf(
            LanguageOption("English", "en"), LanguageOption("Hindi", "hi"), LanguageOption("Urdu", "ur"),
            LanguageOption("Korean", "ko"), LanguageOption("Japanese", "ja"), LanguageOption("Spanish", "es"),
            LanguageOption("Tamil", "ta"), LanguageOption("Telugu", "te"), LanguageOption("Turkish", "tr"),
            LanguageOption("French", "fr"),
        )
    }
}

@OptIn(FlowPreview::class)
@HiltViewModel
class QuizViewModel @Inject constructor(private val repository: Repository) : ViewModel() {
    private val _state = MutableStateFlow(QuizUiState())
    val state: StateFlow<QuizUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { prefill() }
        loadPool()
        viewModelScope.launch {
            _state.map { it.query.trim() }.distinctUntilChanged().debounce(SEARCH_DEBOUNCE_MS).collectLatest { q ->
                if (q.length < 2) {
                    _state.update { it.copy(searchResults = null, searching = false) }
                } else {
                    _state.update { it.copy(searching = true) }
                    val result = repository.search(q)
                    _state.update { s ->
                        s.copy(searching = false, searchResults = result.getOrNull() ?: s.searchResults, error = result.exceptionOrNull()?.userMessage())
                    }
                }
            }
        }
    }

    /** Retaking the quiz starts from what is already known. */
    private suspend fun prefill() {
        val taste = repository.settings.first().taste
        val known = repository.interactions.first().filter { it.signal == Signal.SEEN || it.signal == Signal.LIKE }
        _state.update { s ->
            s.copy(
                watched = known.associate { TitleKey(it.id, it.mediaType) to TitleSummary(it.id, it.mediaType, it.title, posterPath = it.posterPath) },
                loved = known.filter { it.signal == Signal.LIKE }.map { TitleKey(it.id, it.mediaType) }.toSet(),
                genres = taste.lovedGenres.associateWith { GenreChoice.LOVE } + taste.avoidedGenres.associateWith { GenreChoice.AVOID },
                freshness = if (taste == TasteProfile()) s.freshness else taste.freshness,
                mix = taste.mix,
                languages = taste.languages.toSet(),
                minRating = taste.minRating,
            )
        }
    }

    fun loadPool() {
        _state.update { it.copy(loadingPool = true, error = null) }
        viewModelScope.launch {
            repository.onboarding()
                .onSuccess { titles -> _state.update { it.copy(loadingPool = false, pool = titles) } }
                .onFailure { e -> _state.update { it.copy(loadingPool = false, error = e.userMessage()) } }
        }
    }

    fun onQuery(value: String) = _state.update { it.copy(query = value.take(80)) }
    fun setKind(kind: FeedFilter) = _state.update { it.copy(kind = kind) }

    /** Tap a poster: watched or not. Un-watching also un-loves. */
    fun toggleWatched(title: TitleSummary) = _state.update { s ->
        if (title.key in s.watched) s.copy(watched = s.watched - title.key, loved = s.loved - title.key)
        else s.copy(watched = s.watched + (title.key to title))
    }

    fun toggleLoved(title: TitleSummary) = _state.update { s ->
        if (title.key !in s.watched) s
        else s.copy(loved = if (title.key in s.loved) s.loved - title.key else s.loved + title.key)
    }

    /** Not set → love → never show me → not set. */
    fun cycleGenre(canonical: String) = _state.update { s ->
        val next = when (s.genres[canonical]) {
            null -> GenreChoice.LOVE
            GenreChoice.LOVE -> GenreChoice.AVOID
            GenreChoice.AVOID -> null
        }
        s.copy(genres = if (next == null) s.genres - canonical else s.genres + (canonical to next))
    }

    fun setFreshness(value: Freshness) = _state.update { it.copy(freshness = value) }
    fun setMix(value: Mix) = _state.update { it.copy(mix = value) }
    fun setMinRating(value: Double?) = _state.update { it.copy(minRating = value) }
    fun toggleLanguage(code: String?) = _state.update { s ->
        when {
            code == null -> s.copy(languages = emptySet())
            code in s.languages -> s.copy(languages = s.languages - code)
            else -> s.copy(languages = s.languages + code)
        }
    }

    fun goTo(step: QuizStep) = _state.update { it.copy(step = step) }
    fun back() = _state.update { s -> s.copy(step = QuizStep.entries[maxOf(0, s.step.ordinal - 1)]) }

    fun next() {
        val s = _state.value
        if (!s.canNext) return
        if (s.step != QuizStep.REVIEW) {
            _state.update { it.copy(step = QuizStep.entries[s.step.ordinal + 1]) }
            return
        }
        _state.update { it.copy(saving = true) }
        viewModelScope.launch {
            repository.saveTaste(s.taste, s.watched.values.toList(), s.loved)
            _state.update { it.copy(saving = false, done = true) }
        }
    }

    fun restart() = _state.update { it.copy(done = false, step = QuizStep.WATCHED) }

    companion object {
        const val SEARCH_DEBOUNCE_MS = 350L
    }
}
