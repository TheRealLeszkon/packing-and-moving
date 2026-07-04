package com.packingandmoving.surveyagent.camera

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
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
 * Read a media [Uri] (camera `file://` or gallery `content://`, image or video) and wrap its
 * bytes as a multipart part named `files` — the field both POST /surveys/{id}/images and
 * .../videos expect. Original bytes are sent uncompressed; the backend processes. Call off
 * the main thread.
 */
fun Uri.toMediaPart(resolver: ContentResolver): MultipartBody.Part {
    val bytes = resolver.openInputStream(this)?.use { it.readBytes() }
        ?: error("Unable to read media at $this")
    val mimeType = resolveMimeType(resolver)
    return MultipartBody.Part.createFormData(
        name = "files",
        filename = "upload_${System.currentTimeMillis()}_${hashCode()}.${mimeType.fileExtension()}",
        body = bytes.toRequestBody(mimeType.toMediaTypeOrNull()),
    )
}

/** Whether a [Uri]'s content type is a video (drives image vs. video upload endpoint). */
fun Uri.isVideo(resolver: ContentResolver): Boolean =
    resolveMimeType(resolver).startsWith("video", ignoreCase = true)

/**
 * The MIME type for this media [Uri]. [ContentResolver.getType] only resolves a type for
 * gallery `content://` Uris — camera captures are `file://` Uris (type is null) and some
 * devices report a generic `application/octet-stream`. In those cases fall back to the file
 * extension, which we control (`.mp4`, `.jpg`). The backend validates by MIME and only
 * accepts image types and `video/mp4`, so a correct type here is required for uploads to pass.
 */
private fun Uri.resolveMimeType(resolver: ContentResolver): String {
    val resolved = resolver.getType(this)
    if (resolved != null && !resolved.equals("application/octet-stream", ignoreCase = true)) {
        return resolved
    }
    val extension = MimeTypeMap.getFileExtensionFromUrl(toString())
        .ifEmpty { lastPathSegment?.substringAfterLast('.', "").orEmpty() }
        .lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        ?: if (extension == "mp4") "video/mp4" else "application/octet-stream"
}

private fun String.fileExtension(): String = when {
    contains("png", ignoreCase = true) -> "png"
    contains("mp4", ignoreCase = true) -> "mp4"
    contains("quicktime", ignoreCase = true) || contains("mov", ignoreCase = true) -> "mov"
    startsWith("video", ignoreCase = true) -> "mp4"
    else -> "jpg"
}
