package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.theme.Dimens
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AiReportViewModel
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory

/**
 * AI Survey Report (SCREEN_2 → GET /surveys/{id}/items). A searchable inventory list; each
 * card shows the item, its room, and a confidence indicator, and opens the editable detail.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiReportScreen(
    surveyId: String,
    onOpenItem: (surveyId: String, itemId: String) -> Unit,
    onOpenSummary: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiReportViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(surveyId) { viewModel.load(surveyId) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("AI Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { onOpenSummary(surveyId) }) { Text("Summary") }
                },
            )
        },
    ) { innerPadding ->
        Column(Modifier.padding(innerPadding).fillMaxSize()) {
            OutlinedTextField(
                value = uiState.query,
                onValueChange = viewModel::onQueryChange,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                placeholder = { Text("Search items") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Medium, vertical = Spacing.Small),
            )

            when {
                uiState.isLoading && uiState.allItems.isEmpty() ->
                    CenterBox { CircularProgressIndicator() }

                uiState.errorMessage != null && uiState.allItems.isEmpty() ->
                    CenterBox {
                        Text(uiState.errorMessage!!, color = MaterialTheme.colorScheme.error)
                    }

                uiState.visibleItems.isEmpty() ->
                    CenterBox {
                        Text(
                            text = if (uiState.allItems.isEmpty()) "No items detected yet." else "No matches.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Medium),
                    verticalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    items(uiState.visibleItems, key = { it.id }) { item ->
                        ItemCard(item = item, onClick = { onOpenItem(surveyId, item.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(item: SurveyItem, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Leading placeholder tile (photos are loaded on the detail screen).
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(Dimens.ItemThumbnail),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("×${item.quantity}", style = MaterialTheme.typography.titleMedium)
                }
            }

            Spacer(Modifier.width(Spacing.Medium))

            Column(Modifier.weight(1f)) {
                Text(
                    text = item.itemName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val subtitle = listOfNotNull(item.roomLocation, item.category).joinToString(" • ")
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            ConfidenceBadge(item.confidenceScore)
        }
    }
}

/** High/low confidence chip driven by confidence_score (0–1); hidden when unknown. */
@Composable
private fun ConfidenceBadge(confidenceScore: String?) {
    val score = confidenceScore?.toDoubleOrNull() ?: return
    val high = score >= 0.7
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (high) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.errorContainer,
        contentColor = if (high) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onErrorContainer,
    ) {
        Text(
            text = "${(score * 100).toInt()}%",
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(Spacing.Large), contentAlignment = Alignment.Center) {
        content()
    }
}
