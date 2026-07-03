package com.packingandmoving.surveyor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** A single detected item, as returned by GET /processing/{id}. */
@Serializable
data class InventoryItem(
    val id: String,
    @SerialName("source_image_id") val sourceImageId: String? = null,
    /**
     * Relative path (e.g. "/images/{id}") to the photo this item was detected from.
     * Null when Gemini could not attribute the item to a specific photo.
     */
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("item_name") val itemName: String,
    val category: String? = null,
    val quantity: Int? = null,
    @SerialName("needs_to_ship") val needsToShip: Boolean? = null,
    @SerialName("confidence_score") val confidenceScore: Float? = null,
    @SerialName("estimated_weight_kg") val estimatedWeightKg: Float? = null,
    @SerialName("estimated_height_cm") val estimatedHeightCm: Float? = null,
    @SerialName("estimated_width_cm") val estimatedWidthCm: Float? = null,
    @SerialName("estimated_depth_cm") val estimatedDepthCm: Float? = null,
    @SerialName("estimated_value") val estimatedValue: Float? = null,
    @SerialName("estimated_material") val estimatedMaterial: String? = null,
    @SerialName("is_fragile") val isFragile: Boolean? = null,
    @SerialName("needs_disassembly") val needsDisassembly: Boolean? = null,
    @SerialName("needs_special_handling") val needsSpecialHandling: Boolean? = null,
    @SerialName("packing_difficulty") val packingDifficulty: String? = null,
    @SerialName("lifting_difficulty") val liftingDifficulty: String? = null,
    @SerialName("room_location") val roomLocation: String? = null,
    val condition: String? = null,
    val remarks: String? = null,
    @SerialName("created_at") val createdAt: String,
)
