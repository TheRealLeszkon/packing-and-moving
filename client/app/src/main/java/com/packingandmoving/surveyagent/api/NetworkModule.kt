package com.packingandmoving.surveyagent.api

import com.packingandmoving.surveyagent.BuildConfig
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
 * Composition root for networking. Builds a single OkHttp/Retrofit stack against
 * [BuildConfig.API_BASE_URL] (configuration-driven, never hardcoded).
 */
object NetworkModule {

    /** Shared token holder; the auth layer updates it after sign-in/refresh. */
    val tokenProvider: InMemoryTokenProvider = InMemoryTokenProvider()

    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        // Tolerate backend additions; map camelCase <-> snake_case wire keys.
        ignoreUnknownKeys = true
        namingStrategy = JsonNamingStrategy.SnakeCase
        // Omit null properties so PATCH bodies only carry the fields that changed.
        explicitNulls = false
        coerceInputValues = true
    }

    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenProvider))
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

    val api: SurveyAgentApi = Retrofit.Builder()
        .baseUrl(BuildConfig.API_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(SurveyAgentApi::class.java)
}
