package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CreateSurveyScreen(
    onCancel: () -> Unit,
    onSurveyCreated: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Create Survey",
        subtitle = "Intake form (SCREEN_7) → POST /surveys",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Start Survey") { onSurveyCreated(DEMO_SURVEY_ID) },
            PlaceholderAction("Cancel", onCancel),
        ),
    )
}
