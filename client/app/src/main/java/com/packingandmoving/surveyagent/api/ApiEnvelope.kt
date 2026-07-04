package com.packingandmoving.surveyagent.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Success envelope wrapping every 2xx response body: `{ "success": true, "data": <T> }`
 * (frontend-integration.md §2).
 */
@Serializable
data class ApiEnvelope<T>(
    val success: Boolean = true,
    val data: T,
)

/** Error envelope on non-2xx responses: `{ "success": false, "error": {...} }`. */
@Serializable
data class ApiErrorEnvelope(
    val success: Boolean = false,
    val error: ApiErrorBody? = null,
)

@Serializable
data class ApiErrorBody(
    val code: String = "",
    val message: String = "",
    val details: JsonElement? = null,
)
