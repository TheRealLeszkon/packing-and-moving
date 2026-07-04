package com.packingandmoving.surveyagent.viewmodel

import com.packingandmoving.surveyagent.model.Difficulty
import com.packingandmoving.surveyagent.model.ItemCondition
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveyItemCreate
import com.packingandmoving.surveyagent.model.SurveyItemUpdate

/**
 * All user-editable inventory fields as one immutable form value, shared by the edit
 * (ItemDetail) and manual-create screens. Numeric fields are edited as text to preserve
 * Decimal precision; blanks map to null on the wire. `confidenceScore` (0–1) is editable
 * on update only — the create schema does not accept it (it's AI-owned at creation).
 */
data class ItemForm(
    val itemName: String = "",
    val category: String = "",
    val quantity: String = "1",
    val roomLocation: String = "",
    val material: String = "",
    val heightCm: String = "",
    val widthCm: String = "",
    val depthCm: String = "",
    val weightKg: String = "",
    val estimatedValue: String = "",
    val condition: ItemCondition? = null,
    val packingDifficulty: Difficulty? = null,
    val liftingDifficulty: Difficulty? = null,
    val fragile: Boolean = false,
    val needsDisassembly: Boolean = false,
    val needsSpecialHandling: Boolean = false,
    val needsToShip: Boolean = true,
    val remarks: String = "",
    val confidenceScore: String = "",
) {
    val isValidForCreate: Boolean get() = itemName.isNotBlank()
}

fun SurveyItem.toForm(): ItemForm = ItemForm(
    itemName = itemName,
    category = category.orEmpty(),
    quantity = quantity.toString(),
    roomLocation = roomLocation.orEmpty(),
    material = material.orEmpty(),
    heightCm = heightCm.orEmpty(),
    widthCm = widthCm.orEmpty(),
    depthCm = depthCm.orEmpty(),
    weightKg = weightKg.orEmpty(),
    estimatedValue = estimatedValue.orEmpty(),
    condition = condition,
    packingDifficulty = packingDifficulty,
    liftingDifficulty = liftingDifficulty,
    fragile = fragile,
    needsDisassembly = needsDisassembly,
    needsSpecialHandling = needsSpecialHandling,
    needsToShip = needsToShip,
    remarks = remarks.orEmpty(),
    confidenceScore = confidenceScore.orEmpty(),
)

/** Edit: every field is sent; nulls are omitted by the JSON config, so blanks clear fields. */
fun ItemForm.toUpdate(): SurveyItemUpdate = SurveyItemUpdate(
    itemName = itemName.blankToNull(),
    category = category.blankToNull(),
    quantity = quantity.trim().toIntOrNull(),
    roomLocation = roomLocation.blankToNull(),
    material = material.blankToNull(),
    heightCm = heightCm.blankToNull(),
    widthCm = widthCm.blankToNull(),
    depthCm = depthCm.blankToNull(),
    weightKg = weightKg.blankToNull(),
    estimatedValue = estimatedValue.blankToNull(),
    condition = condition,
    packingDifficulty = packingDifficulty,
    liftingDifficulty = liftingDifficulty,
    fragile = fragile,
    needsDisassembly = needsDisassembly,
    needsSpecialHandling = needsSpecialHandling,
    needsToShip = needsToShip,
    remarks = remarks.blankToNull(),
    confidenceScore = confidenceScore.blankToNull(),
)

/** Manual create: name is required; unset optionals are omitted so backend defaults apply. */
fun ItemForm.toCreate(mediaIds: List<String>? = null): SurveyItemCreate = SurveyItemCreate(
    itemName = itemName.trim(),
    category = category.blankToNull(),
    quantity = quantity.trim().toIntOrNull() ?: 1,
    roomLocation = roomLocation.blankToNull(),
    material = material.blankToNull(),
    heightCm = heightCm.blankToNull(),
    widthCm = widthCm.blankToNull(),
    depthCm = depthCm.blankToNull(),
    weightKg = weightKg.blankToNull(),
    estimatedValue = estimatedValue.blankToNull(),
    condition = condition,
    packingDifficulty = packingDifficulty,
    liftingDifficulty = liftingDifficulty,
    fragile = fragile,
    needsDisassembly = needsDisassembly,
    needsSpecialHandling = needsSpecialHandling,
    needsToShip = needsToShip,
    remarks = remarks.blankToNull(),
    mediaIds = mediaIds,
)

private fun String.blankToNull(): String? = trim().ifBlank { null }
