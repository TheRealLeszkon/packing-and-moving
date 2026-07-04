package com.packingandmoving.surveyagent.model

import kotlinx.serialization.Serializable

/**
 * SurveyItemResponse (openapi.json). Measurement/value/confidence fields are backend
 * Decimals serialized as strings; kept as String? here. `source` is "ai" or "manual".
 */
@Serializable
data class SurveyItem(
    val id: String,
    val surveyId: String,
    val itemName: String,
    val category: String?,
    val quantity: Int,
    val roomLocation: String?,
    val heightCm: String?,
    val widthCm: String?,
    val depthCm: String?,
    val weightKg: String?,
    val material: String?,
    val fragile: Boolean,
    val needsDisassembly: Boolean,
    val needsSpecialHandling: Boolean,
    val needsToShip: Boolean,
    val packingDifficulty: Difficulty?,
    val liftingDifficulty: Difficulty?,
    val estimatedValue: String?,
    val condition: ItemCondition?,
    val remarks: String?,
    val confidenceScore: String?,
    val source: String,
    val createdAt: String,
    val updatedAt: String,
    val mediaIds: List<String> = emptyList(),
)

/** SurveyItemListResponse (GET /surveys/{id}/items). */
@Serializable
data class SurveyItemList(
    val items: List<SurveyItem>,
    val count: Int,
)

/**
 * SurveyItemCreate (POST /surveys/{id}/items). Numeric fields accept number or string;
 * sent as String to preserve Decimal precision. Unset fields are omitted from the request
 * (explicitNulls = false), so the backend applies its own defaults.
 */
@Serializable
data class SurveyItemCreate(
    val itemName: String,
    val category: String? = null,
    val quantity: Int = 1,
    val roomLocation: String? = null,
    val heightCm: String? = null,
    val widthCm: String? = null,
    val depthCm: String? = null,
    val weightKg: String? = null,
    val material: String? = null,
    val fragile: Boolean = false,
    val needsDisassembly: Boolean = false,
    val needsSpecialHandling: Boolean = false,
    val needsToShip: Boolean = true,
    val packingDifficulty: Difficulty? = null,
    val liftingDifficulty: Difficulty? = null,
    val estimatedValue: String? = null,
    val condition: ItemCondition? = null,
    val remarks: String? = null,
    val mediaIds: List<String>? = null,
)

/**
 * SurveyItemUpdate (PATCH /survey-items/{id}). Every field is nullable and omitted when
 * null, so a PATCH sends only the fields the caller actually changed.
 */
@Serializable
data class SurveyItemUpdate(
    val itemName: String? = null,
    val category: String? = null,
    val quantity: Int? = null,
    val roomLocation: String? = null,
    val heightCm: String? = null,
    val widthCm: String? = null,
    val depthCm: String? = null,
    val weightKg: String? = null,
    val material: String? = null,
    val fragile: Boolean? = null,
    val needsDisassembly: Boolean? = null,
    val needsSpecialHandling: Boolean? = null,
    val needsToShip: Boolean? = null,
    val packingDifficulty: Difficulty? = null,
    val liftingDifficulty: Difficulty? = null,
    val estimatedValue: String? = null,
    val condition: ItemCondition? = null,
    val remarks: String? = null,
    val confidenceScore: String? = null,
    val mediaIds: List<String>? = null,
)

/** ItemMergeRequest (POST /survey-items/merge). */
@Serializable
data class ItemMergeRequest(
    val itemIds: List<String>,
    val itemName: String? = null,
    val quantity: Int? = null,
)

/** ItemSplitRequest (POST /survey-items/{id}/split). */
@Serializable
data class ItemSplitRequest(
    val parts: List<SurveyItemUpdate>,
)
