package com.packingandmoving.surveyor.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.packingandmoving.surveyor.model.InventoryItem
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.utils.formatConfidence
import com.packingandmoving.surveyor.utils.formatDimensions
import com.packingandmoving.surveyor.utils.formatValue
import com.packingandmoving.surveyor.utils.formatWeight
import com.packingandmoving.surveyor.utils.formatYesNo
import com.packingandmoving.surveyor.utils.toAbsoluteImageUrl

/**
 * Collapsed: image, name, confidence, category.
 * Expanded (chevron toggle): dimensions, weight, value, material, and the handling flags.
 * Tapping the card body (not the chevron) opens the Item Details screen.
 */
@Composable
fun InventoryItemCard(
    item: InventoryItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .animateContentSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                ItemThumbnail(
                    imageUrl = item.imageUrl.toAbsoluteImageUrl(),
                    contentDescription = item.itemName,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.itemName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = item.category ?: "Uncategorized",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatConfidence(item.confidenceScore),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                }
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                    )
                }
            }

            if (expanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                formatDimensions(item.estimatedHeightCm, item.estimatedWidthCm, item.estimatedDepthCm)
                    ?.let { DetailRow("Dimensions", it) }
                formatWeight(item.estimatedWeightKg)?.let { DetailRow("Weight", it) }
                formatValue(item.estimatedValue)?.let { DetailRow("Estimated value", it) }
                item.estimatedMaterial?.let { DetailRow("Material", it) }
                DetailRow("Fragile", formatYesNo(item.isFragile))
                DetailRow("Needs shipping", formatYesNo(item.needsToShip))
                DetailRow("Needs disassembly", formatYesNo(item.needsDisassembly))
                DetailRow("Needs special handling", formatYesNo(item.needsSpecialHandling))
                if (!item.remarks.isNullOrBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = "Notes",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = item.remarks,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
