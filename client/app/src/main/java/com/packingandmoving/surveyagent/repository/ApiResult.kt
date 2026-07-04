package com.packingandmoving.surveyagent.repository

/** The outcome of a repository call: either data or a typed [AppError]. */
sealed interface ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>
    data class Failure(val error: AppError) : ApiResult<Nothing>
}

inline fun <T> ApiResult<T>.onSuccess(block: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) block(data)
    return this
}

inline fun <T> ApiResult<T>.onFailure(block: (AppError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) block(error)
    return this
}

/**
 * A user-presentable error with a stable [code] to branch on. Server codes come from the
 * error envelope (frontend-integration.md §2); the remaining codes are client-side.
 */
data class AppError(
    val code: ErrorCode,
    val message: String,
    val retryAfterSeconds: Int? = null,
)

enum class ErrorCode {
    // Server (branch on these, not on message text).
    UNAUTHENTICATED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    INVALID_STATE_TRANSITION,
    VALIDATION_ERROR,
    RATE_LIMITED,
    EXTERNAL_SERVICE_ERROR,
    INTERNAL_ERROR,

    // Client-side.
    NETWORK,   // no connectivity
    TIMEOUT,   // slow / unreachable
    UNKNOWN;   // unexpected / unparseable

    companion object {
        /** Maps a backend `error.code` string to an [ErrorCode]; unknown strings → [UNKNOWN]. */
        fun fromServerCode(code: String): ErrorCode = when (code) {
            "unauthenticated" -> UNAUTHENTICATED
            "forbidden" -> FORBIDDEN
            "not_found" -> NOT_FOUND
            "conflict" -> CONFLICT
            "invalid_state_transition" -> INVALID_STATE_TRANSITION
            "validation_error" -> VALIDATION_ERROR
            "rate_limited" -> RATE_LIMITED
            "external_service_error" -> EXTERNAL_SERVICE_ERROR
            "internal_error" -> INTERNAL_ERROR
            else -> UNKNOWN
        }
    }
}
