package com.packingandmoving.surveyor.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.packingandmoving.surveyor.ui.theme.Spacing

@Composable
fun HomeScreen(
    onStartNewSurvey: () -> Unit,
    onViewHistory: () -> Unit,
) {
    Scaffold { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(Spacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "Field Survey",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.sm))
            Text(
                text = "Capture a home or office to generate an AI-assisted moving inventory.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(Spacing.xl))

            Button(
                onClick = onStartNewSurvey,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.minTouchTarget + Spacing.xs),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            ) {
                Icon(Icons.Filled.AddAPhoto, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Start New Survey", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(Modifier.height(Spacing.md))

            OutlinedButton(
                onClick = onViewHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Spacing.minTouchTarget + Spacing.xs),
            ) {
                Icon(Icons.Filled.History, contentDescription = null)
                Spacer(Modifier.width(Spacing.sm))
                Text("Survey History", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
