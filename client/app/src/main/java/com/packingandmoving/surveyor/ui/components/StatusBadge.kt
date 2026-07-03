package com.packingandmoving.surveyor.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.packingandmoving.surveyor.model.SurveyStatus

@Composable
fun StatusBadge(status: SurveyStatus, modifier: Modifier = Modifier) {
    val (background, content, label) = when (status) {
        SurveyStatus.COMPLETED -> Triple(
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
            "Completed",
        )
        SurveyStatus.PROCESSING -> Triple(
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
            "Processing",
        )
        SurveyStatus.PENDING -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Pending",
        )
        SurveyStatus.FAILED -> Triple(
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
            "Failed",
        )
        SurveyStatus.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceContainerHighest,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "Unknown",
        )
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = content,
        modifier = modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}
