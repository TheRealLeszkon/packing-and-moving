package com.packingandmoving.surveyagent.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.camera.isVideo
import com.packingandmoving.surveyagent.camera.toMediaPart
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.MediaRepository
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

/** Media staged for upload — a photo or a video, from the camera or the gallery. */
data class StagedMedia(val id: String, val uri: Uri, val isVideo: Boolean)

/** Where the capture flow is: still adding media, uploading+processing, or done. */
enum class CapturePhase { Editing, Working, Ready }

data class CaptureUiState(
    val media: List<StagedMedia> = emptyList(),
    val roomLocation: String = "",
    val phase: CapturePhase = CapturePhase.Editing,
    val processingStatus: SurveyStatus? = null,
    val errorMessage: String? = null,
) {
    val photoCount: Int get() = media.count { !it.isVideo }
    val videoCount: Int get() = media.count { it.isVideo }
}

/**
 * Owns the staged media for one survey's capture session (graph-scoped, so the camera and
 * the review screen share it and media survives rotation). Camera/gallery only *stage* URIs;
 * nothing hits the network until [uploadAndComplete], which uploads photos and videos to
 * their respective endpoints, completes the survey (→ processing), then polls status in
 * place until it leaves `processing`.
 */
class CaptureViewModel(
    private val mediaRepository: MediaRepository,
    private val surveyRepository: SurveyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onRoomLocationChange(value: String) = _uiState.update { it.copy(roomLocation = value) }

    /** Stage picked gallery items; media type is inferred from each URI's content type. */
    fun addMedia(uris: List<Uri>, resolver: ContentResolver) = _uiState.update { state ->
        state.copy(media = state.media + uris.map { StagedMedia(UUID.randomUUID().toString(), it, it.isVideo(resolver)) })
    }

    fun addPhoto(uri: Uri) = stage(uri, isVideo = false)
    fun addVideo(uri: Uri) = stage(uri, isVideo = true)

    private fun stage(uri: Uri, isVideo: Boolean) = _uiState.update { state ->
        state.copy(media = state.media + StagedMedia(UUID.randomUUID().toString(), uri, isVideo))
    }

    fun removeMedia(id: String) = _uiState.update { state ->
        state.copy(media = state.media.filterNot { it.id == id })
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    fun uploadAndComplete(surveyId: String, resolver: ContentResolver) {
        val media = _uiState.value.media
        if (media.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Add at least one photo or video first.") }
            return
        }
        if (_uiState.value.phase == CapturePhase.Working) return

        _uiState.update { it.copy(phase = CapturePhase.Working, errorMessage = null) }
        viewModelScope.launch {
            val (imageParts, videoParts) = withContext(Dispatchers.IO) {
                val images = media.filterNot { it.isVideo }
                    .mapNotNull { runCatching { it.uri.toMediaPart(resolver) }.getOrNull() }
                val videos = media.filter { it.isVideo }
                    .mapNotNull { runCatching { it.uri.toMediaPart(resolver) }.getOrNull() }
                images to videos
            }
            if (imageParts.isEmpty() && videoParts.isEmpty()) {
                return@launch fail("Could not read the selected media.")
            }

            val room = _uiState.value.roomLocation.trim().ifBlank { null }
            if (imageParts.isNotEmpty()) {
                val result = mediaRepository.uploadImages(surveyId, imageParts, room)
                if (result is ApiResult.Failure) return@launch fail(result.error.message)
            }
            if (videoParts.isNotEmpty()) {
                val result = mediaRepository.uploadVideos(surveyId, videoParts, room)
                if (result is ApiResult.Failure) return@launch fail(result.error.message)
            }

            when (val complete = surveyRepository.complete(surveyId)) {
                is ApiResult.Failure -> fail(complete.error.message)
                is ApiResult.Success -> pollUntilReady(surveyId)
            }
        }
    }

    private suspend fun pollUntilReady(surveyId: String) {
        while (true) {
            when (val result = surveyRepository.surveyStatus(surveyId)) {
                is ApiResult.Success -> {
                    val status = result.data.status
                    _uiState.update { it.copy(processingStatus = status) }
                    if (status != SurveyStatus.PROCESSING) {
                        _uiState.update { it.copy(phase = CapturePhase.Ready) }
                        return
                    }
                }
                is ApiResult.Failure -> return fail(result.error.message)
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    private fun fail(message: String) = _uiState.update {
        it.copy(phase = CapturePhase.Editing, errorMessage = message)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
