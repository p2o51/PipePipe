package com.gpo.pipepaw.feature.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import com.gpo.pipepaw.core.extractor.PawDownloader
import com.gpo.pipepaw.core.extractor.VideoItem
import com.gpo.pipepaw.core.extractor.Yt
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.ui.components.friendlyMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.stream.StreamInfo
import androidx.media3.common.Player as Media3Player

/**
 * The one player of the app. It is not tied to a page: the overlay ([PlayerOverlayHost]) shows
 * whatever [session] holds, as a full player or a mini bar, over any page. One ExoPlayer for
 * the process; a new video replaces the old one (a queue comes later).
 */
object Player {
    private lateinit var app: Context
    private val scope = MainScope()
    private var loadJob: Job? = null

    /** What the overlay shows. Null: no player on screen. */
    class Session(
        val url: String,
        /** The card that was tapped: title, thumbnail and channel draw at once, before [info] lands. */
        val preview: VideoItem?,
        val info: StreamInfo? = null,
        val error: String? = null,
    ) {
        val title: String get() = info?.name ?: preview?.title.orEmpty()
        val thumbnail: String get() = info?.thumbnailUrl ?: preview?.thumbnail.orEmpty()
        val channelName: String get() = info?.uploaderName ?: preview?.channelName.orEmpty()
    }

    var session by mutableStateOf<Session?>(null)
        private set

    /** The overlay's target: full player (true) or mini bar (false). */
    var expanded by mutableStateOf(false)

    var isPlaying by mutableStateOf(false)
        private set
    var isBuffering by mutableStateOf(false)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set

    val exo: ExoPlayer by lazy {
        ExoPlayer.Builder(app)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                addListener(object : Media3Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { this@Player.isPlaying = playing }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Media3Player.STATE_BUFFERING
                        if (state == Media3Player.STATE_READY) durationMs = duration.coerceAtLeast(0)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        val s = session ?: return
                        session = Session(s.url, s.preview, s.info, error = "播放出错 ${error.errorCodeName}")
                    }
                })
            }
    }

    private val upstream by lazy {
        OkHttpDataSource.Factory(Yt.downloader.client).setUserAgent(PawDownloader.USER_AGENT)
    }

    fun init(context: Context) {
        app = context.applicationContext
    }

    /** Opens [url] in the player, expanded. Tapping the video that is already playing just expands it. */
    fun play(url: String, preview: VideoItem? = null) {
        expanded = true
        if (session?.url == url && session?.error == null) return
        loadJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        durationMs = 0
        session = Session(url, preview)
        loadJob = scope.launch {
            try {
                val info = Yt.stream(url)
                if (session?.url != url) return@launch
                session = Session(url, preview, info)
                val resolved = withContext(Dispatchers.Default) {
                    StreamResolver.resolve(info, upstream, AppSettings.preferredHeight.value)
                }
                exo.setMediaSource(resolved.source)
                exo.setPlaybackSpeed(AppSettings.defaultSpeed.value)
                exo.prepare()
                exo.playWhenReady = AppSettings.autoPlay.value
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (session?.url == url) session = Session(url, preview, session?.info, error = e.friendlyMessage())
            }
        }
    }

    fun retry() {
        val s = session ?: return
        session = null
        play(s.url, s.preview)
    }

    fun toggle() {
        if (exo.isPlaying) exo.pause() else {
            if (exo.playbackState == Media3Player.STATE_ENDED) exo.seekTo(0)
            exo.play()
        }
    }

    /** Mini bar's close: playback stops and the overlay goes away. */
    fun close() {
        loadJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        expanded = false
        session = null
    }
}
