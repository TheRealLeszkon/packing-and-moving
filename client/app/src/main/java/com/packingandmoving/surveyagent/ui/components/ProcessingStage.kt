package com.packingandmoving.surveyagent.ui.components

/**
 * Maps the backend `processing_stage` value (GET /surveys/{id}/status) to user-facing text.
 * Null / unknown falls back to a generic message.
 */
fun processingStageLabel(stage: String?): String = when (stage) {
    "media_processing" -> "Processing photos & video…"
    "ai_analysis" -> "Analyzing with AI…"
    "finalizing" -> "Finalizing inventory…"
    else -> "Processing…"
}
