package com.reelwise.app.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.clickable
import androidx.compose.ui.unit.dp
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickActionsSheet(
    title: TitleSummary,
    inWatchlist: Boolean,
    onSignal: (Signal) -> Unit,
    onToggleWatchlist: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
            Text(
                title.title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            Action(Icons.Rounded.ThumbUp, "Loved it", "More like this") { onSignal(Signal.LIKE); onDismiss() }
            Action(Icons.Rounded.ThumbDown, "Not for me", "Fewer like this") { onSignal(Signal.DISLIKE); onDismiss() }
            Action(Icons.Rounded.Visibility, "Seen it", "Hide it, no opinion") { onSignal(Signal.SEEN); onDismiss() }
            Action(Icons.Rounded.Block, "Not interested", "Just hide this one") { onSignal(Signal.DISMISS); onDismiss() }
            Action(
                if (inWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                if (inWatchlist) "Remove from watchlist" else "Add to watchlist",
                null,
            ) { onToggleWatchlist(); onDismiss() }
        }
    }
}

@Composable
private fun Action(icon: ImageVector, label: String, hint: String?, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = hint?.let { { Text(it) } },
        leadingContent = { Icon(icon, null) },
        modifier = Modifier.clickable(onClick = onClick),
    )
}
