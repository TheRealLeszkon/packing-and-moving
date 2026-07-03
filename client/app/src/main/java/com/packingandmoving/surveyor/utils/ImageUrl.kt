package com.packingandmoving.surveyor.utils

import com.packingandmoving.surveyor.BuildConfig

/** Resolves a relative path returned by the backend (e.g. "/images/{id}") to a loadable URL. */
fun String?.toAbsoluteImageUrl(): String? {
    if (this.isNullOrBlank()) return null
    if (startsWith("http://") || startsWith("https://")) return this
    return BuildConfig.API_BASE_URL.trimEnd('/') + "/" + trimStart('/')
}
