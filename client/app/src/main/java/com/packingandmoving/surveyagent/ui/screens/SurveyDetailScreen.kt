package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.components.StatusBadge
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.SurveyDetailViewModel

/**
 * Survey detail hub. Renders the survey summary, the lifecycle buttons the server says are
 * available right now (GET /surveys/{id}/status → available_actions), and status-appropriate
 * navigation into the capture / processing / review flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SurveyDetailScreen(
    surveyId: String,
    onOpenCamera: (surveyId: String) -> Unit,
    onOpenProcessing: (surveyId: String) -> Unit,
    onOpenResults: (surveyId: String) -> Unit,
    onOpenAddItem: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SurveyDetailViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showRejectDialog by remember { mutableStateOf(false) }
    var showCompleteConfirm by remember { mutableStateOf(false) }

    // Refresh every time the screen (re)enters the foreground — first entry and returns from
    // the camera/processing/results flow — so status and available actions are never stale.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.load(surveyId, forceReload = true) }

    // Confirm before completing: it ends capture and sends the survey to processing,
    // and the button appears in the same spot Start Survey just occupied.
    if (showCompleteConfirm) {
        AlertDialog(
            onDismissRequest = { showCompleteConfirm = false },
            title = { Text("Complete & process?") },
            text = { Text("This ends media capture and sends the survey for AI analysis. Make sure all photos and videos are uploaded first.") },
            confirmButton = {
                TextButton(onClick = {
                    showCompleteConfirm = false
                    viewModel.complete(surveyId)
                }) { Text("Complete") }
            },
            dismissButton = {
                TextButton(onClick = { showCompleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    if (showRejectDialog) {
        RejectDialog(
            onConfirm = { reason ->
                viewModel.reject(surveyId, reason)
                showRejectDialog = false
            },
            onDismiss = { showRejectDialog = false },
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Survey") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        val survey = uiState.survey
        when {
            uiState.isLoading && survey == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

            survey == null ->
                Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.Center) {
                    Text(
                        text = uiState.errorMessage ?: "Survey unavailable.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

            else -> DetailContent(
                survey = survey,
                availableActions = uiState.availableActions,
                actionInProgress = uiState.isActionInProgress,
                errorMessage = uiState.errorMessage,
                onAction = { action ->
                    // Reject needs a reason and complete is destructive-ish, so both go
                    // through dialogs; the rest fire directly.
                    when (action) {
                        "reject" -> showRejectDialog = true
                        "complete" -> showCompleteConfirm = true
                        else -> runAction(action, surveyId, viewModel)
                    }
                },
                onOpenCamera = { onOpenCamera(surveyId) },
                onOpenProcessing = { onOpenProcessing(surveyId) },
                onOpenResults = { onOpenResults(surveyId) },
                onOpenAddItem = { onOpenAddItem(surveyId) },
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

/** Lifecycle actions this client wires up (surveyor: start/complete/submit; customer: approve/reject; either: cancel). */
private val SUPPORTED_ACTIONS = setOf("start", "complete", "submit", "cancel", "approve", "reject")

private fun runAction(action: String, surveyId: String, viewModel: SurveyDetailViewModel) {
    when (action) {
        "start" -> viewModel.start(surveyId)
        "complete" -> viewModel.complete(surveyId)
        "submit" -> viewModel.submit(surveyId)
        "cancel" -> viewModel.cancel(surveyId)
        "approve" -> viewModel.approve(surveyId)
        // "reject" is handled via a reason dialog in the screen, not here.
    }
}

private fun actionLabel(action: String): String = when (action) {
    "start" -> "Start Survey"
    "complete" -> "Complete & Process"
    "submit" -> "Submit for Approval"
    "cancel" -> "Cancel Survey"
    "approve" -> "Approve"
    "reject" -> "Request Revision"
    "accept" -> "Accept"
    else -> action.replaceFirstChar { it.uppercase() }
}

@Composable
private fun DetailContent(
    survey: Survey,
    availableActions: List<String>,
    actionInProgress: Boolean,
    errorMessage: String?,
    onAction: (String) -> Unit,
    onOpenCamera: () -> Unit,
    onOpenProcessing: () -> Unit,
    onOpenResults: () -> Unit,
    onOpenAddItem: () -> Unit,
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

        AppCard(modifier = Modifier.fillMaxWidth()) {
            StatusBadge(status = survey.status)
            Spacer(Modifier.height(Spacing.Small))
            Text(
                survey.name,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Spacing.ExtraSmall))
            LabeledLine("From", survey.originAddress)
            LabeledLine("To", survey.destinationAddress)
            survey.preferredDatetime?.let { LabeledLine("Preferred", it.take(10)) }

            val estimates = listOfNotNull(
                survey.totalVolumeEstimate?.let { "Est. volume: $it m³" },
                survey.totalValueEstimate?.let { "Est. value: $$it" },
            )
            if (estimates.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.ExtraSmall))
                Text(estimates.joinToString("   •   "), style = MaterialTheme.typography.labelMedium)
            }
        }

        // Status-appropriate navigation into the capture/review flow.
        val nav = navOptionsFor(survey.status)
        if (nav.isNotEmpty()) {
            Text("Open", style = MaterialTheme.typography.titleMedium)
            nav.forEach { option ->
                OutlinedButton(
                    onClick = when (option) {
                        NavOption.Camera -> onOpenCamera
                        NavOption.Processing -> onOpenProcessing
                        NavOption.Results -> onOpenResults
                        NavOption.AddItem -> onOpenAddItem
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(option.label) }
            }
        }

        // Server-driven lifecycle actions.
        if (availableActions.isNotEmpty()) {
            Text("Actions", style = MaterialTheme.typography.titleMedium)
            availableActions.forEach { action ->
                Button(
                    onClick = { onAction(action) },
                    enabled = action in SUPPORTED_ACTIONS && !actionInProgress,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(actionLabel(action)) }
            }
        }

        if (actionInProgress) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        errorMessage?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }

        Spacer(Modifier.height(Spacing.Large))
    }
}

@Composable
private fun LabeledLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

/** Collects the required reason for POST /surveys/{id}/reject. */
@Composable
private fun RejectDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request revision") },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                label = { Text("Reason") },
                minLines = 2,
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(reason.trim()) },
                enabled = reason.isNotBlank(),
            ) { Text("Send") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private enum class NavOption(val label: String) {
    Camera("Capture Media"),
    Processing("View Processing"),
    Results("Survey Results"),
    AddItem("Add Item Manually"),
}

/** Which flow screens make sense to open for a given status. */
private fun navOptionsFor(status: SurveyStatus): List<NavOption> = when (status) {
    SurveyStatus.IN_PROGRESS -> listOf(NavOption.Camera)
    SurveyStatus.PROCESSING -> listOf(NavOption.Processing)
    // Items are editable only in these two statuses (frontend-integration.md §10),
    // so surface the add-item entry alongside the results/item list here.
    SurveyStatus.READY_FOR_REVIEW,
    SurveyStatus.REVISION_REQUIRED -> listOf(NavOption.Results, NavOption.AddItem)
    SurveyStatus.AWAITING_CUSTOMER_APPROVAL,
    SurveyStatus.APPROVED,
    SurveyStatus.COMPLETED -> listOf(NavOption.Results)
    else -> emptyList()
}
