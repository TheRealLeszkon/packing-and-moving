package com.packingandmoving.surveyagent.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import com.packingandmoving.surveyagent.ui.theme.SurveyAgentTheme

/** The three top-level destinations shown in the bottom navigation bar (DESIGN.md §2). */
enum class BottomNavDestination(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Default.Home),
    Surveys("Surveys", Icons.AutoMirrored.Filled.List),
    Settings("Settings", Icons.Default.Settings),
}

/**
 * Bottom navigation bar. Pure UI: the caller owns the selected destination and the
 * selection callback; routing is wired in the navigation layer (Phase 3).
 */
@Composable
fun SurveyBottomNavBar(
    selected: BottomNavDestination,
    onSelect: (BottomNavDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    NavigationBar(modifier = modifier) {
        BottomNavDestination.entries.forEach { destination ->
            NavigationBarItem(
                selected = destination == selected,
                onClick = { onSelect(destination) },
                icon = { Icon(destination.icon, contentDescription = destination.label) },
                label = { Text(destination.label) },
            )
        }
    }
}

@Preview
@Composable
private fun SurveyBottomNavBarPreview() {
    SurveyAgentTheme {
        SurveyBottomNavBar(selected = BottomNavDestination.Home, onSelect = {})
    }
}
