package com.packingandmoving.surveyagent.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Wraps the Credential Manager Google Sign-In flow. Returns the Google ID token whose
 * audience is [serverClientId]; the backend verifies it and issues the app's token pair
 * (frontend-integration.md §3).
 *
 * Requires this app's package name + signing SHA-1 to be registered as an Android OAuth
 * client in the same Google Cloud project as [serverClientId]. Throws the Credential
 * Manager exceptions (cancellation, no available account, etc.) for the caller to handle.
 */
class GoogleAuthManager(private val serverClientId: String) {

    suspend fun getIdToken(context: Context): String {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setServerClientId(serverClientId)
            // Show every Google account on the device, not only previously authorized ones.
            .setFilterByAuthorizedAccounts(false)
            .setAutoSelectEnabled(false)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val response = CredentialManager.create(context).getCredential(context, request)
        val credential = response.credential

        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        error("Unexpected credential type: ${credential.type}")
    }
}
