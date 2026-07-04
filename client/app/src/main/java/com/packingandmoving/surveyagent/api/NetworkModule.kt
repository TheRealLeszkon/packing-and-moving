package com.packingandmoving.surveyagent.api

import android.content.Context
import com.packingandmoving.surveyagent.BuildConfig
import com.packingandmoving.surveyagent.auth.SessionManager
import com.packingandmoving.surveyagent.auth.TokenAuthenticator
import com.packingandmoving.surveyagent.data.TokenStore
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Composition root for networking. Built once from the Application via [init] against
 * [BuildConfig.API_BASE_URL] (configuration-driven, never hardcoded). Exposes two Retrofit
 * surfaces: [authApi] on a bare client (no bearer, used for sign-in/refresh/logout so the
 * refresh call can't recurse) and [api] on an authenticated client (bearer + 401 refresh).
 */
object NetworkModule {

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        // Tolerate backend additions; map camelCase <-> snake_case wire keys.
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        // Omit null properties so PATCH bodies only carry the fields that changed.
        explicitNulls = false
        coerceInputValues = true
    }

    lateinit var sessionManager: SessionManager
        private set

    /** Authenticated surface for everything that needs a bearer token. */
    lateinit var api: SurveyAgentApi
        private set

    /** Bare surface for the auth endpoints (no bearer, no refresh-on-401). */
    lateinit var authApi: SurveyAgentApi
        private set

    fun init(context: Context) {
        if (::api.isInitialized) return

        sessionManager = SessionManager(TokenStore(context.applicationContext))

        authApi = buildRetrofit(bareClient()).create(SurveyAgentApi::class.java)

        val authedClient = bareClient().newBuilder()
            .addInterceptor(AuthInterceptor(sessionManager))
            .authenticator(TokenAuthenticator(sessionManager) { authApi })
            .build()
        api = buildRetrofit(authedClient).create(SurveyAgentApi::class.java)
    }

    private fun bareClient(): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(
            HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) {
                    HttpLoggingInterceptor.Level.BODY
                } else {
                    HttpLoggingInterceptor.Level.NONE
                }
            },
        )
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun buildRetrofit(client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
}
