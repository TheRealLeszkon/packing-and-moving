package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import com.packingandmoving.surveyagent.repository.MediaRepository
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

    fun save(itemId: String) {
        _uiState.update { it.copy(isSaving = true, isSaved = false, errorMessage = null) }
        viewModelScope.launch {
            when (val result = itemRepository.updateItem(itemId, _uiState.value.form.toUpdate())) {
                is ApiResult.Success ->
                    _uiState.update {
                        it.copy(isSaving = false, isSaved = true, item = result.data, form = result.data.toForm())
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
