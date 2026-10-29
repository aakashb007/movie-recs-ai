package com.reelwise.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Repository
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnboardingUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val titles: List<TitleSummary> = emptyList(),
    val ratings: Map<TitleKey, Signal> = emptyMap(),
) {
    val ratedCount: Int get() = ratings.size
    val likeCount: Int get() = ratings.values.count { it == Signal.LIKE }

    /** Five ratings with at least one like is enough for the model to find a direction. */
    val canFinish: Boolean get() = ratedCount >= MIN_RATINGS && likeCount >= 1

    companion object {
        const val MIN_RATINGS = 5
    }
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(private val repository: Repository) : ViewModel() {
    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        _state.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            repository.onboarding()
                .onSuccess { titles -> _state.update { it.copy(loading = false, titles = titles) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    /** Selecting the current signal again clears it. */
    fun rate(title: TitleSummary, signal: Signal) {
        val current = _state.value.ratings[title.key]
        val next = if (current == signal) null else signal
        _state.update {
            it.copy(ratings = if (next == null) it.ratings - title.key else it.ratings + (title.key to next))
        }
        viewModelScope.launch { repository.rate(title, next) }
    }

    fun finish(onDone: () -> Unit) {
        viewModelScope.launch {
            repository.setOnboardingDone(true)
            onDone()
        }
    }
}
