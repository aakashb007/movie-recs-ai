package com.reelwise.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.FeedSkeleton
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.components.RatingPill
import com.reelwise.app.ui.components.SectionHeader
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.bottomScrim
import com.reelwise.app.ui.components.metaLine
import com.reelwise.app.ui.components.spokenDescription
import com.reelwise.app.ui.theme.Eyebrow

@Composable
fun HomeRoute(
    onOpenTitle: (TitleSummary) -> Unit,
    onRateMore: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetFor by remember { mutableStateOf<TitleSummary?>(null) }
    HomeContent(
        state = state,
        onFilter = viewModel::setFilter,
        onRefresh = { viewModel.refresh() },
        onOpenTitle = onOpenTitle,
        onMore = { sheetFor = it },
        onRateMore = onRateMore,
    )
    sheetFor?.let { title ->
        val inWatchlist by remember(title) { viewModel.isInWatchlist(title) }.collectAsStateWithLifecycle(false)
        QuickActionsSheet(
            title = title,
            inWatchlist = inWatchlist,
            onSignal = { signal -> viewModel.rate(title, signal) },
            onToggleWatchlist = { viewModel.toggleWatchlist(title) },
            onDismiss = { sheetFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(
    state: HomeUiState,
    onFilter: (FeedFilter) -> Unit,
    onRefresh: () -> Unit,
    onOpenTitle: (TitleSummary) -> Unit,
    onMore: (TitleSummary) -> Unit,
    onRateMore: () -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.refreshing && state.hasContent,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            item(key = "header") { Header(state.filter, onFilter) }
            when {
                !state.hasContent && state.error != null -> item(key = "error") { ErrorState(state.error, onRefresh) }
                !state.hasContent && (state.firstLoad || state.refreshing) -> item(key = "skeleton") { FeedSkeleton() }
                !state.hasContent -> item(key = "empty") {
                    com.reelwise.app.ui.components.MessageState(
                        Icons.Rounded.AutoAwesome,
                        "You've seen it all",
                        "Every pick here has been rated. Pull to refresh for a new batch.",
                        actionLabel = "Refresh",
                        onAction = onRefresh,
                    )
                }
                else -> {
                    if (state.error != null) item(key = "offline") { OfflineBanner(state.error, onRefresh) }
                    if (state.coldStart) item(key = "cold") { ColdStartBanner(onRateMore) }
                    item(key = "hero") { HeroCarousel(state.hero, onOpenTitle, onMore) }
                    state.sections.forEach { section ->
                        item(key = "section-${section.eyebrow}-${section.title}") {
                            Spacer(Modifier.height(28.dp))
                            SectionHeader(section.eyebrow, section.title)
                            Spacer(Modifier.height(12.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(section.items, key = { "${it.title.mediaType}-${it.title.id}" }) { item ->
                                    PosterCard(
                                        title = item.title,
                                        reason = item.reason.takeIf { section.eyebrow == null },
                                        onClick = { onOpenTitle(item.title) },
                                        onLongPress = { onMore(item.title) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(filter: FeedFilter, onFilter: (FeedFilter) -> Unit) {
    Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 16.dp)) {
        Text("For you", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FeedFilter.ALL to "All", FeedFilter.MOVIES to "Movies", FeedFilter.SERIES to "Series").forEach { (f, label) ->
                FilterChip(selected = filter == f, onClick = { onFilter(f) }, label = { Text(label) })
            }
        }
    }
}

@Composable
private fun HeroCarousel(items: List<RecItem>, onOpen: (TitleSummary) -> Unit, onMore: (TitleSummary) -> Unit) {
    if (items.isEmpty()) return
    val pager = rememberPagerState { items.size }
    Column {
        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 16.dp),
            pageSpacing = 12.dp,
        ) { page ->
            HeroCard(items[page], page + 1, onOpen, onMore)
        }
        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(items.size) { i ->
                val selected = pager.currentPage == i
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = if (selected) 18.dp else 6.dp, height = 6.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                )
            }
        }
    }
}

@Composable
private fun HeroCard(item: RecItem, rank: Int, onOpen: (TitleSummary) -> Unit, onMore: (TitleSummary) -> Unit) {
    val title = item.title
    Surface(
        onClick = { onOpen(title) },
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 5f)
            .semantics { contentDescription = "Top pick $rank. " + title.spokenDescription(item.reason) },
    ) {
        Box {
            ArtworkImage(
                Tmdb.poster(title.posterPath, "w780") ?: Tmdb.backdrop(title.backdropPath),
                title.title,
                Modifier.fillMaxSize(),
            )
            Box(Modifier.fillMaxSize().bottomScrim(Color(0xFF0E0D12)))
            RatingPill(title.voteAverage, Modifier.align(Alignment.TopStart).padding(14.dp))
            IconButton(
                onClick = { onMore(title) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f)),
            ) {
                Icon(Icons.Rounded.MoreHoriz, contentDescription = "More actions for ${title.title}", tint = Color.White)
            }
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text("TOP PICK #$rank", style = Eyebrow, color = Color(0xFFFFB547))
                Spacer(Modifier.height(4.dp))
                Text(
                    title.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(title.metaLine(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.8f))
                if (item.reason.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.16f), contentColor = Color.White) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(14.dp), tint = Color(0xFFFFB547))
                            Spacer(Modifier.width(6.dp))
                            Text(item.reason, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColdStartBanner(onRateMore: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("These are crowd favourites", style = MaterialTheme.typography.titleSmall)
                Text("Like a few titles and this page becomes yours.", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onRateMore) { Text("Rate titles") }
        }
    }
}

@Composable
private fun OfflineBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                "Showing saved picks. $message",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
