package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyItem
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.model.SurveySummary
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
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
    val status: SurveyStatus? = null,
    val availableActions: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val isReanalyzing: Boolean = false,
    val processingStage: String? = null,
    val errorMessage: String? = null,
) {
    /** Re-run AI is offered while the survey is in a reviewable state. */
    val canReanalyze: Boolean
        get() = status == SurveyStatus.READY_FOR_REVIEW || status == SurveyStatus.REVISION_REQUIRED

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
                    status = (status as? ApiResult.Success)?.data?.status ?: state.status,
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

    /** Re-run AI over all photos or only newly-added ones, then poll until it finishes and reload. */
    fun reanalyze(surveyId: String, mode: String) {
        if (_uiState.value.isReanalyzing) return
        _uiState.update { it.copy(isReanalyzing = true, processingStage = null, errorMessage = null) }
        viewModelScope.launch {
            when (val result = surveyRepository.reanalyze(surveyId, mode)) {
                is ApiResult.Failure -> _uiState.update {
                    it.copy(isReanalyzing = false, errorMessage = result.error.message)
                }
                is ApiResult.Success -> pollUntilReanalyzed(surveyId)
            }
        }
    }

    private suspend fun pollUntilReanalyzed(surveyId: String) {
        while (true) {
            when (val result = surveyRepository.surveyStatus(surveyId)) {
                is ApiResult.Success -> {
                    val status = result.data.status
                    _uiState.update { it.copy(processingStage = result.data.processingStage) }
                    if (status != SurveyStatus.PROCESSING) {
                        _uiState.update { it.copy(isReanalyzing = false, processingStage = null) }
                        load(surveyId)
                        return
                    }
                }
                is ApiResult.Failure -> {
                    _uiState.update { it.copy(isReanalyzing = false, errorMessage = result.error.message) }
                    return
                }
            }
            delay(POLL_INTERVAL_MS)
        }
    }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
