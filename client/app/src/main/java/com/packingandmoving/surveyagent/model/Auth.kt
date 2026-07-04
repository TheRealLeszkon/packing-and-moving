package com.packingandmoving.surveyagent.model

import kotlinx.serialization.Serializable

// Auth request/response bodies (openapi.json: /auth/*). Property names are camelCase and
// mapped to snake_case wire keys by the JSON naming strategy configured in NetworkModule.

@Serializable
data class GoogleAuthRequest(val idToken: String)

@Serializable
data class RefreshRequest(val refreshToken: String)

@Serializable
data class LogoutRequest(val refreshToken: String)

@Serializable
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "bearer",
    val expiresIn: Int,
    val user: User,
)

@Serializable
data class MessageResponse(val detail: String)
