package com.packingandmoving.surveyagent.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.packingandmoving.surveyagent.auth.AppRole
import com.packingandmoving.surveyagent.ui.components.AppCard
import com.packingandmoving.surveyagent.ui.theme.Spacing
import kotlinx.coroutines.launch

/**
 * Role selection shown right after login (item 7). The choice is persisted and remembered
 * until changed, giving a clean split between the surveyor and customer experiences.
 */
@Composable
fun RoleSelectScreen(
    onRoleChosen: suspend (AppRole) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    fun choose(role: AppRole) = scope.launch { onRoleChosen(role) }

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
