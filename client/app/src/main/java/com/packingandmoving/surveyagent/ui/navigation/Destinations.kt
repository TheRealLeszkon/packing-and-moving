package com.packingandmoving.surveyagent.ui.navigation

import kotlinx.serialization.Serializable

// Type-safe navigation routes (one per screen). Only IDs are passed between screens;
// each screen reloads its own data (CLAUDE.md "Navigation").

@Serializable
object SignIn

// Bottom-navigation top-level destinations.
@Serializable
object Home

@Serializable
object Surveys

@Serializable
object Settings

// Survey flow.
@Serializable
object CreateSurvey

@Serializable
data class SurveyDetail(val surveyId: String)

// Capture flow: a nested graph so the camera and the photo-review screen share one
// graph-scoped CaptureViewModel (staged photos survive moving between them + rotation).
@Serializable
data class Capture(val surveyId: String)

@Serializable
object ReviewPhotos

@Serializable
data class Camera(val surveyId: String)

@Serializable
data class Processing(val surveyId: String)

// Merged AI report + summary.
@Serializable
data class SurveyResults(val surveyId: String)

@Serializable
data class ItemDetail(val surveyId: String, val itemId: String)

@Serializable
data class AddItem(val surveyId: String)
