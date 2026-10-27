package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.os.SystemClock
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.huluwa.android.VideoCastViewModel
import com.huluwa.android.data.BrowserVideo
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** 默认横屏播放，显式切换沉浸全屏；点击显隐整套控件，滑动或拖动进度条动态定位。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun BrowserVideoPlayer(media: BrowserVideo, onCast: () -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.browserActivity() }
    var failure by remember(media) { mutableStateOf<String?>(null) }
    var fullscreen by rememberSaveable(media.url) { mutableStateOf(false) }
    var controlsVisible by remember(media) { mutableStateOf(true) }
    var interaction by remember(media) { mutableIntStateOf(0) }
    var playing by remember(media) { mutableStateOf(false) }
    var buffering by remember(media) { mutableStateOf(true) }
    var position by remember(media) { mutableLongStateOf(0) }
    var duration by remember(media) { mutableLongStateOf(0) }
    var seekable by remember(media) { mutableStateOf(false) }
    var seeking by remember(media) { mutableStateOf(false) }
    var seekStart by remember(media) { mutableLongStateOf(0) }
    var seekTarget by remember(media) { mutableLongStateOf(0) }
    var resumeAfterSeek by remember(media) { mutableStateOf(false) }
    var dragDistance by remember(media) { mutableFloatStateOf(0f) }
    var dragSpan by remember(media) { mutableLongStateOf(0) }
    var lastSeekAt by remember(media) { mutableLongStateOf(0) }
    val player = remember(media) {
        val data = DefaultHttpDataSource.Factory().setDefaultRequestProperties(media.headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(data)).build().apply {
            setMediaItem(MediaItem.fromUri(media.url))
            if (media.positionMs > 0) seekTo(media.positionMs)
            prepare()
            playWhenReady = true
        }
    }
    fun updatePlayback() {
        position = player.currentPosition.coerceAtLeast(0)
        duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
        seekable = player.isCurrentMediaItemSeekable && duration > 0
        playing = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
    }
    fun beginSeek(): Boolean {
        updatePlayback()
        if (!seekable) return false
        seekStart = position.coerceIn(0, duration)
        seekTarget = seekStart
        resumeAfterSeek = player.playWhenReady
        dragDistance = 0f
        // 一次滑满屏对应 30 秒至 3 分钟，短视频最多覆盖整条时长。
        dragSpan = (duration / 5).coerceIn(30_000L, 180_000L).coerceAtMost(duration)
        lastSeekAt = SystemClock.uptimeMillis()
        seeking = true
        controlsVisible = true
        interaction++
        player.pause()
        return true
    }
    fun previewSeek(target: Long) {
        if (!seeking) return
        seekTarget = target.coerceIn(0, duration)
        val now = SystemClock.uptimeMillis()
        // 拖动时适度刷新画面，避免每个像素都触发一次 HLS 跳转请求。
        if (now - lastSeekAt >= 180) {
            player.seekTo(seekTarget)
            lastSeekAt = now
        }
    }
    fun finishSeek(cancelled: Boolean = false) {
        if (!seeking) return
        val target = if (cancelled) seekStart else seekTarget
        player.seekTo(target)
        position = target
        seeking = false
        if (resumeAfterSeek) player.play() else player.pause()
        controlsVisible = true
        interaction++
    }
    fun leavePlayer() {
        if (fullscreen) {
            fullscreen = false
            controlsVisible = true
            interaction++
        } else dismiss()
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                // 后台不自动恢复声音，即使进入后台时仍处于滑动手势中。
                resumeAfterSeek = false
                finishSeek(cancelled = true)
                player.pause()
            }
        }
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                updatePlayback()
                if (player.playbackState == Player.STATE_ENDED) controlsVisible = true
            }
            override fun onPlayerError(error: PlaybackException) {
                failure = "这条线路暂时无法播放，请返回网页切换线路"
                controlsVisible = true
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        player.addListener(listener)
        updatePlayback()
        onDispose { lifecycle.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    LaunchedEffect(player) {
        while (true) { updatePlayback(); delay(250) }
    }
    LaunchedEffect(controlsVisible, playing, seeking, interaction) {
        if (controlsVisible && playing && !seeking) {
            delay(3000)
            controlsVisible = false
        }
    }
    Dialog(::leavePlayer, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, activity) {
            val orientation = activity?.requestedOrientation
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                window?.let { WindowCompat.getInsetsController(it, view).show(WindowInsetsCompat.Type.systemBars()) }
                if (orientation != null) activity?.requestedOrientation = orientation
            }
        }
        DisposableEffect(window, view, fullscreen) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            fun applyBars() {
                controller?.isAppearanceLightStatusBars = false
                controller?.isAppearanceLightNavigationBars = false
                controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                if (fullscreen) controller?.hide(WindowInsetsCompat.Type.systemBars())
                else controller?.show(WindowInsetsCompat.Type.systemBars())
            }
            applyBars()
            // 投屏面板关闭后重新取得焦点时，恢复用户选定的全屏状态。
            val observer = view.viewTreeObserver
            val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) applyBars() }
            observer.addOnWindowFocusChangeListener(focus)
            onDispose { if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus) }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)
            .then(if (fullscreen) Modifier else Modifier.windowInsetsPadding(WindowInsets.safeDrawing))) {
            AndroidView(factory = { PlayerView(it).apply {
                this.player = player
                useController = false
                keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } }, modifier = Modifier.fillMaxSize())
            // 手势层位于控件下方，进度条、投屏和全屏按钮各自处理触摸。
            Box(Modifier.matchParentSize()
                .pointerInput(player) {
                    detectTapGestures(onTap = { controlsVisible = !controlsVisible; interaction++ })
                }
                .pointerInput(player) {
                    detectHorizontalDragGestures(
                        onDragStart = { beginSeek() },
                        onHorizontalDrag = { change, amount ->
                            if (seeking) {
                                change.consume()
                                dragDistance += amount
                                previewSeek((seekStart + dragDistance.toDouble() / size.width.coerceAtLeast(1) * dragSpan).roundToLong())
                            }
                        },
                        onDragEnd = { finishSeek() },
                        onDragCancel = { finishSeek(cancelled = true) },
                    )
                })
            if (buffering && !seeking && failure == null) CircularProgressIndicator(
                Modifier.align(Alignment.Center), color = Color.White)
            AnimatedVisibility(controlsVisible, Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .8f), Color.Transparent)))
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                    .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(::leavePlayer) { Icon(Icons.Outlined.Close, if (fullscreen) "退出全屏" else "关闭播放", tint = Color.White) }
                    Text(media.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({
                        resumeAfterSeek = false
                        finishSeek()
                        player.pause()
                        onCast()
                    }) { Icon(Icons.Outlined.Cast, "投屏", tint = Color.White) }
                }
            }
            AnimatedVisibility(controlsVisible, Modifier.align(Alignment.BottomCenter), enter = fadeIn(), exit = fadeOut()) {
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .85f))))
                    .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                    .padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({
                        if (player.playWhenReady) player.pause() else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            player.play()
                        }
                        controlsVisible = true
                        interaction++
                    }) { Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        if (playing) "暂停" else "播放", tint = Color.White) }
                    val displayPosition = if (seeking) seekTarget else position
                    Text(playbackTime(displayPosition), color = Color.White, style = MaterialTheme.typography.labelMedium)
                    Slider(value = if (duration > 0) (displayPosition.toDouble() / duration).toFloat().coerceIn(0f, 1f) else 0f,
                        onValueChange = { fraction ->
                            if (seeking || beginSeek()) previewSeek((fraction.toDouble() * duration).roundToLong())
                        }, onValueChangeFinished = { finishSeek() }, enabled = seekable, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                    Text(if (duration > 0) playbackTime(duration) else "--:--", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    IconButton({ fullscreen = !fullscreen; controlsVisible = true; interaction++ }) {
                        Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            if (fullscreen) "退出全屏" else "全屏播放", tint = Color.White)
                    }
                }
            }
            if (seeking) {
                Surface(Modifier.align(Alignment.Center), color = Color.Black.copy(alpha = .78f), contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium) {
                    Column(Modifier.padding(horizontal = 24.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        val delta = seekTarget - seekStart
                        Text("${if (delta >= 0) "快进" else "后退"} ${playbackTime(abs(delta))}", style = MaterialTheme.typography.titleMedium)
                        Text("${playbackTime(seekTarget)} / ${playbackTime(duration)}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            failure?.let { message -> Text(message, Modifier.align(Alignment.Center).padding(24.dp), color = Color.White) }
        }
    }
}

/** 资源列表中的电影港播放沿用同一套横屏播放器和投屏设备面板。 */
@Composable fun MovieVideoViewer(url: String, title: String, referer: String, dismiss: () -> Unit) {
    val context = LocalContext.current
    val media = remember(url, title, referer) {
        BrowserVideo(url, title, referer, WebSettings.getDefaultUserAgent(context), CookieManager.getInstance().getCookie(url).orEmpty())
    }
    val castVm: VideoCastViewModel = viewModel()
    val cast by castVm.state.collectAsStateWithLifecycle()
    var showCast by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { castVm.cancelScan() } }
    BrowserVideoPlayer(media, onCast = { showCast = true; castVm.scan() }, dismiss = dismiss)
    if (showCast) VideoCastDialog(media, cast, castVm, onStarted = {},
        dismiss = { showCast = false; castVm.cancelScan() })
}

private fun playbackTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

private fun Context.browserActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
