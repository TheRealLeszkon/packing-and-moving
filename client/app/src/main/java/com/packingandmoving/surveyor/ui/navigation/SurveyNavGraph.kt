package com.packingandmoving.surveyor.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navigation
import com.packingandmoving.surveyor.repository.SurveyRepository
import com.packingandmoving.surveyor.ui.screens.CaptureScreen
import com.packingandmoving.surveyor.ui.screens.CreateSurveyScreen
import com.packingandmoving.surveyor.ui.screens.HistoryScreen
import com.packingandmoving.surveyor.ui.screens.HomeScreen
import com.packingandmoving.surveyor.ui.screens.ItemDetailsScreen
import com.packingandmoving.surveyor.ui.screens.ProcessingScreen
import com.packingandmoving.surveyor.ui.screens.ReportScreen
import com.packingandmoving.surveyor.ui.screens.ReviewScreen
import com.packingandmoving.surveyor.viewmodel.CaptureViewModel
import com.packingandmoving.surveyor.viewmodel.HistoryViewModel
import com.packingandmoving.surveyor.viewmodel.ItemDetailsViewModel
import com.packingandmoving.surveyor.viewmodel.ProcessingViewModel
import com.packingandmoving.surveyor.viewmodel.ReportViewModel

@Composable
fun SurveyNavGraph(repository: SurveyRepository) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Screen.Home.route) {

        composable(Screen.Home.route) {
            HomeScreen(
                onStartNewSurvey = { navController.navigate(NEW_SURVEY_GRAPH_ROUTE) },
                onViewHistory = { navController.navigate(Screen.History.route) },
            )
        }

        composable(Screen.History.route) {
            val viewModel: HistoryViewModel = viewModel(
                factory = viewModelFactory { initializer { HistoryViewModel(repository) } },
            )
            HistoryScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onSurveyClick = { sessionId -> navController.navigate(reportGraphRoute(sessionId)) },
            )
        }

        // Create Survey -> Capture -> Review share one CaptureViewModel for the survey in progress.
        navigation(startDestination = Screen.CreateSurvey.route, route = NEW_SURVEY_GRAPH_ROUTE) {
            composable(Screen.CreateSurvey.route) { backStackEntry ->
                // Touching the shared ViewModel here seeds the graph's ViewModelStore up front.
                sharedCaptureViewModel(backStackEntry, navController, repository)
                CreateSurveyScreen(
                    onBack = { navController.popBackStack() },
                    onStartCapturing = { navController.navigate(Screen.Capture.route) },
                )
            }
            composable(Screen.Capture.route) { backStackEntry ->
                val captureViewModel = sharedCaptureViewModel(backStackEntry, navController, repository)
                CaptureScreen(
                    viewModel = captureViewModel,
                    onBack = { navController.popBackStack() },
                    onReviewPhotos = { navController.navigate(Screen.Review.route) },
                )
            }
            composable(Screen.Review.route) { backStackEntry ->
                val captureViewModel = sharedCaptureViewModel(backStackEntry, navController, repository)
                ReviewScreen(
                    viewModel = captureViewModel,
                    onBack = { navController.popBackStack() },
                    onUploaded = { sessionId ->
                        navController.navigate(Screen.Processing.createRoute(sessionId)) {
                            popUpTo(NEW_SURVEY_GRAPH_ROUTE) { inclusive = true }
                        }
                    },
                )
            }
        }

        composable(
            route = Screen.Processing.route,
            arguments = listOf(navArgument("sessionId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val sessionId = backStackEntry.arguments!!.getString("sessionId")!!
            val viewModel: ProcessingViewModel = viewModel(
                factory = viewModelFactory { initializer { ProcessingViewModel(sessionId, repository) } },
            )
            ProcessingScreen(
                viewModel = viewModel,
                onCompleted = { completedSessionId ->
                    navController.navigate(reportGraphRoute(completedSessionId)) {
                        popUpTo(Screen.Home.route) { inclusive = false }
                    }
                },
                onReturnHome = {
                    navController.popBackStack(Screen.Home.route, inclusive = false)
                },
            )
        }

        // Report + Item Details share one ReportViewModel so edits made in Item Details show
        // up immediately back on the Report screen.
        // "{sessionId}" in REPORT_GRAPH_ROUTE is auto-typed as a String argument.
        navigation(
            startDestination = Screen.Report.route,
            route = REPORT_GRAPH_ROUTE,
        ) {
            composable(Screen.Report.route) { backStackEntry ->
                val reportViewModel = sharedReportViewModel(backStackEntry, navController, repository)
                ReportScreen(
                    viewModel = reportViewModel,
                    onBack = { navController.popBackStack(Screen.Home.route, inclusive = false) },
                    onItemClick = { itemId -> navController.navigate(Screen.ItemDetails.createRoute(itemId)) },
                    onFinishSurvey = {
                        navController.popBackStack(Screen.Home.route, inclusive = false)
                    },
                )
            }
            composable(
                route = Screen.ItemDetails.route,
                arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
            ) { backStackEntry ->
                val reportViewModel = sharedReportViewModel(backStackEntry, navController, repository)
                val itemId = backStackEntry.arguments!!.getString("itemId")!!
                val item = reportViewModel.findItem(itemId)

                if (item == null) {
                    // Process death or a stale deep link — nothing to show, so bail out.
                    navController.popBackStack()
                } else {
                    val detailsViewModel: ItemDetailsViewModel = viewModel(
                        key = itemId,
                        factory = viewModelFactory { initializer { ItemDetailsViewModel(item) } },
                    )
                    ItemDetailsScreen(
                        viewModel = detailsViewModel,
                        onBack = { navController.popBackStack() },
                        onSave = { updated ->
                            reportViewModel.updateItemLocally(updated)
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun sharedCaptureViewModel(
    backStackEntry: androidx.navigation.NavBackStackEntry,
    navController: androidx.navigation.NavHostController,
    repository: SurveyRepository,
): CaptureViewModel {
    val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(NEW_SURVEY_GRAPH_ROUTE) }
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = viewModelFactory { initializer { CaptureViewModel(repository) } },
    )
}

@Composable
private fun sharedReportViewModel(
    backStackEntry: androidx.navigation.NavBackStackEntry,
    navController: androidx.navigation.NavHostController,
    repository: SurveyRepository,
): ReportViewModel {
    val parentEntry = remember(backStackEntry) { navController.getBackStackEntry(REPORT_GRAPH_ROUTE) }
    val sessionId = parentEntry.arguments!!.getString("sessionId")!!
    return viewModel(
        viewModelStoreOwner = parentEntry,
        factory = viewModelFactory { initializer { ReportViewModel(sessionId, repository) } },
    )
}
