package com.gpo.pipepaw.feature.player

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.ContentFrame
import com.gpo.pipepaw.core.util.Fmt
import com.gpo.pipepaw.ui.theme.YoinMotion
import com.gpo.pipepaw.ui.theme.YoinMotionRole
import com.gpo.pipepaw.ui.theme.withTabularFigures
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.delay

/**
 * Where playback is and what a finger on the seek bar is scrubbing to; one per seek bar.
 * The position is polled at 4 Hz (and not while scrubbing, so the thumb stays under the finger).
 */
@Stable
internal class SeekState {
    var position by mutableLongStateOf(0L)
        internal set
    var scrubbing by mutableStateOf(false)
        private set
    var scrub by mutableFloatStateOf(0f)
        private set

    val duration: Long get() = Player.durationMs
    val fraction: Float get() = if (scrubbing) scrub else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val shownMs: Long get() = if (scrubbing) (scrub * duration).toLong() else position

    fun onScrub(value: Float) {
        scrubbing = true
        scrub = value
    }

    fun onScrubEnd() {
        val target = (scrub * duration).toLong()
        if (duration > 0) Player.exo.seekTo(target)
        position = target
        scrubbing = false
    }
}

@Composable
internal fun rememberSeekState(): SeekState {
    val state = remember { SeekState() }
    LaunchedEffect(state) {
        while (true) {
            if (!state.scrubbing) state.position = Player.exo.currentPosition
            delay(250)
        }
    }
    return state
}

/** Playhead and duration as two labels (no joiner), tabular so they never jitter. */
@Composable
internal fun TimeLabels(seek: SeekState, variant: Color, modifier: Modifier = Modifier) {
    val style = MaterialTheme.typography.labelLarge.withTabularFigures()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.duration(seek.shownMs / 1000).ifEmpty { "0:00" }, style = style)
        Spacer(Modifier.width(8.dp))
        Text(Fmt.duration(seek.duration / 1000), style = style, color = variant)
    }
}

/** Ink on the black fullscreen frame: plain white at three strengths, whatever the theme (a scrim colour). */
private val OnVideo = Color.White
private val OnVideoVariant = Color.White.copy(alpha = 0.72f)

/**
 * The fullscreen player (PiliPaw's fullscreen, fitted to PipePaw's overlay). The picture fills
 * the window; a tap brings the controls, and the picture makes room for them instead of being
 * covered: it shrinks (keeping its aspect) into the band between the top bar (back, title)
 * and the bottom bar (play, times, seek bar, exit), lifts to that band's centre and rounds its
 * corners ([pictureCard]). Nothing is ever drawn on the picture. The controls fold away three
 * seconds after the last touch while playing. Back leaves fullscreen.
 *
 * Window: system bars hidden; a phone turns to landscape for a landscape video; a large screen
 * tries a real rotation and, where the platform only letterboxes (the Pixel Tablet), turns
 * the picture itself and follows the accelerometer (PlayerWindow.kt).
 */
@Composable
internal fun FullscreenPlayer(session: Player.Session) {
    val activity = LocalActivity.current
    val configuration = LocalConfiguration.current
    val phone = configuration.smallestScreenWidthDp < 600
    val portraitVideo = Player.videoAspect < 1f
    FullscreenWindowEffect(activity, fullscreen = true, lockLandscape = phone && !portraitVideo)
    val windowPortrait = configuration.screenHeightDp > configuration.screenWidthDp
    val multiWindow = remember(configuration) { activity?.isInMultiWindowMode == true }
    val large = rememberLargeScreenFullscreen(activity, want = !phone && !portraitVideo && !multiWindow)
    val turned = large == LargeFullscreen.Turn && windowPortrait
    val turn = rememberLandscapeTurn(turned)
    val turnAngle = remember { Animatable(0f) }
    val turnSpec = YoinMotion.defaultSpatialSpec<Float>()
    LaunchedEffect(turned, turn) { turnAngle.animateTo(if (turned) turn else 0f, turnSpec) }

    BackHandler { Player.fullscreen = false }

    var shown by remember { mutableStateOf(true) }
    var poked by remember { mutableLongStateOf(0L) }
    val playing = Player.isPlaying
    LaunchedEffect(shown, playing, poked) {
        if (shown && playing) {
            delay(AutoHideMs)
            shown = false
        }
    }
    val revealState = animateFloatAsState(
        if (shown) 1f else 0f,
        YoinMotion.defaultSpatialSpec(YoinMotionRole.Expressive),
        label = "fullscreenReveal",
    )
    val reveal: () -> Float = remember(revealState) { { revealState.value } }
    val revealing by remember(revealState) { derivedStateOf { revealState.value > 0.004f } }
    val seek = rememberSeekState()

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .turnedContent(turned) { turnAngle.value },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val short = maxHeight < 480.dp
            val topBand = if (short) 48.dp else 56.dp
            val bottomBand = if (short) 56.dp else 64.dp
            Box(
                Modifier
                    .fillMaxSize()
                    .pictureCard(reveal, { Player.videoAspect }, topBand, bottomBand, MinCardScale),
            ) {
                if (session.info != null && session.error == null) {
                    ContentFrame(
                        player = Player.exo,
                        modifier = Modifier.fillMaxSize(),
                        // A TextureView: the card scales and clips it; a SurfaceView cannot be.
                        surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                        shutter = {},
                    )
                }
            }
            // Over the picture (its video view would take the touches), under the bars: taps
            // anywhere, the picture or the black around it, show and hide the controls.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures {
                            shown = !shown
                            poked = System.nanoTime()
                        }
                    },
            )
            if (revealing) {
                CompositionLocalProvider(LocalContentColor provides OnVideo) {
                    TopBar(
                        title = session.title,
                        height = topBand,
                        turned = turned,
                        reveal = reveal,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    BottomBar(
                        seek = seek,
                        height = bottomBand,
                        turned = turned,
                        reveal = reveal,
                        onPoke = { poked = System.nanoTime() },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, height: Dp, turned: Boolean, reveal: () -> Float, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = reveal().coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * -14.dp.toPx()
            }
            .then(if (turned) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout))
            .height(height)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { Player.fullscreen = false }, shapes = IconButtonDefaults.shapes()) {
            Icon(YoinSymbols.Back, contentDescription = "退出全屏")
        }
        Spacer(Modifier.width(12.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BottomBar(seek: SeekState, height: Dp, turned: Boolean, reveal: () -> Float, onPoke: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = reveal().coerceIn(0f, 1f)
                alpha = p
                translationY = (1f - p) * 14.dp.toPx()
            }
            .then(if (turned) Modifier else Modifier.windowInsetsPadding(WindowInsets.displayCutout))
            .height(height)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(
            onClick = {
                Player.toggle()
                onPoke()
            },
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(if (Player.isPlaying) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled, if (Player.isPlaying) "暂停" else "播放")
        }
        TimeLabels(seek, OnVideoVariant, Modifier.padding(start = 6.dp, end = 12.dp))
        Slider(
            value = seek.fraction,
            onValueChange = {
                seek.onScrub(it)
                onPoke()
            },
            onValueChangeFinished = seek::onScrubEnd,
            enabled = seek.duration > 0,
            colors = SliderDefaults.colors(
                thumbColor = OnVideo,
                activeTrackColor = OnVideo,
                inactiveTrackColor = OnVideo.copy(alpha = 0.24f),
            ),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(10.dp))
        IconButton(onClick = { Player.fullscreen = false }, shapes = IconButtonDefaults.shapes(), modifier = Modifier.size(44.dp)) {
            Icon(PiliSymbols.FullscreenExit, contentDescription = "退出全屏")
        }
    }
}

private const val AutoHideMs = 3_000L

/** The card never shrinks below this (a very short window keeps a usable picture). */
private const val MinCardScale = 0.6f

/**
 * Fullscreen picture card (PiliPaw's pictureCard): while the controls show ([reveal] → 1) the
 * picture shrinks (keeping its aspect) into the band between the top and bottom bars, lifts to
 * that band's centre and rounds its corners; hidden, it is the plain full-size picture. The clip
 * morphs from the whole frame to the picture's own rect, so the letterbox (black on black) never
 * shows as a card edge.
 */
internal fun Modifier.pictureCard(
    reveal: () -> Float,
    aspect: () -> Float,
    topBand: Dp,
    bottomBand: Dp,
    minScale: Float,
): Modifier = graphicsLayer {
    val p = reveal()
    if (p <= 0.0005f) {
        scaleX = 1f
        scaleY = 1f
        translationY = 0f
        clip = false
        shape = RectangleShape
        return@graphicsLayer
    }
    val w = size.width
    val h = size.height
    val a = aspect().takeIf { it.isFinite() && it > 0f } ?: (16f / 9f)
    val pw = minOf(w, h * a)
    val ph = pw / a
    val fit = ((h - topBand.toPx() - bottomBand.toPx() - CardGap.toPx() * 2f) / ph).coerceIn(minScale, 1f)
    val s = 1f - (1f - fit) * p
    scaleX = s
    scaleY = s
    translationY = (topBand.toPx() - bottomBand.toPx()) / 2f * p
    val pc = p.coerceIn(0f, 1f)
    val insetX = (w - pw) / 2f * pc
    val insetY = (h - ph) / 2f * pc
    shape = PictureCardShape(Rect(insetX, insetY, w - insetX, h - insetY), CardRadius.toPx() * pc / s.coerceAtLeast(0.1f))
    clip = true
}

private val CardRadius = 20.dp
private val CardGap = 6.dp

private class PictureCardShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius, radius)))
}
