package com.packingandmoving.surveyagent.api

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches `Authorization: Bearer <token>` when a token is available. Endpoints that need
 * no auth (health, /auth/google, /auth/refresh) are simply called before a token exists,
 * so no per-path allowlist is required (frontend-integration.md §2).
 *
 * TODO(auth phase): add an okhttp Authenticator to transparently refresh via /auth/refresh
 * on 401 and persist the rotated refresh token.
 */
class AuthInterceptor(private val tokenProvider: AuthTokenProvider) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = tokenProvider.currentAccessToken()
        val request = if (token.isNullOrBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        }
        return chain.proceed(request)
    }
}
