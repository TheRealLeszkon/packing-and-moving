package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.packingandmoving.surveyor.ui.components.FullScreenError
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.viewmodel.ProcessingUiState
import com.packingandmoving.surveyor.viewmodel.ProcessingViewModel
import kotlinx.coroutines.delay

private val progressMessages = listOf(
    "Uploading photos to the survey team…",
    "Identifying furniture and boxes…",
    "Estimating sizes and weights…",
    "Checking for fragile or special-handling items…",
    "Almost done — finalizing the inventory…",
)

@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    onCompleted: (sessionId: String) -> Unit,
    onReturnHome: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is ProcessingUiState.Completed) {
            onCompleted(state.response.sessionId)
        }
    }

    Scaffold { paddingValues ->
        when (val state = uiState) {
            is ProcessingUiState.Loading, is ProcessingUiState.InProgress -> {
                ProcessingInProgress(modifier = Modifier.padding(paddingValues))
            }
            is ProcessingUiState.Completed -> {
                // Navigation happens in LaunchedEffect above; show progress until it fires.
                ProcessingInProgress(modifier = Modifier.padding(paddingValues))
            }
            is ProcessingUiState.Failed -> {
                FullScreenError(
                    message = state.message,
                    modifier = Modifier.padding(paddingValues),
                    onRetry = { viewModel.retry() },
                    secondaryActionLabel = "Return Home",
                    onSecondaryAction = onReturnHome,
                )
            }
        }
    }
}

@Composable
private fun ProcessingInProgress(modifier: Modifier = Modifier) {
    var messageIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            messageIndex = (messageIndex + 1) % progressMessages.size
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = "Analyzing your survey",
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = progressMessages[messageIndex],
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
