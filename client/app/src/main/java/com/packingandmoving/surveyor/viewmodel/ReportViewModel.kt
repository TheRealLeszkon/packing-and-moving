package com.packingandmoving.surveyor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyor.model.InventoryItem
import com.packingandmoving.surveyor.model.ProcessingResponse
import com.packingandmoving.surveyor.repository.SurveyRepository
import com.packingandmoving.surveyor.utils.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared by the Report and Item Details screens for one survey, so edits made in Item Details
 * are reflected back in the Report list without a second network round trip.
 */
class ReportViewModel(
    private val sessionId: String,
    private val repository: SurveyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReportUiState>(ReportUiState.Loading)
    val uiState: StateFlow<ReportUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = ReportUiState.Loading
            when (val result = repository.getProcessing(sessionId)) {
                is ApiResult.Success -> _uiState.value = ReportUiState.Success(result.data)
                is ApiResult.Error -> _uiState.value = ReportUiState.Error(result.message)
            }
        }
    }

    fun findItem(itemId: String): InventoryItem? =
        (_uiState.value as? ReportUiState.Success)?.response?.items?.find { it.id == itemId }

    /**
     * Applies an edit made on the Item Details screen locally.
     * TODO: once the backend exposes an update endpoint (e.g. PATCH /processing/{sessionId}/items/{itemId}),
     * call it here before applying the change so edits persist across sessions.
     */
    fun updateItemLocally(updated: InventoryItem) {
        val current = _uiState.value as? ReportUiState.Success ?: return
        val updatedItems = current.response.items.map { if (it.id == updated.id) updated else it }
        _uiState.value = ReportUiState.Success(current.response.copy(items = updatedItems))
    }
}

sealed class ReportUiState {
    data object Loading : ReportUiState()
    data class Success(val response: ProcessingResponse) : ReportUiState()
    data class Error(val message: String) : ReportUiState()
}
