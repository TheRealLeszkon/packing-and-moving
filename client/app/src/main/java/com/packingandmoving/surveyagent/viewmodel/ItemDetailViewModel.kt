package com.packingandmoving.surveyagent.viewmodel

import android.content.ContentResolver
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.camera.isVideo
import com.packingandmoving.surveyagent.camera.uploadMediaAndCollectIds
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import com.packingandmoving.surveyagent.repository.MediaRepository
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable item detail (SCREEN_8). The whole item is editable via a shared [ItemForm]; save
 * PATCHes every field (nulls omitted) and delete removes the item. Editing is backend-gated
 * to the assigned surveyor while ready_for_review/revision_required.
 */
data class ItemDetailUiState(
    val isLoading: Boolean = false,
    val item: SurveyItem? = null,
    val form: ItemForm = ItemForm(),
    val imageUrls: List<String> = emptyList(),
    val stagedMedia: List<StagedMedia> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val isDeleting: Boolean = false,
    val isDeleted: Boolean = false,
    val errorMessage: String? = null,
)

class ItemDetailViewModel(
    private val itemRepository: ItemRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private var loadedItemId: String? = null

    /** No single-item GET exists, so locate the item in the survey's list (§7). */
    fun load(surveyId: String, itemId: String, forceReload: Boolean = false) {
        if (!forceReload && loadedItemId == itemId) return
        loadedItemId = itemId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = itemRepository.listItems(surveyId)) {
                is ApiResult.Success -> {
                    val item = result.data.items.firstOrNull { it.id == itemId }
                    if (item == null) {
                        _uiState.update { it.copy(isLoading = false, errorMessage = "Item not found.") }
                    } else {
                        _uiState.update { it.copy(isLoading = false, item = item, form = item.toForm()) }
                        loadImages(item.mediaIds)
                    }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    fun onFormChange(form: ItemForm) = _uiState.update { it.copy(form = form, isSaved = false) }

    /** Stage picked gallery items for upload on the next save. */
    fun addMedia(uris: List<Uri>, resolver: ContentResolver) = _uiState.update { state ->
        state.copy(
            stagedMedia = state.stagedMedia +
                uris.map { StagedMedia(UUID.randomUUID().toString(), it, it.isVideo(resolver)) },
            isSaved = false,
        )
    }

    fun removeMedia(id: String) = _uiState.update { state ->
        state.copy(stagedMedia = state.stagedMedia.filterNot { it.id == id })
    }

    fun save(surveyId: String, itemId: String, resolver: ContentResolver) {
        val form = _uiState.value.form
        val confidence = form.confidenceScore.trim().toDoubleOrNull()
        if (form.confidenceScore.isNotBlank() && (confidence == null || confidence !in 0.0..1.0)) {
            _uiState.update { it.copy(errorMessage = "AI confidence must be a number between 0 and 1.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, isSaved = false, errorMessage = null) }
        viewModelScope.launch {
            // Upload any staged media first, then attach its ids alongside the existing ones.
            val staged = _uiState.value.stagedMedia
            val existingIds = _uiState.value.item?.mediaIds ?: emptyList()
            val newIds: List<String>
            if (staged.isNotEmpty()) {
                val upload = uploadMediaAndCollectIds(
                    mediaRepository, surveyId,
                    photos = staged.filterNot { it.isVideo }.map { it.uri },
                    videos = staged.filter { it.isVideo }.map { it.uri },
                    resolver = resolver,
                    roomLocation = _uiState.value.form.roomLocation.trim().ifBlank { null },
                )
                when (upload) {
                    is ApiResult.Success -> newIds = upload.data
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(isSaving = false, errorMessage = upload.error.message) }
                        return@launch
                    }
                }
            } else {
                newIds = emptyList()
            }

            val update = form.toUpdate().copy(
                mediaIds = if (newIds.isEmpty()) null else existingIds + newIds,
            )
            when (val result = itemRepository.updateItem(itemId, update)) {
                is ApiResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false, isSaved = true, item = result.data,
                            form = result.data.toForm(), stagedMedia = emptyList(),
                        )
                    }
                    loadImages(result.data.mediaIds)
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.error.message) }
            }
        }
    }

    fun delete(itemId: String) {
        _uiState.update { it.copy(isDeleting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = itemRepository.deleteItem(itemId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isDeleting = false, isDeleted = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isDeleting = false, errorMessage = result.error.message) }
            }
        }
    }

    fun consumeSaved() = _uiState.update { it.copy(isSaved = false) }

    private fun loadImages(mediaIds: List<String>) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            val urls = mediaIds.mapNotNull {
                (mediaRepository.getMedia(it) as? ApiResult.Success)?.data?.url
            }
            _uiState.update { it.copy(imageUrls = urls) }
        }
    }
}
