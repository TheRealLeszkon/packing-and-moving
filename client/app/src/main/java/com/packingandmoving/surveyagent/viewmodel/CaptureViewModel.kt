package com.packingandmoving.surveyagent.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.camera.isVideo
import com.packingandmoving.surveyagent.camera.uploadMediaAndCollectIds
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.MediaRepository
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Media staged for upload — a photo or a video, from the camera or the gallery. */
data class StagedMedia(val id: String, val uri: Uri, val isVideo: Boolean)

/** Where the capture flow is: still adding media, uploading+processing, or done. */
enum class CapturePhase { Editing, Working, Ready }

data class CaptureUiState(
    val media: List<StagedMedia> = emptyList(),
    val roomLocation: String = "",
    val phase: CapturePhase = CapturePhase.Editing,
    val uploadedCount: Int = 0,
    val processingStatus: SurveyStatus? = null,
    val processingStage: String? = null,
    val errorMessage: String? = null,
) {
    val photoCount: Int get() = media.count { !it.isVideo }
    val videoCount: Int get() = media.count { it.isVideo }
}

/**
 * Owns the staged media for one survey's capture session (graph-scoped, so the camera and
 * the review screen share it and media survives rotation). Camera/gallery only *stage* URIs;
 * nothing hits the network until [uploadBatch], which uploads the staged photos/videos and
 * returns to editing so more batches can follow (the backend only accepts media while the
 * survey is `in_progress`). A separate, explicit [completeAndProcess] then completes the
 * survey (→ processing) once and polls status in place until it leaves `processing`.
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

    /**
     * Uploads the currently staged media as one batch, then returns to editing with the
     * staged list cleared so the surveyor can add more. Does NOT complete the survey.
     */
    fun uploadBatch(surveyId: String, resolver: ContentResolver) {
        val media = _uiState.value.media
        if (media.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Add at least one photo or video first.") }
            return
        }
        if (_uiState.value.phase == CapturePhase.Working) return

        _uiState.update { it.copy(phase = CapturePhase.Working, errorMessage = null) }
        viewModelScope.launch {
            val room = _uiState.value.roomLocation.trim().ifBlank { null }
            val result = uploadMediaAndCollectIds(
                mediaRepository = mediaRepository,
                surveyId = surveyId,
                photos = media.filterNot { it.isVideo }.map { it.uri },
                videos = media.filter { it.isVideo }.map { it.uri },
                resolver = resolver,
                roomLocation = room,
            )
            when (result) {
                is ApiResult.Failure -> fail(result.error.message)
                is ApiResult.Success ->
                    if (result.data.isEmpty()) {
                        fail("Could not read the selected media.")
                    } else {
                        _uiState.update {
                            it.copy(
                                phase = CapturePhase.Editing,
                                media = emptyList(),
                                roomLocation = "",
                                uploadedCount = it.uploadedCount + result.data.size,
                            )
                        }
                    }
            }
        }
    }

    /**
     * Completes the survey (`in_progress → processing`) and polls until the AI worker is
     * done. Only valid once at least one batch has been uploaded and nothing is still staged.
     */
    fun completeAndProcess(surveyId: String) {
        if (_uiState.value.phase == CapturePhase.Working) return
        if (_uiState.value.media.isNotEmpty()) {
            _uiState.update { it.copy(errorMessage = "Upload or remove the staged media first.") }
            return
        }
        if (_uiState.value.uploadedCount == 0) {
            _uiState.update { it.copy(errorMessage = "Upload at least one photo or video first.") }
            return
        }

        _uiState.update { it.copy(phase = CapturePhase.Working, errorMessage = null) }
        viewModelScope.launch {
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
                    _uiState.update { it.copy(processingStatus = status, processingStage = result.data.processingStage) }
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
