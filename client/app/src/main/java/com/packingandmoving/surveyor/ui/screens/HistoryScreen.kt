package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Arrangement
import com.packingandmoving.surveyor.ui.components.EmptyState
import com.packingandmoving.surveyor.ui.components.FullScreenError
import com.packingandmoving.surveyor.ui.components.FullScreenLoading
import com.packingandmoving.surveyor.ui.components.SurveyHistoryCard
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.viewmodel.HistoryUiState
import com.packingandmoving.surveyor.viewmodel.HistoryViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    onBack: () -> Unit,
    onSurveyClick: (sessionId: String) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Survey History") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        when (val state = uiState) {
            is HistoryUiState.Loading -> FullScreenLoading(
                "Loading surveys…",
                modifier = Modifier.padding(paddingValues),
            )
            is HistoryUiState.Error -> FullScreenError(
                message = state.message,
                modifier = Modifier.padding(paddingValues),
                onRetry = { viewModel.load() },
            )
            is HistoryUiState.Success -> {
                if (state.surveys.isEmpty()) {
                    EmptyState(
                        "No surveys yet. Start a new survey from the home screen.",
                        modifier = Modifier.padding(paddingValues),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        contentPadding = PaddingValues(Spacing.md),
                        verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                    ) {
                        items(state.surveys, key = { it.sessionId }) { survey ->
                            SurveyHistoryCard(
                                summary = survey,
                                onClick = { onSurveyClick(survey.sessionId) },
                            )
                        }
                    }
                }
            }
        }
    }
}
