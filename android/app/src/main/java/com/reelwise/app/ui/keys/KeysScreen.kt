package com.reelwise.app.ui.keys

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.reelwise.app.ui.theme.BrandRed
import com.reelwise.app.ui.theme.Eyebrow
import com.reelwise.app.ui.theme.PosterType

@Composable
fun KeysRoute(onDone: () -> Unit, onBack: (() -> Unit)?, viewModel: KeysViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) { if (state.saved) onDone() }
    val uri = LocalUriHandler.current
    KeysContent(
        state = state,
        onTmdb = viewModel::onTmdb,
        onGroq = viewModel::onGroq,
        onSave = viewModel::testAndSave,
        onBack = onBack,
        onOpenUrl = { runCatching { uri.openUri(it) } },
    )
}

@Composable
fun KeysContent(
    state: KeysUiState,
    onTmdb: (String) -> Unit,
    onGroq: (String) -> Unit,
    onSave: () -> Unit,
    onBack: (() -> Unit)?,
    onOpenUrl: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            }
            Text("REELWISE", style = PosterType.copy(fontSize = MaterialTheme.typography.titleLarge.fontSize), color = BrandRed)
        }
        Spacer(Modifier.height(20.dp))
        Text(if (state.editing) "API KEYS" else "ONE-TIME SETUP", style = Eyebrow, color = MaterialTheme.colorScheme.tertiary)
        Text(
            "Connect your movie data",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Reelwise runs entirely on this phone. It talks to TMDB with your own free key, and optionally to Groq for typed requests.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        KeyField(
            label = "TMDB API key or read access token",
            value = state.tmdbKey,
            onChange = onTmdb,
            supporting = "Required. Free at themoviedb.org → Settings → API.",
            imeAction = ImeAction.Next,
        )
        TextButton(onClick = { onOpenUrl("https://www.themoviedb.org/settings/api") }) { Text("Get a TMDB key") }

        Spacer(Modifier.height(8.dp))
        KeyField(
            label = "Groq API key (optional)",
            value = state.groqKey,
            onChange = onGroq,
            supporting = "Makes Ask understand requests like \"Dark, but lighter\". Without it, Ask matches keywords.",
            imeAction = ImeAction.Done,
        )
        TextButton(onClick = { onOpenUrl("https://console.groq.com/keys") }) { Text("Get a free Groq key") }

        AnimatedVisibility(state.error != null, enter = fadeIn() + expandVertically(), exit = fadeOut() + shrinkVertically()) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            ) {
                Text(state.error.orEmpty(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(14.dp))
            }
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSave,
            enabled = state.canSave,
            shape = RoundedCornerShape(26.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            if (state.checking) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(10.dp))
                Text("Checking with TMDB…")
            } else if (state.saved) {
                Icon(Icons.Rounded.CheckCircle, null)
                Spacer(Modifier.size(8.dp))
                Text("Saved")
            } else {
                Text("Test & save")
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
            Text(
                "Keys are stored only on this phone and are left out of backups.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun KeyField(label: String, value: String, onChange: (String) -> Unit, supporting: String, imeAction: ImeAction) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        supportingText = { Text(supporting) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction, autoCorrectEnabled = false),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = if (visible) "Hide key" else "Show key",
                )
            }
        },
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    )
}
