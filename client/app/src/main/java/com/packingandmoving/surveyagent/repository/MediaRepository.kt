package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.SurveyAgentApi
import com.packingandmoving.surveyagent.model.Media
import com.packingandmoving.surveyagent.model.MediaUploadResult
import com.packingandmoving.surveyagent.model.MessageResponse
import com.packingandmoving.surveyagent.model.Page
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Media listing, retrieval, and upload (frontend-integration.md §10, §11). The actual
 * file→[MultipartBody.Part] construction (content resolver / CameraX output) is handled by
 * the camera phase; this repository forwards prepared parts and surfaces the typed result.
 */
class MediaRepository(private val api: SurveyAgentApi) {

    suspend fun listMedia(
        surveyId: String,
        limit: Int? = null,
        offset: Int? = null,
    ): ApiResult<Page<Media>> = safeApiCall { api.listMedia(surveyId, limit, offset) }

    suspend fun getMedia(mediaId: String): ApiResult<Media> =
        safeApiCall { api.getMedia(mediaId) }

    suspend fun deleteMedia(mediaId: String): ApiResult<MessageResponse> =
        safeApiCall { api.deleteMedia(mediaId) }

    suspend fun uploadImages(
        surveyId: String,
        files: List<MultipartBody.Part>,
        roomLocation: String? = null,
    ): ApiResult<MediaUploadResult> =
        safeApiCall { api.uploadImages(surveyId, files, roomLocation?.toPlainTextPart()) }

    suspend fun uploadVideos(
        surveyId: String,
        files: List<MultipartBody.Part>,
        roomLocation: String? = null,
    ): ApiResult<MediaUploadResult> =
        safeApiCall { api.uploadVideos(surveyId, files, roomLocation?.toPlainTextPart()) }

    private fun String.toPlainTextPart(): RequestBody =
        toRequestBody("text/plain".toMediaTypeOrNull())
}
