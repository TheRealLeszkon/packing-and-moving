package com.packingandmoving.surveyagent.ui.components

import android.net.Uri
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage

/**
 * Fullscreen viewer for a photo or video. Photos support pinch-to-zoom and pan; videos
 * play (looping, muted) via [VideoView] — works for both local staged files and the
 * backend's signed URLs. Close with the button or a tap on the backdrop.
 */
@Composable
fun MediaViewerDialog(model: Any, isVideo: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f)),
        ) {
            if (isVideo) {
                AndroidView(
                    factory = { context ->
                        VideoView(context).apply {
                            setVideoURI(Uri.parse(model.toString()))
                            setOnPreparedListener { player ->
                                player.isLooping = true
                                player.setVolume(0f, 0f) // recordings have no audio track anyway
                                start()
                            }
                        }
                    },
                    onRelease = { it.stopPlayback() },
                    modifier = Modifier.fillMaxSize().align(Alignment.Center),
                )
            } else {
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }
                AsyncImage(
                    model = model,
                    contentDescription = "Expanded photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 6f)
                                offsetX += pan.x
                                offsetY += pan.y
                                if (scale == 1f) { offsetX = 0f; offsetY = 0f }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                )
            }

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(40.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close viewer", tint = Color.White)
            }
        }
    }
}
