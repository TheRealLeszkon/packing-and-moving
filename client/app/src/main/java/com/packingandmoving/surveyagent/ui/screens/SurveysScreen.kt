package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.ui.components.SurveyCard
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.SurveysUiState
import com.packingandmoving.surveyagent.viewmodel.SurveysViewModel

/**
 * Surveyor request board: open requests to accept and surveys already assigned to you
 * (GET /survey-requests/available|assigned, POST .../accept). Requires the surveyor role;
 * a non-surveyor account sees the forbidden error here.
 */
@Composable
fun SurveysScreen(
    onOpenSurvey: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveysViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        when {
            uiState.isLoading && uiState.available.isEmpty() && uiState.assigned.isEmpty() -> item {
                Box(Modifier.fillMaxWidth().padding(Spacing.Large), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.errorMessage != null && uiState.available.isEmpty() && uiState.assigned.isEmpty() -> item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(top = Spacing.Large),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = uiState.errorMessage!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                    TextButton(onClick = viewModel::refresh) { Text("Retry") }
                }
            }

            else -> boardContent(uiState, onAccept = viewModel::accept, onOpenSurvey = onOpenSurvey)
        }
    }
}

private fun LazyListScope.boardContent(
    uiState: SurveysUiState,
    onAccept: (String) -> Unit,
    onOpenSurvey: (String) -> Unit,
) {
    item { SectionHeader("Available Requests") }
    if (uiState.available.isEmpty()) {
        item { EmptyLine("No open requests right now.") }
    } else {
        items(uiState.available, key = { it.id }) { survey ->
            SurveyCard(
                survey = survey,
                action = { OutlinedButton(onClick = { onAccept(survey.id) }) { Text("Accept") } },
            )
        }
    }

    item { SectionHeader("Assigned to You") }
    if (uiState.assigned.isEmpty()) {
        item { EmptyLine("Nothing assigned yet.") }
    } else {
        items(uiState.assigned, key = { it.id }) { survey ->
            SurveyCard(survey = survey, onClick = { onOpenSurvey(survey.id) })
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = Spacing.Medium, bottom = Spacing.ExtraSmall),
    )
}

@Composable
private fun EmptyLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
