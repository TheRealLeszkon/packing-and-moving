package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SummaryScreen(
    surveyId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Final Summary",
        subtitle = "Sign-off (SCREEN_6) → GET /surveys/{id}/summary",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Complete Survey", onComplete),
            PlaceholderAction("Back", onBack),
        ),
    )
}
