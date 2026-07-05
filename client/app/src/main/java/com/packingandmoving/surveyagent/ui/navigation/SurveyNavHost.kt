package com.packingandmoving.surveyagent.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.toRoute
import com.packingandmoving.surveyagent.api.NetworkModule
import com.packingandmoving.surveyagent.ui.screens.CameraScreen
import com.packingandmoving.surveyagent.ui.screens.CreateSurveyScreen
import com.packingandmoving.surveyagent.ui.screens.HomeScreen
import com.packingandmoving.surveyagent.ui.screens.ItemDetailScreen
import com.packingandmoving.surveyagent.ui.screens.ManualItemScreen
import com.packingandmoving.surveyagent.ui.screens.PhotoReviewScreen
import com.packingandmoving.surveyagent.ui.screens.ProcessingScreen
import com.packingandmoving.surveyagent.ui.screens.RoleSelectScreen
import com.packingandmoving.surveyagent.ui.screens.SettingsScreen
import com.packingandmoving.surveyagent.ui.screens.SignInScreen
import com.packingandmoving.surveyagent.ui.screens.SurveyDetailScreen
import com.packingandmoving.surveyagent.ui.screens.SurveyResultsScreen
import com.packingandmoving.surveyagent.ui.screens.SurveysScreen
import com.packingandmoving.surveyagent.viewmodel.AppViewModelFactory
import com.packingandmoving.surveyagent.viewmodel.CaptureViewModel

/**
 * The app's navigation graph. Screens receive plain navigation callbacks (not the
 * controller) so they stay decoupled from navigation. Sliding transitions per DESIGN §4.
 */
@Composable
fun SurveyNavHost(
    navController: NavHostController,
    startDestination: Any,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { slideIntoContainer(SlideDirection.Start) + fadeIn() },
        exitTransition = { slideOutOfContainer(SlideDirection.Start) + fadeOut() },
        popEnterTransition = { slideIntoContainer(SlideDirection.End) + fadeIn() },
        popExitTransition = { slideOutOfContainer(SlideDirection.End) + fadeOut() },
    ) {
        composable<SignIn> {
            SignInScreen(
                onSignedIn = {
                    // Choose a workspace role on first sign-in; skip it if already chosen.
                    val next: Any =
                        if (NetworkModule.sessionManager.selectedRole.value == null) RoleSelect else Home
                    navController.navigate(next) { popUpTo(SignIn) { inclusive = true } }
                },
            )
        }

        composable<RoleSelect> {
            RoleSelectScreen(
                onRoleChosen = { role ->
                    NetworkModule.sessionManager.setRole(role)
                    navController.navigate(Home) { popUpTo(0) { inclusive = true } }
                },
            )
        }

        composable<Home> {
            HomeScreen(
                onCreateSurvey = { navController.navigate(CreateSurvey) },
                onOpenSurvey = { surveyId -> navController.navigate(SurveyDetail(surveyId)) },
            )
        }

        composable<Surveys> {
            SurveysScreen(
                onOpenSurvey = { surveyId -> navController.navigate(SurveyDetail(surveyId)) },
            )
        }

        composable<Settings> {
            SettingsScreen(
                onSwitchRole = { navController.navigate(RoleSelect) },
            )
        }

        composable<CreateSurvey> {
            CreateSurveyScreen(
                onCancel = { navController.popBackStack() },
                onSurveyCreated = { surveyId ->
                    navController.navigate(SurveyDetail(surveyId)) {
                        popUpTo(CreateSurvey) { inclusive = true }
                    }
                },
            )
        }

        composable<SurveyDetail> { entry ->
            val route = entry.toRoute<SurveyDetail>()
            SurveyDetailScreen(
                surveyId = route.surveyId,
                onOpenCamera = { id -> navController.navigate(Capture(id)) },
                onOpenProcessing = { id -> navController.navigate(Processing(id)) },
                onOpenResults = { id -> navController.navigate(SurveyResults(id)) },
                onOpenAddItem = { id -> navController.navigate(AddItem(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        // Capture flow: nested graph so PhotoReview + Camera share one graph-scoped
        // CaptureViewModel (staged photos persist across camera<->review and rotation).
        navigation<Capture>(startDestination = ReviewPhotos) {
            composable<ReviewPhotos> { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(entry.destination.parent!!.route!!)
                }
                val surveyId = parentEntry.toRoute<Capture>().surveyId
                val captureViewModel: CaptureViewModel =
                    viewModel(viewModelStoreOwner = parentEntry, factory = AppViewModelFactory)
                PhotoReviewScreen(
                    surveyId = surveyId,
                    viewModel = captureViewModel,
                    onTakePhotos = { navController.navigate(Camera(surveyId)) },
                    onViewResults = { id ->
                        navController.navigate(SurveyResults(id)) {
                            popUpTo(Capture(surveyId)) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }

            composable<Camera> { entry ->
                val parentEntry = remember(entry) {
                    navController.getBackStackEntry(entry.destination.parent!!.route!!)
                }
                val captureViewModel: CaptureViewModel =
                    viewModel(viewModelStoreOwner = parentEntry, factory = AppViewModelFactory)
                CameraScreen(
                    captureViewModel = captureViewModel,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<Processing> { entry ->
            val route = entry.toRoute<Processing>()
            ProcessingScreen(
                surveyId = route.surveyId,
                onReadyForReview = { id ->
                    navController.navigate(SurveyResults(id)) {
                        popUpTo(Processing(id)) { inclusive = true }
                    }
                },
            )
        }

        // Merged AI report + summary (single destination after processing).
        composable<SurveyResults> { entry ->
            val route = entry.toRoute<SurveyResults>()
            SurveyResultsScreen(
                surveyId = route.surveyId,
                onOpenItem = { surveyId, itemId -> navController.navigate(ItemDetail(surveyId, itemId)) },
                onAddItem = { id -> navController.navigate(AddItem(id)) },
                onBack = { navController.popBackStack() },
                onSubmitted = {
                    navController.navigate(Home) { popUpTo(Home) { inclusive = true } }
                },
            )
        }

        composable<AddItem> { entry ->
            val route = entry.toRoute<AddItem>()
            ManualItemScreen(
                surveyId = route.surveyId,
                onCreated = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }

        composable<ItemDetail> { entry ->
            val route = entry.toRoute<ItemDetail>()
            ItemDetailScreen(
                surveyId = route.surveyId,
                itemId = route.itemId,
                onBack = { navController.popBackStack() },
            )
        }

    }
}
