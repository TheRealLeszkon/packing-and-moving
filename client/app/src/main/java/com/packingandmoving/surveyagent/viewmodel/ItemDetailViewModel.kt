package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveyItemUpdate
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import com.packingandmoving.surveyagent.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable item form (SCREEN_8). Numeric fields are edited as text to preserve Decimal
 * precision; blanks are sent as null. The form is seeded from the loaded item and only
 * changed fields are PATCHed (SurveyItemUpdate omits nulls).
 */
data class ItemDetailUiState(
    val isLoading: Boolean = false,
    val item: SurveyItem? = null,
    val heightCm: String = "",
    val widthCm: String = "",
    val depthCm: String = "",
    val weightKg: String = "",
    val estimatedValue: String = "",
    val fragile: Boolean = false,
    val needsDisassembly: Boolean = false,
    val remarks: String = "",
    val imageUrls: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val errorMessage: String? = null,
)

class ItemDetailViewModel(
    private val itemRepository: ItemRepository,
    private val mediaRepository: MediaRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ItemDetailUiState())
    val uiState: StateFlow<ItemDetailUiState> = _uiState.asStateFlow()

    private var loadedItemId: String? = null

    /**
     * There is no single-item GET endpoint, so the item is located in the survey's item
     * list and the form seeded from it (frontend-integration.md §7).
     */
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
                        _uiState.update { seedForm(it, item) }
                        loadImages(item.mediaIds)
                    }
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Fetch each evidencing photo's short-lived signed URL for display (§11). */
    private fun loadImages(mediaIds: List<String>) {
        if (mediaIds.isEmpty()) return
        viewModelScope.launch {
            val urls = mediaIds.mapNotNull { itemMediaUrl(it) }
            _uiState.update { it.copy(imageUrls = urls) }
        }
    }

    private suspend fun itemMediaUrl(mediaId: String): String? =
        (mediaRepository.getMedia(mediaId) as? ApiResult.Success)?.data?.url

    private fun seedForm(state: ItemDetailUiState, item: SurveyItem) = state.copy(
        isLoading = false,
        item = item,
        heightCm = item.heightCm.orEmpty(),
        widthCm = item.widthCm.orEmpty(),
        depthCm = item.depthCm.orEmpty(),
        weightKg = item.weightKg.orEmpty(),
        estimatedValue = item.estimatedValue.orEmpty(),
        fragile = item.fragile,
        needsDisassembly = item.needsDisassembly,
        remarks = item.remarks.orEmpty(),
    )

    fun onHeightChange(value: String) = _uiState.update { it.copy(heightCm = value) }
    fun onWidthChange(value: String) = _uiState.update { it.copy(widthCm = value) }
    fun onDepthChange(value: String) = _uiState.update { it.copy(depthCm = value) }
    fun onWeightChange(value: String) = _uiState.update { it.copy(weightKg = value) }
    fun onValueChange(value: String) = _uiState.update { it.copy(estimatedValue = value) }
    fun onFragileChange(value: Boolean) = _uiState.update { it.copy(fragile = value) }
    fun onNeedsDisassemblyChange(value: Boolean) = _uiState.update { it.copy(needsDisassembly = value) }
    fun onRemarksChange(value: String) = _uiState.update { it.copy(remarks = value) }

    fun save(itemId: String) {
        val state = _uiState.value
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val update = SurveyItemUpdate(
                heightCm = state.heightCm.blankToNull(),
                widthCm = state.widthCm.blankToNull(),
                depthCm = state.depthCm.blankToNull(),
                weightKg = state.weightKg.blankToNull(),
                estimatedValue = state.estimatedValue.blankToNull(),
                fragile = state.fragile,
                needsDisassembly = state.needsDisassembly,
                remarks = state.remarks.blankToNull(),
            )
            when (val result = itemRepository.updateItem(itemId, update)) {
                is ApiResult.Success ->
                    _uiState.update { seedForm(it, result.data).copy(isSaving = false, isSaved = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.error.message) }
            }
        }
    }

    private fun String.blankToNull(): String? = trim().ifBlank { null }
}
