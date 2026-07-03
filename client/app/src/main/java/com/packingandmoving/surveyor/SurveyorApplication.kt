package com.packingandmoving.surveyor

import android.app.Application
import com.packingandmoving.surveyor.api.NetworkModule
import com.packingandmoving.surveyor.repository.SurveyRepository

/** Holds the app's single [SurveyRepository] instance — plain manual DI, no framework needed. */
class SurveyorApplication : Application() {

    val repository: SurveyRepository by lazy {
        SurveyRepository(NetworkModule.api, contentResolver)
    }
}
