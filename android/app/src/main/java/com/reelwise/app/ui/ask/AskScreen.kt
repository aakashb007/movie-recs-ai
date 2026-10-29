package com.reelwise.app.ui.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.AskResponse
import com.reelwise.app.data.model.QueryIntent
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.MessageState
import com.reelwise.app.ui.components.TitleRow
import com.reelwise.app.ui.theme.Eyebrow

@Composable
fun AskRoute(onOpenTitle: (TitleSummary) -> Unit, viewModel: AskViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AskContent(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onSubmit = viewModel::submit,
        onClear = viewModel::clear,
        onOpenTitle = onOpenTitle,
    )
}

@Composable
fun AskContent(
    state: AskUiState,
    onQueryChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onClear: () -> Unit,
    onOpenTitle: (TitleSummary) -> Unit,
) {
    val focus = LocalFocusManager.current
    val submit = { q: String -> focus.clearFocus(); onSubmit(q) }
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(Modifier.statusBarsPadding().padding(start = 16.dp, end = 16.dp, top = 16.dp)) {
                Text("ASK REELWISE", style = Eyebrow, color = MaterialTheme.colorScheme.primary)
                Text(
                    "What are you in the mood for?",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Describe a mood, a plot, or a title you loved") },
                    leadingIcon = { Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary) },
                    trailingIcon = {
                        when {
                            state.loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                            state.result != null && state.query.isNotEmpty() -> IconButton(onClick = onClear) {
                                Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                            }
                            else -> IconButton(onClick = { submit(state.query) }, enabled = state.query.trim().length >= 2) {
                                Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Search")
                            }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { submit(state.query) }),
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        val result = state.result
        when {
            state.error != null -> item(key = "error") { ErrorState(state.error, onRetry = { onSubmit(state.query) }) }
            result == null && !state.loading -> item(key = "suggestions") { Suggestions(submit) }
            result != null -> {
                item(key = "interpretation") { Interpretation(result) }
                if (result.items.isEmpty()) {
                    item(key = "empty") {
                        MessageState(
                            Icons.Rounded.SearchOff,
                            "Nothing matched all of that",
                            "Try loosening a filter: a wider decade, or fewer genres.",
                        )
                    }
                }
                items(result.items, key = { "${it.title.mediaType}-${it.title.id}" }) { item ->
                    TitleRow(item.title, item.reason, onClick = { onOpenTitle(item.title) }, modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Suggestions(onPick: (String) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        Text("Try one of these", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AskViewModel.SUGGESTIONS.forEach { s -> SuggestionChip(onClick = { onPick(s) }, label = { Text(s) }) }
        }
    }
}

/** Shows how the request was understood, so a wrong reading is visible instead of silently wrong results. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Interpretation(result: AskResponse) {
    val chips = interpretationChips(result.interpretation, result.references.map { it.title })
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    if (result.parser == "llm") "HOW I READ THAT" else "MATCHED BY KEYWORDS",
                    style = Eyebrow,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (result.interpretation.summary.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(result.interpretation.summary, style = MaterialTheme.typography.bodyMedium)
            }
            if (chips.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    chips.forEach { AssistChip(onClick = {}, label = { Text(it) }) }
                }
            }
            Text(
                "${result.items.size} titles",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

fun interpretationChips(intent: QueryIntent, references: List<String>): List<String> = buildList {
    when (intent.mediaType) {
        "movie" -> add("Movies")
        "tv" -> add("Series")
    }
    intent.genres.forEach { add(it.replaceFirstChar(Char::titlecase)) }
    intent.excludeGenres.forEach { add("No ${it.replaceFirstChar(Char::titlecase)}") }
    references.forEach { add("Like $it") }
    intent.keywords.forEach { add(it) }
    when {
        intent.yearMin != null && intent.yearMax != null -> add("${intent.yearMin}–${intent.yearMax}")
        intent.yearMin != null -> add("${intent.yearMin}+")
        intent.yearMax != null -> add("Before ${intent.yearMax + 1}")
    }
    intent.minRating?.let { add("Rated $it+") }
    intent.language?.let { add("Language: ${it.uppercase()}") }
}
