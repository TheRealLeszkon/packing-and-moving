package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.packingandmoving.surveyor.model.InventoryItem
import com.packingandmoving.surveyor.ui.components.DifficultySelector
import com.packingandmoving.surveyor.ui.components.LabeledSwitch
import com.packingandmoving.surveyor.ui.components.LabeledTextField
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.utils.formatConfidence
import com.packingandmoving.surveyor.utils.toAbsoluteImageUrl
import com.packingandmoving.surveyor.viewmodel.ItemDetailsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailsScreen(
    viewModel: ItemDetailsViewModel,
    onBack: () -> Unit,
    onSave: (InventoryItem) -> Unit,
) {
    val item by viewModel.item.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Item Details") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState()),
        ) {
            ItemHeroImage(imageUrl = item.imageUrl.toAbsoluteImageUrl(), itemName = item.itemName)

            Column(modifier = Modifier.padding(Spacing.md)) {
                Text(
                    text = formatConfidence(item.confidenceScore),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                )
                Spacer(Modifier.height(Spacing.md))

                LabeledTextField(
                    label = "Item name",
                    value = item.itemName,
                    onValueChange = { new -> viewModel.update { it.copy(itemName = new) } },
                )
                Spacer(Modifier.height(Spacing.sm))
                LabeledTextField(
                    label = "Category",
                    value = item.category.orEmpty(),
                    onValueChange = { new -> viewModel.update { it.copy(category = new) } },
                )
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LabeledTextField(
                        label = "Quantity",
                        value = item.quantity?.toString().orEmpty(),
                        onValueChange = { new ->
                            viewModel.update { it.copy(quantity = new.toIntOrNull()) }
                        },
                        keyboardType = KeyboardType.Number,
                        modifier = Modifier.fillMaxWidth(0.4f),
                    )
                    LabeledTextField(
                        label = "Room location",
                        value = item.roomLocation.orEmpty(),
                        onValueChange = { new -> viewModel.update { it.copy(roomLocation = new) } },
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                Text("Dimensions & weight", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LabeledTextField(
                        label = "Height (cm)",
                        value = item.estimatedHeightCm?.toString().orEmpty(),
                        onValueChange = { new ->
                            viewModel.update { it.copy(estimatedHeightCm = new.toFloatOrNull()) }
                        },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(0.33f),
                    )
                    LabeledTextField(
                        label = "Width (cm)",
                        value = item.estimatedWidthCm?.toString().orEmpty(),
                        onValueChange = { new ->
                            viewModel.update { it.copy(estimatedWidthCm = new.toFloatOrNull()) }
                        },
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                    LabeledTextField(
                        label = "Depth (cm)",
                        value = item.estimatedDepthCm?.toString().orEmpty(),
                        onValueChange = { new ->
                            viewModel.update { it.copy(estimatedDepthCm = new.toFloatOrNull()) }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    LabeledTextField(
                        label = "Weight (kg)",
                        value = item.estimatedWeightKg?.toString().orEmpty(),
                        onValueChange = { new ->
                            viewModel.update { it.copy(estimatedWeightKg = new.toFloatOrNull()) }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                    LabeledTextField(
                        label = "Estimated value ($)",
                        value = item.estimatedValue?.toString().orEmpty(),
                        onValueChange = { new ->
                            viewModel.update { it.copy(estimatedValue = new.toFloatOrNull()) }
                        },
                        keyboardType = KeyboardType.Decimal,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                LabeledTextField(
                    label = "Material",
                    value = item.estimatedMaterial.orEmpty(),
                    onValueChange = { new -> viewModel.update { it.copy(estimatedMaterial = new) } },
                )
                Spacer(Modifier.height(Spacing.sm))
                LabeledTextField(
                    label = "Condition",
                    value = item.condition.orEmpty(),
                    onValueChange = { new -> viewModel.update { it.copy(condition = new) } },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                Text("Handling", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(Spacing.sm))
                LabeledSwitch(
                    label = "Fragile",
                    checked = item.isFragile ?: false,
                    onCheckedChange = { new -> viewModel.update { it.copy(isFragile = new) } },
                )
                LabeledSwitch(
                    label = "Needs shipping",
                    checked = item.needsToShip ?: false,
                    onCheckedChange = { new -> viewModel.update { it.copy(needsToShip = new) } },
                )
                LabeledSwitch(
                    label = "Needs disassembly",
                    checked = item.needsDisassembly ?: false,
                    onCheckedChange = { new -> viewModel.update { it.copy(needsDisassembly = new) } },
                )
                LabeledSwitch(
                    label = "Needs special handling",
                    checked = item.needsSpecialHandling ?: false,
                    onCheckedChange = { new -> viewModel.update { it.copy(needsSpecialHandling = new) } },
                )

                Spacer(Modifier.height(Spacing.md))
                DifficultySelector(
                    label = "Packing difficulty",
                    value = item.packingDifficulty,
                    onValueChange = { new -> viewModel.update { it.copy(packingDifficulty = new) } },
                )
                Spacer(Modifier.height(Spacing.md))
                DifficultySelector(
                    label = "Lifting difficulty",
                    value = item.liftingDifficulty,
                    onValueChange = { new -> viewModel.update { it.copy(liftingDifficulty = new) } },
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.md))
                LabeledTextField(
                    label = "Notes",
                    value = item.remarks.orEmpty(),
                    onValueChange = { new -> viewModel.update { it.copy(remarks = new) } },
                    singleLine = false,
                )

                Spacer(Modifier.height(Spacing.lg))
                Button(
                    onClick = { onSave(item) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(Spacing.minTouchTarget),
                ) {
                    Text("Save Changes")
                }
                Spacer(Modifier.height(Spacing.md))
            }
        }
    }
}

@Composable
private fun ItemHeroImage(imageUrl: String?, itemName: String) {
    if (imageUrl != null) {
        AsyncImage(
            model = imageUrl,
            contentDescription = itemName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
        )
    } else {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Inventory2,
                contentDescription = itemName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.height(64.dp),
            )
        }
    }
}
