package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.ui.components.StatusBadge
import com.packingandmoving.surveyagent.ui.components.processingStageLabel
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.ProcessingViewModel

/**
 * "Processing…" screen (frontend-integration.md §6). Polls GET /surveys/{id}/status via the
 * ViewModel and advances to the AI report once the survey leaves `processing`. No push
 * channel exists, so this poll is the mechanism; AI failures still resolve to
 * ready_for_review, so it always terminates.
 */
@Composable
fun ProcessingScreen(
    surveyId: String,
    onReadyForReview: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProcessingViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(surveyId) { viewModel.start(surveyId) }
    LaunchedEffect(uiState.isReadyForReview) {
        if (uiState.isReadyForReview) onReadyForReview(surveyId)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = { viewModel.retry(surveyId) }) { Text("Retry") }
        } else {
            CircularProgressIndicator()
            Spacer(Modifier.height(Spacing.Large))
            Text(
                text = processingStageLabel(uiState.processingStage),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.Small))
            Text(
                text = "This can take a minute. You can wait here — we'll open the report automatically.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            uiState.status?.let { status ->
                Spacer(Modifier.height(Spacing.Large))
                StatusBadge(status = status)
            }
        }
    }
}
