package com.reelwise.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.data.model.FeedFilter
import com.reelwise.app.data.model.Freshness
import com.reelwise.app.data.model.Mix
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.shimmer
import com.reelwise.app.ui.theme.BrandRed
import com.reelwise.app.ui.theme.ButtonRed
import com.reelwise.app.ui.theme.Eyebrow
import com.reelwise.app.ui.theme.PosterType

/** Every quiz action in one place, so screens and tests pass a single object. */
data class QuizActions(
    val onQuery: (String) -> Unit = {},
    val onKind: (FeedFilter) -> Unit = {},
    val onToggleWatched: (TitleSummary) -> Unit = {},
    val onToggleLoved: (TitleSummary) -> Unit = {},
    val onCycleGenre: (String) -> Unit = {},
    val onFreshness: (Freshness) -> Unit = {},
    val onMix: (Mix) -> Unit = {},
    val onLanguage: (String?) -> Unit = {},
    val onGoTo: (QuizStep) -> Unit = {},
    val onBack: () -> Unit = {},
    val onNext: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRestart: () -> Unit = {},
    val onFinished: () -> Unit = {},
)

@Composable
fun QuizRoute(onFinished: () -> Unit, viewModel: QuizViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    QuizContent(
        state,
        QuizActions(
            onQuery = viewModel::onQuery, onKind = viewModel::setKind, onToggleWatched = viewModel::toggleWatched,
            onToggleLoved = viewModel::toggleLoved, onCycleGenre = viewModel::cycleGenre, onFreshness = viewModel::setFreshness,
            onMix = viewModel::setMix, onLanguage = viewModel::toggleLanguage, onGoTo = viewModel::goTo, onBack = viewModel::back,
            onNext = viewModel::next, onRetry = viewModel::loadPool, onRestart = viewModel::restart, onFinished = onFinished,
        ),
    )
}

@Composable
fun QuizContent(state: QuizUiState, actions: QuizActions) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        if (!state.done) Header(state, actions)
        Box(Modifier.weight(1f)) {
            AnimatedContent(
                targetState = if (state.done) null else state.step,
                transitionSpec = {
                    val forward = (targetState?.ordinal ?: 99) > (initialState?.ordinal ?: -1)
                    (slideInHorizontally(tween(320)) { w -> if (forward) w / 3 else -w / 3 } + fadeIn(tween(320)))
                        .togetherWith(slideOutHorizontally(tween(260)) { w -> if (forward) -w / 3 else w / 3 } + fadeOut(tween(200)))
                },
                label = "quiz-step",
            ) { step ->
                when (step) {
                    QuizStep.WATCHED -> WatchedStep(state, actions)
                    QuizStep.GENRES -> GenresStep(state, actions)
                    QuizStep.PREFS -> PrefsStep(state, actions)
                    QuizStep.REVIEW -> ReviewStep(state, actions)
                    null -> DoneStep(state, actions)
                }
            }
        }
        if (!state.done) Footer(state, actions)
    }
}

@Composable
private fun Header(state: QuizUiState, actions: QuizActions) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("REELWISE", style = PosterType.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = BrandRed)
            Spacer(Modifier.weight(1f))
            Text(
                "Step ${state.step.ordinal + 1} of ${QuizStep.entries.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            QuizStep.entries.forEach { step ->
                val color by animateColorAsState(
                    if (step.ordinal <= state.step.ordinal) BrandRed else MaterialTheme.colorScheme.surfaceContainerHighest,
                    label = "bar",
                )
                Box(
                    Modifier
                        .weight(1f)
                        .height(24.dp)
                        .clickable(enabled = step.ordinal < state.step.ordinal, onClickLabel = "Back to step ${step.ordinal + 1}") {
                            actions.onGoTo(step)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.fillMaxWidth().height(4.dp).clip(CircleShape).background(color))
                }
            }
        }
    }
}

@Composable
private fun StepTitle(title: String, body: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
    Spacer(Modifier.height(6.dp))
    Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WatchedStep(state: QuizUiState, actions: QuizActions) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                StepTitle(
                    "What have you already watched?",
                    "Tap everything you've seen and the heart on the ones you loved. Reelwise will never suggest these again.",
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = state.query,
                    onValueChange = actions.onQuery,
                    placeholder = { Text("Search any movie or show") },
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = { if (state.searching) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) },
                    singleLine = true,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(FeedFilter.ALL to "All", FeedFilter.MOVIES to "Movies", FeedFilter.SERIES to "Series").forEach { (k, label) ->
                        FilterChip(selected = state.kind == k, onClick = { actions.onKind(k) }, label = { Text(label) })
                    }
                }
            }
        }
        when {
            state.error != null && state.pool.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                ErrorState(state.error, actions.onRetry)
            }
            state.loadingPool && state.searchResults == null -> items(12) {
                Box(Modifier.aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp)).shimmer())
            }
            state.visible.isEmpty() -> item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Nothing found. Try another spelling.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            }
            else -> items(state.visible, key = { "${it.mediaType}-${it.id}" }) { title ->
                QuizPoster(title, title.key in state.watched, title.key in state.loved, actions)
            }
        }
    }
}

@Composable
private fun QuizPoster(title: TitleSummary, watched: Boolean, loved: Boolean, actions: QuizActions) {
    val ring by animateColorAsState(if (watched) BrandRed else Color.Transparent, label = "ring")
    val press by animateFloatAsState(if (watched) 0.96f else 1f, spring(Spring.DampingRatioMediumBouncy), label = "press")
    val shown by animateFloatAsState(if (watched) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "check")
    Column {
        Box(
            Modifier
                .aspectRatio(2f / 3f)
                .scale(press)
                .clip(RoundedCornerShape(12.dp))
                .border(3.dp, ring, RoundedCornerShape(12.dp))
                .toggleable(value = watched, role = Role.Checkbox, onValueChange = { actions.onToggleWatched(title) })
                .semantics {
                    contentDescription = title.title + (title.year?.let { ", $it" } ?: "")
                    stateDescription = when {
                        loved -> "Watched and loved"
                        watched -> "Watched"
                        else -> "Not watched"
                    }
                },
        ) {
            ArtworkImage(Tmdb.poster(title.posterPath, "w185"), title.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = shown.coerceIn(0f, 1f) }.background(Color.Black.copy(alpha = 0.45f)))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        alpha = shown.coerceIn(0f, 1f)
                        scaleX = shown
                        scaleY = shown
                    }
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(BrandRed),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, null, tint = Color.White)
            }
            if (watched) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(44.dp)
                        .toggleable(value = loved, role = Role.Switch, onValueChange = { actions.onToggleLoved(title) })
                        .semantics { contentDescription = "Loved ${title.title}" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(30.dp).clip(CircleShape).background(if (loved) ButtonRed else Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (loved) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                }
            }
        }
        Text(
            title.title,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GenresStep(state: QuizUiState, actions: QuizActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        StepTitle("Which genres are you into?", "Tap once for love it. Tap again for never show me. A third tap clears it.")
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Legend(BrandRed, "Love it")
            Legend(MaterialTheme.colorScheme.outline, "Never show me", crossed = true)
        }
        FlowRow(
            Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuizUiState.GENRES.forEach { g -> GenreChip(g.label, state.genres[g.canonical]) { actions.onCycleGenre(g.canonical) } }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String, crossed: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .then(if (crossed) Modifier.border(1.5.dp, color, CircleShape) else Modifier.background(color)),
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun GenreChip(label: String, choice: GenreChoice?, onClick: () -> Unit) {
    val bg by animateColorAsState(if (choice == GenreChoice.LOVE) ButtonRed else Color.Transparent, label = "bg")
    val fg = when (choice) {
        GenreChoice.LOVE -> Color.White
        GenreChoice.AVOID -> MaterialTheme.colorScheme.onSurfaceVariant
        null -> MaterialTheme.colorScheme.onSurface
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = bg,
        contentColor = fg,
        border = BorderStroke(1.5.dp, if (choice == GenreChoice.LOVE) ButtonRed else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(44.dp).semantics {
            stateDescription = when (choice) {
                GenreChoice.LOVE -> "Love it"
                GenreChoice.AVOID -> "Never show me"
                null -> "Not set"
            }
        },
    ) {
        Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            AnimatedVisibility(choice != null) {
                Icon(
                    if (choice == GenreChoice.AVOID) Icons.Rounded.Block else Icons.Rounded.Favorite,
                    null,
                    modifier = Modifier.padding(end = 6.dp).size(16.dp),
                )
            }
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                textDecoration = if (choice == GenreChoice.AVOID) TextDecoration.LineThrough else null,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PrefsStep(state: QuizUiState, actions: QuizActions) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        StepTitle("A few more things", "These shape what counts as a good pick for you.")
        SectionLabel("HOW NEW SHOULD PICKS BE?")
        listOf(
            Triple(Freshness.LATEST, "Latest releases", "Out in the last 2 years"),
            Triple(Freshness.RECENT, "Fairly recent", "The last 10 years"),
            Triple(Freshness.ANY, "Any era", "Classics welcome too"),
        ).forEach { (value, label, hint) ->
            val selected = state.freshness == value
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.5.dp, if (selected) BrandRed else MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { actions.onFreshness(value) }),
            ) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    RadioButton(selected = selected, onClick = null)
                }
            }
        }
        SectionLabel("MOVIES OR SERIES?")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf(Mix.BOTH to "Both", Mix.MOVIES to "Mostly movies", Mix.SERIES to "Mostly series").forEach { (value, label) ->
                val selected = state.mix == value
                val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.onBackground else Color.Transparent, label = "mix")
                Box(
                    Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { actions.onMix(value) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (selected) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        SectionLabel("LANGUAGES YOU WATCH IN")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.languages.isEmpty(), onClick = { actions.onLanguage(null) }, label = { Text("Any language") })
            QuizUiState.LANGUAGES.forEach { l ->
                FilterChip(selected = l.code in state.languages, onClick = { actions.onLanguage(l.code) }, label = { Text(l.label) })
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = Eyebrow,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 22.dp, bottom = 10.dp),
    )
}

@Composable
private fun ReviewStep(state: QuizUiState, actions: QuizActions) {
    val genreLabel = { names: List<String> -> QuizUiState.GENRES.filter { it.canonical in names }.joinToString(" · ") { it.label } }
    val freshness = when (state.freshness) {
        Freshness.LATEST -> "Latest releases"
        Freshness.RECENT -> "Fairly recent"
        Freshness.ANY -> "Any era"
    }
    val mix = when (state.mix) {
        Mix.BOTH -> "Movies and series"
        Mix.MOVIES -> "Mostly movies"
        Mix.SERIES -> "Mostly series"
    }
    val languages = QuizUiState.LANGUAGES.filter { it.code in state.languages }.joinToString(", ") { it.label }.ifEmpty { "Any language" }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
        StepTitle("Your taste profile", "Check it, then save. It stays on this phone and you can change it any time in Profile.")
        Spacer(Modifier.height(14.dp))
        ReviewCard("ALREADY WATCHED · ${state.watched.size}", onEdit = { actions.onGoTo(QuizStep.WATCHED) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(state.watched.values.toList(), key = { "${it.mediaType}-${it.id}" }) { t ->
                    Box(Modifier.width(54.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
                        ArtworkImage(Tmdb.poster(t.posterPath, "w154"), t.title, Modifier.fillMaxSize())
                        if (t.key in state.loved) {
                            Icon(
                                Icons.Rounded.Favorite, contentDescription = "Loved", tint = Color.White,
                                modifier = Modifier.align(Alignment.TopEnd).padding(3.dp).size(14.dp),
                            )
                        }
                    }
                }
            }
        }
        ReviewCard("LOVED · ${state.loved.size}", onEdit = { actions.onGoTo(QuizStep.WATCHED) }) {
            Text(state.watched.values.filter { it.key in state.loved }.joinToString(", ") { it.title }.ifEmpty { "None marked" })
        }
        ReviewCard("GENRES YOU LOVE", onEdit = { actions.onGoTo(QuizStep.GENRES) }) { Text(genreLabel(state.lovedGenres).ifEmpty { "None picked" }) }
        ReviewCard("NEVER SHOW", onEdit = { actions.onGoTo(QuizStep.GENRES) }) { Text(genreLabel(state.avoidedGenres).ifEmpty { "Nothing excluded" }) }
        ReviewCard("PREFERENCES", onEdit = { actions.onGoTo(QuizStep.PREFS) }) { Text("$freshness · $mix · $languages") }
    }
}

@Composable
private fun ReviewCard(label: String, onEdit: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = Eyebrow, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onEdit, modifier = Modifier.semantics { contentDescription = "Edit ${label.lowercase()}" }) {
                    Text("Edit", color = MaterialTheme.colorScheme.tertiary)
                }
            }
            Box(Modifier.padding(end = 12.dp)) {
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyLarge,
                ) { content() }
            }
        }
    }
}

@Composable
private fun DoneStep(state: QuizUiState, actions: QuizActions) {
    var progress by remember { mutableFloatStateOf(0f) }
    val animated by animateFloatAsState(progress, tween(1600), label = "progress")
    LaunchedEffect(Unit) { progress = 1f }
    var shown by remember { mutableFloatStateOf(0f) }
    val checkScale by animateFloatAsState(shown, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow), label = "check")
    LaunchedEffect(Unit) { shown = 1f }
    val loves = QuizUiState.GENRES.filter { it.canonical in state.lovedGenres }.take(3).joinToString(", ") { it.label }
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(104.dp).scale(checkScale).clip(CircleShape).background(BrandRed), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("Taste saved", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.watched.size} watched titles will never be suggested. Picks will lean towards ${loves.ifEmpty { "what you loved" }}.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        LinearProgressIndicator(
            progress = { animated },
            color = BrandRed,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.width(220.dp).height(4.dp).clip(CircleShape),
        )
        Spacer(Modifier.height(28.dp))
        Button(onClick = actions.onFinished, shape = RoundedCornerShape(26.dp), modifier = Modifier.height(52.dp)) {
            Text("See my picks", modifier = Modifier.padding(horizontal = 16.dp))
        }
        TextButton(onClick = actions.onRestart) { Text("Change my answers") }
    }
}

@Composable
private fun Footer(state: QuizUiState, actions: QuizActions) {
    val note = when (state.step) {
        QuizStep.WATCHED -> if (state.watched.size < QuizUiState.MIN_WATCHED) {
            "Pick at least ${QuizUiState.MIN_WATCHED} you've seen"
        } else {
            "${state.watched.size} watched · ${state.loved.size} loved"
        }
        QuizStep.GENRES -> if (state.lovedGenres.isEmpty()) "Love at least 1 genre" else "${state.lovedGenres.size} loved · ${state.avoidedGenres.size} avoided"
        QuizStep.PREFS -> "Almost done"
        QuizStep.REVIEW -> "Saved only on this phone"
    }
    Surface(color = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
        Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.step != QuizStep.WATCHED) {
                    TextButton(onClick = actions.onBack) { Text("Back") }
                }
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = actions.onNext,
                    enabled = state.canNext,
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier.height(50.dp),
                ) {
                    if (state.saving) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(if (state.step == QuizStep.REVIEW) "Looks good, save" else "Next")
                    }
                }
            }
        }
    }
}
