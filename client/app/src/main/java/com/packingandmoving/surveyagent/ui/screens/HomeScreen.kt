package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun HomeScreen(
    onCreateSurvey: () -> Unit,
    onOpenSurvey: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Home",
        subtitle = "Agent dashboard (SCREEN_9)",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Start New Survey", onCreateSurvey),
            PlaceholderAction("Open Survey") { onOpenSurvey(DEMO_SURVEY_ID) },
        ),
    )
}
