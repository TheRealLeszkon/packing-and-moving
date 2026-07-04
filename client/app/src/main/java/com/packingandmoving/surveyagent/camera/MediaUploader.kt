package com.packingandmoving.surveyagent.camera

import android.content.ContentResolver
import android.net.Uri
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Uploads staged photos/videos for a survey and returns the created media ids, so a caller
 * can attach them to a new or existing item. Parts are built off the main thread; the two
 * upload endpoints are hit only when there's something to send. Fails fast on either error.
 * Keeps [ContentResolver] out of the repository layer.
 */
suspend fun uploadMediaAndCollectIds(
    mediaRepository: MediaRepository,
    surveyId: String,
    photos: List<Uri>,
    videos: List<Uri>,
    resolver: ContentResolver,
    roomLocation: String? = null,
): ApiResult<List<String>> {
    val (imageParts, videoParts) = withContext(Dispatchers.IO) {
        val images = photos.mapNotNull { runCatching { it.toMediaPart(resolver) }.getOrNull() }
        val vids = videos.mapNotNull { runCatching { it.toMediaPart(resolver) }.getOrNull() }
        images to vids
    }

    val ids = mutableListOf<String>()
    if (imageParts.isNotEmpty()) {
        when (val result = mediaRepository.uploadImages(surveyId, imageParts, roomLocation)) {
            is ApiResult.Success -> ids += result.data.items.map { it.id }
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
        }
    }
    if (videoParts.isNotEmpty()) {
        when (val result = mediaRepository.uploadVideos(surveyId, videoParts, roomLocation)) {
            is ApiResult.Success -> ids += result.data.items.map { it.id }
            is ApiResult.Failure -> return ApiResult.Failure(result.error)
        }
    }
    return ApiResult.Success(ids)
}
