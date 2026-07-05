package com.packingandmoving.surveyagent.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.packingandmoving.surveyagent.camera.getCameraProvider
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.CaptureViewModel
import kotlinx.coroutines.delay
import java.io.File

private enum class CameraMode { Photo, Video }

/**
 * Camera capture. Live CameraX preview (FIT_CENTER so the full sensor frame shows — no
 * zoomed-in crop) with a Photo/Video mode toggle. Photos save a full-quality JPEG; videos
 * record to MP4. Both only *stage* their URI in the shared [CaptureViewModel] — upload
 * happens later from the review screen, so the shutter returns instantly.
 */
@Composable
fun CameraScreen(
    captureViewModel: CaptureViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by captureViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (hasPermission) {
            CameraContent(
                stagedCount = uiState.media.size,
                onPhotoCaptured = captureViewModel::addPhoto,
                onVideoCaptured = captureViewModel::addVideo,
                onDone = onBack,
            )
        } else {
            PermissionRequest(
                onGrant = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                onBack = onBack,
            )
        }
    }
}

@Composable
private fun CameraContent(
    stagedCount: Int,
    onPhotoCaptured: (Uri) -> Unit,
    onVideoCaptured: (Uri) -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // FILL_CENTER: fill the whole view edge-to-edge (no letterbox/black bars). The
    // "too zoomed in" feel is fixed by starting at the widest lens (min zoom) below.
    val previewView = remember {
        PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
    }
    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val videoCapture = remember {
        VideoCapture.withOutput(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.HD)).build())
    }

    var mode by remember { mutableStateOf(CameraMode.Photo) }
    var flashEnabled by remember { mutableStateOf(false) }
    var captureError by remember { mutableStateOf<String?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var zoomRatio by remember { mutableFloatStateOf(1f) }
    var minZoom by remember { mutableFloatStateOf(1f) }
    var maxZoom by remember { mutableFloatStateOf(1f) }
    var zoomInitialized by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var isRecording by remember { mutableStateOf(false) }

    // Elapsed-time ticker for the REC indicator; resets whenever a recording starts.
    var recordSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(isRecording) {
        recordSeconds = 0
        while (isRecording) {
            delay(1_000)
            recordSeconds++
        }
    }

    fun applyZoom(target: Float) {
        val clamped = target.coerceIn(minZoom, maxZoom)
        zoomRatio = clamped
        camera?.cameraControl?.setZoomRatio(clamped)
    }

    // Rebind whenever the mode changes (photo vs. video use case).
    LaunchedEffect(mode) {
        val provider = context.getCameraProvider()
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        provider.unbindAll()
        val useCase = if (mode == CameraMode.Photo) imageCapture else videoCapture
        camera = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, useCase)
        val zoomState = camera?.cameraInfo?.zoomState?.value
        minZoom = zoomState?.minZoomRatio ?: 1f
        maxZoom = zoomState?.maxZoomRatio ?: 1f
        // Start at the widest lens (e.g. 0.5×) the first time; keep the user's zoom on rebind.
        if (!zoomInitialized) {
            zoomRatio = minZoom
            zoomInitialized = true
        }
        applyZoom(zoomRatio)
    }

    fun capturePhoto() {
        imageCapture.flashMode =
            if (flashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) = onPhotoCaptured(Uri.fromFile(file))
                override fun onError(exception: ImageCaptureException) {
                    captureError = exception.message ?: "Failed to capture photo."
                }
            },
        )
    }

    fun toggleRecording() {
        val current = recording
        if (current != null) {
            current.stop()
            return
        }
        val file = File(context.cacheDir, "video_${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        // No audio → no RECORD_AUDIO permission needed.
        recording = videoCapture.output.prepareRecording(context, options)
            .start(ContextCompat.getMainExecutor(context)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> isRecording = true
                    is VideoRecordEvent.Finalize -> {
                        isRecording = false
                        recording = null
                        if (event.hasError()) {
                            captureError = "Video failed (code ${event.error})."
                        } else {
                            onVideoCaptured(Uri.fromFile(file))
                        }
                    }
                }
            }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(maxZoom) {
                    detectTransformGestures { _, _, zoom, _ -> applyZoom(zoomRatio * zoom) }
                },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(Spacing.Small),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDone) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
            }
            Text(
                text = "SCAN ROOM INVENTORY",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (mode == CameraMode.Photo) {
                TextButton(onClick = { flashEnabled = !flashEnabled }) {
                    Text(if (flashEnabled) "Flash On" else "Flash Off", color = Color.White)
                }
            }
        }

        if (isRecording) {
            RecordingIndicator(
                seconds = recordSeconds,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(Spacing.Medium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.Small),
        ) {
            captureError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
            }

            if (maxZoom > minZoom && !isRecording) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.Small),
                ) {
                    OutlinedButton(onClick = { applyZoom(zoomRatio - 0.5f) }) { Text("–", color = Color.White) }
                    Slider(value = zoomRatio, onValueChange = ::applyZoom, valueRange = minZoom..maxZoom, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { applyZoom(zoomRatio + 0.5f) }) { Text("+", color = Color.White) }
                }
                Text("${"%.1f".format(zoomRatio)}×", color = Color.White)
            }

            // Photo / Video mode toggle (hidden mid-recording).
            if (!isRecording) {
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.Small)) {
                    FilterChip(selected = mode == CameraMode.Photo, onClick = { mode = CameraMode.Photo }, label = { Text("Photo") })
                    FilterChip(selected = mode == CameraMode.Video, onClick = { mode = CameraMode.Video }, label = { Text("Video") })
                }
            }

            Text("Collected: $stagedCount", color = Color.White)

            ShutterButton(
                mode = mode,
                isRecording = isRecording,
                onClick = { if (mode == CameraMode.Photo) capturePhoto() else toggleRecording() },
            )

            if (!isRecording) {
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) {
                    Text(if (stagedCount > 0) "Done ($stagedCount)" else "Done")
                }
            }
        }
    }
}

/** Blinking red dot + elapsed MM:SS — the affirmative "recording now" signal. */
@Composable
private fun RecordingIndicator(seconds: Int, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "rec")
    val dotAlpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "recDot",
    )
    Row(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), CircleShape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).background(Color.Red.copy(alpha = dotAlpha), CircleShape))
        Text(
            text = "REC %02d:%02d".format(seconds / 60, seconds % 60),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ShutterButton(mode: CameraMode, isRecording: Boolean, onClick: () -> Unit) {
    val innerColor = when {
        mode == CameraMode.Video && isRecording -> Color.Red
        mode == CameraMode.Video -> Color(0xFFE53935)
        else -> Color.White
    }
    Box(
        modifier = Modifier
            .size(72.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(6.dp)
            .background(innerColor, CircleShape)
            .clickable(onClickLabel = if (mode == CameraMode.Photo) "Capture" else "Record", onClick = onClick),
    )
}

@Composable
private fun PermissionRequest(onGrant: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(Spacing.Large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Camera access is needed to capture inventory photos and videos.",
            color = Color.White,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.Medium))
        Button(onClick = onGrant) { Text("Grant camera access") }
        Spacer(Modifier.height(Spacing.Small))
        Button(onClick = onBack) { Text("Go back") }
    }
}
