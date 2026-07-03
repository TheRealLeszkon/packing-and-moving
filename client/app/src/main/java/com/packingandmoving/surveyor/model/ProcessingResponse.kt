package com.packingandmoving.surveyor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /processing/{id} — full survey status, uploaded images, and detected items. */
@Serializable
data class ProcessingResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    val images: List<SurveyImage> = emptyList(),
    val items: List<InventoryItem> = emptyList(),
    @SerialName("needs_more_images") val needsMoreImages: Boolean? = null,
    @SerialName("requested_images") val requestedImages: List<String>? = null,
    @SerialName("error_message") val errorMessage: String? = null,
) {
    val surveyStatus: SurveyStatus get() = SurveyStatus.from(status)
}

/** GET /processing — one row per survey, for the history list. */
@Serializable
data class ProcessingSummary(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("image_count") val imageCount: Int,
    @SerialName("item_count") val itemCount: Int,
) {
    val surveyStatus: SurveyStatus get() = SurveyStatus.from(status)
}

/** Response to POST /upload. */
@Serializable
data class UploadResponse(
    @SerialName("session_id") val sessionId: String,
    val status: String,
    @SerialName("image_count") val imageCount: Int,
    val message: String,
)
