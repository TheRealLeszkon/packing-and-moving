package com.packingandmoving.surveyor.ui.navigation

/** Every destination in the app, and the nav-graph routes used to scope shared ViewModels. */
sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object History : Screen("history")

    data object CreateSurvey : Screen("create_survey")
    data object Capture : Screen("capture")
    data object Review : Screen("review")

    data object Processing : Screen("processing/{sessionId}") {
        fun createRoute(sessionId: String) = "processing/$sessionId"
    }

    /** Start destination of the report_graph — sessionId comes from the parent graph's args. */
    data object Report : Screen("report")

    data object ItemDetails : Screen("item/{itemId}") {
        fun createRoute(itemId: String) = "item/$itemId"
    }
}

/** Route for the nested graph that shares one [com.packingandmoving.surveyor.viewmodel.CaptureViewModel]. */
const val NEW_SURVEY_GRAPH_ROUTE = "new_survey_graph"

/** Route pattern (with sessionId arg) for the nested graph that shares one ReportViewModel. */
const val REPORT_GRAPH_ROUTE = "report_graph/{sessionId}"
fun reportGraphRoute(sessionId: String) = "report_graph/$sessionId"
