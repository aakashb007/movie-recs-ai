package com.reelwise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.compose.SubcomposeAsyncImage
import coil.request.ImageRequest

object Tmdb {
    private const val BASE = "https://image.tmdb.org/t/p/"
    fun poster(path: String?, size: String = "w342") = path?.let { BASE + size + it }
    fun backdrop(path: String?, size: String = "w780") = path?.let { BASE + size + it }
    fun logo(path: String?) = path?.let { BASE + "w92" + it }
    fun profile(path: String?) = path?.let { BASE + "w185" + it }
}

/**
 * An image that degrades to a tinted card with the title when there is no artwork or it
 * fails to load, so a missing poster never leaves a blank hole in a row.
 */
@Composable
fun ArtworkImage(
    url: String?,
    fallbackText: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
) {
    val fallback: @Composable () -> Unit = { ArtworkFallback(fallbackText) }
    if (url == null) {
        Box(modifier) { fallback() }
        return
    }
    SubcomposeAsyncImage(
        model = url,
        contentDescription = null, // the surrounding card describes the title
        contentScale = contentScale,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().shimmer()) },
        error = { fallback() },
    )
}

/**
 * Artwork that also reports its main colour (Palette), so a screen can tint itself to match.
 * Software bitmaps are needed for Palette to read pixels, so this costs a little more memory:
 * use it for the few hero images, not for every poster in a row.
 */
@Composable
fun PaletteImage(url: String?, fallbackText: String, onColor: (Color) -> Unit, modifier: Modifier = Modifier) {
    if (url == null) {
        Box(modifier) { ArtworkFallback(fallbackText) }
        return
    }
    val context = LocalContext.current
    val request = remember(url) { ImageRequest.Builder(context).data(url).allowHardware(false).crossfade(true).build() }
    SubcomposeAsyncImage(
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier,
        loading = { Box(Modifier.fillMaxSize().shimmer()) },
        error = { ArtworkFallback(fallbackText) },
        onSuccess = { success ->
            val bitmap = runCatching { success.result.drawable.toBitmap() }.getOrNull() ?: return@SubcomposeAsyncImage
            Palette.from(bitmap).maximumColorCount(12).generate { palette ->
                palette?.let { p ->
                    val fallback = p.getDominantColor(0xFF333333.toInt())
                    onColor(Color(p.getDarkVibrantColor(p.getDarkMutedColor(fallback))))
                }
            }
        },
    )
}

@Composable
private fun ArtworkFallback(text: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.surfaceContainerHighest))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            color = colors.onPrimaryContainer,
            textAlign = TextAlign.Center,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(12.dp),
        )
    }
}
