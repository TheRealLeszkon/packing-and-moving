package com.packingandmoving.surveyor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.packingandmoving.surveyor.model.ProcessingSummary
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.utils.formatDisplayDate

@Composable
fun SurveyHistoryCard(
    summary: ProcessingSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = formatDisplayDate(summary.createdAt),
                    style = MaterialTheme.typography.titleMedium,
                )
                StatusBadge(status = summary.surveyStatus)
            }
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = "${summary.imageCount} photo${if (summary.imageCount == 1) "" else "s"} · " +
                    "${summary.itemCount} item${if (summary.itemCount == 1) "" else "s"} detected",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
