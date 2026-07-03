package com.packingandmoving.surveyor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.packingandmoving.surveyor.ui.navigation.SurveyNavGraph
import com.packingandmoving.surveyor.ui.theme.FieldSurveyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as SurveyorApplication).repository

        setContent {
            FieldSurveyTheme {
                SurveyNavGraph(repository = repository)
            }
        }
    }
}
