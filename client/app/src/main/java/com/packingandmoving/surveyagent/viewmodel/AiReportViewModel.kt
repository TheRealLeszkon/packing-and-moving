package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiReportUiState(
    val isLoading: Boolean = false,
    val allItems: List<SurveyItem> = emptyList(),
    val query: String = "",
    val errorMessage: String? = null,
) {
    /** Items after applying the search filter — derived, so the composable holds no logic. */
    val visibleItems: List<SurveyItem>
        get() = if (query.isBlank()) {
            allItems
        } else {
            allItems.filter { item ->
                item.itemName.contains(query, ignoreCase = true) ||
                    item.roomLocation?.contains(query, ignoreCase = true) == true
            }
        }
}

/** AI Survey Report (SCREEN_2 → GET /surveys/{id}/items). */
class AiReportViewModel(private val itemRepository: ItemRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AiReportUiState())
    val uiState: StateFlow<AiReportUiState> = _uiState.asStateFlow()

    private var loadedSurveyId: String? = null

    fun load(surveyId: String, forceReload: Boolean = false) {
        if (!forceReload && loadedSurveyId == surveyId) return
        loadedSurveyId = surveyId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = itemRepository.listItems(surveyId)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isLoading = false, allItems = result.data.items) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    /** Delete an item and drop it from the list on success (§ DELETE /survey-items/{id}). */
    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            when (val result = itemRepository.deleteItem(itemId)) {
                is ApiResult.Success ->
                    _uiState.update { state ->
                        state.copy(allItems = state.allItems.filterNot { it.id == itemId })
                    }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
