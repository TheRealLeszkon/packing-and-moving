package com.packingandmoving.surveyor.utils

import java.util.Locale

fun formatConfidence(score: Float?): String =
    if (score == null) "Unknown confidence" else "${(score * 100).toInt()}% confidence"

fun formatDimensions(heightCm: Float?, widthCm: Float?, depthCm: Float?): String? {
    if (heightCm == null && widthCm == null && depthCm == null) return null
    val h = heightCm?.let { "%.0f".format(Locale.US, it) } ?: "?"
    val w = widthCm?.let { "%.0f".format(Locale.US, it) } ?: "?"
    val d = depthCm?.let { "%.0f".format(Locale.US, it) } ?: "?"
    return "$h × $w × $d cm (H × W × D)"
}

fun formatWeight(kg: Float?): String? = kg?.let { "%.1f kg".format(Locale.US, it) }

fun formatValue(value: Float?): String? = value?.let { "$%.2f".format(Locale.US, it) }

fun formatYesNo(value: Boolean?): String = when (value) {
    true -> "Yes"
    false -> "No"
    null -> "Unknown"
}
