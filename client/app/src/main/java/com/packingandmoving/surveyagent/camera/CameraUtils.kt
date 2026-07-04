package com.packingandmoving.surveyagent.camera

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/** Await the CameraX provider (its ListenableFuture) as a suspend call. */
suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    val future = ProcessCameraProvider.getInstance(this)
    future.addListener(
        {
            runCatching { future.get() }
                .onSuccess(continuation::resume)
                .onFailure(continuation::resumeWithException)
        },
        ContextCompat.getMainExecutor(this),
    )
}

/**
 * Read an image [Uri] (camera `file://` or gallery `content://`) and wrap its bytes as a
 * multipart part named `files` — the field POST /surveys/{id}/images expects. The original
 * bytes are sent uncompressed; the backend resizes. Call off the main thread.
 */
fun Uri.toImagePart(resolver: ContentResolver): MultipartBody.Part {
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() }
        ?: error("Unable to read image at $this")
    val mimeType = resolver.getType(this) ?: "image/jpeg"
    val extension = if (mimeType.contains("png", ignoreCase = true)) "png" else "jpg"
    return MultipartBody.Part.createFormData(
        name = "files",
        filename = "upload_${System.currentTimeMillis()}_${hashCode()}.$extension",
        body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
    )
}
