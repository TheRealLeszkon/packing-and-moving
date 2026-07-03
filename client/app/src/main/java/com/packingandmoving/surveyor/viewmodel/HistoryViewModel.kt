package com.packingandmoving.surveyor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyor.model.ProcessingSummary
import com.packingandmoving.surveyor.repository.SurveyRepository
import com.packingandmoving.surveyor.utils.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HistoryViewModel(private val repository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow<HistoryUiState>(HistoryUiState.Loading)
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = HistoryUiState.Loading
            when (val result = repository.listProcessing()) {
                is ApiResult.Success -> _uiState.value = HistoryUiState.Success(result.data)
                is ApiResult.Error -> _uiState.value = HistoryUiState.Error(result.message)
            }
        }
    }
}

sealed class HistoryUiState {
    data object Loading : HistoryUiState()
    data class Success(val surveys: List<ProcessingSummary>) : HistoryUiState()
    data class Error(val message: String) : HistoryUiState()
}
