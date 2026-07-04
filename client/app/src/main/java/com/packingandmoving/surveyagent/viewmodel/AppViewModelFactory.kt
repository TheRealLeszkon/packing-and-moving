package com.packingandmoving.surveyagent.viewmodel

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.packingandmoving.surveyagent.repository.AppRepositories

/**
 * Single factory that constructs every screen ViewModel with its repositories from
 * [AppRepositories]. Screens obtain a ViewModel with `viewModel(factory = AppViewModelFactory)`.
 * Explicit manual wiring — no DI framework (CLAUDE.md "avoid unnecessary abstractions").
 */
val AppViewModelFactory: ViewModelProvider.Factory = viewModelFactory {
    initializer { AuthViewModel(AppRepositories.auth) }
    initializer { HomeViewModel(AppRepositories.survey) }
    initializer { SurveysViewModel(AppRepositories.survey) }
    initializer { SettingsViewModel(AppRepositories.user, AppRepositories.auth) }
    initializer { CreateSurveyViewModel(AppRepositories.survey) }
    initializer { SurveyDetailViewModel(AppRepositories.survey) }
    initializer { CaptureViewModel(AppRepositories.media, AppRepositories.survey) }
    initializer { ProcessingViewModel(AppRepositories.survey) }
    initializer { SurveyResultsViewModel(AppRepositories.survey, AppRepositories.item) }
    initializer { ItemDetailViewModel(AppRepositories.item, AppRepositories.media) }
    initializer { ManualItemViewModel(AppRepositories.item) }
}
