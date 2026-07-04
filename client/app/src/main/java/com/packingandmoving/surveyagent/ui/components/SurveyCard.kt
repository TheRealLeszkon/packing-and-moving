package com.packingandmoving.surveyagent.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.ui.theme.Spacing

/**
 * Standard survey summary card used by Home and the request board: name + status badge,
 * the origin → destination route, any volume/value estimates, and an optional trailing
 * [action] (e.g. an "Accept" button). Tapping the card triggers [onClick] when provided.
 */
@Composable
fun SurveyCard(
    survey: Survey,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    action: (@Composable () -> Unit)? = null,
) {
    val cardModifier = modifier
        .fillMaxWidth()
        .let { if (onClick != null) it.clickable(onClick = onClick) else it }

    AppCard(modifier = cardModifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = survey.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = Spacing.Small),
            )
            StatusBadge(status = survey.status)
        }

        Spacer(Modifier.height(Spacing.ExtraSmall))
        Text(
            text = "${survey.originAddress} → ${survey.destinationAddress}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        val estimates = listOfNotNull(
            survey.totalVolumeEstimate?.let { "Est. volume: $it m³" },
            survey.totalValueEstimate?.let { "Est. value: $$it" },
        )
        if (estimates.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.ExtraSmall))
            Text(
                text = estimates.joinToString("   •   "),
                style = MaterialTheme.typography.labelMedium,
            )
        }

        if (action != null) {
            Spacer(Modifier.height(Spacing.Small))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                action()
            }
        }
    }
}
