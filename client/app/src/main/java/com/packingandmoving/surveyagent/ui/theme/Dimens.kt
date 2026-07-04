package com.packingandmoving.surveyagent.ui.theme

import androidx.compose.ui.unit.dp

// Spacing scale (DESIGN.md §3): 16dp screen margins, 24dp between major sections.
object Spacing {
    val ExtraSmall = 4.dp
    val Small = 8.dp
    val Medium = 16.dp
    val Large = 24.dp
    val ExtraLarge = 32.dp
}

// Elevation levels (DESIGN.md §1).
object Elevation {
    val Level0 = 0.dp  // Background surface
    val Level1 = 1.dp  // Cards
    val Level2 = 3.dp  // FABs and active navigation
}

// Recurring component sizes referenced across the screen specs.
object Dimens {
    val MinTouchTarget = 48.dp
    val PrimaryButtonHeight = 56.dp
    val AvatarSize = 40.dp
    val ItemThumbnail = 64.dp
    val GalleryThumbnail = 80.dp
}
