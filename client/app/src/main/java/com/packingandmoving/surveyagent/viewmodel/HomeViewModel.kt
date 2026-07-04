package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.Survey
import com.packingandmoving.surveyagent.model.SurveyStatus
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = false,
    val surveys: List<Survey> = emptyList(),
    val errorMessage: String? = null,
) {
    /** The survey the surveyor is actively working (surfaced as a "continue" card). */
    val draftSurvey: Survey?
        get() = surveys.firstOrNull {
            it.status == SurveyStatus.IN_PROGRESS || it.status == SurveyStatus.REVISION_REQUIRED
        }

    /** Everything else, shown in the recent list (the draft is not repeated here). */
    val recentSurveys: List<Survey>
        get() = surveys.filter { it != draftSurvey }
}

/** Home dashboard (SCREEN_9): the surveyor's owned/assigned surveys (GET /surveys/my). */
class HomeViewModel(private val surveyRepository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = surveyRepository.mySurveys()) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isLoading = false, surveys = result.data.items) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }
}
