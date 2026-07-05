package com.packingandmoving.surveyagent.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.packingandmoving.surveyagent.api.NetworkModule
import com.packingandmoving.surveyagent.auth.AuthState
import com.packingandmoving.surveyagent.model.UserRole
import com.packingandmoving.surveyagent.repository.ApiResult
import com.packingandmoving.surveyagent.repository.AppRepositories
import com.packingandmoving.surveyagent.ui.components.BottomNavDestination
import com.packingandmoving.surveyagent.ui.components.SurveyBottomNavBar

/**
 * App entry point below the theme. Owns the [NavHostController], picks the start destination
 * from the persisted session, shows the bottom bar only on the three top-level destinations,
 * and returns to sign-in whenever the session is cleared (explicit sign-out or a failed
 * token refresh).
 */
@Composable
fun SurveyApp(
    navController: NavHostController = rememberNavController(),
) {
    val session = NetworkModule.sessionManager
    val startDestination: Any = remember {
        if (session.currentAccessToken() == null) SignIn else Home
    }
    val authState by session.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState == AuthState.SignedOut) {
            navController.navigate(SignIn) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val activeTab = currentDestination?.toBottomNavDestination()

    // Tabs are role-specific (GET /users/me → role): surveyors work the request board and
    // have no use for the customer-facing Home/"start survey" screen, while customers never
    // see the surveyor job board. Until the role resolves we show the customer set.
    val userRole by produceState<UserRole?>(initialValue = null, authState) {
        value = if (authState == AuthState.SignedIn) {
            (AppRepositories.user.getProfile() as? ApiResult.Success)?.data?.role
        } else {
            null
        }
    }
    val visibleTabs = remember(userRole) {
        when (userRole) {
            UserRole.SURVEYOR -> listOf(BottomNavDestination.Surveys, BottomNavDestination.Settings)
            else -> listOf(BottomNavDestination.Home, BottomNavDestination.Settings)
        }
    }

    // Startup can only default signed-in users to Home (role isn't known synchronously);
    // once a surveyor's role resolves, send them to their Surveys landing and drop Home.
    LaunchedEffect(userRole, activeTab) {
        if (userRole == UserRole.SURVEYOR && activeTab == BottomNavDestination.Home) {
            navController.navigate(Surveys) {
                popUpTo(Home) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    Scaffold(
        bottomBar = {
            if (activeTab != null && activeTab in visibleTabs) {
                SurveyBottomNavBar(
                    selected = activeTab,
                    onSelect = navController::navigateToTab,
                    destinations = visibleTabs,
                )
            }
        },
    ) { innerPadding ->
        SurveyNavHost(
            navController = navController,
            startDestination = startDestination,
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
