package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveySummary
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SurveyResultsUiState(
    val isLoading: Boolean = false,
    val summary: SurveySummary? = null,
    val items: List<SurveyItem> = emptyList(),
    val query: String = "",
    val availableActions: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
) {
    val visibleItems: List<SurveyItem>
        get() = if (query.isBlank()) items else items.filter { item ->
            item.itemName.contains(query, ignoreCase = true) ||
                item.roomLocation?.contains(query, ignoreCase = true) == true ||
                item.category?.contains(query, ignoreCase = true) == true
        }
}

/**
 * Backs the merged Survey Results screen (summary totals + per-item breakdowns + the
 * searchable inventory). Loads the summary, the items, and the status (for the submit
 * action) together, and supports delete + submit. Reloading after an edit keeps totals and
 * the list in sync.
 */
class SurveyResultsViewModel(
    private val surveyRepository: SurveyRepository,
    private val itemRepository: ItemRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyResultsUiState())
    val uiState: StateFlow<SurveyResultsUiState> = _uiState.asStateFlow()

    fun load(surveyId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val summaryDeferred = async { surveyRepository.summary(surveyId) }
            val itemsDeferred = async { itemRepository.listItems(surveyId) }
            val statusDeferred = async { surveyRepository.surveyStatus(surveyId) }
            val summary = summaryDeferred.await()
            val items = itemsDeferred.await()
            val status = statusDeferred.await()

            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    summary = (summary as? ApiResult.Success)?.data ?: state.summary,
                    items = (items as? ApiResult.Success)?.data?.items ?: state.items,
                    availableActions = (status as? ApiResult.Success)?.data?.availableActions
                        ?: state.availableActions,
                    errorMessage = listOf(summary, items, status)
                        .filterIsInstance<ApiResult.Failure>()
                        .firstOrNull()?.error?.message,
                )
            }
        }
    }

    fun onQueryChange(value: String) = _uiState.update { it.copy(query = value) }

    fun deleteItem(surveyId: String, itemId: String) {
        viewModelScope.launch {
            when (val result = itemRepository.deleteItem(itemId)) {
                is ApiResult.Success -> {
                    _uiState.update { s -> s.copy(items = s.items.filterNot { it.id == itemId }) }
                    load(surveyId) // refresh totals
                }
                is ApiResult.Failure -> _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }

    fun submit(surveyId: String) {
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = surveyRepository.submit(surveyId)) {
                is ApiResult.Success -> _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.message) }
            }
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
