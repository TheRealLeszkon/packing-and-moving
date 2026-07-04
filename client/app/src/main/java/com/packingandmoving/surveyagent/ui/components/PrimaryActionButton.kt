package com.packingandmoving.surveyagent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.packingandmoving.surveyagent.ui.theme.Dimens
import com.packingandmoving.surveyagent.ui.theme.SurveyAgentTheme

/**
 * Full-width primary call-to-action (DESIGN.md §2). 56dp tall, Primary container,
 * 8dp corners (from the theme), with an optional leading icon.
 */
@Composable
fun PrimaryActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.PrimaryButtonHeight),
        enabled = enabled,
        shape = MaterialTheme.shapes.small,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Preview
@Composable
private fun PrimaryActionButtonPreview() {
    SurveyAgentTheme {
        PrimaryActionButton(
            text = "Start New Survey",
            onClick = {},
            leadingIcon = Icons.Default.Add,
        )
    }
}
