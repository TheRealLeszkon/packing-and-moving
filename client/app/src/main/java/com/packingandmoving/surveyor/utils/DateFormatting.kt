package com.packingandmoving.surveyor.utils

import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

private val displayFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a")

/** Formats an ISO-8601 timestamp from the backend for display; falls back to the raw string. */
fun formatDisplayDate(isoTimestamp: String): String =
    try {
        OffsetDateTime.parse(isoTimestamp).format(displayFormatter)
    } catch (e: DateTimeParseException) {
        isoTimestamp
    }
