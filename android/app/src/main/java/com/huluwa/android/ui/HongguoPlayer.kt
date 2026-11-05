package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream
import com.huluwa.android.hongguo.HongguoDataSourceFactory
import kotlinx.coroutines.delay

private val HongguoPlayerBackground = Color(0xFF191F1C)
private val HongguoPlayerSurface = Color(0xFF303633)

/** 深色短剧播放页：顶部集数与倍速，底部简介、进度和选集，不显示侧边社交操作。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun HongguoPlayer(
    playback: HongguoPlayback?, stream: HongguoStream?, request: Long, startPosition: Long,
    title: String, intro: String, episodeNumber: Int?, episodeCount: Int,
    loading: Boolean, error: String?,
    onBack: () -> Unit, onShowEpisodes: () -> Unit,
    onSwipeForward: () -> Unit, onSwipeBack: () -> Unit, onFirstFrame: () -> Unit,
    onProgress: (Long, Long, Boolean) -> Unit, onEnded: () -> Unit, onError: (String) -> Unit, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val activity = remember(context) { context.hongguoActivity() }
    var fullscreen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var introExpanded by remember(title) { mutableStateOf(false) }
    var controlsVisible by remember(request) { mutableStateOf(true) }
    var interaction by remember(request) { mutableIntStateOf(0) }
    var playing by remember(request) { mutableStateOf(false) }
    var firstFrameSent by remember(request) { mutableStateOf(false) }
    var dragDistance by remember(request) { mutableFloatStateOf(0f) }
    var position by remember(request) { mutableLongStateOf(startPosition) }
    var duration by remember(request) { mutableLongStateOf(0L) }
    var seekable by remember(request) { mutableStateOf(false) }
    var seekFraction by remember(request) { mutableStateOf<Float?>(null) }
    var videoAspectRatio by remember(request, stream) {
        mutableFloatStateOf(if (stream != null && stream.width > 0 && stream.height > 0) {
            stream.width.toFloat() / stream.height
        } else 16f / 9f)
    }
    val episodeLabel = episodeNumber?.let { "第${it}集" } ?: "选择分集"
    HongguoPlayerBars(fullscreen)
    BackHandler(enabled = fullscreen) { fullscreen = false }
    DisposableEffect(activity, fullscreen) {
        val orientation = activity?.requestedOrientation
        if (fullscreen) activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            if (fullscreen && orientation != null) activity?.requestedOrientation = orientation
        }
    }
    val player = remember(request, stream?.url) {
        if (playback == null || stream == null) null else {
            val factory = HongguoDataSourceFactory(stream, playback.userAgent)
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(factory)).build().apply {
                setAudioAttributes(androidx.media3.common.AudioAttributes.DEFAULT, true)
                setHandleAudioBecomingNoisy(true)
                setMediaItem(MediaItem.Builder().setUri(stream.url).setMimeType(MimeTypes.VIDEO_MP4).build())
                if (startPosition > 0) seekTo(startPosition)
                prepare()
                playWhenReady = true
            }
        }
    }
    val report = remember(player) { onProgress }
    val ended = remember(player) { onEnded }
    val failed = remember(player) { onError }
    val progress = seekFraction ?: if (duration > 0) (position.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f
    val seek: (Float) -> Unit = {
        seekFraction = it
        controlsVisible = true
        interaction++
    }
    val finishSeek: () -> Unit = {
        seekFraction?.let { fraction ->
            position = (fraction.toDouble() * duration).toLong()
            player?.let { it.seekTo(position); hongguoPosition(it, report, true) }
        }
        seekFraction = null
    }
    val togglePlayback: () -> Unit = {
        player?.let {
            if (it.playWhenReady) it.pause() else {
                if (it.playbackState == Player.STATE_ENDED) it.seekTo(0)
                it.play()
            }
        }
        controlsVisible = true
        interaction++
    }
    val leavePlayer: () -> Unit = { if (fullscreen) fullscreen = false else onBack() }

    if (player != null) {
        DisposableEffect(player, lifecycle) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    playing = player.isPlaying
                    if (!firstFrameSent && playbackState == Player.STATE_READY) {
                        firstFrameSent = true
                        onFirstFrame()
                    }
                    if (playbackState == Player.STATE_ENDED) { hongguoPosition(player, report, true); ended() }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        videoAspectRatio = videoSize.width.toFloat() * videoSize.pixelWidthHeightRatio / videoSize.height
                    }
                }
                override fun onPlayerError(error: PlaybackException) {
                    hongguoPosition(player, report, true)
                    val reason = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }
                        .firstOrNull { it.startsWith("红果") || it.startsWith("视频") || it.startsWith("CDN") }
                    failed(reason ?: "视频播放失败，请重试")
                }
            }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) { hongguoPosition(player, report, true); player.pause() }
            }
            player.addListener(listener)
            lifecycle.lifecycle.addObserver(observer)
            onDispose {
                hongguoPosition(player, report, true)
                lifecycle.lifecycle.removeObserver(observer)
                player.removeListener(listener)
                player.release()
            }
        }
        LaunchedEffect(player, speed) { player.setPlaybackSpeed(speed) }
        LaunchedEffect(player) {
            while (true) {
                position = player.currentPosition.coerceAtLeast(0L)
                duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                seekable = player.isCurrentMediaItemSeekable
                hongguoPosition(player, report, false)
                delay(500)
            }
        }
        LaunchedEffect(player, controlsVisible, playing, interaction, seekFraction, showSpeed, showMore) {
            if (controlsVisible && playing && seekFraction == null && !showSpeed && !showMore) {
                delay(3000)
                controlsVisible = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(HongguoPlayerBackground)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(top = if (fullscreen) 0.dp else 56.dp)) {
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                if (player != null) key(player) {
                    AndroidView(
                        factory = { viewContext ->
                            PlayerView(viewContext).apply {
                                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                useController = false
                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                setShutterBackgroundColor(HongguoPlayerBackground.toArgb())
                                setBackgroundColor(HongguoPlayerBackground.toArgb())
                                keepScreenOn = true
                                this.player = player
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        onRelease = { view -> view.player = null },
                    )
                    Box(Modifier.matchParentSize()
                        .pointerInput(player) {
                            detectTapGestures {
                                controlsVisible = !controlsVisible
                                interaction++
                            }
                        }
                        .pointerInput(player) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, distance ->
                                    dragDistance += distance
                                    if (dragDistance < -40f || dragDistance > 40f) change.consume()
                                },
                                onDragEnd = {
                                    if (dragDistance < -80f) onSwipeForward()
                                    else if (dragDistance > 80f) onSwipeBack()
                                    dragDistance = 0f
                                },
                                onDragCancel = { dragDistance = 0f },
                            )
                        }
                    )
                    androidx.compose.animation.AnimatedVisibility(visible = (controlsVisible || !playing) && !loading && error == null,
                        enter = fadeIn(), exit = fadeOut()) {
                        FilledIconButton(togglePlayback, Modifier.size(64.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .35f), contentColor = Color.White)) {
                            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                                if (playing) "暂停" else "播放", Modifier.size(36.dp))
                        }
                    }
                }
                when {
                    error != null -> Surface(
                        modifier = Modifier.padding(24.dp), color = Color.Black.copy(alpha = .8f), contentColor = Color.White,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(error, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onRetry, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("重新播放") }
                        }
                    }
                    loading -> CircularProgressIndicator(color = Color.White)
                    player == null -> Text("请选择可播放的分集", color = Color.White)
                }
                if (!fullscreen && player != null && error == null) {
                    val videoHeight = minOf(maxHeight, maxWidth / videoAspectRatio)
                    FilledTonalButton(
                        onClick = { fullscreen = true; controlsVisible = true; interaction++ },
                        modifier = Modifier.offset(y = minOf(videoHeight / 2 + 32.dp, maxHeight / 2 - 24.dp)),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = .04f), contentColor = Color.White),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Outlined.Fullscreen, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("全屏观看", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (!fullscreen) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClick = onShowEpisodes),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text(title, Modifier.weight(1f, fill = false), color = Color.White,
                            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 4.dp).size(22.dp), tint = Color.White)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        Text(if (intro.isBlank()) "$episodeLabel · 共${episodeCount}集" else "$episodeLabel | $intro",
                            Modifier.weight(1f).padding(vertical = 12.dp), color = Color.White.copy(alpha = .72f),
                            style = MaterialTheme.typography.bodyMedium, maxLines = if (introExpanded) 4 else 1, overflow = TextOverflow.Ellipsis)
                        if (intro.isNotBlank()) TextButton({ introExpanded = !introExpanded },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                            Text(if (introExpanded) "收起" else "展开")
                        }
                    }
                    HongguoSeekBar(progress, seekable, seek, finishSeek)
                    Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(onClick = onShowEpisodes, modifier = Modifier.weight(1f), color = HongguoPlayerSurface,
                            contentColor = Color.White, shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(horizontal = 16.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("选集 · 全${episodeCount}集", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                Icon(Icons.Outlined.KeyboardArrowUp, null)
                            }
                        }
                        IconButton({ fullscreen = true; controlsVisible = true; interaction++ }) {
                            Icon(Icons.Outlined.Fullscreen, "全屏观看", Modifier.size(28.dp), tint = Color.White)
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = !fullscreen || controlsVisible, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth()
                .background(if (fullscreen) Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent))
                    else Brush.verticalGradient(listOf(HongguoPlayerBackground, HongguoPlayerBackground)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(leavePlayer) { Icon(Icons.Outlined.ChevronLeft, if (fullscreen) "退出全屏" else "返回", Modifier.size(32.dp), tint = Color.White) }
                Text(episodeLabel, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold, maxLines = 1)
                Box {
                    TextButton({ showSpeed = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Outlined.Timer, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${speed}x", style = MaterialTheme.typography.titleMedium)
                    }
                    DropdownMenu(expanded = showSpeed, onDismissRequest = { showSpeed = false }, containerColor = HongguoPlayerSurface) {
                        listOf(.5f, .75f, 1f, 1.25f, 1.5f, 2f).forEach { value ->
                            DropdownMenuItem(text = { Text("${value}x", color = Color.White, fontWeight = if (speed == value) FontWeight.Bold else FontWeight.Normal) },
                                onClick = { speed = value; showSpeed = false; interaction++ })
                        }
                    }
                }
                Box {
                    IconButton({ showMore = true }) { Icon(Icons.Outlined.MoreVert, "更多播放操作", tint = Color.White) }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }, containerColor = HongguoPlayerSurface) {
                        DropdownMenuItem(text = { Text("选集", color = Color.White) }, onClick = { showMore = false; onShowEpisodes() })
                        DropdownMenuItem(text = { Text("重新加载", color = Color.White) }, onClick = { showMore = false; onRetry() })
                    }
                }
            }
        }
        if (fullscreen) AnimatedVisibility(visible = controlsVisible, modifier = Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
            Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .75f))))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)).padding(horizontal = 16.dp)) {
                HongguoSeekBar(progress, seekable, seek, finishSeek)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(togglePlayback) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "暂停" else "播放", tint = Color.White) }
                    val displayPosition = seekFraction?.let { (it.toDouble() * duration).toLong() } ?: position
                    val elapsedSeconds = displayPosition / 1000
                    val totalSeconds = duration / 1000
                    Text("%02d:%02d / %02d:%02d".format(elapsedSeconds / 60, elapsedSeconds % 60, totalSeconds / 60, totalSeconds % 60),
                        color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.weight(1f))
                    TextButton(onShowEpisodes, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("选集") }
                    IconButton({ fullscreen = false }) { Icon(Icons.Outlined.FullscreenExit, "退出全屏", tint = Color.White) }
                }
            }
        }
    }
}

/** 保留完整触控高度，视觉上使用细线与圆点。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HongguoSeekBar(progress: Float, enabled: Boolean, onSeek: (Float) -> Unit, onSeekFinished: () -> Unit) {
    Slider(value = progress, onValueChange = onSeek, onValueChangeFinished = onSeekFinished, enabled = enabled,
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "播放进度" },
        thumb = { Box(Modifier.size(5.dp).background(Color.White, CircleShape)) },
        track = {
            Canvas(Modifier.fillMaxWidth().height(2.dp)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(Color.White.copy(alpha = .2f), cornerRadius = radius)
                drawRoundRect(Color.White.copy(alpha = .65f), size = Size(size.width * progress, size.height), cornerRadius = radius)
            }
        },
    )
}

/** 竖屏显示浅色系统栏图标，全屏隐藏系统栏，离开播放页时恢复宿主设置。 */
@Composable private fun HongguoPlayerBars(fullscreen: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.hongguoActivity() }
    val window = activity?.window
    DisposableEffect(window, view, fullscreen) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        val previousLightStatus = controller?.isAppearanceLightStatusBars
        val previousLightNavigation = controller?.isAppearanceLightNavigationBars
        fun apply() {
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars())
            else controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        apply()
        val observer = view.viewTreeObserver
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) apply() }
        observer.addOnWindowFocusChangeListener(focus)
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
            if (previousLightStatus != null) controller?.isAppearanceLightStatusBars = previousLightStatus
            if (previousLightNavigation != null) controller?.isAppearanceLightNavigationBars = previousLightNavigation
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun hongguoPosition(player: ExoPlayer, callback: (Long, Long, Boolean) -> Unit, force: Boolean) {
    callback(player.currentPosition.coerceAtLeast(0), player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L, force)
}

private fun Context.hongguoActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
