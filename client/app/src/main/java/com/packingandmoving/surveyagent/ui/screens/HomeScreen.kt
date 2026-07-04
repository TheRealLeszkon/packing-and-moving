package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.components.PrimaryActionButton
import com.packingandmoving.surveyagent.ui.components.StatusBadge
import com.packingandmoving.surveyagent.ui.components.SurveyCard
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.HomeUiState
import com.packingandmoving.surveyagent.viewmodel.HomeViewModel

/**
 * Home dashboard (SCREEN_9): greeting, the "start new survey" action, an optional
 * continue-draft card, and the recent survey list (GET /surveys/my). Loading, empty and
 * error states are all handled.
 */
@Composable
fun HomeScreen(
    onCreateSurvey: () -> Unit,
    onOpenSurvey: (surveyId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.Medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.Large),
    ) {
        item {
            Column(Modifier.padding(top = Spacing.Large)) {
                Text("Hello", style = MaterialTheme.typography.headlineLarge)
                Text(
                    text = "Here's your survey activity.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            PrimaryActionButton(
                text = "Start New Survey",
                onClick = onCreateSurvey,
                leadingIcon = Icons.Default.Add,
            )
        }

        uiState.draftSurvey?.let { draft ->
            item {
                DraftCard(survey = draft, onContinue = { onOpenSurvey(draft.id) })
            }
        }

        item {
            Text(
                text = "Recent Surveys",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        homeListContent(uiState, onOpenSurvey, onRetry = viewModel::refresh)
    }
}

/** Loading / empty / error / list content for the recent surveys section. */
private fun androidx.compose.foundation.lazy.LazyListScope.homeListContent(
    uiState: HomeUiState,
    onOpenSurvey: (String) -> Unit,
    onRetry: () -> Unit,
) {
    when {
        uiState.isLoading && uiState.surveys.isEmpty() -> item {
            Box(Modifier.fillMaxWidth().padding(Spacing.Large), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        uiState.errorMessage != null && uiState.surveys.isEmpty() -> item {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = uiState.errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
        }

        uiState.recentSurveys.isEmpty() -> item {
            Text(
                text = "No surveys yet. Start one above.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> items(uiState.recentSurveys, key = { it.id }) { survey ->
            SurveyCard(survey = survey, onClick = { onOpenSurvey(survey.id) })
        }
    }
}

@Composable
private fun DraftCard(survey: Survey, onContinue: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth()) {
        StatusBadge(status = survey.status)
        Spacer(Modifier.height(Spacing.Small))
        Text(
            text = survey.name,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = survey.originAddress,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(Spacing.Small))
        OutlinedButton(onClick = onContinue) { Text("Continue Survey") }
    }
}
