package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.packingandmoving.surveyagent.BuildConfig
import com.packingandmoving.surveyagent.auth.GoogleAuthManager
import com.packingandmoving.surveyagent.ui.theme.Spacing
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.AuthViewModel
import kotlinx.coroutines.launch

/**
 * Sign-in screen. Uses Credential Manager to obtain a Google ID token, then exchanges it
 * for the app session via [AuthViewModel]. Navigates onward once signed in.
 */
@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AuthViewModel = viewModel(factory = AppViewModelFactory),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val googleAuth = remember { GoogleAuthManager(BuildConfig.GOOGLE_SERVER_CLIENT_ID) }

    LaunchedEffect(uiState.isSignedIn) {
        if (uiState.isSignedIn) onSignedIn()
    }

    fun signIn() {
        viewModel.startSignIn()
        scope.launch {
            try {
                val idToken = googleAuth.getIdToken(context)
                viewModel.signIn(idToken)
            } catch (e: GetCredentialCancellationException) {
                viewModel.onSignInFailed("Sign-in cancelled.")
            } catch (e: NoCredentialException) {
                viewModel.onSignInFailed("No Google account available on this device.")
            } catch (e: GetCredentialException) {
                viewModel.onSignInFailed(e.message ?: "Google sign-in failed.")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "SurveyAgent",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Spacing.Small))
        Text(
            text = "AI-assisted packing & moving surveys",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(Spacing.ExtraLarge))

        Button(
            onClick = { signIn() },
            enabled = !uiState.isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            } else {
                Text("Sign in with Google")
            }
        }

        val error = uiState.errorMessage
        if (error != null) {
            Spacer(Modifier.height(Spacing.Medium))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
            )
        }
    }
}
