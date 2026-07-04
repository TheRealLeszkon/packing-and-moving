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

data class SurveyDetailUiState(
    val isLoading: Boolean = false,
    val survey: Survey? = null,
    val availableActions: List<String> = emptyList(),
    val isActionInProgress: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Survey detail hub (GET /surveys/{id} + /status). The UI renders buttons from
 * [SurveyDetailUiState.availableActions] — transitions are driven by the server, not
 * hardcoded (frontend-integration.md §5).
 */
class SurveyDetailViewModel(private val surveyRepository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(SurveyDetailUiState())
    val uiState: StateFlow<SurveyDetailUiState> = _uiState.asStateFlow()

    private var loadedSurveyId: String? = null

    fun load(surveyId: String, forceReload: Boolean = false) {
        if (!forceReload && loadedSurveyId == surveyId) return
        loadedSurveyId = surveyId
        refresh(surveyId)
    }

    fun refresh(surveyId: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val survey = surveyRepository.getSurvey(surveyId)
            val status = surveyRepository.surveyStatus(surveyId)
            _uiState.update { state ->
                state.copy(
                    isLoading = false,
                    survey = (survey as? ApiResult.Success)?.data ?: state.survey,
                    availableActions = (status as? ApiResult.Success)?.data?.availableActions
                        ?: state.availableActions,
                    errorMessage = listOf(survey, status)
                        .filterIsInstance<ApiResult.Failure>()
                        .firstOrNull()?.error?.message,
                )
            }
        }
    }

    fun start(surveyId: String) = runTransition(surveyId) { surveyRepository.start(surveyId) }
    fun complete(surveyId: String) = runTransition(surveyId) { surveyRepository.complete(surveyId) }
    fun submit(surveyId: String) = runTransition(surveyId) { surveyRepository.submit(surveyId) }
    fun approve(surveyId: String) = runTransition(surveyId) { surveyRepository.approve(surveyId) }
    fun reject(surveyId: String, reason: String) =
        runTransition(surveyId) { surveyRepository.reject(surveyId, reason) }
    fun cancel(surveyId: String, reason: String? = null) =
        runTransition(surveyId) { surveyRepository.cancel(surveyId, reason) }

    private fun runTransition(surveyId: String, action: suspend () -> ApiResult<Survey>) {
        _uiState.update { it.copy(isActionInProgress = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = action()) {
                is ApiResult.Success -> {
                    _uiState.update { it.copy(isActionInProgress = false, survey = result.data) }
                    refresh(surveyId) // pull fresh available_actions for the new status
                }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isActionInProgress = false, errorMessage = result.error.message) }
            }
        }
    }
}
