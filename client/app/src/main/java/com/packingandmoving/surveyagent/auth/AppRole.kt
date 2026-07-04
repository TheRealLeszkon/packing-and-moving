package com.packingandmoving.surveyagent.auth

/**
 * How the user chose to use the app after signing in (item 7). This is a UI-workspace
 * preference, not the backend account role — the server still enforces its own RBAC.
 */
enum class AppRole { Surveyor, Customer }
