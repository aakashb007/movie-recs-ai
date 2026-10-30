package com.reelwise.app.ui.account

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import android.text.format.DateUtils
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.reelwise.app.data.sync.GoogleDriveAuth
import com.reelwise.app.data.sync.GoogleGrant
import com.reelwise.app.data.sync.SyncManager
import com.reelwise.app.data.sync.SyncState
import com.reelwise.app.data.sync.grant
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class AccountViewModel @Inject constructor(
    val auth: GoogleDriveAuth,
    private val sync: SyncManager,
) : ViewModel() {
    val state: StateFlow<SyncState> = sync.state

    fun connected(grant: GoogleGrant) {
        viewModelScope.launch { sync.connect(grant) }
    }

    fun syncNow() {
        viewModelScope.launch { sync.upload() }
    }

    fun restore() {
        viewModelScope.launch { sync.restore() }
    }

    fun includeKeys(enabled: Boolean) {
        viewModelScope.launch { sync.setIncludeKeys(enabled) }
    }

    fun signOut() {
        viewModelScope.launch { sync.signOut() }
    }

    fun failed(message: String) = sync.report(message)

    fun acknowledgeRestore() = sync.acknowledgeRestore()
}

/**
 * Returns a function that runs Google's sign-in: an account picker and consent screen the first
 * time, silent afterwards. Grants go to [onGrant]; anything else becomes a readable message.
 */
@Composable
fun rememberGoogleSignIn(auth: GoogleDriveAuth, onGrant: (GoogleGrant) -> Unit, onError: (String) -> Unit): () -> Unit {
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        try {
            auth.resultFromIntent(result.data).grant()?.let(onGrant) ?: onError("Google sign-in was cancelled.")
        } catch (e: Exception) {
            onError(signInError(e))
        }
    }
    return {
        scope.launch {
            try {
                val result = auth.authorize()
                val pending = result.pendingIntent
                if (result.hasResolution() && pending != null) {
                    launcher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                } else {
                    result.grant()?.let(onGrant) ?: onError("Google didn't return access. Try again.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onError(signInError(e))
            }
        }
    }
}

/** A readable reason for a failed sign-in. */
fun signInError(e: Exception): String = when ((e as? ApiException)?.statusCode) {
    // Google found no Android OAuth client for this package + signing-key SHA-1.
    CommonStatusCodes.DEVELOPER_ERROR ->
        "APK not signed with the registered key. Install the release APK from GitHub (debug builds can't sign in)."
    CommonStatusCodes.NETWORK_ERROR -> "No internet connection. Connect and try again."
    CommonStatusCodes.CANCELED -> "Google sign-in was cancelled."
    else -> "Google sign-in failed (${e.message ?: e.javaClass.simpleName})."
}

@Composable
fun GoogleAccountCard(viewModel: AccountViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signIn = rememberGoogleSignIn(viewModel.auth, viewModel::connected, viewModel::failed)
    GoogleAccountContent(
        state,
        onSignIn = signIn,
        onSyncNow = viewModel::syncNow,
        onSignOut = viewModel::signOut,
        onRestore = viewModel::restore,
        onIncludeKeys = viewModel::includeKeys,
    )
}

@Composable
fun GoogleAccountContent(
    state: SyncState,
    onSignIn: () -> Unit,
    onSyncNow: () -> Unit,
    onSignOut: () -> Unit,
    onRestore: () -> Unit = {},
    onIncludeKeys: (Boolean) -> Unit = {},
) {
    val account = state.account
    var confirmRestore by remember { mutableStateOf(false) }
    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("Restore from Google Drive?") },
            text = { Text("Your ratings, My list and taste profile on this phone will be replaced by the copy in your Drive.") },
            confirmButton = { TextButton(onClick = { confirmRestore = false; onRestore() }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmRestore = false }) { Text("Cancel") } },
        )
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (account == null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.CloudSync, null, tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(10.dp))
                    Text("Keep your data safe", style = MaterialTheme.typography.titleMedium)
                }
                Text(
                    "Back up ratings, My list and your taste profile to a private folder in your own Google Drive. " +
                        "Restore everything on a new phone. API keys stay on this phone unless you choose otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 12.dp),
                )
                GoogleButton(busy = state.busy, onClick = onSignIn)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            (account.name ?: account.email).take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(account.name ?: "Google account", style = MaterialTheme.typography.titleSmall)
                        Text(account.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.CloudDone, contentDescription = "Backed up", tint = MaterialTheme.colorScheme.tertiary)
                }
                Text(
                    account.lastSyncedAt?.let { "Backed up to Google Drive " + DateUtils.getRelativeTimeSpanString(it) }
                        ?: "Not backed up yet",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
                if (state.needsSignIn) {
                    Spacer(Modifier.height(10.dp))
                    GoogleButton(busy = state.busy, onClick = onSignIn, label = "Sign in again")
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onSyncNow, enabled = !state.busy) { Text("Back up now") }
                    OutlinedButton(onClick = { confirmRestore = true }, enabled = !state.busy) { Text("Restore from Drive") }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Include API keys in backup", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Off: keys stay only on this phone. On: TMDB, Groq and OMDb keys are saved in your private Drive folder too.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = state.includeKeys, onCheckedChange = onIncludeKeys)
                }
                TextButton(onClick = onSignOut, modifier = Modifier.padding(top = 4.dp)) { Text("Sign out") }
            }
            AnimatedVisibility(state.message != null) {
                Text(
                    state.message.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Google's sign-in button style: white with the four-colour G, per their branding guidelines. */
@Composable
fun GoogleButton(busy: Boolean, onClick: () -> Unit, label: String = "Sign in with Google") {
    Button(
        onClick = onClick,
        enabled = !busy,
        shape = RoundedCornerShape(24.dp),
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF1F1F1F)),
        border = BorderStroke(1.dp, Color(0xFF747775)),
        modifier = Modifier.height(48.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color(0xFF1F1F1F))
        } else {
            GoogleG()
        }
        Spacer(Modifier.width(10.dp))
        Text(label, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoogleG() {
    Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(Modifier.size(20.dp)) {
            val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.2f)
            val inset = stroke.width / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke.width, size.height - stroke.width)
            val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
            drawArc(Color(0xFFEA4335), -150f, 105f, false, topLeft, arcSize, style = stroke)
            drawArc(Color(0xFFFBBC05), 150f, 60f, false, topLeft, arcSize, style = stroke)
            drawArc(Color(0xFF34A853), 45f, 105f, false, topLeft, arcSize, style = stroke)
            drawArc(Color(0xFF4285F4), -45f, 90f, false, topLeft, arcSize, style = stroke)
            drawRect(
                Color(0xFF4285F4),
                topLeft = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2 - stroke.width / 2),
                size = androidx.compose.ui.geometry.Size(size.width / 2 - inset / 2, stroke.width),
            )
        }
    }
}
