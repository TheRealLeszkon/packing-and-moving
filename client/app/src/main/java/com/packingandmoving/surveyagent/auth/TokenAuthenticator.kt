package com.packingandmoving.surveyagent.auth

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.RefreshRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Transparently refreshes the access token when a request comes back 401. Uses the bare
 * [refreshApi] (no auth interceptor/authenticator) so the refresh call can't recurse, and
 * rotates the stored refresh token per §3. If refresh fails the session is cleared, which
 * routes the UI back to sign-in.
 *
 * OkHttp calls this on a network thread and expects a blocking result, so the suspend
 * work is bridged with [runBlocking].
 */
class TokenAuthenticator(
    private val session: SessionManager,
    private val refreshApi: () -> SurveyAgentApi,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Only requests that carried a bearer token are candidates for a refresh-and-retry.
        val failedToken = response.request.header("Authorization")
            ?.removePrefix("Bearer ")
            ?: return null

        // Give up after a couple of attempts to avoid an auth loop.
        if (responseCount(response) >= 2) return null

        synchronized(this) {
            // Another request may have already refreshed while we were queued.
            val current = session.currentAccessToken()
            if (current != null && current != failedToken) {
                return response.request.retryWith(current)
            }

            val refreshToken = session.currentRefreshToken() ?: return null
            val newTokens = runBlocking {
                runCatching { refreshApi().refreshTokens(RefreshRequest(refreshToken)).data }
                    .getOrNull()
            }

            if (newTokens == null) {
                runBlocking { session.clear() }
                return null
            }

            runBlocking { session.save(newTokens) }
            return response.request.retryWith(newTokens.accessToken)
        }
    }

    private fun Request.retryWith(accessToken: String): Request =
        newBuilder().header("Authorization", "Bearer $accessToken").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
