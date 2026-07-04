package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun ProcessingScreen(
    surveyId: String,
    onReadyForReview: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Processing…",
        subtitle = "Polls GET /surveys/{id}/status until ready_for_review",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Ready for Review") { onReadyForReview(surveyId) },
        ),
    )
}
