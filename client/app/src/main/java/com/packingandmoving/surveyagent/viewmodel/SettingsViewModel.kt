package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.model.User
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.AuthRepository
import com.packingandmoving.surveyagent.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isLoading: Boolean = false,
    val user: User? = null,
    val isLoggedOut: Boolean = false,
    val errorMessage: String? = null,
)

/** Settings: current profile (GET /users/me) and sign-out (POST /auth/logout). */
class SettingsViewModel(
    private val userRepository: UserRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = userRepository.getProfile()) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isLoading = false, user = result.data) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    /**
     * Revokes the given refresh token. The token itself comes from secure storage wired
     * in the authentication phase; the screen supplies it here.
     */
    fun logout(refreshToken: String) {
        viewModelScope.launch {
            when (val result = authRepository.logout(refreshToken)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isLoggedOut = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(errorMessage = result.error.message) }
            }
        }
    }
}
