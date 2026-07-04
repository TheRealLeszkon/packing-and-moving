package com.packingandmoving.surveyagent.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.packingandmoving.surveyagent.model.SurveyStatus

/** Human-readable label for a survey status (frontend-integration.md §5 wire values). */
fun SurveyStatus.label(): String = when (this) {
    SurveyStatus.SCHEDULED -> "Scheduled"
    SurveyStatus.ASSIGNED -> "Assigned"
    SurveyStatus.IN_PROGRESS -> "In Progress"
    SurveyStatus.PROCESSING -> "Processing"
    SurveyStatus.READY_FOR_REVIEW -> "Ready for Review"
    SurveyStatus.AWAITING_CUSTOMER_APPROVAL -> "Awaiting Approval"
    SurveyStatus.REVISION_REQUIRED -> "Revision Required"
    SurveyStatus.APPROVED -> "Approved"
    SurveyStatus.COMPLETED -> "Completed"
    SurveyStatus.CANCELLED -> "Cancelled"
}

/**
 * Small rounded status chip. Terminal-positive states use the Tertiary container,
 * cancelled uses the Error container, everything in-flight uses the Secondary container
 * (DESIGN.md §3.1).
 */
@Composable
fun StatusBadge(status: SurveyStatus, modifier: Modifier = Modifier) {
    val container: Color
    val content: Color
    when (status) {
        SurveyStatus.APPROVED, SurveyStatus.COMPLETED -> {
            container = MaterialTheme.colorScheme.tertiaryContainer
            content = MaterialTheme.colorScheme.onTertiaryContainer
        }
        SurveyStatus.CANCELLED -> {
            container = MaterialTheme.colorScheme.errorContainer
            content = MaterialTheme.colorScheme.onErrorContainer
        }
        else -> {
            container = MaterialTheme.colorScheme.secondaryContainer
            content = MaterialTheme.colorScheme.onSecondaryContainer
        }
    }

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = status.label(),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}
