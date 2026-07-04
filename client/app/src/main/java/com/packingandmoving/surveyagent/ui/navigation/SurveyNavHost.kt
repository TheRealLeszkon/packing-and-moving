package com.packingandmoving.surveyagent.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.packingandmoving.surveyagent.ui.screens.AiReportScreen
import com.packingandmoving.surveyagent.ui.screens.CameraScreen
import com.packingandmoving.surveyagent.ui.screens.CreateSurveyScreen
import com.packingandmoving.surveyagent.ui.screens.HomeScreen
import com.packingandmoving.surveyagent.ui.screens.ItemDetailScreen
import com.packingandmoving.surveyagent.ui.screens.ProcessingScreen
import com.packingandmoving.surveyagent.ui.screens.SettingsScreen
import com.packingandmoving.surveyagent.ui.screens.SignInScreen
import com.packingandmoving.surveyagent.ui.screens.SummaryScreen
import com.packingandmoving.surveyagent.ui.screens.SurveyDetailScreen
import com.packingandmoving.surveyagent.ui.screens.SurveysScreen

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
                    navController.navigate(Home) {
                        popUpTo(SignIn) { inclusive = true }
                    }
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
            SettingsScreen()
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
                onOpenCamera = { id -> navController.navigate(Camera(id)) },
                onOpenProcessing = { id -> navController.navigate(Processing(id)) },
                onOpenReport = { id -> navController.navigate(AiReport(id)) },
                onOpenSummary = { id -> navController.navigate(Summary(id)) },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Camera> { entry ->
            val route = entry.toRoute<Camera>()
            CameraScreen(
                surveyId = route.surveyId,
                onFinishCapture = { id ->
                    navController.navigate(Processing(id)) {
                        popUpTo(Camera(id)) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }

        composable<Processing> { entry ->
            val route = entry.toRoute<Processing>()
            ProcessingScreen(
                surveyId = route.surveyId,
                onReadyForReview = { id ->
                    navController.navigate(AiReport(id)) {
                        popUpTo(Processing(id)) { inclusive = true }
                    }
                },
            )
        }

        composable<AiReport> { entry ->
            val route = entry.toRoute<AiReport>()
            AiReportScreen(
                surveyId = route.surveyId,
                onOpenItem = { surveyId, itemId ->
                    navController.navigate(ItemDetail(surveyId, itemId))
                },
                onOpenSummary = { id -> navController.navigate(Summary(id)) },
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

        composable<Summary> { entry ->
            val route = entry.toRoute<Summary>()
            SummaryScreen(
                surveyId = route.surveyId,
                onComplete = {
                    navController.navigate(Home) {
                        popUpTo(Home) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
