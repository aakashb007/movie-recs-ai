package com.reelwise.app.ui.profile

import com.reelwise.app.ui.navigation.navClearance
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.reelwise.app.BuildConfig
import com.reelwise.app.data.Repository
import com.reelwise.app.data.local.InteractionEntity
import com.reelwise.app.data.local.Settings
import com.reelwise.app.data.local.ThemeMode
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.PosterCard
import com.reelwise.app.ui.components.SectionHeader
import com.reelwise.app.ui.theme.Eyebrow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ProfileUiState(
    val settings: Settings? = null,
    val liked: List<TitleSummary> = emptyList(),
    val dislikedCount: Int = 0,
    val seenCount: Int = 0,
)

private fun InteractionEntity.toSummary() = TitleSummary(id = id, mediaType = mediaType, title = title, posterPath = posterPath)

@HiltViewModel
class ProfileViewModel @Inject constructor(private val repository: Repository) : ViewModel() {
    val state: StateFlow<ProfileUiState> = combine(repository.settings, repository.interactions) { settings, list ->
        ProfileUiState(
            settings = settings,
            liked = list.filter { it.signal == Signal.LIKE }.sortedByDescending { it.updatedAt }.map { it.toSummary() },
            dislikedCount = list.count { it.signal == Signal.DISLIKE },
            seenCount = list.count { it.signal == Signal.SEEN },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    fun setRegion(region: String) = viewModelScope.launch { repository.setRegion(region) }
    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repository.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { repository.setDynamicColor(enabled) }
    fun reset(then: () -> Unit) = viewModelScope.launch {
        repository.resetTaste()
        then()
    }
}

/** Regions TMDB has watch-provider data for that people most often pick; any ISO code works server-side. */
val REGIONS = listOf(
    "US", "GB", "CA", "AU", "IN", "PK", "AE", "SA", "DE", "FR", "ES", "IT", "NL", "SE", "NO", "DK", "IE",
    "BR", "MX", "AR", "JP", "KR", "ID", "PH", "SG", "MY", "TR", "ZA", "NG", "EG", "NZ",
).sortedBy { Locale("", it).displayCountry }

@Composable
fun ProfileRoute(
    onOpenTitle: (TitleSummary) -> Unit,
    onRetakeQuiz: () -> Unit,
    onApiKeys: () -> Unit,
    viewModel: ProfileViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProfileContent(
        state = state,
        onOpenTitle = onOpenTitle,
        onRegion = { viewModel.setRegion(it) },
        onDynamicColor = { viewModel.setDynamicColor(it) },
        onThemeMode = { viewModel.setThemeMode(it) },
        onApiKeys = onApiKeys,
        onRetakeQuiz = onRetakeQuiz,
        onReset = { viewModel.reset(onRetakeQuiz) },
    )
}

@Composable
fun ProfileContent(
    state: ProfileUiState,
    onOpenTitle: (TitleSummary) -> Unit,
    onRegion: (String) -> Unit,
    onDynamicColor: (Boolean) -> Unit,
    onRetakeQuiz: () -> Unit,
    onReset: () -> Unit,
    onThemeMode: (ThemeMode) -> Unit = {},
    onApiKeys: () -> Unit = {},
) {
    var confirmReset by remember { mutableStateOf(false) }
    var regionMenu by remember { mutableStateOf(false) }
    val settings = state.settings
    LazyColumn(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = navClearance()),
    ) {
        item {
            Column(Modifier.statusBarsPadding().padding(16.dp)) {
                Text("YOUR TASTE", style = Eyebrow, color = MaterialTheme.colorScheme.tertiary)
                Text("Profile", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat("Loved", state.liked.size, Modifier.weight(1f))
                    Stat("Not for me", state.dislikedCount, Modifier.weight(1f))
                    Stat("Seen", state.seenCount, Modifier.weight(1f))
                }
            }
        }
        if (state.liked.isNotEmpty()) {
            item {
                SectionHeader(null, "Titles you loved")
                Spacer(Modifier.height(12.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(state.liked, key = { "${it.mediaType}-${it.id}" }) { t ->
                        PosterCard(t, onClick = { onOpenTitle(t) }, width = 104.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
        item {
            SectionHeader(null, "Settings")
            Spacer(Modifier.height(4.dp))
            val colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background)
            Box {
                ListItem(
                    headlineContent = { Text("Streaming region") },
                    supportingContent = {
                        Text(settings?.region?.let { Locale("", it).displayCountry + " ($it)" } ?: "…")
                    },
                    leadingContent = { Icon(Icons.Rounded.Public, null) },
                    colors = colors,
                    modifier = Modifier.clickable { regionMenu = true },
                )
                DropdownMenu(expanded = regionMenu, onDismissRequest = { regionMenu = false }) {
                    REGIONS.forEach { code ->
                        DropdownMenuItem(
                            text = { Text(Locale("", code).displayCountry) },
                            onClick = { regionMenu = false; onRegion(code) },
                        )
                    }
                }
            }
            ListItem(
                headlineContent = { Text("Theme") },
                supportingContent = {
                    Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(ThemeMode.DARK to "Dark", ThemeMode.LIGHT to "Light", ThemeMode.SYSTEM to "System").forEach { (mode, label) ->
                            FilterChip(selected = settings?.themeMode == mode, onClick = { onThemeMode(mode) }, label = { Text(label) })
                        }
                    }
                },
                leadingContent = { Icon(Icons.Rounded.Contrast, null) },
                colors = colors,
            )
            ListItem(
                headlineContent = { Text("API keys") },
                supportingContent = { Text("Your TMDB and Groq keys, stored only on this phone") },
                leadingContent = { Icon(Icons.Rounded.Key, null) },
                colors = colors,
                modifier = Modifier.clickable(onClick = onApiKeys),
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ListItem(
                    headlineContent = { Text("Match wallpaper colours") },
                    supportingContent = { Text("Use Material You colours instead of the Reelwise palette") },
                    leadingContent = { Icon(Icons.Rounded.Palette, null) },
                    trailingContent = {
                        Switch(checked = settings?.dynamicColor == true, onCheckedChange = onDynamicColor)
                    },
                    colors = colors,
                )
            }
            ListItem(
                headlineContent = { Text("Retake the taste quiz") },
                supportingContent = { Text("Add more watched titles, change genres or filters") },
                leadingContent = { Icon(Icons.Rounded.Replay, null) },
                colors = colors,
                modifier = Modifier.clickable(onClick = onRetakeQuiz),
            )
            ListItem(
                headlineContent = { Text("Clear my taste profile") },
                supportingContent = { Text("Deletes every rating on this phone") },
                leadingContent = { Icon(Icons.Rounded.DeleteOutline, null, tint = MaterialTheme.colorScheme.error) },
                colors = colors,
                modifier = Modifier.clickable { confirmReset = true },
            )
        }
        item {
            Column(Modifier.padding(16.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(
                    "This product uses the TMDB API but is not endorsed or certified by TMDB. " +
                        "Streaming availability is provided by JustWatch. Recommendations are worked out " +
                        "on this phone from your ratings and TMDB data; typed requests can use Groq, and IMDb " +
                        "ratings come from OMDb when you add a key.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reelwise ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Text("Made by Aakash Saleem", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Clear your taste profile?") },
            text = { Text("All your ratings will be deleted and your picks will start from scratch. Your watchlist stays.") },
            confirmButton = {
                TextButton(onClick = { confirmReset = false; onReset() }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Stat(label: String, value: Int, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(18.dp), modifier = modifier) {
        Column(Modifier.padding(14.dp).semantics(mergeDescendants = true) {}) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.tertiary)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
