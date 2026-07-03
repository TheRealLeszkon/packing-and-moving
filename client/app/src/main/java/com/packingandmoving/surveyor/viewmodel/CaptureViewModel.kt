package com.packingandmoving.surveyor.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyor.repository.SurveyRepository
import com.packingandmoving.surveyor.utils.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Shared across the Create Survey, Capture, and Review screens for one survey-in-progress. */
class CaptureViewModel(private val repository: SurveyRepository) : ViewModel() {

    private val _photos = MutableStateFlow<List<Uri>>(emptyList())
    val photos: StateFlow<List<Uri>> = _photos.asStateFlow()

    private val _uploadState = MutableStateFlow<UploadState>(UploadState.Idle)
    val uploadState: StateFlow<UploadState> = _uploadState.asStateFlow()

    fun addPhoto(uri: Uri) {
        _photos.update { it + uri }
    }

    fun addPhotos(uris: List<Uri>) {
        _photos.update { it + uris }
    }

    fun removePhoto(uri: Uri) {
        _photos.update { current -> current.filterNot { it == uri } }
    }

    fun uploadPhotos() {
        val currentPhotos = _photos.value
        if (currentPhotos.isEmpty()) return

        viewModelScope.launch {
            _uploadState.value = UploadState.Uploading
            when (val result = repository.uploadImages(currentPhotos)) {
                is ApiResult.Success -> _uploadState.value = UploadState.Success(result.data.sessionId)
                is ApiResult.Error -> _uploadState.value = UploadState.Error(result.message)
            }
        }
    }

    fun dismissUploadError() {
        _uploadState.value = UploadState.Idle
    }
}

sealed class UploadState {
    data object Idle : UploadState()
    data object Uploading : UploadState()
    data class Success(val sessionId: String) : UploadState()
    data class Error(val message: String) : UploadState()
}
