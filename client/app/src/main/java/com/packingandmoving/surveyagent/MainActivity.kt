package com.packingandmoving.surveyagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.packingandmoving.surveyagent.ui.navigation.SurveyApp
import com.packingandmoving.surveyagent.ui.theme.SurveyAgentTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SurveyAgentTheme {
                SurveyApp()
            }
        }
    }
}
