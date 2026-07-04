package com.packingandmoving.surveyagent.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.authDataStore by preferencesDataStore(name = "auth")

/** The persisted access/refresh token pair. */
data class StoredTokens(val accessToken: String, val refreshToken: String)

/**
 * Persists the auth token pair in app-private DataStore. Storage is sandboxed to this app;
 * tokens are short-lived and the refresh token rotates on every use (frontend-integration.md
 * §3), which limits the value of a stolen copy.
 */
class TokenStore(private val context: Context) {

    suspend fun read(): StoredTokens? {
        val prefs = context.authDataStore.data.first()
        val access = prefs[ACCESS_TOKEN]
        val refresh = prefs[REFRESH_TOKEN]
        return if (access != null && refresh != null) StoredTokens(access, refresh) else null
    }

    suspend fun save(accessToken: String, refreshToken: String) {
        context.authDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = accessToken
            prefs[REFRESH_TOKEN] = refreshToken
        }
    }

    suspend fun clear() {
        context.authDataStore.edit { it.clear() }
    }

    private companion object {
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val REFRESH_TOKEN = stringPreferencesKey("refresh_token")
    }
}
