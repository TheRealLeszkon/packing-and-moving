package com.packingandmoving.surveyagent.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import com.packingandmoving.surveyagent.model.Difficulty
import com.packingandmoving.surveyagent.model.ItemCondition
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.ItemForm

/**
 * The full, shared inventory-item form (every editable field). Used by the item-edit and
 * manual-create screens so both stay identical. Emits a new [ItemForm] on any change.
 */
@Composable
fun ItemFormFields(form: ItemForm, onChange: (ItemForm) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        OutlinedTextField(
            value = form.itemName,
            onValueChange = { onChange(form.copy(itemName = it)) },
            label = { Text("Name *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.category,
            onValueChange = { onChange(form.copy(category = it)) },
            label = { Text("Category") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            NumberField("Quantity", form.quantity, KeyboardType.Number, Modifier.weight(1f)) {
                onChange(form.copy(quantity = it))
            }
            TextField("Room", form.roomLocation, Modifier.weight(1f)) { onChange(form.copy(roomLocation = it)) }
        }

        Text("Dimensions & weight", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            NumberField("Height (cm)", form.heightCm, KeyboardType.Decimal, Modifier.weight(1f)) {
                onChange(form.copy(heightCm = it))
            }
            NumberField("Width (cm)", form.widthCm, KeyboardType.Decimal, Modifier.weight(1f)) {
                onChange(form.copy(widthCm = it))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            NumberField("Depth (cm)", form.depthCm, KeyboardType.Decimal, Modifier.weight(1f)) {
                onChange(form.copy(depthCm = it))
            }
            NumberField("Weight (kg)", form.weightKg, KeyboardType.Decimal, Modifier.weight(1f)) {
                onChange(form.copy(weightKg = it))
            }
        }
        NumberField("Estimated value", form.estimatedValue, KeyboardType.Decimal, Modifier.fillMaxWidth()) {
            onChange(form.copy(estimatedValue = it))
        }
        TextField("Material", form.material, Modifier.fillMaxWidth()) { onChange(form.copy(material = it)) }

        Text("Classification", style = MaterialTheme.typography.titleMedium)
        EnumDropdown(
            label = "Condition",
            options = listOf<ItemCondition?>(null) + ItemCondition.entries,
            selected = form.condition,
            optionLabel = { it?.name?.pretty() ?: "Not set" },
            onSelect = { onChange(form.copy(condition = it)) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            EnumDropdown(
                label = "Packing difficulty",
                options = listOf<Difficulty?>(null) + Difficulty.entries,
                selected = form.packingDifficulty,
                optionLabel = { it?.name?.pretty() ?: "Not set" },
                onSelect = { onChange(form.copy(packingDifficulty = it)) },
                modifier = Modifier.weight(1f),
            )
            EnumDropdown(
                label = "Lifting difficulty",
                options = listOf<Difficulty?>(null) + Difficulty.entries,
                selected = form.liftingDifficulty,
                optionLabel = { it?.name?.pretty() ?: "Not set" },
                onSelect = { onChange(form.copy(liftingDifficulty = it)) },
                modifier = Modifier.weight(1f),
            )
        }

        Text("Handling", style = MaterialTheme.typography.titleMedium)
        SwitchRow("Fragile", form.fragile) { onChange(form.copy(fragile = it)) }
        SwitchRow("Needs disassembly", form.needsDisassembly) { onChange(form.copy(needsDisassembly = it)) }
        SwitchRow("Needs special handling", form.needsSpecialHandling) { onChange(form.copy(needsSpecialHandling = it)) }
        SwitchRow("Needs to ship", form.needsToShip) { onChange(form.copy(needsToShip = it)) }

        OutlinedTextField(
            value = form.remarks,
            onValueChange = { onChange(form.copy(remarks = it)) },
            label = { Text("Notes") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun TextField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = modifier,
    )
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    keyboardType: KeyboardType,
    modifier: Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier,
    )
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T?>,
    selected: T?,
    optionLabel: (T?) -> String,
    onSelect: (T?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = optionLabel(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun String.pretty(): String = lowercase().replaceFirstChar { it.uppercase() }
