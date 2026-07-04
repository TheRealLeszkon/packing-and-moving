package com.packingandmoving.surveyagent.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Enum wire values must match the backend exactly (openapi.json §components.schemas,
// frontend-integration.md §9). Serial names are pinned so a Kotlin rename can't drift.

@Serializable
enum class UserRole {
    @SerialName("customer") CUSTOMER,
    @SerialName("surveyor") SURVEYOR,
    @SerialName("admin") ADMIN,
}

@Serializable
enum class SurveyStatus {
    @SerialName("scheduled") SCHEDULED,
    @SerialName("assigned") ASSIGNED,
    @SerialName("in_progress") IN_PROGRESS,
    @SerialName("processing") PROCESSING,
    @SerialName("ready_for_review") READY_FOR_REVIEW,
    @SerialName("awaiting_customer_approval") AWAITING_CUSTOMER_APPROVAL,
    @SerialName("revision_required") REVISION_REQUIRED,
    @SerialName("approved") APPROVED,
    @SerialName("completed") COMPLETED,
    @SerialName("cancelled") CANCELLED,
}

@Serializable
enum class MediaType {
    @SerialName("image") IMAGE,
    @SerialName("video") VIDEO,
    @SerialName("processed_image") PROCESSED_IMAGE,
    @SerialName("extracted_frame") EXTRACTED_FRAME,
}

@Serializable
enum class ProcessingStatus {
    @SerialName("pending") PENDING,
    @SerialName("processing") PROCESSING,
    @SerialName("completed") COMPLETED,
    @SerialName("failed") FAILED,
    @SerialName("skipped") SKIPPED,
}

@Serializable
enum class Difficulty {
    @SerialName("easy") EASY,
    @SerialName("medium") MEDIUM,
    @SerialName("hard") HARD,
}

@Serializable
enum class ItemCondition {
    @SerialName("new") NEW,
    @SerialName("good") GOOD,
    @SerialName("fair") FAIR,
    @SerialName("poor") POOR,
    @SerialName("damaged") DAMAGED,
}
