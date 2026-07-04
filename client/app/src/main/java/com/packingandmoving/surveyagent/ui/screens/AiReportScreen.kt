package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AiReportScreen(
    surveyId: String,
    onOpenItem: (surveyId: String, itemId: String) -> Unit,
    onOpenSummary: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "AI Survey Report",
        subtitle = "Detected items (SCREEN_2) → GET /surveys/{id}/items",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Open Item") { onOpenItem(surveyId, DEMO_ITEM_ID) },
            PlaceholderAction("Summary") { onOpenSummary(surveyId) },
            PlaceholderAction("Back", onBack),
        ),
    )
}
