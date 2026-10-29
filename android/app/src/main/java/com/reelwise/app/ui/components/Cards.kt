package com.reelwise.app.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.onClick as onClickAction
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.reelwise.app.data.model.MediaType
import com.reelwise.app.data.model.TitleSummary
import java.util.Locale

fun TitleSummary.metaLine(): String = listOfNotNull(
    year?.toString(),
    if (mediaType == MediaType.TV) "Series" else "Film",
    genres.firstOrNull(),
).joinToString(" · ")

fun formatRating(vote: Double): String = String.format(Locale.US, "%.1f", vote)

fun TitleSummary.spokenDescription(reason: String? = null): String = buildString {
    append(title)
    year?.let { append(", $it") }
    append(if (mediaType == MediaType.TV) ", series" else ", film")
    if (voteAverage > 0) append(", rated ${formatRating(voteAverage)} out of 10")
    if (!reason.isNullOrBlank()) append(". $reason")
}

@Composable
fun RatingPill(vote: Double, modifier: Modifier = Modifier) {
    if (vote <= 0.0) return
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = Color.Black.copy(alpha = 0.66f),
        contentColor = Color.White,
    ) {
        Row(Modifier.padding(horizontal = 7.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(3.dp))
            Text(formatRating(vote), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Poster card used in every row. Long-press opens quick actions; screen readers get the
 * same actions as named custom actions instead of a gesture they cannot discover.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    title: TitleSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    reason: String? = null,
    width: Dp = 132.dp,
    onLongPress: (() -> Unit)? = null,
) {
    Column(
        modifier
            .width(width)
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongPress)
            .clearAndSetSemantics {
                contentDescription = title.spokenDescription(reason)
                role = Role.Button
                if (onLongPress != null) {
                    customActions = listOf(CustomAccessibilityAction("More actions") { onLongPress(); true })
                }
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(16.dp)),
        ) {
            ArtworkImage(Tmdb.poster(title.posterPath), title.title, Modifier.fillMaxSize())
            RatingPill(title.voteAverage, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Text(
            title.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp, end = 2.dp),
        )
        Text(
            title.metaLine(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
        if (!reason.isNullOrBlank()) {
            Text(
                reason,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp, start = 2.dp, end = 2.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** Full-width row used for search results, where the overview helps people decide. */
@Composable
fun TitleRow(
    title: TitleSummary,
    reason: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = title.spokenDescription(reason)
                role = Role.Button
                // Surface's own click semantics sit inside this node and are cleared, so restate it.
                onClickAction { onClick(); true }
            },
    ) {
        Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .width(84.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                ArtworkImage(Tmdb.poster(title.posterPath, "w185"), title.title, Modifier.fillMaxSize())
            }
            Column(Modifier.weight(1f).padding(vertical = 4.dp)) {
                Text(title.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title.metaLine(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (title.voteAverage > 0) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Rounded.Star, null, tint = Color(0xFFFFC857), modifier = Modifier.size(14.dp))
                        Text(formatRating(title.voteAverage), style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (!reason.isNullOrBlank()) {
                    Text(
                        reason,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Text(
                    title.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
fun SectionHeader(eyebrow: String?, title: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 16.dp)) {
        if (eyebrow != null) {
            Text(
                eyebrow.uppercase(),
                style = com.reelwise.app.ui.theme.Eyebrow,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A soft scrim so text over artwork stays readable whatever the image. */
fun Modifier.bottomScrim(color: Color): Modifier = background(
    androidx.compose.ui.graphics.Brush.verticalGradient(
        0f to Color.Transparent,
        0.45f to color.copy(alpha = 0.35f),
        1f to color,
    ),
)
