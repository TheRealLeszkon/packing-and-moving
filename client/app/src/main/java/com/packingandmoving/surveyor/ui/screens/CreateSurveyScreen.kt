package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.packingandmoving.surveyor.ui.theme.Spacing

private val tips = listOf(
    "Capture each room from a corner so every item is visible.",
    "Take close-ups of appliance model labels and fragile items.",
    "Make sure rooms are well lit — avoid backlighting.",
    "You can add more photos or retake any of them before uploading.",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateSurveyScreen(
    onBack: () -> Unit,
    onStartCapturing: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Survey") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.md),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Before you start",
                    style = MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(Spacing.md))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    Column(modifier = Modifier.padding(Spacing.md)) {
                        tips.forEach { tip ->
                            Text(
                                text = "•  $tip",
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(vertical = Spacing.xs),
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onStartCapturing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.minTouchTarget + Spacing.xs),
            ) {
                Text("Start Capturing", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
