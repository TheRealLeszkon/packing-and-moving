package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.packingandmoving.surveyagent.ui.theme.Spacing

// Placeholder ids used only to exercise navigation from screens not yet wired to real data.
const val DEMO_SURVEY_ID = "demo-survey"
const val DEMO_ITEM_ID = "demo-item"

/** A navigation action rendered as a button on a placeholder screen. */
data class PlaceholderAction(val label: String, val onClick: () -> Unit)

/**
 * Temporary screen body used during Phase 3 so navigation can be exercised end to end.
 * Each real screen replaces this call with its actual UI in a later phase.
 */
@Composable
fun PlaceholderScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: List<PlaceholderAction> = emptyList(),
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Medium),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
        )
        if (subtitle != null) {
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actions.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.Large))
            actions.forEach { action ->
                OutlinedButton(
                    onClick = action.onClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = Spacing.ExtraSmall),
                ) {
                    Text(action.label)
                }
            }
        }
    }
}
