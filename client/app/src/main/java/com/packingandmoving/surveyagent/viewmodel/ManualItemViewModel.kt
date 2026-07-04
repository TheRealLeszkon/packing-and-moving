package com.packingandmoving.surveyagent.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.camera.isVideo
import com.packingandmoving.surveyagent.camera.uploadMediaAndCollectIds
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import com.packingandmoving.surveyagent.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Manual item creation (POST /surveys/{id}/items). Uses the same [ItemForm] as the editor so
 * a manual item looks identical to an AI-detected one. Gallery photos/videos can be attached:
 * they upload first (allowed during review), then the returned media ids go on the create.
 */
data class ManualItemUiState(
    val form: ItemForm = ItemForm(),
    val stagedMedia: List<StagedMedia> = emptyList(),
    val isSaving: Boolean = false,
    val createdItemId: String? = null,
    val errorMessage: String? = null,
)

class ManualItemViewModel(
    private val itemRepository: ItemRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualItemUiState())
    val uiState: StateFlow<ManualItemUiState> = _uiState.asStateFlow()

    fun onFormChange(form: ItemForm) = _uiState.update { it.copy(form = form) }

    fun addMedia(uris: List<Uri>, resolver: ContentResolver) = _uiState.update { state ->
        state.copy(
            stagedMedia = state.stagedMedia +
                uris.map { StagedMedia(UUID.randomUUID().toString(), it, it.isVideo(resolver)) },
        )
    }

    fun removeMedia(id: String) = _uiState.update { state ->
        state.copy(stagedMedia = state.stagedMedia.filterNot { it.id == id })
    }

    fun create(surveyId: String, resolver: ContentResolver) {
        val form = _uiState.value.form
        if (!form.isValidForCreate) {
            _uiState.update { it.copy(errorMessage = "Name is required.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val staged = _uiState.value.stagedMedia
            val mediaIds: List<String>?
            if (staged.isNotEmpty()) {
                val upload = uploadMediaAndCollectIds(
                    mediaRepository, surveyId,
                    photos = staged.filterNot { it.isVideo }.map { it.uri },
                    videos = staged.filter { it.isVideo }.map { it.uri },
                    resolver = resolver,
                    roomLocation = form.roomLocation.trim().ifBlank { null },
                )
                when (upload) {
                    is ApiResult.Success -> mediaIds = upload.data.ifEmpty { null }
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(isSaving = false, errorMessage = upload.error.message) }
                        return@launch
                    }
                }
            } else {
                mediaIds = null
            }

            when (val result = itemRepository.addItem(surveyId, form.toCreate(mediaIds))) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isSaving = false, createdItemId = result.data.id) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.error.message) }
            }
        }
    }
}
