package com.reelwise.app.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.shimmer
import com.reelwise.app.ui.theme.Eyebrow

@Composable
fun OnboardingRoute(onFinished: () -> Unit, viewModel: OnboardingViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    OnboardingContent(
        state = state,
        onRate = viewModel::rate,
        onFinish = { viewModel.finish(onFinished) },
        onRetry = viewModel::load,
    )
}

@Composable
fun OnboardingContent(
    state: OnboardingUiState,
    onRate: (TitleSummary, Signal) -> Unit,
    onFinish: () -> Unit,
    onRetry: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Box(Modifier.weight(1f)) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 148.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Header(state) }
                when {
                    state.error != null -> item(span = { GridItemSpan(maxLineSpan) }) {
                        ErrorState(state.error, onRetry)
                    }
                    state.loading -> items(8) {
                        Box(
                            Modifier
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(20.dp))
                                .shimmer(),
                        )
                    }
                    else -> items(state.titles, key = { "${it.mediaType}-${it.id}" }) { title ->
                        RateCard(title, state.ratings[title.key], onRate)
                    }
                }
            }
        }
        BottomBar(state, onFinish)
    }
}

@Composable
private fun Header(state: OnboardingUiState) {
    Column(
        Modifier
            .statusBarsPadding()
            .padding(top = 24.dp, bottom = 8.dp),
    ) {
        Text("WELCOME TO REELWISE", style = Eyebrow, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text(
            "What have you loved lately?",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Rate a few titles you've seen and skip the rest. Every rating sharpens your picks. " +
                "Your ratings stay on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        val target = OnboardingUiState.MIN_RATINGS
        val progress by animateFloatAsState((state.ratedCount.coerceAtMost(target)).toFloat() / target, label = "p")
        LinearProgressIndicator(
            progress = { progress },
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            if (state.ratedCount < target) "${state.ratedCount} of $target ratings" else "${state.ratedCount} ratings. Nice!",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RateCard(title: TitleSummary, signal: Signal?, onRate: (TitleSummary, Signal) -> Unit) {
    val borderColor by animateColorAsState(
        when (signal) {
            Signal.LIKE -> MaterialTheme.colorScheme.primary
            Signal.DISLIKE -> MaterialTheme.colorScheme.outline
            else -> Color.Transparent
        },
        label = "border",
    )
    val dim by animateFloatAsState(if (signal == Signal.DISLIKE) 0.55f else 0f, label = "dim")
    Column {
        Box(
            Modifier
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(20.dp))
                .border(3.dp, borderColor, RoundedCornerShape(20.dp)),
        ) {
            ArtworkImage(Tmdb.poster(title.posterPath), title.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim)))
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                        ),
                    )
                    .padding(top = 24.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                RateButton(
                    checked = signal == Signal.DISLIKE,
                    label = "Not for me: ${title.title}",
                    onClick = { onRate(title, Signal.DISLIKE) },
                ) { Icon(Icons.Rounded.ThumbDown, null) }
                RateButton(
                    checked = signal == Signal.LIKE,
                    label = "Loved it: ${title.title}",
                    onClick = { onRate(title, Signal.LIKE) },
                ) { Icon(Icons.Rounded.ThumbUp, null) }
            }
        }
        Text(
            title.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            listOfNotNull(title.year?.toString(), title.genres.firstOrNull()).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun RateButton(checked: Boolean, label: String, onClick: () -> Unit, icon: @Composable () -> Unit) {
    FilledIconToggleButton(
        checked = checked,
        onCheckedChange = { onClick() },
        colors = IconButtonDefaults.filledIconToggleButtonColors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White,
        ),
        modifier = Modifier
            .size(48.dp)
            .semantics {
                contentDescription = label
                stateDescription = if (checked) "Selected" else "Not selected"
            },
        content = icon,
    )
}

@Composable
private fun BottomBar(state: OnboardingUiState, onFinish: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 3.dp) {
        Row(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onFinish) { Text("Skip for now") }
            Spacer(Modifier.weight(1f))
            Button(onClick = onFinish, enabled = state.canFinish) {
                Text(if (state.canFinish) "Show my picks" else "Like at least 1, rate 5")
            }
        }
    }
}
