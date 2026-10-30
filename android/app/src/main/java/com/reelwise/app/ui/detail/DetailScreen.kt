package com.reelwise.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.Provider
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleDetail
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.components.SectionHeader
import com.reelwise.app.ui.components.SignalBar
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.formatRating
import com.reelwise.app.ui.theme.Eyebrow

@Composable
fun DetailRoute(
    onBack: () -> Unit,
    onOpenTitle: (TitleSummary) -> Unit,
    onOpenPerson: (Int) -> Unit = {},
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val uri = LocalUriHandler.current
    DetailContent(
        state = state,
        onBack = onBack,
        onSignal = viewModel::rate,
        onToggleWatchlist = viewModel::toggleWatchlist,
        onOpenTitle = onOpenTitle,
        onRetry = viewModel::reload,
        onOpenUrl = { runCatching { uri.openUri(it) } },
        onOpenPerson = onOpenPerson,
    )
}

@Composable
fun DetailContent(
    state: DetailUiState,
    onBack: () -> Unit,
    onSignal: (Signal?) -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenTitle: (TitleSummary) -> Unit,
    onRetry: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPerson: (Int) -> Unit = {},
) {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        val detail = state.detail
        when {
            detail != null -> Body(detail, state, onSignal, onToggleWatchlist, onOpenTitle, onOpenUrl, onOpenPerson)
            state.error != null -> Box(Modifier.align(Alignment.Center)) { ErrorState(state.error, onRetry) }
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f)),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Body(
    detail: TitleDetail,
    state: DetailUiState,
    onSignal: (Signal?) -> Unit,
    onToggleWatchlist: () -> Unit,
    onOpenTitle: (TitleSummary) -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenPerson: (Int) -> Unit,
) {
    val s = detail.summary
    val bg = MaterialTheme.colorScheme.background
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "backdrop") {
            Box(Modifier.fillMaxWidth().aspectRatio(16f / 11f)) {
                ArtworkImage(Tmdb.backdrop(s.backdropPath, "w1280") ?: Tmdb.poster(s.posterPath, "w780"), s.title, Modifier.fillMaxSize())
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0f to bg.copy(alpha = 0.25f),
                                0.55f to Color.Transparent,
                                1f to bg,
                            ),
                        ),
                )
            }
        }
        item(key = "title") {
            Row(Modifier.padding(horizontal = 16.dp).offset(y = (-56).dp)) {
                Box(
                    Modifier
                        .width(112.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    ArtworkImage(Tmdb.poster(s.posterPath), s.title, Modifier.fillMaxSize())
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.align(Alignment.Bottom)) {
                    Text(
                        if (s.mediaType == MediaType.TV) "SERIES" else "FILM",
                        style = Eyebrow,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        s.title,
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(
                        factsLine(detail),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (s.voteAverage > 0) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.semantics(mergeDescendants = true) {
                                contentDescription = "Rated ${formatRating(s.voteAverage)} out of 10 from ${s.voteCount} votes"
                            },
                        ) {
                            Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(formatRating(s.voteAverage), style = MaterialTheme.typography.titleMedium)
                            Text(
                                " / 10 · ${"%,d".format(java.util.Locale.US, s.voteCount)} votes",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        item(key = "actions") {
            Column(Modifier.offset(y = (-40).dp).padding(horizontal = 16.dp)) {
                detail.imdb?.let { score ->
                    Row(
                        Modifier
                            .padding(bottom = 12.dp)
                            .then(detail.imdbId?.let { id -> Modifier.clickable(onClickLabel = "Open on IMDb") { onOpenUrl("https://www.imdb.com/title/$id/") } } ?: Modifier)
                            .semantics(mergeDescendants = true) {},
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        com.reelwise.app.ui.home.ImdbBadge(score.rating)
                        score.votes?.let {
                            Text("${"%,d".format(java.util.Locale.US, it)} IMDb votes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        score.rottenTomatoes?.let {
                            Text("Rotten Tomatoes $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    detail.trailerYoutubeKey?.let { key ->
                        Button(onClick = { onOpenUrl("https://www.youtube.com/watch?v=$key") }) {
                            Icon(Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Trailer")
                        }
                    }
                    FilledTonalButton(onClick = onToggleWatchlist) {
                        Icon(if (state.inWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.inWatchlist) "On watchlist" else "Watchlist")
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.horizontalScroll(rememberScrollState())) { SignalBar(state.signal, onSignal) }
                if (s.genres.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.genres.forEach { SuggestionChip(onClick = {}, label = { Text(it) }, enabled = false) }
                    }
                }
                if (detail.tagline.isNotBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "“${detail.tagline}”",
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (s.overview.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(s.overview, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item(key = "providers") { WhereToWatch(detail, state.region, onOpenUrl) }
        if (detail.crew.isNotEmpty()) {
            item(key = "crew") {
                Spacer(Modifier.height(24.dp))
                val heading = when {
                    detail.crew.all { it.job == "Director" } -> "Directed by"
                    detail.crew.all { it.job == "Creator" } -> "Created by"
                    else -> "Created and directed by"
                }
                SectionHeader(null, heading)
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(detail.crew, key = { it.id }) { person ->
                        PersonChip(person.name, person.job, person.profilePath, { onOpenPerson(person.id) })
                    }
                }
            }
        }
        if (detail.cast.isNotEmpty()) {
            item(key = "cast") {
                Spacer(Modifier.height(24.dp))
                SectionHeader(null, "Cast")
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(detail.cast, key = { (it.id ?: 0).toString() + it.name + it.character }) { member ->
                        PersonChip(member.name, member.character, member.profilePath, member.id?.let { id -> { onOpenPerson(id) } })
                    }
                }
            }
        }
        if (detail.similar.isNotEmpty()) {
            item(key = "similar") {
                Spacer(Modifier.height(28.dp))
                SectionHeader("If you like ${s.title}", "More like this")
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(detail.similar, key = { "${it.mediaType}-${it.id}" }) { t ->
                        PosterCard(t, onClick = { onOpenTitle(t) }, width = 120.dp)
                    }
                }
            }
        }
        item(key = "attribution") {
            Text(
                "Title data and images from TMDB. Streaming availability from JustWatch.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 28.dp),
            )
        }
    }
}

/** A round photo with a name and role; tappable when it leads to a filmography. */
@Composable
fun PersonChip(name: String, role: String, profilePath: String?, onClick: (() -> Unit)?) {
    Column(
        Modifier
            .width(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "See filmography", onClick = onClick) else Modifier)
            .padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape)) {
            ArtworkImage(Tmdb.profile(profilePath), name.take(1), Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(6.dp))
        Text(name, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            role,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun factsLine(detail: TitleDetail): String = listOfNotNull(
    detail.summary.year?.toString(),
    detail.runtime?.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60}h ${it % 60}m" else "${it}m" },
    detail.seasons?.let { if (it == 1) "1 season" else "$it seasons" },
    detail.status.takeIf { detail.summary.mediaType == MediaType.TV && it.isNotBlank() },
).joinToString(" · ")

@Composable
private fun WhereToWatch(detail: TitleDetail, region: String, onOpenUrl: (String) -> Unit) {
    val p = detail.providers
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text(
            "Where to watch in $region",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(10.dp))
        if (p.isEmpty) {
            Text(
                "Not available to stream, rent or buy in $region right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            listOf("Stream" to p.stream, "Free" to p.free, "Rent" to p.rent, "Buy" to p.buy)
                .filter { it.second.isNotEmpty() }
                .forEach { (label, providers) -> ProviderRow(label, providers) }
            p.link?.let { link -> TextButton(onClick = { onOpenUrl(link) }) { Text("See all options") } }
        }
    }
}

@Composable
private fun ProviderRow(label: String, providers: List<Provider>) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(64.dp),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            providers.forEach { provider ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.size(44.dp).semantics { contentDescription = provider.name },
                ) {
                    ArtworkImage(Tmdb.logo(provider.logoPath), provider.name.take(2), Modifier.fillMaxSize())
                }
            }
        }
    }
}
