package com.packingandmoving.surveyor.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyor.model.ProcessingResponse
import com.packingandmoving.surveyor.model.SurveyStatus
import com.packingandmoving.surveyor.repository.SurveyRepository
import com.packingandmoving.surveyor.utils.ApiResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val POLL_INTERVAL_MS = 2500L

class ProcessingViewModel(
    private val sessionId: String,
    private val repository: SurveyRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ProcessingUiState>(ProcessingUiState.Loading)
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    init {
        startPolling()
    }

    fun retry() {
        startPolling()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        _uiState.value = ProcessingUiState.Loading
        pollingJob = viewModelScope.launch {
            while (true) {
                when (val result = repository.getProcessing(sessionId)) {
                    is ApiResult.Success -> {
                        val response = result.data
                        when (response.surveyStatus) {
                            SurveyStatus.COMPLETED -> {
                                _uiState.value = ProcessingUiState.Completed(response)
                                return@launch
                            }
                            SurveyStatus.FAILED -> {
                                _uiState.value = ProcessingUiState.Failed(
                                    response.errorMessage ?: "AI analysis failed. Please try again."
                                )
                                return@launch
                            }
                            SurveyStatus.PENDING, SurveyStatus.PROCESSING, SurveyStatus.UNKNOWN -> {
                                _uiState.value = ProcessingUiState.InProgress
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        _uiState.value = ProcessingUiState.Failed(result.message)
                        return@launch
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }
}

sealed class ProcessingUiState {
    data object Loading : ProcessingUiState()
    data object InProgress : ProcessingUiState()
    data class Completed(val response: ProcessingResponse) : ProcessingUiState()
    data class Failed(val message: String) : ProcessingUiState()
}
