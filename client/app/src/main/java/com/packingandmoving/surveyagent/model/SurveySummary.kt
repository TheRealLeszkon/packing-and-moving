package com.packingandmoving.surveyagent.model

import kotlinx.serialization.Serializable

/** SurveySummaryResponse (GET /surveys/{id}/summary). Totals are Decimal-as-string. */
@Serializable
data class SurveySummary(
    val surveyId: String,
    val distinctItems: Int,
    val totalQuantity: Int,
    val totalVolumeM3: String,
    val totalValue: String,
    val fragileItems: Int,
    val needsDisassemblyItems: Int,
    val needsSpecialHandlingItems: Int,
    val byCategory: List<CategoryBreakdown>,
    val byRoom: List<RoomBreakdown>,
)

@Serializable
data class CategoryBreakdown(
    val category: String?,
    val distinctItems: Int,
    val quantity: Int,
)

@Serializable
data class RoomBreakdown(
    val roomLocation: String?,
    val distinctItems: Int,
    val quantity: Int,
)
