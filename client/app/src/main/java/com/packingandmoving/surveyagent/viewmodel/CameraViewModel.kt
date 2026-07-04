package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.MultipartBody

data class CameraUiState(
    val roomLocation: String = "",
    val isUploading: Boolean = false,
    val uploadedCount: Int = 0,
    val errorMessage: String? = null,
)

/**
 * Camera capture (SCREEN_5). Owns the room-label + upload state; capturing frames and
 * turning them into [MultipartBody.Part]s is CameraX work handled in the camera phase,
 * which then calls [uploadImages] / [uploadVideos].
 */
class CameraViewModel(private val mediaRepository: MediaRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState: StateFlow<CameraUiState> = _uiState.asStateFlow()

    fun onRoomLocationChange(value: String) = _uiState.update { it.copy(roomLocation = value) }

    fun uploadImages(surveyId: String, files: List<MultipartBody.Part>) =
        upload { mediaRepository.uploadImages(surveyId, files, roomLocationOrNull()) }

    fun uploadVideos(surveyId: String, files: List<MultipartBody.Part>) =
        upload { mediaRepository.uploadVideos(surveyId, files, roomLocationOrNull()) }

    private fun roomLocationOrNull() = _uiState.value.roomLocation.trim().ifBlank { null }

    private fun upload(action: suspend () -> ApiResult<com.packingandmoving.surveyagent.model.MediaUploadResult>) {
        _uiState.update { it.copy(isUploading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is ApiResult.Success ->
                    _uiState.update {
                        it.copy(isUploading = false, uploadedCount = it.uploadedCount + result.data.count)
                    }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isUploading = false, errorMessage = result.error.message) }
            }
        }
    }
}
