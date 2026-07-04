package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.InMemoryTokenProvider
import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.GoogleAuthRequest
import com.packingandmoving.surveyagent.model.LogoutRequest
import com.packingandmoving.surveyagent.model.MessageResponse
import com.packingandmoving.surveyagent.model.RefreshRequest
import com.packingandmoving.surveyagent.model.TokenResponse
import com.packingandmoving.surveyagent.model.User

/**
 * Authentication and token lifecycle (frontend-integration.md §3). On a successful
 * sign-in or refresh the returned access token is pushed to the [tokenProvider] so
 * subsequent requests are authenticated. Persisting the rotating refresh token is the
 * caller's job in the auth phase.
 */
class AuthRepository(
    private val api: SurveyAgentApi,
    private val tokenProvider: InMemoryTokenProvider,
) {
    suspend fun signInWithGoogle(idToken: String): ApiResult<TokenResponse> =
        safeApiCall { api.signInWithGoogle(GoogleAuthRequest(idToken)) }
            .onSuccess { tokenProvider.update(it.accessToken) }

    suspend fun refresh(refreshToken: String): ApiResult<TokenResponse> =
        safeApiCall { api.refreshTokens(RefreshRequest(refreshToken)) }
            .onSuccess { tokenProvider.update(it.accessToken) }

    suspend fun logout(refreshToken: String): ApiResult<MessageResponse> =
        safeApiCall { api.logout(LogoutRequest(refreshToken)) }
            .onSuccess { tokenProvider.update(null) }

    suspend fun currentUser(): ApiResult<User> = safeApiCall { api.authMe() }
}
