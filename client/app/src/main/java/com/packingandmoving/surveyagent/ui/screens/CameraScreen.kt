package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun CameraScreen(
    surveyId: String,
    onFinishCapture: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Camera",
        subtitle = "Capture inventory (SCREEN_5) → POST media",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Finish & Process") { onFinishCapture(surveyId) },
            PlaceholderAction("Back", onBack),
        ),
    )
}
