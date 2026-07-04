package com.packingandmoving.surveyagent.api

/**
 * Supplies the current access token to [AuthInterceptor]. Implemented by the auth layer's
 * SessionManager, which keeps the token in memory (backed by persistent storage) so it can
 * be attached to requests synchronously.
 */
interface AuthTokenProvider {
    fun currentAccessToken(): String?
}
