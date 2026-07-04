package com.packingandmoving.surveyagent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.packingandmoving.surveyagent.ui.components.BottomNavDestination
import com.packingandmoving.surveyagent.ui.components.SurveyBottomNavBar

/**
 * App entry point below the theme. Owns the [NavHostController] and shows the bottom
 * navigation bar only on the three top-level destinations.
 */
@Composable
fun SurveyApp(
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val activeTab = currentDestination?.toBottomNavDestination()

    Scaffold(
        bottomBar = {
            if (activeTab != null) {
                SurveyBottomNavBar(
                    selected = activeTab,
                    onSelect = navController::navigateToTab,
                )
            }
        },
    ) { innerPadding ->
        SurveyNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}

private fun NavDestination.toBottomNavDestination(): BottomNavDestination? = when {
    hasRoute<Home>() -> BottomNavDestination.Home
    hasRoute<Surveys>() -> BottomNavDestination.Surveys
    hasRoute<Settings>() -> BottomNavDestination.Settings
    else -> null
}

private fun NavHostController.navigateToTab(tab: BottomNavDestination) {
    val route: Any = when (tab) {
        BottomNavDestination.Home -> Home
        BottomNavDestination.Surveys -> Surveys
        BottomNavDestination.Settings -> Settings
    }
    navigate(route) {
        // Standard bottom-nav behavior: single instance per tab with saved state.
        popUpTo(Home) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
