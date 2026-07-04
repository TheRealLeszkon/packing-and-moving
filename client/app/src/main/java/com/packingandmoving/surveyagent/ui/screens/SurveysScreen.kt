package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

// Placeholder survey id used only to exercise navigation during Phase 3.
const val DEMO_SURVEY_ID = "demo-survey"
const val DEMO_ITEM_ID = "demo-item"

@Composable
fun SurveysScreen(
    onOpenSurvey: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Surveys",
        subtitle = "Request board & my surveys",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Open Survey") { onOpenSurvey(DEMO_SURVEY_ID) },
        ),
    )
}
