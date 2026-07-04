package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.packingandmoving.surveyagent.ui.theme.Dimens
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.ItemDetailUiState
import com.packingandmoving.surveyagent.viewmodel.ItemDetailViewModel

/**
 * Item Detail / edit (SCREEN_8 → PATCH /survey-items/{id}). Shows the evidencing photos
 * (signed URLs via Coil) and an editable form; only changed fields are sent. Editing is
 * backend-gated to the assigned surveyor while ready_for_review/revision_required.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailScreen(
    surveyId: String,
    itemId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ItemDetailViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(surveyId, itemId) { viewModel.load(surveyId, itemId) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(uiState.item?.itemName ?: "Item") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            SaveBar(
                isSaving = uiState.isSaving,
                isSaved = uiState.isSaved,
                enabled = uiState.item != null && !uiState.isSaving,
                onSave = { viewModel.save(itemId) },
            )
        },
    ) { innerPadding ->
        when {
            uiState.isLoading && uiState.item == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            uiState.item == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(uiState.errorMessage ?: "Item unavailable.", color = MaterialTheme.colorScheme.error)
                }

            else -> EditForm(uiState, viewModel, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun EditForm(
    uiState: ItemDetailUiState,
    viewModel: ItemDetailViewModel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.Medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Spacer(Modifier.height(Spacing.Small))

        Hero(imageUrls = uiState.imageUrls, confidenceScore = uiState.item?.confidenceScore)

        SectionTitle("Physical properties")
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            DecimalField("Height (cm)", uiState.heightCm, viewModel::onHeightChange, Modifier.weight(1f))
            DecimalField("Width (cm)", uiState.widthCm, viewModel::onWidthChange, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            DecimalField("Depth (cm)", uiState.depthCm, viewModel::onDepthChange, Modifier.weight(1f))
            DecimalField("Weight (kg)", uiState.weightKg, viewModel::onWeightChange, Modifier.weight(1f))
        }
        DecimalField("Estimated value", uiState.estimatedValue, viewModel::onValueChange, Modifier.fillMaxWidth())

        SectionTitle("Handling")
        SwitchRow("Fragile", uiState.fragile, viewModel::onFragileChange)
        SwitchRow("Needs disassembly", uiState.needsDisassembly, viewModel::onNeedsDisassemblyChange)

        SectionTitle("Notes")
        OutlinedTextField(
            value = uiState.remarks,
            onValueChange = viewModel::onRemarksChange,
            label = { Text("Remarks") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )

        uiState.errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Spacing.Medium))
    }
}

@Composable
private fun Hero(imageUrls: List<String>, confidenceScore: String?) {
    if (imageUrls.isEmpty()) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(200.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text("No photos") }
        }
        return
    }

    Box {
        AsyncImage(
            model = imageUrls.first(),
            contentDescription = "Item photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(MaterialTheme.shapes.medium),
        )
        confidenceScore?.toDoubleOrNull()?.let { score ->
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(Spacing.Small),
            ) {
                Text(
                    "${(score * 100).toInt()}% match",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }

    if (imageUrls.size > 1) {
        Spacer(Modifier.height(Spacing.Small))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            items(imageUrls.drop(1)) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(Dimens.GalleryThumbnail)
                        .clip(MaterialTheme.shapes.small),
                )
            }
        }
    }
}

@Composable
private fun DecimalField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SaveBar(isSaving: Boolean, isSaved: Boolean, enabled: Boolean, onSave: () -> Unit) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
            horizontalArrangement = Arrangement.spacedBy(Spacing.Medium),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSaved) {
                Text("Saved ✓", color = MaterialTheme.colorScheme.primary)
            }
            Button(onClick = onSave, enabled = enabled, modifier = Modifier.weight(1f)) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save Changes")
                }
            }
        }
    }
}
