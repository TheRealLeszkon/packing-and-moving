package com.packingandmoving.surveyagent.model

import kotlinx.serialization.Serializable

/**
 * MediaResponse (openapi.json). `url` is a short-lived signed GCS URL, present only on
 * GET /media/{id} and list responses (not on the upload ack) — assume it can expire
 * (~15 min) and re-fetch when needed (frontend-integration.md §11).
 */
@Serializable
data class Media(
    val id: String,
    val surveyId: String,
    val mediaType: MediaType,
    val processingStatus: ProcessingStatus,
    val roomLocation: String?,
    val width: Int?,
    val height: Int?,
    val durationSeconds: String?,
    val frameNumber: Int?,
    val parentVideoId: String?,
    val uploadTimestamp: String,
    val url: String? = null,
)

/** MediaUploadResponse (POST images/videos ack). */
@Serializable
data class MediaUploadResult(
    val items: List<Media>,
    val count: Int,
)
