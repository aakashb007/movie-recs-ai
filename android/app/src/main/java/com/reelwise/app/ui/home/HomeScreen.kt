package com.reelwise.app.ui.home

import com.reelwise.app.ui.navigation.navClearance
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.RecItem
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.FeedSkeleton
import com.reelwise.app.ui.components.MessageState
import com.reelwise.app.ui.components.PaletteImage
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.components.SectionHeader
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.metaLine
import com.reelwise.app.ui.components.spokenDescription
import com.reelwise.app.ui.theme.BrandRed
import com.reelwise.app.ui.theme.PosterType
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

/** Callbacks for the home screen, bundled so previews and tests pass one object. */
data class HomeActions(
    val onFilter: (FeedFilter) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenTitle: (TitleSummary) -> Unit = {},
    val onMore: (TitleSummary) -> Unit = {},
    val onRateMore: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onToggleSaved: (TitleSummary) -> Unit = {},
    val onNotForMe: (TitleSummary) -> Unit = {},
)

@Composable
fun HomeRoute(
    onOpenTitle: (TitleSummary) -> Unit,
    onRateMore: () -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var sheetFor by remember { mutableStateOf<TitleSummary?>(null) }
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    HomeContent(
        state = state,
        actions = HomeActions(
            onFilter = viewModel::setFilter,
            onRefresh = { viewModel.refresh() },
            onOpenTitle = onOpenTitle,
            onMore = { sheetFor = it },
            onRateMore = onRateMore,
            onSearch = onSearch,
            onToggleTheme = { viewModel.toggleTheme(dark) },
            onToggleSaved = viewModel::toggleWatchlist,
            onNotForMe = { viewModel.rate(it, Signal.DISLIKE) },
        ),
        autoplay = true,
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
fun HomeContent(state: HomeUiState, actions: HomeActions, autoplay: Boolean = false) {
    PullToRefreshBox(
        isRefreshing = state.refreshing && state.hasContent,
        onRefresh = actions.onRefresh,
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
    ) {
        LazyColumn(contentPadding = PaddingValues(bottom = navClearance()), modifier = Modifier.fillMaxSize()) {
            when {
                !state.hasContent && state.error != null -> {
                    item(key = "header") { TopBar(state, actions, Modifier.statusBarsPadding()) }
                    item(key = "error") { ErrorState(state.error, actions.onRefresh) }
                }
                !state.hasContent && (state.firstLoad || state.refreshing) -> {
                    item(key = "header") { TopBar(state, actions, Modifier.statusBarsPadding()) }
                    item(key = "skeleton") { FeedSkeleton() }
                }
                !state.hasContent -> {
                    item(key = "header") { TopBar(state, actions, Modifier.statusBarsPadding()) }
                    item(key = "empty") {
                        MessageState(
                            Icons.Rounded.AutoAwesome,
                            "You've seen it all",
                            "Every pick here has been rated. Pull to refresh for a new batch.",
                            actionLabel = "Refresh",
                            onAction = actions.onRefresh,
                        )
                    }
                }
                else -> {
                    item(key = "hero") { Hero(state, actions, autoplay) }
                    if (state.error != null) item(key = "offline") { OfflineBanner(state.error, actions.onRefresh) }
                    if (state.coldStart) item(key = "cold") { ColdStartBanner(actions.onRateMore) }
                    state.sections.forEach { section ->
                        item(key = "section-${section.eyebrow}-${section.title}") {
                            Column(Modifier.animateItem()) {
                                Spacer(Modifier.height(24.dp))
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
                                            onLongPress = { actions.onMore(item.title) },
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
    }
}

@Composable
private fun TopBar(state: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "REELWISE",
                style = PosterType.copy(fontSize = 26.sp, letterSpacing = 1.sp),
                color = BrandRed,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            IconButton(onClick = actions.onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Ask Reelwise") }
            IconButton(onClick = actions.onToggleTheme) {
                Icon(
                    if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
                )
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(FeedFilter.ALL to "All", FeedFilter.MOVIES to "Movies", FeedFilter.SERIES to "Series").forEach { (f, label) ->
                FilterChip(selected = state.filter == f, onClick = { actions.onFilter(f) }, label = { Text(label) })
            }
        }
    }
}

/** A colour from a title's id, until the poster loads and Palette gives the real one. */
private fun fallbackTint(key: TitleKey): Color {
    val hues = listOf(0xFF6B4320, 0xFF1C3348, 0xFF5A2440, 0xFF2B5E52, 0xFF3E4A8A, 0xFF7A2A22)
    return Color(hues[(key.id + key.mediaType.ordinal).mod(hues.size)])
}

@Composable
private fun Hero(state: HomeUiState, actions: HomeActions, autoplay: Boolean) {
    val items = state.hero
    val pager = rememberPagerState { items.size }
    val tints = remember { mutableStateMapOf<TitleKey, Color>() }
    val current = items.getOrNull(pager.currentPage) ?: return
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5f
    val rawTint = tints[current.title.key] ?: fallbackTint(current.title.key)
    // The page takes on the front poster's colour: deep in dark mode, a pale wash in light mode.
    val tint by animateColorAsState(
        if (dark) rawTint.copy(alpha = 0.55f).compositeOver(Color.Black) else rawTint.copy(alpha = 0.16f).compositeOver(Color.White),
        tween(700),
        label = "tint",
    )

    if (autoplay) AutoAdvance(pager, items.size)

    Box(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(0f to tint, 0.7f to tint, 1f to background)),
    ) {
        Column(Modifier.statusBarsPadding()) {
            TopBar(state, actions)
            Spacer(Modifier.height(12.dp))
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val cardWidth = 196.dp
                val side = (maxWidth - cardWidth) / 2
                // Glow behind the front card, in its colour.
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(width = 180.dp, height = 260.dp)
                        .graphicsLayer { alpha = 0.6f }
                        .shadow(60.dp, RoundedCornerShape(40.dp), ambientColor = rawTint, spotColor = rawTint),
                )
                HorizontalPager(
                    state = pager,
                    pageSize = PageSize.Fixed(cardWidth),
                    contentPadding = PaddingValues(horizontal = side),
                    pageSpacing = (-28).dp,
                    beyondViewportPageCount = 2,
                    modifier = Modifier.fillMaxWidth().height(310.dp),
                ) { page ->
                    StackCard(items[page], pager, page, onTint = { tints[items[page].title.key] = it }) { actions.onOpenTitle(items[page].title) }
                }
            }
            AnimatedContent(
                targetState = current,
                transitionSpec = { (fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 6 }).togetherWith(fadeOut(tween(150))) },
                label = "hero-text",
                contentKey = { it.title.key },
            ) { item -> HeroText(item, state, actions) }
            Dots(items.size, pager.currentPage)
        }
    }
}

@Composable
private fun AutoAdvance(pager: PagerState, count: Int) {
    val dragged by pager.interactionSource.collectIsDraggedAsState()
    var lastTouch by remember { mutableLongStateOf(0L) }
    LaunchedEffect(dragged) { if (dragged) lastTouch = System.currentTimeMillis() }
    LaunchedEffect(count) {
        while (count > 1) {
            delay(AUTO_ADVANCE_MS)
            // Hold still for a while after the person swipes; they are looking.
            if (!pager.isScrollInProgress && System.currentTimeMillis() - lastTouch > PAUSE_AFTER_TOUCH_MS) {
                pager.animateScrollToPage((pager.currentPage + 1) % count, animationSpec = tween(700))
            }
        }
    }
}

private const val AUTO_ADVANCE_MS = 4500L
private const val PAUSE_AFTER_TOUCH_MS = 9000L

@Composable
private fun StackCard(item: RecItem, pager: PagerState, page: Int, onTint: (Color) -> Unit, onClick: () -> Unit) {
    val offset = ((pager.currentPage - page) + pager.currentPageOffsetFraction)
    val distance = offset.absoluteValue.coerceIn(0f, 1f)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        shadowElevation = if (distance < 0.5f) 18.dp else 0.dp,
        modifier = Modifier
            .height(294.dp)
            .graphicsLayer {
                val scale = lerp(1f, 0.8f, distance)
                scaleX = scale
                scaleY = scale
                rotationZ = offset.coerceIn(-1.5f, 1.5f) * 6f
                alpha = lerp(1f, 0.55f, distance)
            }
            .semantics { contentDescription = item.title.spokenDescription(item.reason) },
    ) {
        Box {
            PaletteImage(Tmdb.poster(item.title.posterPath, "w500"), item.title.title, onTint, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = distance * 0.45f)))
        }
    }
}

@Composable
private fun HeroText(item: RecItem, state: HomeUiState, actions: HomeActions) {
    val title = item.title
    val saved = title.key in state.saved
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title.title,
            style = PosterType.copy(fontSize = 34.sp, lineHeight = 36.sp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(title.metaLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.reason.isNotBlank()) {
            Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = BrandRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.reason, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { actions.onOpenTitle(title) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Details")
            }
            FilledTonalButton(
                onClick = { actions.onToggleSaved(title) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                AnimatedContent(saved, label = "saved") { isSaved ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (isSaved) Icons.Rounded.Check else Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isSaved) "On my list" else "My list")
                    }
                }
            }
            FilledTonalIconButton(
                onClick = { actions.onNotForMe(title) },
                shape = RoundedCornerShape(10.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Rounded.ThumbDown, contentDescription = "Not for me")
            }
        }
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), horizontalArrangement = Arrangement.Center) {
        repeat(count) { i ->
            val width by animateDpAsState(if (i == current) 22.dp else 6.dp, label = "dot")
            val color by animateColorAsState(
                if (i == current) BrandRed else MaterialTheme.colorScheme.outlineVariant,
                label = "dot-color",
            )
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(width = width, height = 5.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun ColdStartBanner(onRateMore: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("These are crowd favourites", style = MaterialTheme.typography.titleSmall)
                Text("Take the taste quiz and this page becomes yours.", style = MaterialTheme.typography.bodySmall)
            }
            FilledTonalButton(onClick = onRateMore) { Text("Take quiz") }
        }
    }
}

@Composable
private fun OfflineBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
    ) {
        Row(Modifier.padding(start = 14.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text("Showing saved picks. $message", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}
