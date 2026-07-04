package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.packingandmoving.surveyagent.auth.AppRole
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Role selection shown right after login (item 7). Choosing a role also switches the backend
 * account role (demo self-role switch) so RBAC-gated flows work; [onRoleChosen] returns an
 * error message on failure, or null on success (after which it navigates onward).
 */
@Composable
fun RoleSelectScreen(
    onRoleChosen: suspend (AppRole) -> String?,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var isSwitching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    fun choose(role: AppRole) {
        if (isSwitching) return
        scope.launch {
            isSwitching = true
            errorMessage = null
            errorMessage = onRoleChosen(role)
            isSwitching = false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(Spacing.Large),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Continue as", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Spacing.Large))

        RoleCard(
            title = "Surveyor",
            description = "Create and perform surveys.",
            onClick = { choose(AppRole.Surveyor) },
        )
        Spacer(Modifier.height(Spacing.Medium))
        RoleCard(
            title = "Customer",
            description = "View surveys and inventory.",
            onClick = { choose(AppRole.Customer) },
        )

        if (isSwitching) {
            Spacer(Modifier.height(Spacing.Large))
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        }
        errorMessage?.let {
            Spacer(Modifier.height(Spacing.Medium))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun RoleCard(title: String, description: String, onClick: () -> Unit) {
    AppCard(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.ExtraSmall))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
