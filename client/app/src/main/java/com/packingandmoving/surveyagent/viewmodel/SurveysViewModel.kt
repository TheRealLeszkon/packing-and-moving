package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SurveysUiState(
    val isLoading: Boolean = false,
    val available: List<Survey> = emptyList(),
    val assigned: List<Survey> = emptyList(),
    val errorMessage: String? = null,
)

/**
 * Surveyor request board: open requests to accept and already-assigned surveys
 * (GET /survey-requests/available|assigned; POST .../accept).
 */
class SurveysViewModel(private val surveyRepository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveysUiState())
    val uiState: StateFlow<SurveysUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val available = surveyRepository.availableRequests()
            val assigned = surveyRepository.assignedRequests()
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    available = (available as? ApiResult.Success)?.data?.items ?: state.available,
                    assigned = (assigned as? ApiResult.Success)?.data?.items ?: state.assigned,
                    errorMessage = listOf(available, assigned)
                        .filterIsInstance<ApiResult.Failure>()
                        .firstOrNull()?.error?.message,
                )
            }
        }
    }

    fun accept(surveyId: String) {
        viewModelScope.launch {
            when (val result = surveyRepository.acceptRequest(surveyId)) {
                is ApiResult.Success -> refresh()
                is ApiResult.Failure ->
                    _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }
}
