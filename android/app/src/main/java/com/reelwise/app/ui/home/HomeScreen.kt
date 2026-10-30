package com.reelwise.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
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
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.MessageState
import com.reelwise.app.ui.components.PaletteImage
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.formatRating
import com.reelwise.app.ui.components.metaLine
import com.reelwise.app.ui.components.shimmer
import com.reelwise.app.ui.components.spokenDescription
import com.reelwise.app.ui.navigation.navClearance
import com.reelwise.app.ui.radial.radialMenu
import com.reelwise.app.ui.radial.RadialRequest
import com.reelwise.app.ui.radial.titleOptions
import com.reelwise.app.ui.theme.BrandRed
import com.reelwise.app.ui.theme.PosterType
import kotlin.math.absoluteValue

/** Callbacks for the For you and Browse screens, bundled so previews and tests pass one object. */
data class HomeActions(
    val onFilter: (FeedFilter) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onOpenTitle: (TitleSummary) -> Unit = {},
    val onRateMore: () -> Unit = {},
    val onSearch: () -> Unit = {},
    val onToggleTheme: () -> Unit = {},
    val onToggleSaved: (TitleSummary) -> Unit = {},
    val onRate: (TitleSummary, Signal) -> Unit = { _, _ -> },
    /** Swiped on from a For you card: a quiet "not this one" the taste model learns from. */
    val onSwipedPast: (TitleSummary) -> Unit = {},
) {
    fun menuFor(title: TitleSummary, saved: Boolean) =
        RadialRequest(title, titleOptions(title, saved, onOpenTitle, onRate, onToggleSaved))
}

@Composable
fun rememberHomeActions(viewModel: HomeViewModel, onOpenTitle: (TitleSummary) -> Unit, onRateMore: () -> Unit, onSearch: () -> Unit): HomeActions {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return remember(viewModel, dark) {
        HomeActions(
            onFilter = viewModel::setFilter,
            onRefresh = { viewModel.refresh() },
            onOpenTitle = onOpenTitle,
            onRateMore = onRateMore,
            onSearch = onSearch,
            onToggleTheme = { viewModel.toggleTheme(dark) },
            onToggleSaved = viewModel::toggleWatchlist,
            onRate = { t, s -> viewModel.rate(t, s) },
            onSwipedPast = viewModel::swipedPast,
        )
    }
}

@Composable
fun HomeRoute(
    onOpenTitle: (TitleSummary) -> Unit,
    onRateMore: () -> Unit,
    onSearch: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ForYouContent(state, rememberHomeActions(viewModel, onOpenTitle, onRateMore, onSearch))
}

/** "For you": every pick as one big poster, swiped left and right. Hold a poster for the circle menu. */
@Composable
fun ForYouContent(state: HomeUiState, actions: HomeActions) {
    val pager = rememberPagerState { state.items.size }
    val latestItems by rememberUpdatedState(state.items)
    LaunchedEffect(pager) {
        var last = pager.settledPage
        snapshotFlow { pager.settledPage }.collect { page ->
            if (page > last) latestItems.getOrNull(last)?.let { actions.onSwipedPast(it.title) }
            last = page
        }
    }
    val tints = remember { mutableStateMapOf<TitleKey, Color>() }
    val current = state.items.getOrNull(pager.currentPage)
    val background = MaterialTheme.colorScheme.background
    val dark = background.luminance() < 0.5f
    val raw = current?.let { tints[it.title.key] ?: fallbackTint(it.title.key) } ?: background
    // The screen takes on the front poster's colour: deep in dark mode, a pale wash in light mode.
    val tint by animateColorAsState(
        if (current == null) background
        else if (dark) raw.copy(alpha = 0.6f).compositeOver(Color.Black) else raw.copy(alpha = 0.18f).compositeOver(Color.White),
        tween(700),
        label = "tint",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to tint, 0.75f to tint.copy(alpha = 0.85f).compositeOver(background), 1f to background)),
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().padding(bottom = navClearance())) {
            TopBar(state, actions, title = null)
            when {
                state.items.isEmpty() && state.error != null -> ErrorState(state.error, actions.onRefresh)
                state.items.isEmpty() && (state.firstLoad || state.refreshing) -> BigCardSkeleton(Modifier.weight(1f))
                state.items.isEmpty() -> MessageState(
                    Icons.Rounded.AutoAwesome,
                    "You've seen it all",
                    "Every pick here has been rated. Refresh for a new batch.",
                    actionLabel = "Refresh",
                    onAction = actions.onRefresh,
                )
                else -> {
                    if (state.coldStart) {
                        TextButton(onClick = actions.onRateMore, modifier = Modifier.padding(horizontal = 8.dp)) {
                            Text("These are crowd favourites. Take the taste quiz to make them yours →")
                        }
                    }
                    state.error?.let { OfflineNote(it, actions.onRefresh) }
                    BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                        val cardHeight = minOf(maxHeight * 0.96f, maxWidth * 0.72f * 1.5f)
                        val cardWidth = cardHeight / 1.5f
                        HorizontalPager(
                            state = pager,
                            pageSize = PageSize.Fixed(cardWidth),
                            contentPadding = PaddingValues(horizontal = (maxWidth - cardWidth) / 2),
                            pageSpacing = -(cardWidth * 0.1f),
                            beyondViewportPageCount = 2,
                            modifier = Modifier.fillMaxSize(),
                        ) { page ->
                            val item = state.items[page]
                            BigCard(
                                item = item,
                                pager = pager,
                                page = page,
                                height = cardHeight,
                                onTint = { tints[item.title.key] = it },
                                onClick = { actions.onOpenTitle(item.title) },
                                menu = { actions.menuFor(item.title, item.title.key in state.saved) },
                            )
                        }
                    }
                    current?.let { item ->
                        AnimatedContent(
                            targetState = item,
                            transitionSpec = { (fadeIn(tween(280)) + slideInVertically(tween(280)) { it / 6 }).togetherWith(fadeOut(tween(140))) },
                            contentKey = { it.title.key },
                            label = "card-text",
                        ) { shown -> CardText(shown, shown.title.key in state.saved, actions) }
                    }
                    Text(
                        "${pager.currentPage + 1} / ${state.items.size}  ·  hold a poster for quick actions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun TopBar(state: HomeUiState, actions: HomeActions, title: String?, modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Column(modifier.padding(top = 8.dp)) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (title == null) {
                Text(
                    "REELWISE",
                    style = PosterType.copy(fontSize = 26.sp, letterSpacing = 1.sp),
                    color = BrandRed,
                    modifier = Modifier.weight(1f).semantics { heading() },
                )
            } else {
                Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f).semantics { heading() })
            }
            IconButton(onClick = actions.onRefresh) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh picks") }
            IconButton(onClick = actions.onSearch) { Icon(Icons.Rounded.Search, contentDescription = "Ask Reelwise") }
            IconButton(onClick = actions.onToggleTheme) {
                Icon(
                    if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                    contentDescription = if (dark) "Switch to light theme" else "Switch to dark theme",
                )
            }
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FILTERS.forEach { (f, label) ->
                FilterChip(selected = state.filter == f, onClick = { actions.onFilter(f) }, label = { Text(label) })
            }
        }
    }
}

val FILTERS = listOf(
    FeedFilter.ALL to "All",
    FeedFilter.MOVIES to "Movies",
    FeedFilter.SERIES to "Series",
    FeedFilter.FEEL_GOOD to "Feel-good",
    FeedFilter.CLASSICS to "Classics",
)

/** A colour from a title's id, until the poster loads and Palette gives the real one. */
private fun fallbackTint(key: TitleKey): Color {
    val hues = listOf(0xFF6B4320, 0xFF1C3348, 0xFF5A2440, 0xFF2B5E52, 0xFF3E4A8A, 0xFF7A2A22)
    return Color(hues[(key.id + key.mediaType.ordinal).mod(hues.size)])
}

@Composable
private fun BigCard(
    item: RecItem,
    pager: PagerState,
    page: Int,
    height: androidx.compose.ui.unit.Dp,
    onTint: (Color) -> Unit,
    onClick: () -> Unit,
    menu: () -> RadialRequest,
) {
    val offset = (pager.currentPage - page) + pager.currentPageOffsetFraction
    val distance = offset.absoluteValue.coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .graphicsLayer {
                val scale = lerp(1f, 0.82f, distance)
                scaleX = scale
                scaleY = scale
                rotationZ = offset.coerceIn(-1.5f, 1.5f) * 5f
                alpha = lerp(1f, 0.5f, distance)
            }
            .shadow(if (distance < 0.5f) 24.dp else 0.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClickLabel = "Open details", onClick = onClick)
            .radialMenu(menu)
            .semantics {
                contentDescription = item.title.spokenDescription(item.reason)
                role = Role.Button
            },
    ) {
        PaletteImage(Tmdb.poster(item.title.posterPath, "w780"), item.title.title, onTint, Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = distance * 0.45f)))
        item.title.imdbRating?.let { ImdbBadge(it, Modifier.align(Alignment.TopStart).padding(12.dp)) }
    }
}

@Composable
fun ImdbBadge(rating: Double, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, shape = RoundedCornerShape(6.dp), color = Color(0xFFF5C518), contentColor = Color.Black) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("IMDb", style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Black))
            Spacer(Modifier.width(4.dp))
            Text(formatRating(rating), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun CardText(item: RecItem, saved: Boolean, actions: HomeActions) {
    val title = item.title
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            title.title,
            style = PosterType.copy(fontSize = 30.sp, lineHeight = 32.sp),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title.metaLine(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (title.voteAverage > 0 && title.imdbRating == null) {
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(14.dp))
                Text(formatRating(title.voteAverage), style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (item.reason.isNotBlank()) {
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = BrandRed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(item.reason, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { actions.onOpenTitle(title) },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onBackground,
                    contentColor = MaterialTheme.colorScheme.background,
                ),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Icon(Icons.Rounded.Info, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Details")
            }
            FilledTonalButton(
                onClick = { actions.onToggleSaved(title) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.weight(1f).height(46.dp),
            ) {
                Icon(if (saved) Icons.Rounded.Check else Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (saved) "On my list" else "My list")
            }
            FilledTonalIconButton(
                onClick = { actions.onRate(title, Signal.DISLIKE) },
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(46.dp),
            ) {
                Icon(Icons.Rounded.ThumbDown, contentDescription = "Not for me")
            }
        }
    }
}

@Composable
private fun BigCardSkeleton(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().semantics { contentDescription = "Loading recommendations" }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth(0.7f).height(420.dp).clip(RoundedCornerShape(24.dp)).shimmer())
    }
}

@Composable
private fun OfflineNote(message: String, onRetry: () -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text("Saved picks. $message", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 2)
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}
