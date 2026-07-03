package com.packingandmoving.surveyor.camera

import android.content.Context
import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.FileProvider
import androidx.core.content.ContextCompat
import com.packingandmoving.surveyor.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

/** Takes a photo with the given [imageCapture] use case and returns its content [Uri] via callback. */
fun takePhoto(
    context: Context,
    imageCapture: ImageCapture,
    onSaved: (Uri) -> Unit,
    onError: (String) -> Unit,
) {
    val photoFile = createCaptureFile(context)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    photoFile,
                )
                onSaved(uri)
            }

            override fun onError(exception: ImageCaptureException) {
                onError(exception.message ?: "Failed to capture photo.")
            }
        },
    )
}

/** Creates a unique file inside the cache dir declared in file_paths.xml. */
private fun createCaptureFile(context: Context): File {
    val directory = File(context.cacheDir, "captured_images").apply { mkdirs() }
    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(java.util.Date())
    return File(directory, "SURVEY_$timestamp.jpg")
}
