package com.gpo.pipepaw

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.gpo.pipepaw.core.extractor.Yt
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.feature.player.Player
import com.gpo.pipepaw.feature.subscriptions.Subscriptions
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PawApp : Application(), SingletonImageLoader.Factory {
    /** One connection pool for the extractor, the player and the images. */
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    }

    override fun onCreate() {
        super.onCreate()
        AppSettings.init(this)
        Subscriptions.init(this)
        Yt.init(this, http)
        Player.init(this)
    }

    /** Thumbnails come from a handful of i.ytimg / ggpht hosts: lift OkHttp's 5-per-host cap. */
    private val imageHttp: OkHttpClient by lazy {
        http.newBuilder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 64
                maxRequestsPerHost = 16
            })
            .build()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttp })) }
            .crossfade(true)
            .build()
}
