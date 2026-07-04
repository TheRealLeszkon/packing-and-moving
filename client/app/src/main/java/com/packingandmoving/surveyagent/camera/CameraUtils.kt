package com.packingandmoving.surveyagent.camera

import android.content.Context
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
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
 * Wrap a captured JPEG file as a multipart part named `files` — the field the backend's
 * POST /surveys/{id}/images expects (it accepts a list under that name).
 */
fun File.toImagePart(): MultipartBody.Part =
    MultipartBody.Part.createFormData(
        name = "files",
        filename = name,
        body = asRequestBody("image/jpeg".toMediaType()),
    )
