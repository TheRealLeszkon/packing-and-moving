package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun SignInScreen(
    onSignedIn: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlaceholderScreen(
        title = "Sign In",
        subtitle = "Google Sign-In → POST /auth/google",
        modifier = modifier,
        actions = listOf(
            PlaceholderAction("Sign In", onSignedIn),
        ),
    )
}
