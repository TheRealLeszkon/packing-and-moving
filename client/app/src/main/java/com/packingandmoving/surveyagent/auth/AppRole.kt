package com.packingandmoving.surveyagent.auth

import com.packingandmoving.surveyagent.model.UserRole

/**
 * How the user chose to use the app after signing in (item 7). This drives the UI workspace
 * and, for the demo role switch, is mirrored to the backend account role via [toUserRole].
 */
enum class AppRole { Surveyor, Customer }

/** The backend role this workspace preference maps to (for the demo self-role switch). */
fun AppRole.toUserRole(): UserRole = when (this) {
    AppRole.Surveyor -> UserRole.SURVEYOR
    AppRole.Customer -> UserRole.CUSTOMER
}
