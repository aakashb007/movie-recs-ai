package com.reelwise.app.ui.watchlist

import com.reelwise.app.ui.navigation.navClearance
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reelwise.app.data.Repository
import com.reelwise.app.data.local.WatchlistEntity
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.MessageState
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.theme.Eyebrow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

fun WatchlistEntity.toSummary() = TitleSummary(
    id = id, mediaType = mediaType, title = title, year = year, posterPath = posterPath, voteAverage = voteAverage,
)

@HiltViewModel
class WatchlistViewModel @Inject constructor(private val repository: Repository) : ViewModel() {
    val titles: StateFlow<List<TitleSummary>?> = repository.watchlist
        .map { list -> list.map { it.toSummary() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun remove(title: TitleSummary) {
        viewModelScope.launch { repository.toggleWatchlist(title) }
    }
}

@Composable
fun WatchlistRoute(onOpenTitle: (TitleSummary) -> Unit, onBrowse: () -> Unit, viewModel: WatchlistViewModel = hiltViewModel()) {
    val titles by viewModel.titles.collectAsStateWithLifecycle()
    WatchlistContent(titles, onOpenTitle, viewModel::remove, onBrowse)
}

@Composable
fun WatchlistContent(
    titles: List<TitleSummary>?,
    onOpenTitle: (TitleSummary) -> Unit,
    onRemove: (TitleSummary) -> Unit,
    onBrowse: () -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 120.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = navClearance()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Text("SAVED FOR LATER", style = Eyebrow, color = MaterialTheme.colorScheme.tertiary)
                Text("Watchlist", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            }
        }
        if (titles != null && titles.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                MessageState(
                    Icons.Rounded.BookmarkBorder,
                    "Nothing saved yet",
                    "Tap Watchlist on any title to keep it here for movie night.",
                    actionLabel = "Find something",
                    onAction = onBrowse,
                )
            }
        }
        items(titles.orEmpty(), key = { "${it.mediaType}-${it.id}" }) { title ->
            Box {
                PosterCard(title, onClick = { onOpenTitle(title) }, width = 200.dp)
                IconButton(
                    onClick = { onRemove(title) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Icon(Icons.Rounded.Close, contentDescription = "Remove ${title.title} from watchlist", tint = Color.White)
                }
            }
        }
    }
}
