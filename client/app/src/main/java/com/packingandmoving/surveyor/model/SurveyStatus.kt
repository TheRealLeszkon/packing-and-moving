package com.packingandmoving.surveyor.model

/** Mirrors the free-text `status` values the backend stores on a SurveySession. */
enum class SurveyStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    UNKNOWN;

    companion object {
        fun from(raw: String): SurveyStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}
