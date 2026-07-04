package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveySummary
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SummaryUiState(
    val isLoading: Boolean = false,
    val summary: SurveySummary? = null,
    val availableActions: List<String> = emptyList(),
    val isSubmitting: Boolean = false,
    val isSubmitted: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Final summary / sign-off (SCREEN_6 → GET /surveys/{id}/summary). "Complete Survey"
 * submits the survey for customer approval (POST /surveys/{id}/submit).
 */
class SummaryViewModel(private val surveyRepository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SummaryUiState())
    val uiState: StateFlow<SummaryUiState> = _uiState.asStateFlow()

    private var loadedSurveyId: String? = null

    fun load(surveyId: String, forceReload: Boolean = false) {
        if (!forceReload && loadedSurveyId == surveyId) return
        loadedSurveyId = surveyId
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val summary = surveyRepository.summary(surveyId)
            val status = surveyRepository.surveyStatus(surveyId)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    summary = (summary as? ApiResult.Success)?.data ?: state.summary,
                    availableActions = (status as? ApiResult.Success)?.data?.availableActions
                        ?: state.availableActions,
                    errorMessage = listOf(summary, status)
                        .filterIsInstance<ApiResult.Failure>()
                        .firstOrNull()?.error?.message,
                )
            }
        }
    }

    fun submit(surveyId: String) {
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = surveyRepository.submit(surveyId)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isSubmitting = false, isSubmitted = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.message) }
            }
        }
    }
}
