package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.SurveyCreate
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.SurveyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Intake form (SCREEN_7 → POST /surveys). The backend only stores name + origin/
 * destination addresses (+ optional datetime); the design's Phone/Email/Notes fields have
 * no backend columns yet (frontend-integration.md §13), so they are omitted here.
 */
data class CreateSurveyUiState(
    val name: String = "",
    val originAddress: String = "",
    val destinationAddress: String = "",
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val createdSurveyId: String? = null,
) {
    val canSubmit: Boolean
        get() = name.isNotBlank() &&
            originAddress.isNotBlank() &&
            destinationAddress.isNotBlank() &&
            !isSubmitting
}

class CreateSurveyViewModel(private val surveyRepository: SurveyRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSurveyUiState())
    val uiState: StateFlow<CreateSurveyUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) = _uiState.update { it.copy(name = value) }
    fun onOriginChange(value: String) = _uiState.update { it.copy(originAddress = value) }
    fun onDestinationChange(value: String) = _uiState.update { it.copy(destinationAddress = value) }

    fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
        viewModelScope.launch {
            val body = SurveyCreate(
                name = state.name.trim(),
                originAddress = state.originAddress.trim(),
                destinationAddress = state.destinationAddress.trim(),
            )
            when (val result = surveyRepository.createSurvey(body)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isSubmitting = false, createdSurveyId = result.data.id) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = result.error.message) }
            }
        }
    }
}
