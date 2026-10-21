package com.gpo.pipepaw.feature.player

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
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

    /** The picture fills the window ([FullscreenPlayer]); only while expanded. */
    var fullscreen by mutableStateOf(false)

    /** The video's display aspect (width / height, pixel ratio applied); 16:9 until the first frame says otherwise. */
    var videoAspect by mutableFloatStateOf(16f / 9f)
        private set

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
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                addListener(object : Media3Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) { this@Player.isPlaying = playing }
                    override fun onVideoSizeChanged(size: VideoSize) {
                        if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                    }
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Media3Player.STATE_BUFFERING
                        if (state == Media3Player.STATE_READY) durationMs = duration.coerceAtLeast(0)
                    }
                    override fun onPlayerError(error: PlaybackException) {
                        val s = session ?: return
                        if (error.errorCode in RecoverableErrors && recover(s)) return
                        session = Session(s.url, s.preview, s.info, error = "播放出错 ${error.errorCodeName}")
                    }
                })
            }
    }

    private val upstream by lazy { YoutubeDataSource.upstream(Yt.downloader.client) }

    /**
     * HTTP and network failures (a 403 on an expired or refused URL above all) re-extract the
     * stream and pick up where it was, as PipePipe's PlayerErrorHandler does; twice per video,
     * then the error shows with 重试.
     */
    private val RecoverableErrors = setOf(
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS,
        PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
        PlaybackException.ERROR_CODE_TIMEOUT,
    )
    private const val MaxRecoveries = 2
    private var recoveries = 0

    private fun recover(s: Session): Boolean {
        if (recoveries >= MaxRecoveries) return false
        recoveries++
        val position = exo.currentPosition
        val resume = exo.playWhenReady
        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val info = Yt.stream(s.url)
                if (session?.url != s.url) return@launch
                session = Session(s.url, s.preview, info)
                val resolved = withContext(Dispatchers.Default) {
                    StreamResolver.resolve(info, upstream, AppSettings.preferredHeight.value)
                }
                exo.setMediaSource(resolved.source, position)
                exo.prepare()
                exo.playWhenReady = resume
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (session?.url == s.url) session = Session(s.url, s.preview, session?.info, error = e.friendlyMessage())
            }
        }
        return true
    }

    fun init(context: Context) {
        app = context.applicationContext
        scope.launch { AppSettings.backgroundPlay.state.collect { syncBackgroundHost() } }
    }

    /**
     * The media session (notification, lock screen, Bluetooth keys). Made with the player and
     * kept for the process; [PlaybackService] borrows it while background play wants it.
     */
    private val mediaSession: MediaSession? by lazy {
        runCatching {
            MediaSession.Builder(app, exo)
                .setId("pipepaw-player")
                .setMediaButtonPreferences(PlaybackService.buttons)
                .setBitmapLoader(CoilBitmapLoader(app))
                .setSessionActivity(PlaybackService.sessionActivity(app))
                .build()
        }.onFailure { android.util.Log.w("PipePaw", "media session", it) }.getOrNull()
    }
    private var hosted = false

    /** Hands the session to [PlaybackService] while a video is open and 后台播放 is on. */
    private fun syncBackgroundHost() {
        if (session == null && !hosted) return
        val s = mediaSession ?: return
        val want = session != null && AppSettings.backgroundPlay.value
        if (want == hosted) return
        hosted = want
        if (want) PlaybackService.attach(app, s) else PlaybackService.detach(s)
    }

    /** Opens [url] in the player, expanded. Tapping the video that is already playing just expands it. */
    fun play(url: String, preview: VideoItem? = null) {
        expanded = true
        if (session?.url == url && session?.error == null) return
        loadJob?.cancel()
        exo.stop()
        exo.clearMediaItems()
        durationMs = 0
        recoveries = 0
        session = Session(url, preview)
        syncBackgroundHost()
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
        fullscreen = false
        session = null
        syncBackgroundHost()
    }
}
