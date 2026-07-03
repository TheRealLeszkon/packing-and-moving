package com.packingandmoving.surveyor.utils

import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Turns a caught exception from a Retrofit call into a message safe to show in the UI. */
fun Throwable.toFriendlyMessage(): String = when (this) {
    is UnknownHostException -> "Can't reach the server. Check your internet connection."
    is SocketTimeoutException -> "The request timed out. Please try again."
    is HttpException -> when (code()) {
        404 -> "That survey could not be found."
        413, 415 -> "One of the selected files isn't a supported image."
        in 500..599 -> "The server ran into a problem. Please try again shortly."
        else -> "Something went wrong (HTTP ${code()})."
    }
    is IOException -> "Network error. Please check your connection and try again."
    else -> message ?: "An unexpected error occurred."
}
