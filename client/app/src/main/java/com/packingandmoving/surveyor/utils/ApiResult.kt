package com.packingandmoving.surveyor.utils

/** Outcome of a repository call, carrying a user-friendly message on failure. */
sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String) : ApiResult<Nothing>()
}
