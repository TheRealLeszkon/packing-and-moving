package com.packingandmoving.surveyagent

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.packingandmoving.surveyagent.api.NetworkModule
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath

/**
 * Builds the networking/auth graph once at process start and loads any persisted session
 * into memory before the first screen renders. Also configures the app-wide Coil
 * [ImageLoader] (explicit OkHttp network fetcher + memory/disk cache + crossfade) so signed
 * media URLs load reliably and thumbnails are cached rather than re-fetched.
 */
class SurveyAgentApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        NetworkModule.init(this)
        runBlocking { NetworkModule.sessionManager.hydrate() }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.25).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .build()
}
