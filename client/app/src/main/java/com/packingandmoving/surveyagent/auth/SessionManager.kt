package com.packingandmoving.surveyagent.auth

import com.packingandmoving.surveyagent.api.AuthTokenProvider
import com.packingandmoving.surveyagent.data.TokenStore
import com.packingandmoving.surveyagent.model.TokenResponse
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicReference

/** Whether the app currently holds a session. `Unknown` until [SessionManager.hydrate] runs. */
enum class AuthState { Unknown, SignedIn, SignedOut }

/**
 * Single source of truth for the auth session. Holds the access + refresh tokens in memory
 * for synchronous access by the OkHttp interceptor/authenticator, and mirrors them to
 * [TokenStore] so the session survives process death. [authState] lets the UI react to
 * sign-in and to sign-out (including a forced sign-out when a refresh finally fails).
 */
class SessionManager(private val tokenStore: TokenStore) : AuthTokenProvider {

    private val accessToken = AtomicReference<String?>(null)
    private val refreshToken = AtomicReference<String?>(null)

    private val _authState = MutableStateFlow(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    override fun currentAccessToken(): String? = accessToken.get()

    fun currentRefreshToken(): String? = refreshToken.get()

    /** Loads any persisted session into memory. Call once at startup before the UI renders. */
    suspend fun hydrate() {
        val stored = tokenStore.read()
        if (stored != null) {
            accessToken.set(stored.accessToken)
            refreshToken.set(stored.refreshToken)
            _authState.value = AuthState.SignedIn
        } else {
            _authState.value = AuthState.SignedOut
        }
    }

    /** Persists a freshly issued token pair (sign-in or refresh) and marks the app signed in. */
    suspend fun save(tokens: TokenResponse) {
        accessToken.set(tokens.accessToken)
        refreshToken.set(tokens.refreshToken)
        tokenStore.save(tokens.accessToken, tokens.refreshToken)
        _authState.value = AuthState.SignedIn
    }

    /** Clears the session everywhere and signals the UI to return to sign-in. */
    suspend fun clear() {
        accessToken.set(null)
        refreshToken.set(null)
        tokenStore.clear()
        _authState.value = AuthState.SignedOut
    }
}
