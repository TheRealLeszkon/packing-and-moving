package com.packingandmoving.surveyagent.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.packingandmoving.surveyagent.R

// Work Sans (DESIGN.md §1). Bundled as a single variable font; the weight axis is
// applied per style on API 26+ and degrades to synthetic weights below that.
@OptIn(ExperimentalTextApi::class)
private fun workSans(weight: Int) = Font(
    resId = R.font.work_sans,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

private val WorkSans = FontFamily(
    workSans(400),
    workSans(500),
    workSans(700),
)

private val default = Typography()

val Typography = default.copy(
    displayLarge = default.displayLarge.copy(
        fontFamily = WorkSans, fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 40.sp,
    ),
    headlineMedium = default.headlineMedium.copy(
        fontFamily = WorkSans, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp,
    ),
    headlineSmall = default.headlineSmall.copy(fontFamily = WorkSans, fontWeight = FontWeight.Bold),
    titleLarge = default.titleLarge.copy(
        fontFamily = WorkSans, fontWeight = FontWeight.Bold, fontSize = 18.sp, lineHeight = 24.sp,
    ),
    titleMedium = default.titleMedium.copy(fontFamily = WorkSans, fontWeight = FontWeight.Medium),
    bodyLarge = default.bodyLarge.copy(
        fontFamily = WorkSans, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp,
    ),
    bodyMedium = default.bodyMedium.copy(
        fontFamily = WorkSans, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelLarge = default.labelLarge.copy(
        fontFamily = WorkSans, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp,
    ),
    labelMedium = default.labelMedium.copy(fontFamily = WorkSans),
    labelSmall = default.labelSmall.copy(fontFamily = WorkSans),
)
