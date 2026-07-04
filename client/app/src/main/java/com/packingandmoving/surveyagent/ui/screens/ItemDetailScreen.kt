package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ItemDetailScreen(
    surveyId: String,
    itemId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Item Detail",
        subtitle = "Edit item $itemId (SCREEN_8) → PATCH /survey-items/{id}",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Back", onBack),
        ),
    )
}
