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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.model.SurveySummary
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.SummaryViewModel

/**
 * Final survey summary / sign-off (SCREEN_6 → GET /surveys/{id}/summary). Shows the tallied
 * inventory and, when the server allows it, submits the survey for customer approval
 * (POST /surveys/{id}/submit). "submit" is offered only when it's in available_actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(
    surveyId: String,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SummaryViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(surveyId) { viewModel.load(surveyId) }
    LaunchedEffect(uiState.isSubmitted) { if (uiState.isSubmitted) onComplete() }

    val canSubmit = "submit" in uiState.availableActions

    Scaffold(
        modifier = modifier,
        topBar = {
            androidx.compose.material3.TopAppBar(
                title = { Text("Summary") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            if (uiState.summary != null && canSubmit) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.submit(surveyId) },
                        enabled = !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth().padding(Spacing.Medium),
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                        } else {
                            Text("Submit for Approval")
                        }
                    }
                }
            }
        },
    ) { innerPadding ->
        val summary = uiState.summary
        when {
            uiState.isLoading && summary == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            summary == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "Summary unavailable.",
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }

            else -> SummaryContent(summary, uiState.errorMessage, Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun SummaryContent(summary: SurveySummary, errorMessage: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.Medium)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(Spacing.Medium),
    ) {
        Spacer(Modifier.height(Spacing.Small))
        Text("Inventory summary", style = MaterialTheme.typography.headlineSmall)

        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            StatTile("Total volume", "${summary.totalVolumeM3} m³", Modifier.weight(1f))
            StatTile("Total value", "$${summary.totalValue}", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
            StatTile("Distinct items", summary.distinctItems.toString(), Modifier.weight(1f))
            StatTile("Total quantity", summary.totalQuantity.toString(), Modifier.weight(1f))
        }

        val flags = buildList {
            if (summary.fragileItems > 0) add("Fragile: ${summary.fragileItems}")
            if (summary.needsDisassemblyItems > 0) add("Disassembly: ${summary.needsDisassemblyItems}")
            if (summary.needsSpecialHandlingItems > 0) add("Special handling: ${summary.needsSpecialHandlingItems}")
        }
        if (flags.isNotEmpty()) {
            Text("Special requirements", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                flags.forEach { Chip(it) }
            }
        }

        if (summary.byRoom.isNotEmpty()) {
            Text("By room", style = MaterialTheme.typography.titleMedium)
            AppCard(modifier = Modifier.fillMaxWidth()) {
                summary.byRoom.forEach { room ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(room.roomLocation ?: "Unspecified", style = MaterialTheme.typography.bodyMedium)
                        Text("${room.quantity}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Spacing.Large))
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.headlineSmall)
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Chip(text: String) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}
