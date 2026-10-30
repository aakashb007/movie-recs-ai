package com.reelwise.app.data.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.Scope
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** An access token for Drive's app folder, and who granted it. */
data class GoogleGrant(val token: String, val email: String?, val name: String?)

/** Lets sync get a fresh token without UI; tests swap in a fake. */
interface DriveTokens {
    /** A token if the person already granted access, or null if they must sign in again. */
    suspend fun silentGrant(): GoogleGrant?
}

/**
 * Google sign-in for Drive backup, via Google Identity Services' AuthorizationClient. Google
 * matches the app by package name and signing-key SHA-1 against an Android OAuth client in the
 * person's Google Cloud project, so no client id ships in the app.
 */
@Singleton
class GoogleDriveAuth @Inject constructor(@ApplicationContext context: Context) : DriveTokens {
    private val client = Identity.getAuthorizationClient(context)
    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(DRIVE_APPDATA), Scope(Scopes.EMAIL), Scope(Scopes.PROFILE)))
        .build()

    /** First step of signing in: either a grant straight away or a consent screen to show. */
    suspend fun authorize(): AuthorizationResult = client.authorize(request).await()

    fun resultFromIntent(data: Intent?): AuthorizationResult = client.getAuthorizationResultFromIntent(data)

    override suspend fun silentGrant(): GoogleGrant? =
        runCatching { authorize() }.getOrNull()?.takeIf { !it.hasResolution() }?.grant()

    companion object {
        const val DRIVE_APPDATA = "https://www.googleapis.com/auth/drive.appdata"
    }
}

fun AuthorizationResult.grant(): GoogleGrant? {
    val token = accessToken ?: return null
    val account = toGoogleSignInAccount()
    return GoogleGrant(token, account?.email, account?.displayName)
}
