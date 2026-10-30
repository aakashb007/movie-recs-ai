package com.reelwise.app.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reelwise.app.data.Repository
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.userMessage
import com.reelwise.app.ui.navigation.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUiState(
    val loading: Boolean = true,
    val detail: TitleDetail? = null,
    val error: String? = null,
    val signal: Signal? = null,
    val inWatchlist: Boolean = false,
    val region: String = "US",
)

private data class Load(val loading: Boolean = true, val detail: TitleDetail? = null, val error: String? = null)

@HiltViewModel
class DetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: Repository,
) : ViewModel() {
    private val route = savedStateHandle.toRoute<Dest.Detail>()
    private val mediaType = if (route.type == MediaType.TV.wire) MediaType.TV else MediaType.MOVIE
    private val load = MutableStateFlow(Load())

    val state: StateFlow<DetailUiState> = combine(
        load,
        repository.signalFor(mediaType, route.id),
        repository.isInWatchlist(mediaType, route.id),
        repository.settings.map { it.region },
    ) { l, signal, saved, region ->
        DetailUiState(l.loading, l.detail, l.error, signal, saved, region)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DetailUiState())

    init {
        reload()
    }

    fun reload() {
        load.value = Load()
        viewModelScope.launch {
            repository.detail(mediaType, route.id)
                .onSuccess {
                    load.value = Load(loading = false, detail = it)
                    repository.noteOpened(it.summary)
                }
                .onFailure { load.value = Load(loading = false, error = it.userMessage()) }
        }
    }

    fun rate(signal: Signal?) {
        val summary = load.value.detail?.summary ?: return
        viewModelScope.launch { repository.rate(summary, signal) }
    }

    fun toggleWatchlist() {
        val summary = load.value.detail?.summary ?: return
        viewModelScope.launch { repository.toggleWatchlist(summary) }
    }
}
