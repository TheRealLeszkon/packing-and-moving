package com.packingandmoving.surveyagent.model

import kotlinx.serialization.Serializable

/** UserResponse (openapi.json). */
@Serializable
data class User(
    val id: String,
    val email: String,
    val name: String?,
    val role: UserRole,
    val isActive: Boolean,
    val createdAt: String,
)

/** UserUpdate (PUT /users/me). */
@Serializable
data class UserUpdate(val name: String? = null)
