package com.reelwise.app.ui.person

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.reelwise.app.data.Repository
import com.reelwise.app.data.model.CreditKind
import com.reelwise.app.data.model.PersonCredit
import com.reelwise.app.data.model.PersonDetail
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleKey
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.data.userMessage
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.ErrorState
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.components.SectionHeader
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.metaLine
import com.reelwise.app.ui.navigation.Dest
import com.reelwise.app.ui.radial.RadialRequest
import com.reelwise.app.ui.radial.radialMenu
import com.reelwise.app.ui.radial.titleOptions
import com.reelwise.app.ui.theme.BrandRed
import com.reelwise.app.ui.theme.Eyebrow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PersonUiState(
    val loading: Boolean = true,
    val person: PersonDetail? = null,
    val error: String? = null,
    /** Titles the person has already watched or rated, to tick them in the filmography. */
    val rated: Set<TitleKey> = emptySet(),
    val saved: Set<TitleKey> = emptySet(),
)

private data class PersonLoad(val loading: Boolean = true, val person: PersonDetail? = null, val error: String? = null)

@HiltViewModel
class PersonViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: Repository,
) : ViewModel() {
    private val personId = savedStateHandle.toRoute<Dest.Person>().id
    private val load = MutableStateFlow(PersonLoad())

    val state: StateFlow<PersonUiState> = combine(
        load,
        repository.interactions.map { list -> list.map { TitleKey(it.id, it.mediaType) }.toSet() },
        repository.watchlist.map { list -> list.map { TitleKey(it.id, it.mediaType) }.toSet() },
    ) { l, rated, saved -> PersonUiState(l.loading, l.person, l.error, rated, saved) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState())

    init {
        reload()
    }

    fun reload() {
        load.value = PersonLoad()
        viewModelScope.launch {
            repository.person(personId)
                .onSuccess { load.value = PersonLoad(loading = false, person = it) }
                .onFailure { load.value = PersonLoad(loading = false, error = it.userMessage()) }
        }
    }

    fun rate(title: TitleSummary, signal: Signal) {
        viewModelScope.launch { repository.rate(title, signal) }
    }

    fun toggleWatchlist(title: TitleSummary) {
        viewModelScope.launch { repository.toggleWatchlist(title) }
    }
}

@Composable
fun PersonRoute(onBack: () -> Unit, onOpenTitle: (TitleSummary) -> Unit, viewModel: PersonViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PersonContent(
        state = state,
        onBack = onBack,
        onOpenTitle = onOpenTitle,
        onRetry = viewModel::reload,
        menuFor = { t -> RadialRequest(t, titleOptions(t, t.key in state.saved, onOpenTitle, viewModel::rate, viewModel::toggleWatchlist)) },
    )
}

/** A director's or actor's page: photo, bio, best-known titles and the full filmography. */
@Composable
fun PersonContent(
    state: PersonUiState,
    onBack: () -> Unit,
    onOpenTitle: (TitleSummary) -> Unit,
    onRetry: () -> Unit,
    menuFor: (TitleSummary) -> RadialRequest? = { null },
) {
    val person = state.person
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            person != null -> PersonBody(person, state, onOpenTitle, menuFor)
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

@Composable
private fun PersonBody(person: PersonDetail, state: PersonUiState, onOpenTitle: (TitleSummary) -> Unit, menuFor: (TitleSummary) -> RadialRequest?) {
    var kind by rememberSaveable { mutableStateOf<CreditKind?>(null) }
    val kinds = remember(person) { person.credits.map { it.kind }.distinct() }
    val shown = person.credits.filter { kind == null || it.kind == kind }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "header") {
            Column(
                Modifier.fillMaxWidth().statusBarsPadding().padding(top = 56.dp, start = 20.dp, end = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.size(132.dp).clip(CircleShape)) {
                    ArtworkImage(Tmdb.profile(person.profilePath), person.name.take(1), Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(14.dp))
                Text(person.department.uppercase(), style = Eyebrow, color = MaterialTheme.colorScheme.tertiary)
                Text(person.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                val life = listOfNotNull(
                    person.birthday?.let { "Born ${it.take(4)}" + (person.placeOfBirth?.let { p -> " · $p" } ?: "") },
                    person.deathday?.let { "Died ${it.take(4)}" },
                ).joinToString("  ·  ")
                if (life.isNotEmpty()) {
                    Text(life, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (person.biography.isNotBlank()) {
            item(key = "bio") {
                var expanded by rememberSaveable { mutableStateOf(false) }
                Text(
                    person.biography,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (expanded) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .animateContentSize()
                        .clickable(onClickLabel = if (expanded) "Show less" else "Read more") { expanded = !expanded },
                )
            }
        }
        if (person.knownFor.isNotEmpty()) {
            item(key = "known") {
                SectionHeader(null, "Known for")
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(person.knownFor, key = { "${it.mediaType}-${it.id}" }) { t ->
                        PosterCard(
                            t,
                            onClick = { onOpenTitle(t) },
                            width = 116.dp,
                            menu = menuFor(t)?.let { req -> Modifier.radialMenu { req } } ?: Modifier,
                        )
                    }
                }
            }
        }
        item(key = "filmography-title") {
            Spacer(Modifier.height(24.dp))
            SectionHeader(null, "Filmography · ${person.credits.size}")
            if (kinds.size > 1) {
                Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == null, onClick = { kind = null }, label = { Text("All") })
                    kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(kindLabel(k)) }) }
                }
            }
        }
        items(shown, key = { "${it.kind}-${it.title.mediaType}-${it.title.id}" }) { credit ->
            CreditRow(credit, credit.title.key in state.rated, onOpenTitle, menuFor(credit.title))
        }
    }
}

private fun kindLabel(k: CreditKind) = when (k) {
    CreditKind.DIRECTED -> "Directed"
    CreditKind.CREATED -> "Created"
    CreditKind.ACTED -> "Acted"
}

@Composable
private fun CreditRow(credit: PersonCredit, rated: Boolean, onOpenTitle: (TitleSummary) -> Unit, menu: RadialRequest?) {
    val t = credit.title
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open details") { onOpenTitle(t) }
            .then(menu?.let { req -> Modifier.radialMenu { req } } ?: Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            t.year?.toString() ?: "Soon",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(46.dp),
        )
        Box(Modifier.width(44.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(6.dp))) {
            ArtworkImage(Tmdb.poster(t.posterPath, "w154"), t.title, Modifier.fillMaxSize())
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(t.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val role = when (credit.kind) {
                CreditKind.ACTED -> credit.role.takeIf { it.isNotBlank() }?.let { "as $it" } ?: "Cast"
                else -> credit.role
            }
            Text(
                "$role · ${t.metaLine()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (rated) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = BrandRed,
                modifier = Modifier.size(20.dp).semantics { contentDescription = "You've rated or watched this" },
            )
        }
    }
}
