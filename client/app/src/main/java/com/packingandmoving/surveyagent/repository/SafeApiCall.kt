package com.packingandmoving.surveyagent.repository

import com.packingandmoving.surveyagent.api.ApiEnvelope
import com.packingandmoving.surveyagent.api.ApiErrorEnvelope
import com.packingandmoving.surveyagent.api.NetworkModule
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

/**
 * Runs an API call and normalizes the result: unwraps the success envelope's `data`, or
 * maps any failure (HTTP error envelope, no connectivity, timeout, unexpected) to a typed
 * [AppError]. Exceptions are never swallowed silently (CLAUDE.md "Error Handling").
 */
suspend fun <T> safeApiCall(
    json: Json = NetworkModule.json,
    call: suspend () -> ApiEnvelope<T>,
): ApiResult<T> = try {
    ApiResult.Success(call().data)
} catch (e: HttpException) {
    ApiResult.Failure(e.toAppError(json))
} catch (e: SocketTimeoutException) {
    ApiResult.Failure(AppError(ErrorCode.TIMEOUT, "The request timed out. Please try again."))
} catch (e: IOException) {
    ApiResult.Failure(AppError(ErrorCode.NETWORK, "No internet connection."))
} catch (e: Exception) {
    ApiResult.Failure(AppError(ErrorCode.UNKNOWN, e.message ?: "Something went wrong."))
}

private fun HttpException.toAppError(json: Json): AppError {
    val raw = response()?.errorBody()?.string()
    val envelope = raw?.let {
        runCatching { json.decodeFromString<ApiErrorEnvelope>(it) }.getOrNull()
    }
    val body = envelope?.error
    if (body != null) {
        return AppError(
            code = ErrorCode.fromServerCode(body.code),
            message = body.message.ifBlank { "Request failed (${code()})." },
        )
    }
    // Fall back to the HTTP status when the body isn't the expected envelope.
    val fallbackCode = when (code()) {
        401 -> ErrorCode.UNAUTHENTICATED
        403 -> ErrorCode.FORBIDDEN
        404 -> ErrorCode.NOT_FOUND
        409 -> ErrorCode.CONFLICT
        422 -> ErrorCode.VALIDATION_ERROR
        429 -> ErrorCode.RATE_LIMITED
        502 -> ErrorCode.EXTERNAL_SERVICE_ERROR
        in 500..599 -> ErrorCode.INTERNAL_ERROR
        else -> ErrorCode.UNKNOWN
    }
    return AppError(fallbackCode, "Request failed (${code()}).")
}
