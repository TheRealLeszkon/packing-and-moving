package com.packingandmoving.surveyor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** One uploaded photo, as returned by GET /processing/{id}. */
@Serializable
data class SurveyImage(
    val id: String,
    /** Relative path (e.g. "/images/{id}") — resolve against the API base URL before loading. */
    val url: String,
    @SerialName("original_filename") val originalFilename: String,
    @SerialName("mime_type") val mimeType: String,
    @SerialName("created_at") val createdAt: String,
)
