package com.packingandmoving.surveyagent.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.camera.toImagePart
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

/** A photo staged for upload — from the camera (`file://`) or the gallery (`content://`). */
data class StagedPhoto(val id: String, val uri: Uri)

/** Where the capture flow is: still adding photos, uploading+processing, or done. */
enum class CapturePhase { Editing, Working, Ready }

data class CaptureUiState(
    val photos: List<StagedPhoto> = emptyList(),
    val roomLocation: String = "",
    val phase: CapturePhase = CapturePhase.Editing,
    val processingStatus: SurveyStatus? = null,
    val errorMessage: String? = null,
)

/**
 * Owns the staged photos for one survey's capture session (graph-scoped, so the camera and
 * the review screen share it and photos survive rotation). Camera/gallery only *stage* URIs;
 * nothing hits the network until [uploadAndComplete], which uploads the batch, completes the
 * survey (→ processing), then polls status in place until it leaves `processing` — so the
 * review screen can show progress inline without a separate spinner screen.
 */
class CaptureViewModel(
    private val mediaRepository: MediaRepository,
    private val surveyRepository: SurveyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    fun onRoomLocationChange(value: String) = _uiState.update { it.copy(roomLocation = value) }

    fun addPhotos(uris: List<Uri>) = _uiState.update { state ->
        state.copy(photos = state.photos + uris.map { StagedPhoto(UUID.randomUUID().toString(), it) })
    }

    fun addPhoto(uri: Uri) = addPhotos(listOf(uri))

    fun removePhoto(id: String) = _uiState.update { state ->
        state.copy(photos = state.photos.filterNot { it.id == id })
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    fun uploadAndComplete(surveyId: String, resolver: ContentResolver) {
        val photos = _uiState.value.photos
        if (photos.isEmpty()) {
            _uiState.update { it.copy(errorMessage = "Add at least one photo first.") }
            return
        }
        if (_uiState.value.phase == CapturePhase.Working) return

        _uiState.update { it.copy(phase = CapturePhase.Working, errorMessage = null) }
        viewModelScope.launch {
            val parts = withContext(Dispatchers.IO) {
                photos.mapNotNull { runCatching { it.uri.toImagePart(resolver) }.getOrNull() }
            }
            if (parts.isEmpty()) {
                fail("Could not read the selected photos.")
                return@launch
            }

            val room = _uiState.value.roomLocation.trim().ifBlank { null }
            when (val upload = mediaRepository.uploadImages(surveyId, parts, room)) {
                is ApiResult.Failure -> return@launch fail(upload.error.message)
                is ApiResult.Success -> Unit
            }
            when (val complete = surveyRepository.complete(surveyId)) {
                is ApiResult.Failure -> return@launch fail(complete.error.message)
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
