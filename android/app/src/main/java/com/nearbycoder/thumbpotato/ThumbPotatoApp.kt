package com.nearbycoder.thumbpotato

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.nearbycoder.thumbpotato.data.remote.Http
import com.nearbycoder.thumbpotato.reminders.Reminders
import java.io.File

class ThumbPotatoApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Reminders.ensureChannels(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .crossfade(250)
            // Share the app's client so Wikimedia sees our User-Agent on box art.
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { Http.client })) }
            .memoryCache {
                MemoryCache.Builder().maxSizePercent(context, 0.2).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "images"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .build()
}

val Context.appContainer: AppContainer
    get() = (applicationContext as ThumbPotatoApp).container
