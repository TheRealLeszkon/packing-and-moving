package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.auth.SessionManager
import com.packingandmoving.surveyagent.model.GoogleAuthRequest
import com.packingandmoving.surveyagent.model.LogoutRequest
import com.packingandmoving.surveyagent.model.MessageResponse
import com.packingandmoving.surveyagent.model.TokenResponse
import com.packingandmoving.surveyagent.model.User

/**
 * Authentication and token lifecycle (frontend-integration.md §3). Sign-in exchanges a
 * Google ID token for the app's token pair and hands it to the [session], which persists it
 * and drives auth-aware navigation. Automatic refresh-on-401 is handled by the OkHttp
 * authenticator, so this layer only covers sign-in, logout, and the current user.
 */
class AuthRepository(
    private val api: SurveyAgentApi,
    private val authApi: SurveyAgentApi,
    private val session: SessionManager,
) {
    suspend fun signInWithGoogle(idToken: String): ApiResult<TokenResponse> =
        safeApiCall { authApi.signInWithGoogle(GoogleAuthRequest(idToken)) }
            .also { result -> if (result is ApiResult.Success) session.save(result.data) }

    /**
     * Revokes the refresh token server-side (best effort) and always clears the local
     * session so sign-out succeeds even offline.
     */
    suspend fun logout(): ApiResult<MessageResponse> {
        val refreshToken = session.currentRefreshToken()
        val result = if (refreshToken != null) {
            safeApiCall { authApi.logout(LogoutRequest(refreshToken)) }
        } else {
            ApiResult.Success(MessageResponse("Signed out."))
        }
        session.clear()
        return result
    }

    suspend fun currentUser(): ApiResult<User> = safeApiCall { api.authMe() }
}
