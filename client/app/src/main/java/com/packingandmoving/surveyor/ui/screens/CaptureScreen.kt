package com.packingandmoving.surveyor.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.packingandmoving.surveyor.camera.CameraPreview
import com.packingandmoving.surveyor.camera.takePhoto
import com.packingandmoving.surveyor.ui.theme.Spacing
import com.packingandmoving.surveyor.viewmodel.CaptureViewModel

@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onBack: () -> Unit,
    onReviewPhotos: () -> Unit,
) {
    val context = LocalContext.current
    val photos by viewModel.photos.collectAsState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> viewModel.addPhotos(uris) }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    val onBackState = rememberUpdatedState(onBack)

    Box(modifier = Modifier.fillMaxSize()) {
        if (hasCameraPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureReady = { imageCapture = it },
            )
        } else {
            CameraPermissionRationale(
                onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            )
        }

        // Top overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { onBackState.value() },
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = "${photos.size} photo${if (photos.size == 1) "" else "s"}",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(50))
                    .padding(horizontal = Spacing.md, vertical = Spacing.xs),
            )
        }

        // Bottom overlay controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(Spacing.lg),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .size(Spacing.minTouchTarget)
                        .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = "Choose from gallery", tint = Color.White)
                }

                CaptureButton(
                    enabled = hasCameraPermission && imageCapture != null,
                    onClick = {
                        val capture = imageCapture ?: return@CaptureButton
                        takePhoto(
                            context = context,
                            imageCapture = capture,
                            onSaved = { uri -> viewModel.addPhoto(uri) },
                            onError = { /* Swallowing is unsafe; the user can just retry the shot. */ },
                        )
                    },
                )

                if (photos.isNotEmpty()) {
                    AsyncImage(
                        model = photos.last(),
                        contentDescription = "Last captured photo",
                        modifier = Modifier
                            .size(Spacing.minTouchTarget)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                } else {
                    Spacer(Modifier.size(Spacing.minTouchTarget))
                }
            }

            Spacer(Modifier.height(Spacing.md))

            Button(
                onClick = onReviewPhotos,
                enabled = photos.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.minTouchTarget),
            ) {
                Text("Review Photos (${photos.size})", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(72.dp)
            .background(if (enabled) Color.White else Color.Gray, CircleShape),
    ) {
        Icon(
            imageVector = Icons.Filled.CameraAlt,
            contentDescription = "Take photo",
            tint = Color.Black,
            modifier = Modifier.size(32.dp),
        )
    }
}

@Composable
private fun CameraPermissionRationale(onRequestPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(Spacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Camera access is needed to capture survey photos.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(Spacing.md))
        Button(onClick = onRequestPermission) {
            Text("Grant Camera Permission")
        }
    }
}
