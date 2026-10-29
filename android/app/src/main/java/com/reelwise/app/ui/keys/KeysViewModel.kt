package com.reelwise.app.ui.keys

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Repository
import com.reelwise.app.data.userMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class KeysUiState(
    val tmdbKey: String = "",
    val groqKey: String = "",
    val checking: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    /** True when keys were already saved before this visit (opened from Profile). */
    val editing: Boolean = false,
) {
    val canSave: Boolean get() = tmdbKey.isNotBlank() && !checking
}

@HiltViewModel
class KeysViewModel @Inject constructor(private val repository: Repository) : ViewModel() {
    private val _state = MutableStateFlow(KeysUiState())
    val state: StateFlow<KeysUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val (tmdb, groq) = repository.savedKeys()
            _state.update { it.copy(tmdbKey = tmdb.orEmpty(), groqKey = groq.orEmpty(), editing = !tmdb.isNullOrBlank()) }
        }
    }

    fun onTmdb(value: String) = _state.update { it.copy(tmdbKey = value.trim(), error = null, saved = false) }
    fun onGroq(value: String) = _state.update { it.copy(groqKey = value.trim(), error = null, saved = false) }

    /** Calls TMDB with the key; only a key TMDB accepts is saved. */
    fun testAndSave() {
        val s = _state.value
        if (!s.canSave) return
        _state.update { it.copy(checking = true, error = null) }
        viewModelScope.launch {
            repository.saveKeys(s.tmdbKey, s.groqKey.ifBlank { null })
                .onSuccess { _state.update { it.copy(checking = false, saved = true) } }
                .onFailure { e -> _state.update { it.copy(checking = false, error = e.userMessage()) } }
        }
    }
}
