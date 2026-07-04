package com.packingandmoving.surveyagent.model

import kotlinx.serialization.Serializable

/**
 * A paginated list envelope (Page_X_ in openapi.json). `data` of every list endpoint.
 */
@Serializable
data class Page<T>(
    val items: List<T>,
    val total: Int,
    val limit: Int,
    val offset: Int,
)

/** SurveyCreate (POST /surveys). */
@Serializable
data class SurveyCreate(
    val name: String,
    val originAddress: String,
    val destinationAddress: String,
    val preferredDatetime: String? = null,
)

/**
 * SurveyResponse (openapi.json). Decimal fields are serialized by the backend as
 * strings (Python Decimal) — kept as String? here to avoid float rounding; parse at
 * display time.
 */
@Serializable
data class Survey(
    val id: String,
    val name: String,
    val originAddress: String,
    val destinationAddress: String,
    val customerId: String,
    val surveyorId: String?,
    val preferredDatetime: String?,
    val status: SurveyStatus,
    val totalVolumeEstimate: String?,
    val totalValueEstimate: String?,
    val createdAt: String,
    val updatedAt: String,
)

/** SurveyStatusResponse (GET /surveys/{id}/status). Render buttons from availableActions. */
@Serializable
data class SurveyStatusInfo(
    val id: String,
    val status: SurveyStatus,
    val availableActions: List<String>,
    val processingStage: String? = null,
)

/** ReanalyzeRequest (POST /surveys/{id}/reanalyze). mode = "all" | "new_only". */
@Serializable
data class ReanalyzeRequest(val mode: String)

/** ReanalyzeResponse (202 from POST /surveys/{id}/reanalyze). */
@Serializable
data class ReanalyzeResult(
    val surveyId: String,
    val status: SurveyStatus,
    val jobIds: List<String> = emptyList(),
)

/** Optional reason body for POST /surveys/{id}/cancel. */
@Serializable
data class CancelRequest(val reason: String? = null)

/** Required reason body for POST /surveys/{id}/reject. */
@Serializable
data class RejectRequest(val reason: String)
