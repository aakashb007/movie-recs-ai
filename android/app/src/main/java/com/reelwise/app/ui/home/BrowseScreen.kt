package com.reelwise.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.FeedSkeleton
import com.reelwise.app.ui.components.MessageState
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.components.SectionHeader
import com.reelwise.app.ui.navigation.navClearance
import com.reelwise.app.ui.radial.radialMenu

@Composable
fun BrowseRoute(
    onOpenTitle: (TitleSummary) -> Unit,
    onRateMore: () -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BrowseContent(state, rememberHomeActions(viewModel, onOpenTitle, onRateMore, onSearch))
}

/** "Browse": the same picks as For you, in rows titled by why they were picked. */
@Composable
fun BrowseContent(state: HomeUiState, actions: HomeActions) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = navClearance()),
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding(),
    ) {
        item(key = "header") { TopBar(state, actions, title = "Browse") }
        when {
            state.sections.isEmpty() && state.error != null -> item(key = "error") { ErrorState(state.error, actions.onRefresh) }
            state.sections.isEmpty() && (state.firstLoad || state.refreshing) -> item(key = "skeleton") { FeedSkeleton() }
            state.sections.isEmpty() -> item(key = "empty") {
                MessageState(
                    Icons.Rounded.AutoAwesome,
                    "You've seen it all",
                    "Every pick here has been rated. Refresh for a new batch.",
                    actionLabel = "Refresh",
                    onAction = actions.onRefresh,
                )
            }
            else -> state.sections.forEach { section ->
                item(key = "section-${section.eyebrow}-${section.title}") {
                    Column(Modifier.animateItem()) {
                        Spacer(Modifier.height(20.dp))
                        SectionHeader(section.eyebrow, section.title)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(section.items, key = { "${it.title.mediaType}-${it.title.id}" }) { item ->
                                PosterCard(
                                    title = item.title,
                                    reason = item.reason.takeIf { section.eyebrow == null },
                                    isNew = item.isNew(state.currentYear),
                                    onClick = { actions.onOpenTitle(item.title) },
                                    menu = Modifier.radialMenu { actions.menuFor(item.title, item.title.key in state.saved) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
