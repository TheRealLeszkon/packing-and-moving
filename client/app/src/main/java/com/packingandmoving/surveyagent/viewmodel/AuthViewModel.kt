package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isLoading: Boolean = false,
    val isSignedIn: Boolean = false,
    val errorMessage: String? = null,
)

/**
 * Sign-in screen. The screen obtains a Google ID token via Credential Manager and hands it
 * here; this ViewModel exchanges it for the app's token pair (persisted by the repository)
 * and owns the loading/error/signed-in state.
 */
class AuthViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    /** Enter the loading state while the Google account picker is shown. */
    fun startSignIn() = _uiState.update { it.copy(isLoading = true, errorMessage = null) }

    fun signIn(googleIdToken: String) {
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            when (val result = authRepository.signInWithGoogle(googleIdToken)) {
                is ApiResult.Success ->
                    _uiState.update { it.copy(isLoading = false, isSignedIn = true) }
                is ApiResult.Failure ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = result.error.message) }
            }
        }
    }

    /** Surface a failure from the Google credential step (cancel, no account, etc.). */
    fun onSignInFailed(message: String) =
        _uiState.update { it.copy(isLoading = false, errorMessage = message) }

    fun consumeError() = _uiState.update { it.copy(errorMessage = null) }
}
