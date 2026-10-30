package com.reelwise.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.reelwise.app.data.model.Signal

private data class SignalOption(val signal: Signal, val label: String, val on: ImageVector, val off: ImageVector)

private val options = listOf(
    SignalOption(Signal.LIKE, "Loved it", Icons.Rounded.ThumbUp, Icons.Outlined.ThumbUp),
    SignalOption(Signal.MORE, "More like this", Icons.Rounded.AutoAwesome, Icons.Outlined.AutoAwesome),
    SignalOption(Signal.DISLIKE, "Not for me", Icons.Rounded.ThumbDown, Icons.Outlined.ThumbDown),
    SignalOption(Signal.SEEN, "Seen it", Icons.Rounded.Visibility, Icons.Outlined.Visibility),
)

/** Tapping the selected option again clears it. Wraps onto a second line on narrow screens. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SignalBar(current: Signal?, onSignal: (Signal?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val selected = current == option.signal
            FilterChip(
                selected = selected,
                onClick = { onSignal(if (selected) null else option.signal) },
                label = { Text(option.label) },
                leadingIcon = {
                    Icon(
                        if (selected) option.on else option.off,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}
