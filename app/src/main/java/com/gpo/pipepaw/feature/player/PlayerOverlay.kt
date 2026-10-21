package com.gpo.pipepaw.feature.player

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.core.text.HtmlCompat
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.gpo.pipepaw.core.extractor.ChannelItem
import com.gpo.pipepaw.core.extractor.toVideo
import com.gpo.pipepaw.core.settings.AppSettings
import com.gpo.pipepaw.core.util.Fmt
import com.gpo.pipepaw.feature.subscriptions.Subscriptions
import com.gpo.pipepaw.navigation.LocalNavigator
import com.gpo.pipepaw.navigation.Route
import com.gpo.pipepaw.ui.components.Avatar
import com.gpo.pipepaw.ui.components.NetImage
import com.gpo.pipepaw.ui.components.ValueLabel
import com.gpo.pipepaw.ui.components.VideoRow
import com.gpo.pipepaw.ui.shell.floatingBarInset
import com.gpo.pipepaw.ui.theme.ContinuousRoundedCornerShape
import com.gpo.pipepaw.ui.theme.CoverTheme
import com.gpo.pipepaw.ui.theme.YoinMotion
import com.gpo.yoin.symbols.PiliSymbols
import com.gpo.yoin.symbols.YoinSymbols
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

private val MiniHeight = 64.dp
private val MiniMargin = 12.dp
private val MiniMaxWidth = 560.dp
private val RelatedPaneWidth = 400.dp

/** How far a predictive-back gesture previews the collapse before the finger lets go. */
private const val BackPreview = 0.3f

/** Bottom space pages leave for the mini bar while a video is open. */
@Composable
fun miniPlayerInset(): Dp = if (Player.session != null) MiniHeight + MiniMargin else 0.dp

/**
 * The player as a shell overlay (Yoin Now Playing, predictive-back class ShellOverlayDown):
 * one progress p runs mini bar (0) ⇄ full player (1). The tap, the drag, the collapse
 * button and system back all drive that one value; the picture is the same view the whole
 * way, so it flies between the bar's corner and the top of the page with no swap.
 *
 * Content stays pure: no control ever sits on the picture. Play, the seek bar and times live
 * under it in the page's colours (PiliPaw 0.3.3).
 *
 * Wide windows (≥ 840dp wide, ≥ 480dp tall): the picture and the details on the left, related
 * videos in a 400dp column on the right. The picture never shrinks to make room.
 */
@Composable
fun PlayerOverlayHost() {
    val session = Player.session ?: return
    val nav = LocalNavigator.current
    val onShell = nav.backStack.lastOrNull() == Route.Main
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }
    val spatial = YoinMotion.defaultSpatialSpec<Float>()
    val expanded = Player.expanded
    val feedback = remember { GestureFeedback() }
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(expanded) {
        if (!expanded) Player.fullscreen = false
        progress.animateTo(if (expanded) 1f else 0f, spatial)
    }

    // Open at all (expanded, or left part-way by a drag): a collapsed bar never blocks the
    // page's own back.
    val open by remember { derivedStateOf { progress.value > 0.001f } }
    PredictiveBackHandler(enabled = expanded || open) { events ->
        val from = progress.value
        try {
            events.collect { e -> progress.snapTo(from - BackPreview * e.progress) }
            // Commit (also the 3-button / no-gesture path: an empty flow lands here at once).
            if (Player.expanded) Player.expanded = false
            else scope.launch { progress.animateTo(0f, spatial) }
        } catch (e: CancellationException) {
            scope.launch { progress.animateTo(from, spatial) }
            throw e
        }
    }

    // 后台播放 off: leaving the app pauses, coming back resumes what was playing.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        var resumeOnReturn = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (!AppSettings.backgroundPlay.value && Player.isPlaying) {
                    Player.exo.pause()
                    resumeOnReturn = true
                }
                Lifecycle.Event.ON_START -> if (resumeOnReturn) {
                    resumeOnReturn = false
                    Player.exo.play()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val view = LocalView.current
    DisposableEffect(Player.isPlaying) {
        view.keepScreenOn = Player.isPlaying
        onDispose { view.keepScreenOn = false }
    }

    CoverTheme(session.thumbnail) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val density = LocalDensity.current
            val w = maxWidth
            val h = maxHeight
            val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val miniBottom = if (onShell) navBottom + floatingBarInset() else navBottom + MiniMargin
            val twoPane = w >= 840.dp && h >= 480.dp

            // Rects (dp): the container, and the picture inside it, at both ends.
            val miniW = minOf(w - MiniMargin * 2, MiniMaxWidth)
            val miniX = (w - miniW) / 2
            val miniY = h - miniBottom - MiniHeight
            val leftW = if (twoPane) w - RelatedPaneWidth else w
            var fullVideoW = if (twoPane) leftW - 48.dp else w
            var fullVideoH = fullVideoW * 9f / 16f
            val maxVideoH = (h - statusTop) * 0.62f
            if (fullVideoH > maxVideoH) {
                fullVideoH = maxVideoH
                fullVideoW = fullVideoH * 16f / 9f
            }
            val fullVideoX = if (twoPane) 24.dp else (w - fullVideoW) / 2
            val fullVideoY = statusTop + if (twoPane) 16.dp else 0.dp
            val miniVideoW = MiniHeight * 16f / 9f

            val p = progress.value
            val containerX = lerp(miniX, 0.dp, p)
            val containerY = lerp(miniY, 0.dp, p)
            val containerW = lerp(miniW, w, p)
            val containerH = lerp(MiniHeight, h, p)
            val videoX = lerp(0.dp, fullVideoX, p)
            val videoY = lerp(0.dp, fullVideoY, p)
            val videoW = lerp(miniVideoW, fullVideoW, p)
            val videoH = lerp(MiniHeight, fullVideoH, p)
            val corner = lerp(16.dp, 0.dp, p)
            val videoCorner = lerp(12.dp, if (twoPane) 16.dp else 0.dp, p)
            val rangePx = with(density) { (miniY).toPx() }.coerceAtLeast(1f)

            val drag = rememberDraggableState { delta ->
                scope.launch { progress.snapTo((progress.value - delta / rangePx).coerceIn(0f, 1f)) }
            }
            // The mini content fades out by p = 0.35; it stays composed while a drag that began
            // on it runs, or its draggable would be disposed mid-drag and never settle.
            var dragging by remember { mutableStateOf(false) }
            val settle: (Float) -> Unit = { velocity ->
                dragging = false
                val target = when {
                    velocity > FlingVelocity -> false
                    velocity < -FlingVelocity -> true
                    else -> progress.value > 0.5f
                }
                if (target == Player.expanded) {
                    scope.launch { progress.animateTo(if (target) 1f else 0f, spatial, initialVelocity = -velocity / rangePx) }
                } else {
                    Player.expanded = target
                }
            }

            // Behind the full player: the page dims a little as it rises.
            if (p > 0.001f) {
                Box(Modifier.fillMaxSize().graphicsLayer { alpha = p }.background(Color.Black.copy(alpha = 0.32f)))
            }

            val scheme = MaterialTheme.colorScheme
            Box(
                Modifier
                    .offset { IntOffset(containerX.roundToPx(), containerY.roundToPx()) }
                    .size(containerW, containerH)
                    .clip(ContinuousRoundedCornerShape(corner))
                    .background(lerp(scheme.surfaceContainerHigh, scheme.surface, p)),
            ) {
                // Full content first (under the picture in z-order).
                if (p > 0.01f) {
                    val alpha = ((p - 0.5f) / 0.5f).coerceIn(0f, 1f)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                this.alpha = alpha
                                translationY = (1f - p) * 48.dp.toPx()
                            },
                    ) {
                        FullContent(
                            session = session,
                            twoPane = twoPane,
                            topUnderVideo = fullVideoY + fullVideoH,
                            leftWidth = leftW,
                            statusTop = statusTop,
                            navBottom = navBottom,
                            onCollapse = { Player.expanded = false },
                        )
                    }
                }

                // Mini bar content, right of the picture.
                val miniAlpha = (1f - p / 0.35f).coerceIn(0f, 1f)
                if (miniAlpha > 0f || dragging) {
                    MiniContent(
                        session = session,
                        modifier = Modifier
                            .padding(start = miniVideoW)
                            .fillMaxWidth()
                            .height(MiniHeight)
                            .graphicsLayer { alpha = miniAlpha }
                            .clickable { Player.expanded = true }
                            .draggable(drag, Orientation.Vertical, onDragStarted = { dragging = true }, onDragStopped = { settle(it) }),
                    )
                }

                // The picture: one view, mini corner ⇄ page top.
                Box(
                    Modifier
                        .offset { IntOffset(videoX.roundToPx(), videoY.roundToPx()) }
                        .size(videoW, videoH)
                        .clip(ContinuousRoundedCornerShape(videoCorner))
                        .background(Color.Black)
                        .clickable(enabled = p < 0.5f) { Player.expanded = true }
                        .draggable(drag, Orientation.Vertical, onDragStarted = { dragging = true }, onDragStopped = { settle(it) })
                        // Open: double tap skips, a hold speeds up (a tap does nothing, nothing to show).
                        .then(if (expanded) Modifier.pictureGestures(feedback, haptics) {} else Modifier),
                ) {
                    Picture(session)
                    if (expanded) {
                        DoubleTapSeekLayer(feedback.skip, big = false)
                        HoldSpeedPill(feedback, big = false, modifier = Modifier.align(Alignment.Center))
                    }
                }
            }
        }
        // Over everything, the overlay included: it takes the video surface while it is up.
        if (Player.fullscreen) FullscreenPlayer(session)
    }
}

private const val FlingVelocity = 1200f

@Composable
private fun BoxScope.Picture(session: Player.Session) {
    // Fullscreen owns the one video surface while it is up.
    if (session.info != null && session.error == null && !Player.fullscreen) {
        ContentFrame(
            player = Player.exo,
            modifier = Modifier.fillMaxSize(),
            // A TextureView: it scales with its frame through the whole fly, a SurfaceView would lag.
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = {},
        )
    }
    val waiting = session.info == null || (Player.isBuffering && Player.durationMs == 0L)
    if (waiting && session.error == null) {
        NetImage(session.thumbnail, Modifier.fillMaxSize(), shape = ContinuousRoundedCornerShape(0.dp), thumbWidth = null)
        LoadingIndicator(modifier = Modifier.align(Alignment.Center))
    }
    session.error?.let { message ->
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(message, color = Color.White, style = MaterialTheme.typography.labelLarge)
            FilledTonalButton(onClick = Player::retry) { Text("重试") }
        }
    }
}

@Composable
private fun MiniContent(session: Player.Session, modifier: Modifier) {
    Row(modifier.padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(session.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                session.channelName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = Player::toggle, shapes = IconButtonDefaults.shapes()) {
            Icon(if (Player.isPlaying) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled, if (Player.isPlaying) "暂停" else "播放")
        }
        IconButton(onClick = Player::close, shapes = IconButtonDefaults.shapes()) {
            Icon(YoinSymbols.Close, "关闭")
        }
    }
}

@Composable
private fun FullContent(
    session: Player.Session,
    twoPane: Boolean,
    topUnderVideo: Dp,
    leftWidth: Dp,
    statusTop: Dp,
    navBottom: Dp,
    onCollapse: () -> Unit,
) {
    val info = session.info
    val related = remember(info) {
        info?.relatedItems.orEmpty().filterIsInstance<StreamInfoItem>().map { it.toVideo() }
    }
    if (twoPane) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .width(leftWidth)
                    .fillMaxHeight()
                    .padding(top = topUnderVideo)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = navBottom + 24.dp),
            ) {
                Controls(onCollapse)
                Details(session, info)
            }
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxHeight().width(RelatedPaneWidth),
                contentPadding = PaddingValues(top = statusTop + 16.dp, bottom = navBottom + 24.dp),
            ) {
                items(related, key = { it.url }) { VideoRow(it, thumbWidth = 144.dp) }
            }
        }
    } else {
        Column(Modifier.fillMaxSize().padding(top = topUnderVideo)) {
            Controls(onCollapse, Modifier.padding(horizontal = 16.dp))
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = navBottom + 24.dp),
            ) {
                item(key = "details") { Details(session, info, Modifier.padding(horizontal = 16.dp)) }
                items(related, key = { it.url }) { VideoRow(it) }
            }
        }
    }
}

/** Under the picture, in the page's colours: collapse, the seek bar, the times, fullscreen and play. */
@Composable
private fun Controls(onCollapse: () -> Unit, modifier: Modifier = Modifier) {
    val seek = rememberSeekState()
    Column(modifier.fillMaxWidth().padding(top = 8.dp)) {
        Slider(
            value = seek.fraction,
            onValueChange = seek::onScrub,
            onValueChangeFinished = seek::onScrubEnd,
            enabled = seek.duration > 0,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCollapse, shapes = IconButtonDefaults.shapes()) {
                Icon(YoinSymbols.ChevronDown, "收起")
            }
            Spacer(Modifier.weight(1f))
            TimeLabels(seek, MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { Player.fullscreen = true }, shapes = IconButtonDefaults.shapes()) {
                Icon(PiliSymbols.Fullscreen, "全屏")
            }
            FilledTonalIconButton(onClick = Player::toggle, shapes = IconButtonDefaults.shapes()) {
                Icon(if (Player.isPlaying) YoinSymbols.PauseFilled else YoinSymbols.PlayFilled, if (Player.isPlaying) "暂停" else "播放")
            }
        }
    }
}

@Composable
private fun Details(session: Player.Session, info: StreamInfo?, modifier: Modifier = Modifier) {
    val nav = LocalNavigator.current
    Column(modifier.padding(top = 8.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(session.title, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (info != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (info.viewCount >= 0) ValueLabel(Fmt.count(info.viewCount), "次观看")
                if (info.likeCount >= 0) ValueLabel(Fmt.count(info.likeCount), "赞")
                Fmt.ago(info.uploadDate?.offsetDateTime()?.toInstant()?.toEpochMilli() ?: 0L).takeIf { it.isNotEmpty() }?.let {
                    Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val channel = ChannelItem(
                url = info.uploaderUrl.orEmpty(),
                name = info.uploaderName.orEmpty(),
                avatar = info.uploaderAvatarUrl.orEmpty(),
                subscribers = info.uploaderSubscriberCount,
                videos = -1,
                verified = false,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(ContinuousRoundedCornerShape(12.dp))
                        .clickable(enabled = channel.url.isNotEmpty()) {
                            Player.expanded = false
                            nav.push(Route.Channel(channel.url, channel.name, channel.avatar))
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Avatar(channel.avatar, size = 40.dp)
                    Column {
                        Text(channel.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (channel.subscribers >= 0) ValueLabel(Fmt.count(channel.subscribers), "订阅者")
                    }
                }
                SubscribeButton(channel)
            }
            Description(info)
        }
    }
}

@Composable
fun SubscribeButton(channel: ChannelItem) {
    val subs by Subscriptions.channels.collectAsState()
    val subscribed = subs.any { it.url == channel.url }
    if (subscribed) {
        FilledTonalButton(onClick = { Subscriptions.toggle(channel) }) { Text("已订阅") }
    } else {
        Button(onClick = { Subscriptions.toggle(channel) }, enabled = channel.url.isNotEmpty()) { Text("订阅") }
    }
}

@Composable
private fun Description(info: StreamInfo) {
    val text = remember(info) {
        val d = info.description ?: return@remember ""
        when (d.type) {
            org.schabi.newpipe.extractor.stream.Description.HTML ->
                HtmlCompat.fromHtml(d.content, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
            else -> d.content.orEmpty().trim()
        }
    }
    if (text.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        maxLines = if (open) Int.MAX_VALUE else 3,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { open = !open }
            .animateContentSize(YoinMotion.defaultSpatialSpec())
            .padding(16.dp),
    )
}
