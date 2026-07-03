package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.packingandmoving.surveyor.ui.components.EmptyState
import com.packingandmoving.surveyor.ui.components.FullScreenError
import com.packingandmoving.surveyor.ui.components.FullScreenLoading
import com.packingandmoving.surveyor.ui.components.InventoryItemCard
import com.packingandmoving.surveyor.ui.components.StatusBadge
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.viewmodel.ReportUiState
import com.packingandmoving.surveyor.viewmodel.ReportViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: ReportViewModel,
    onBack: () -> Unit,
    onItemClick: (itemId: String) -> Unit,
    onFinishSurvey: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Survey Report") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val state = uiState
                    if (state is ReportUiState.Success) {
                        StatusBadge(
                            status = state.response.surveyStatus,
                            modifier = Modifier.padding(end = Spacing.md),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is ReportUiState.Loading -> FullScreenLoading(
                "Loading survey report…",
                modifier = Modifier.padding(paddingValues),
            )
            is ReportUiState.Error -> FullScreenError(
                message = state.message,
                modifier = Modifier.padding(paddingValues),
                onRetry = { viewModel.load() },
            )
            is ReportUiState.Success -> {
                if (state.response.items.isEmpty()) {
                    EmptyState(
                        "No items were detected in this survey.",
                        modifier = Modifier.padding(paddingValues),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                        LazyColumn(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentPadding = PaddingValues(Spacing.md),
                            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                        ) {
                            if (state.response.needsMoreImages == true && !state.response.requestedImages.isNullOrEmpty()) {
                                item {
                                    RequestedImagesCard(state.response.requestedImages)
                                }
                            }
                            items(state.response.items, key = { it.id }) { item ->
                                InventoryItemCard(item = item, onClick = { onItemClick(item.id) })
                            }
                        }
                        Button(
                            onClick = onFinishSurvey,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(Spacing.md)
                                .height(Spacing.minTouchTarget),
                        ) {
                            Text("Finish Survey")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestedImagesCard(requestedImages: List<String>) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(Spacing.md)) {
            Row {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(Spacing.xs))
                Text(
                    text = "More photos would improve this survey",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
            requestedImages.forEach { request ->
                Text(
                    text = "•  $request",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(top = Spacing.xs),
                )
            }
        }
    }
}
