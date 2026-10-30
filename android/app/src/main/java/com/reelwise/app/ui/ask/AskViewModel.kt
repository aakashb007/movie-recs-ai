package com.reelwise.app.ui.ask

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Repository
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AskUiState(
    val query: String = "",
    val loading: Boolean = false,
    val result: AskResponse? = null,
    val error: String? = null,
)

@HiltViewModel
class AskViewModel @Inject constructor(private val repository: Repository) : ViewModel() {
    private val _state = MutableStateFlow(AskUiState())
    val state: StateFlow<AskUiState> = _state.asStateFlow()
    private var job: Job? = null

    fun onQueryChange(value: String) = _state.update { it.copy(query = value.take(MAX_LENGTH)) }

    fun submit(query: String = _state.value.query) {
        val q = query.trim()
        if (q.length < 2) return
        job?.cancel()
        _state.update { it.copy(query = q, loading = true, error = null) }
        job = viewModelScope.launch {
            repository.ask(q)
                .onSuccess { r -> _state.update { it.copy(loading = false, result = r) } }
                .onFailure { e -> _state.update { it.copy(loading = false, error = e.userMessage()) } }
        }
    }

    fun clear() {
        job?.cancel()
        _state.value = AskUiState()
    }

    companion object {
        const val MAX_LENGTH = 300
        val SUGGESTIONS = listOf(
            "Feel-good movies for tonight",
            "Old cult classics",
            "Mind-bending sci-fi like Inception",
            "A cosy comedy series to binge",
            "90s crime thrillers, no horror",
            "Feel-good animated movies for family night",
            "Acclaimed Korean dramas",
            "Something like Dark but lighter",
        )
    }
}
