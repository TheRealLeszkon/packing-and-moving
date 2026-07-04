package com.packingandmoving.surveyagent

import android.app.Application
import com.packingandmoving.surveyagent.api.NetworkModule
import kotlinx.coroutines.runBlocking

/**
 * Builds the networking/auth graph once at process start and loads any persisted session
 * into memory before the first screen renders, so the app can decide up front whether to
 * open on sign-in or on Home.
 */
class SurveyAgentApp : Application() {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        runBlocking { NetworkModule.sessionManager.hydrate() }
    }
}
