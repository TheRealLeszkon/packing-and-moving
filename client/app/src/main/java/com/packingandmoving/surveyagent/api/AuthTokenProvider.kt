package com.packingandmoving.surveyagent.api

import java.util.concurrent.atomic.AtomicReference

/**
 * Supplies the current access token to [AuthInterceptor]. Secure, persistent storage
 * (DataStore / EncryptedSharedPreferences) is added in the authentication phase; for now
 * an in-memory holder is enough to attach the bearer token to requests.
 */
interface AuthTokenProvider {
    fun currentAccessToken(): String?
}

/** Simple thread-safe in-memory token holder. */
class InMemoryTokenProvider : AuthTokenProvider {
    private val accessToken = AtomicReference<String?>(null)

    override fun currentAccessToken(): String? = accessToken.get()

    fun update(token: String?) {
        accessToken.set(token)
    }
}
