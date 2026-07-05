package com.packingandmoving.surveyagent.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.packingandmoving.surveyagent.ui.theme.Elevation
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.ui.theme.SurveyAgentTheme

/**
 * Standard content card (DESIGN.md §1): Surface Container background, 8dp corners,
 * Level-1 elevation, 16dp internal padding. Callers fill the column body.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    containerColor: Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = containerColor ?: MaterialTheme.colorScheme.surfaceContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Level1),
    ) {
        Column(
            modifier = Modifier.padding(Spacing.Medium),
            content = content,
        )
    }
}

@Preview
@Composable
private fun AppCardPreview() {
    SurveyAgentTheme {
        AppCard {
            Text("Card title", style = MaterialTheme.typography.titleLarge)
            Text("Supporting text", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
