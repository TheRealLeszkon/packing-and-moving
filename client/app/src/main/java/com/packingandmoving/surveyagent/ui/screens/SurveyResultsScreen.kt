package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveySummary
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.SurveyResultsViewModel

/**
 * Survey Results (merges the old AI Report + Summary). Top: totals and per-item value/volume/
 * weight breakdowns so the AI estimates are transparent. Below: the searchable, expandable
 * inventory (edit on tap-through, delete inline, add manually). Submits for approval when the
 * server allows it. This is the single destination after processing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyResultsScreen(
    surveyId: String,
    onOpenItem: (surveyId: String, itemId: String) -> Unit,
    onAddItem: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    onSubmitted: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveyResultsViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load(surveyId) }
    LaunchedEffect(uiState.isSubmitted) { if (uiState.isSubmitted) onSubmitted() }
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { snackbar.showSnackbar(it); viewModel.consumeError() }
    }

    pendingDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Delete item?") },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteItem(surveyId, id); pendingDeleteId = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("Cancel") } },
        )
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Survey Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { onAddItem(surveyId) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Item") },
            )
        },
        bottomBar = {
            if ("submit" in uiState.availableActions) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.submit(surveyId) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
                    ) { Text(if (uiState.isSubmitting) "Submitting…" else "Submit for Approval") }
                }
            }
        },
    ) { innerPadding ->
        if (uiState.isLoading && uiState.items.isEmpty() && uiState.summary == null) {
            Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            uiState.summary?.let { summary ->
                item { SummarySection(summary, uiState.items) }
            }

            item {
                Text(
                    "Inventory Items",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = Spacing.Small),
                )
            }
            item {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    placeholder = { Text("Search items") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (uiState.visibleItems.isEmpty()) {
                item {
                    Text(
                        if (uiState.items.isEmpty()) "No items yet. Add one with the button." else "No matches.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.visibleItems, key = { it.id }) { item ->
                    ExpandableItemCard(
                        item = item,
                        onEdit = { onOpenItem(surveyId, item.id) },
                        onDelete = { pendingDeleteId = item.id },
                    )
                }
            }

            item { Spacer(Modifier.height(Spacing.ExtraLarge)) }
        }
    }
}

@Composable
private fun SummarySection(summary: SurveySummary, items: List<SurveyItem>) {
    val totalWeight = items.sumOf { weightContribution(it) ?: 0.0 }

    Column(verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
        Text("Survey Summary", style = MaterialTheme.typography.titleLarge)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            StatTile("Est. value", summary.totalValue, Modifier.weight(1f))
            StatTile("Est. volume", "${summary.totalVolumeM3} m³", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            StatTile("Est. weight", if (totalWeight > 0) "%.1f kg".format(totalWeight) else "—", Modifier.weight(1f))
            StatTile("Items", "${summary.totalQuantity} (${summary.distinctItems} kinds)", Modifier.weight(1f))
        }

        val categoryRows = summary.byCategory
            .sortedByDescending { it.quantity }
            .map { (it.category ?: "Uncategorized") to "${it.quantity}" }
        Breakdown("Category breakdown", categoryRows)
        Breakdown("Value breakdown", contributions(items, ::valueContribution) { "%,.0f".format(it) })
        Breakdown("Volume breakdown", contributions(items, ::volumeContribution) { "%.2f m³".format(it) })
        Breakdown("Weight breakdown", contributions(items, ::weightContribution) { "%.1f kg".format(it) })
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Expandable breakdown card: title + count, tap to reveal each contributor. */
@Composable
private fun Breakdown(title: String, rows: List<Pair<String, String>>) {
    if (rows.isEmpty()) return
    var expanded by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(
                if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
        }
        if (expanded) {
            Spacer(Modifier.height(Spacing.ExtraSmall))
            rows.forEach { (name, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = Spacing.Small),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ExpandableItemCard(item: SurveyItem, onEdit: () -> Unit, onDelete: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    AppCard(modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(
                modifier = Modifier.weight(1f).clickable { expanded = !expanded },
            ) {
                Text(item.itemName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOfNotNull(item.roomLocation, item.category).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text("×${item.quantity}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = Spacing.Small))
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete item") }
        }

        if (expanded) {
            Spacer(Modifier.height(Spacing.ExtraSmall))
            val dims = listOfNotNull(item.heightCm, item.widthCm, item.depthCm)
            if (dims.size == 3) DetailRow("Dimensions", "${item.heightCm}×${item.widthCm}×${item.depthCm} cm")
            item.weightKg?.let { DetailRow("Weight", "$it kg") }
            item.estimatedValue?.let { DetailRow("Value", it) }
            item.material?.let { DetailRow("Material", it) }
            item.confidenceScore?.toDoubleOrNull()?.let { DetailRow("Confidence", "${(it * 100).toInt()}%") }
            Spacer(Modifier.height(Spacing.ExtraSmall))
            TextButton(onClick = onEdit) { Text("Edit item") }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---- per-item contribution helpers (client-derived transparency) ----

private fun valueContribution(item: SurveyItem): Double? =
    item.estimatedValue?.toDoubleOrNull()?.let { it * item.quantity }

private fun weightContribution(item: SurveyItem): Double? =
    item.weightKg?.toDoubleOrNull()?.let { it * item.quantity }

private fun volumeContribution(item: SurveyItem): Double? {
    val h = item.heightCm?.toDoubleOrNull() ?: return null
    val w = item.widthCm?.toDoubleOrNull() ?: return null
    val d = item.depthCm?.toDoubleOrNull() ?: return null
    return h * w * d / 1_000_000.0 * item.quantity // cm³ → m³
}

private inline fun contributions(
    items: List<SurveyItem>,
    amount: (SurveyItem) -> Double?,
    format: (Double) -> String,
): List<Pair<String, String>> =
    items.mapNotNull { item -> amount(item)?.takeIf { it > 0 }?.let { item.itemName to it } }
        .sortedByDescending { it.second }
        .map { it.first to format(it.second) }
