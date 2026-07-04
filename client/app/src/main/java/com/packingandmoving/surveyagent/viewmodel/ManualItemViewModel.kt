package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.ItemRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Manual item creation (POST /surveys/{id}/items). Uses the same [ItemForm] as the editor so
 * a manual item looks identical to an AI-detected one. Data-only: new photos can't be
 * attached here because uploads are blocked once the survey leaves IN_PROGRESS (see the
 * backend report).
 */
data class ManualItemUiState(
    val form: ItemForm = ItemForm(),
    val isSaving: Boolean = false,
    val createdItemId: String? = null,
    val errorMessage: String? = null,
)

class ManualItemViewModel(private val itemRepository: ItemRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(ManualItemUiState())
    val uiState: StateFlow<ManualItemUiState> = _uiState.asStateFlow()

    fun onFormChange(form: ItemForm) = _uiState.update { it.copy(form = form) }

    fun create(surveyId: String) {
        val form = _uiState.value.form
        if (!form.isValidForCreate) {
            _uiState.update { it.copy(errorMessage = "Name is required.") }
            return
        }
        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = itemRepository.addItem(surveyId, form.toCreate())) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isSaving = false, createdItemId = result.data.id) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isSaving = false, errorMessage = result.error.message) }
            }
        }
    }
}
