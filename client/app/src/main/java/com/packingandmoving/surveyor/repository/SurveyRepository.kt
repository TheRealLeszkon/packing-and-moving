package com.packingandmoving.surveyor.repository

import android.content.ContentResolver
import android.net.Uri
import com.packingandmoving.surveyor.api.SurveyApi
import com.packingandmoving.surveyor.model.ProcessingResponse
import com.packingandmoving.surveyor.model.ProcessingSummary
import com.packingandmoving.surveyor.model.UploadResponse
import com.packingandmoving.surveyor.utils.ApiResult
import com.packingandmoving.surveyor.utils.toFriendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to the FastAPI backend. The client only uploads images and reads status back —
 * all analysis happens server-side.
 */
class SurveyRepository(
    private val api: SurveyApi,
    private val contentResolver: ContentResolver,
) {

    suspend fun uploadImages(uris: List<Uri>): ApiResult<UploadResponse> = safeCall {
        val parts = uris.map { it.toMultipartPart(contentResolver) }
        api.uploadImages(parts)
    }

    suspend fun getProcessing(sessionId: String): ApiResult<ProcessingResponse> = safeCall {
        api.getProcessing(sessionId)
    }

    suspend fun listProcessing(): ApiResult<List<ProcessingSummary>> = safeCall {
        api.listProcessing()
    }

    private suspend fun <T> safeCall(block: suspend () -> T): ApiResult<T> =
        withContext(Dispatchers.IO) {
            try {
                ApiResult.Success(block())
            } catch (e: Exception) {
                ApiResult.Error(e.toFriendlyMessage())
            }
        }
}

private fun Uri.toMultipartPart(contentResolver: ContentResolver): MultipartBody.Part {
    val mimeType = contentResolver.getType(this) ?: "image/jpeg"
    val bytes = contentResolver.openInputStream(this)?.use { it.readBytes() }
        ?: throw java.io.IOException("Could not read image at $this")
    val fileName = "photo_${System.currentTimeMillis()}.${mimeType.substringAfterLast('/')}"
    val requestBody = bytes.toRequestBody(mimeType.toMediaTypeOrNull())
    return MultipartBody.Part.createFormData("files", fileName, requestBody)
}
