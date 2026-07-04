package com.packingandmoving.surveyagent.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.packingandmoving.surveyagent.viewmodel.CapturePhase
import com.packingandmoving.surveyagent.viewmodel.CaptureViewModel
import com.packingandmoving.surveyagent.ui.theme.Spacing
import kotlinx.coroutines.delay

/**
 * Photo review hub (new). Shows every staged photo (camera + gallery), lets the user remove
 * or add more, and only uploads on Continue — which uploads the batch, completes the survey,
 * and then shows processing progress inline (photos stay visible) until results are ready.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoReviewScreen(
    surveyId: String,
    viewModel: CaptureViewModel,
    onTakePhotos: () -> Unit,
    onViewResults: (surveyId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showAddSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(),
    ) { uris -> if (uris.isNotEmpty()) viewModel.addPhotos(uris) }

    fun openGallery() = galleryLauncher.launch(
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
    )

    if (showAddSheet) {
        ModalBottomSheet(onDismissRequest = { showAddSheet = false }, sheetState = sheetState) {
            AddPhotosSheet(
                onTakePhotos = { showAddSheet = false; onTakePhotos() },
                onChooseFromGallery = { showAddSheet = false; openGallery() },
            )
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Survey Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            BottomActions(
                phase = uiState.phase,
                photoCount = uiState.photos.size,
                roomLocation = uiState.roomLocation,
                errorMessage = uiState.errorMessage,
                enabled = uiState.phase == CapturePhase.Editing,
                onRoomChange = viewModel::onRoomLocationChange,
                onAddMore = { showAddSheet = true },
                onContinue = { viewModel.uploadAndComplete(surveyId, context.contentResolver) },
                onViewResults = { onViewResults(surveyId) },
            )
        },
    ) { innerPadding ->
        if (uiState.photos.isEmpty()) {
            EmptyState(
                onTakePhotos = onTakePhotos,
                onChooseFromGallery = ::openGallery,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.Small),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                verticalArrangement = Arrangement.spacedBy(Spacing.Small),
            ) {
                items(uiState.photos, key = { it.id }) { photo ->
                    PhotoThumb(
                        model = photo.uri,
                        removable = uiState.phase == CapturePhase.Editing,
                        onRemove = { viewModel.removePhoto(photo.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoThumb(model: Any, removable: Boolean, onRemove: () -> Unit) {
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(MaterialTheme.shapes.medium),
    ) {
        AsyncImage(
            model = model,
            contentDescription = "Survey photo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (removable) {
            Surface(
                onClick = onRemove,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.55f),
                contentColor = Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(28.dp),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Remove photo", modifier = Modifier.padding(4.dp))
            }
        }
    }
}

@Composable
private fun AddPhotosSheet(onTakePhotos: () -> Unit, onChooseFromGallery: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(Spacing.Large),
        verticalArrangement = Arrangement.spacedBy(Spacing.Small),
    ) {
        Text("Add Photos", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.Small))
        Button(onClick = onTakePhotos, modifier = Modifier.fillMaxWidth()) { Text("Take Photos") }
        OutlinedButton(onClick = onChooseFromGallery, modifier = Modifier.fillMaxWidth()) {
            Text("Choose From Gallery")
        }
        Spacer(Modifier.height(Spacing.Medium))
    }
}

@Composable
private fun EmptyState(
    onTakePhotos: () -> Unit,
    onChooseFromGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(Spacing.Large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Add Photos", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(Spacing.Small))
        Text(
            "Capture the room or pick existing photos to build the inventory.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.Large))
        Button(onClick = onTakePhotos, modifier = Modifier.fillMaxWidth()) { Text("Take Photos") }
        Spacer(Modifier.height(Spacing.Small))
        OutlinedButton(onClick = onChooseFromGallery, modifier = Modifier.fillMaxWidth()) {
            Text("Choose From Gallery")
        }
    }
}

@Composable
private fun BottomActions(
    phase: CapturePhase,
    photoCount: Int,
    roomLocation: String,
    errorMessage: String?,
    enabled: Boolean,
    onRoomChange: (String) -> Unit,
    onAddMore: () -> Unit,
    onContinue: () -> Unit,
    onViewResults: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(Spacing.Medium), verticalArrangement = Arrangement.spacedBy(Spacing.Small)) {
            errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            when (phase) {
                CapturePhase.Editing -> {
                    if (photoCount > 0) {
                        OutlinedTextField(
                            value = roomLocation,
                            onValueChange = onRoomChange,
                            label = { Text("Room (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                            OutlinedButton(onClick = onAddMore, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.size(4.dp))
                                Text("Add More")
                            }
                            Button(onClick = onContinue, enabled = enabled, modifier = Modifier.weight(1f)) {
                                Text("Continue")
                            }
                        }
                    }
                }
                CapturePhase.Working -> ProcessingIndicator()
                CapturePhase.Ready -> Button(onClick = onViewResults, modifier = Modifier.fillMaxWidth()) {
                    Text("View Survey Results")
                }
            }
        }
    }
}

/** Inline processing indicator with cycling status copy (item 4/5). */
@Composable
private fun ProcessingIndicator() {
    val messages = remember {
        listOf(
            "Analyzing your inventory…",
            "Detecting objects…",
            "Estimating dimensions…",
            "Matching furniture…",
            "Calculating confidence…",
        )
    }
    var index by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1800)
            index = (index + 1) % messages.size
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.Medium)) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        Text(messages[index], style = MaterialTheme.typography.bodyLarge)
    }
}
