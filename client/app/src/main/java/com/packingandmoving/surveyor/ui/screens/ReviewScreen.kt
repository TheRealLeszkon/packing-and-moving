package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.packingandmoving.surveyor.ui.components.EmptyState
import com.packingandmoving.surveyor.ui.components.PhotoThumbnail
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.viewmodel.CaptureViewModel
import com.packingandmoving.surveyor.viewmodel.UploadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(
    viewModel: CaptureViewModel,
    onBack: () -> Unit,
    onUploaded: (sessionId: String) -> Unit,
) {
    val photos by viewModel.photos.collectAsState()
    val uploadState by viewModel.uploadState.collectAsState()

    LaunchedEffect(uploadState) {
        val state = uploadState
        if (state is UploadState.Success) {
            onUploaded(state.sessionId)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Photos") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        if (photos.isEmpty()) {
            EmptyState(
                "No photos yet. Go back and capture at least one photo.",
                modifier = Modifier.padding(paddingValues),
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(Spacing.md),
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(photos, key = { it.toString() }) { uri ->
                    PhotoThumbnail(uri = uri, onRemove = { viewModel.removePhoto(uri) })
                }
            }

            if (uploadState is UploadState.Error) {
                Text(
                    text = (uploadState as UploadState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = Spacing.md),
                )
            }

            Button(
                onClick = { viewModel.uploadPhotos() },
                enabled = photos.isNotEmpty() && uploadState !is UploadState.Uploading,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Spacing.md)
                    .height(Spacing.minTouchTarget),
            ) {
                if (uploadState is UploadState.Uploading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Upload ${photos.size} Photo${if (photos.size == 1) "" else "s"}")
                }
            }
        }
    }
}
