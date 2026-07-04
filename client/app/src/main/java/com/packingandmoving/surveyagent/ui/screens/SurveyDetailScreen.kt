package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SurveyDetailScreen(
    surveyId: String,
    onOpenCamera: (surveyId: String) -> Unit,
    onOpenProcessing: (surveyId: String) -> Unit,
    onOpenReport: (surveyId: String) -> Unit,
    onOpenSummary: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Survey Detail",
        subtitle = "survey $surveyId — actions from GET /surveys/{id}/status",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Capture (Camera)") { onOpenCamera(surveyId) },
            PlaceholderAction("Processing") { onOpenProcessing(surveyId) },
            PlaceholderAction("AI Report") { onOpenReport(surveyId) },
            PlaceholderAction("Summary") { onOpenSummary(surveyId) },
            PlaceholderAction("Back", onBack),
        ),
    )
}
