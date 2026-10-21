package com.gpo.pipepaw

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.gpo.pipepaw.core.extractor.Yt
import com.gpo.pipepaw.core.extractor.decoder.WebViewJsDecoder
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.feature.player.Player
import com.gpo.pipepaw.feature.subscriptions.Subscriptions
import okhttp3.Dispatcher
import org.schabi.newpipe.extractor.services.youtube.YoutubeApiDecoder
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class PawApp : Application(), SingletonImageLoader.Factory {
    /** One connection pool for the extractor, the player and the images. */
    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).build()
    }

    override fun onCreate() {
        super.onCreate()
        com.gpo.pipepaw.core.util.CrashLog.install(this)
        AppSettings.init(this)
        com.gpo.pipepaw.core.account.Accounts.init(this)
        Subscriptions.init(this)
        Yt.init(this, http)
        // Signatures and n solved on the phone (V8 in a WebView); the extractor falls back to
        // PipePipe's remote decoder if this ever fails. Warmed off the main thread at start.
        val decoder = WebViewJsDecoder(this)
        YoutubeApiDecoder.setLocalDecoder(decoder)
        Thread({ decoder.prewarm() }, "decoder-prewarm").apply { isDaemon = true }.start()
        Player.init(this)
        com.gpo.pipepaw.feature.player.Segments.configure()
        com.gpo.pipepaw.feature.account.SignIn.refresh()
        com.gpo.pipepaw.core.account.AccountSubscriptions.start()
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
