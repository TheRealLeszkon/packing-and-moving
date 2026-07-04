package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProcessingUiState(
    val status: SurveyStatus? = null,
    val processingStage: String? = null,
    val isReadyForReview: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * "Processing…" screen (frontend-integration.md §6). There is no push channel, so this
 * polls GET /surveys/{id}/status until the survey leaves `processing`. AI failures still
 * advance to `ready_for_review`, so the poll always terminates.
 */
class ProcessingViewModel(private val surveyRepository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ProcessingUiState())
    val uiState: StateFlow<ProcessingUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    fun start(surveyId: String) {
        if (pollingJob?.isActive == true) return
        pollingJob = viewModelScope.launch {
            while (isActive) {
                when (val result = surveyRepository.surveyStatus(surveyId)) {
                    is ApiResult.Success -> {
                        val status = result.data.status
                        _uiState.update { it.copy(status = status, processingStage = result.data.processingStage, errorMessage = null) }
                        if (status != SurveyStatus.PROCESSING) {
                            _uiState.update {
                                it.copy(isReadyForReview = status == SurveyStatus.READY_FOR_REVIEW)
                            }
                            return@launch
                        }
                    }
                    is ApiResult.Failure -> {
                        _uiState.update { it.copy(errorMessage = result.error.message) }
                        return@launch
                    }
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun retry(surveyId: String) {
        pollingJob?.cancel()
        _uiState.update { it.copy(errorMessage = null) }
        start(surveyId)
    }

    private companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
