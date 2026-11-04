package com.huluwa.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.DefaultTimeBar
import androidx.media3.ui.PlayerView
import androidx.media3.ui.TimeBar
import com.huluwa.android.R
import com.huluwa.android.data.BrowserVideo
import kotlinx.coroutines.delay
import kotlin.math.abs
import java.util.Locale

private const val VIDEO_SWIPE_SEEK_MS = 5_000L

/** 网页原位播放与全屏共用一个播放实例，切换视图时保留缓冲、进度及暂停状态。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun BrowserInlineVideoPlayer(
    media: BrowserVideo,
    fullscreenRequest: Int,
    suspended: Boolean,
    onCast: (BrowserVideo) -> Unit,
    onFailure: (Long) -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val latestOnCast by rememberUpdatedState(onCast)
    val latestOnFailure by rememberUpdatedState(onFailure)
    val latestSuspended by rememberUpdatedState(suspended)
    var fullscreen by remember(media) { mutableStateOf(fullscreenRequest > 0) }
    var controlsVisible by remember(media) { mutableStateOf(true) }
    var swipeFeedback by remember(media) { mutableStateOf<String?>(null) }
    var swipeSequence by remember(media) { mutableIntStateOf(0) }
    var scrubPosition by remember(media) { mutableStateOf<Long?>(null) }
    var scrubDuration by remember(media) { mutableLongStateOf(0) }
    LaunchedEffect(swipeSequence) {
        if (swipeSequence > 0) {
            delay(800)
            swipeFeedback = null
        }
    }
    val player = remember(media) {
        val data = DefaultHttpDataSource.Factory().setDefaultRequestProperties(media.headers)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(data))
            .setSeekBackIncrementMs(VIDEO_SWIPE_SEEK_MS)
            .setSeekForwardIncrementMs(VIDEO_SWIPE_SEEK_MS)
            .build().apply {
            setMediaItem(MediaItem.fromUri(media.url))
            if (media.positionMs > 0) seekTo(media.positionMs)
            playWhenReady = true
            prepare()
        }
    }
    val views = remember(player) { arrayOfNulls<PlayerView>(2) }
    val activeView = remember(player) { arrayOfNulls<PlayerView>(1) }
    val scrubView = remember(player) { arrayOfNulls<PlayerView>(1) }
    val scrubCleanups = remember(player) { mutableMapOf<PlayerView, () -> Unit>() }
    LaunchedEffect(fullscreenRequest) { if (fullscreenRequest > 0) fullscreen = true }
    LaunchedEffect(suspended) { if (suspended) player.pause() }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) player.pause()
        }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && latestSuspended) player.pause()
            }
            override fun onPlayerError(error: PlaybackException) {
                latestOnFailure(player.currentPosition.coerceAtLeast(0))
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            player.removeListener(listener)
            activeView[0]?.player = null
            activeView[0] = null
            player.release()
        }
    }
    fun attach(view: PlayerView) {
        if (activeView[0] !== view) {
            PlayerView.switchTargetView(player, activeView[0], view)
            activeView[0] = view
            view.showController()
            view.showProgressScrubber()
            controlsVisible = true
        }
        view.setFullscreenButtonState(fullscreen)
    }
    @Composable fun surface(inFullscreen: Boolean) {
        val slot = if (inFullscreen) 1 else 0
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { viewContext ->
                    (LayoutInflater.from(viewContext).inflate(R.layout.browser_inline_video, null, false) as PlayerView).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        controllerShowTimeoutMs = 3000
                        keepScreenOn = true
                        scrubCleanups[this] = trackProgressScrubbing { position ->
                            if (position != null) {
                                scrubView[0] = this
                                scrubPosition = position.coerceAtLeast(0)
                                scrubDuration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0
                                swipeFeedback = null
                            } else if (scrubView[0] === this) {
                                scrubView[0] = null
                                scrubPosition = null
                            }
                        }
                        enableFiveSecondSwipe(player, allowed = { !latestSuspended }) { forward ->
                            swipeFeedback = if (forward) "快进 5 秒" else "后退 5 秒"
                            swipeSequence++
                        }
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setFullscreenButtonClickListener { fullscreen = it }
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            if (visibility == View.VISIBLE) showProgressScrubber()
                            if (activeView[0] === this) controlsVisible = visibility == View.VISIBLE
                        })
                        var compactLayout = false
                        addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                            val compact = findViewById<View>(androidx.media3.ui.R.id.exo_minimal_controls)?.visibility == View.VISIBLE
                            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop || compact != compactLayout) {
                                compactLayout = compact
                                showProgressScrubber()
                            }
                        }
                        views[slot] = this
                        if (inFullscreen || !fullscreen) attach(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onReset = null,
                onRelease = { view ->
                    scrubCleanups.remove(view)?.invoke()
                    // 全屏窗口关闭时立即把同一个播放实例接回网页视图。
                    if (activeView[0] === view) {
                        val replacement = if (inFullscreen) views[0] else null
                        PlayerView.switchTargetView(player, view, replacement)
                        activeView[0] = replacement
                    } else view.player = null
                    views[slot] = null
                },
                update = { view -> if (inFullscreen || !fullscreen) attach(view) },
            )
            val feedback = scrubPosition?.let { position ->
                "${browserVideoTime(position)} / ${if (scrubDuration > 0) browserVideoTime(scrubDuration) else "--:--"}"
            } ?: swipeFeedback
            feedback?.let { message ->
                Surface(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Black.copy(alpha = .72f),
                    contentColor = Color.White,
                    shape = MaterialTheme.shapes.medium,
                ) { Text(message, Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) }
            }
            if (controlsVisible) IconButton(
                onClick = {
                    player.pause()
                    latestOnCast(media.copy(positionMs = player.currentPosition.coerceAtLeast(0)))
                },
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(40.dp),
            ) { Icon(Icons.Outlined.Cast, "投屏", tint = Color.White) }
        }
    }
    surface(inFullscreen = false)
    if (fullscreen) Dialog(
        onDismissRequest = { fullscreen = false },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        val activity = remember(context) { context.inlineVideoActivity() }
        DisposableEffect(window, activity) {
            val orientation = activity?.requestedOrientation
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // 显式进入横屏，不受系统竖屏锁定影响；退出时恢复进入全屏前的方向设置。
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.let {
                WindowCompat.getInsetsController(it, view).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                if (orientation != null) activity?.requestedOrientation = orientation
            }
        }
        surface(inFullscreen = true)
    }
}




/** 保留原生定位行为，只补充拖动时间提示，并临时移除覆盖整幅画面的暗色背景。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerView.trackProgressScrubbing(onPosition: (Long?) -> Unit): () -> Unit {
    val timeBar = findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress) ?: return {}
    val controlsBackground = findViewById<View>(androidx.media3.ui.R.id.exo_controls_background)
    val originalBackground = controlsBackground?.background
    val listener = object : TimeBar.OnScrubListener {
        override fun onScrubStart(timeBar: TimeBar, position: Long) {
            // 替换背景而非 alpha，控件自己的淡入淡出动画也不会让拖动中的画面变暗。
            controlsBackground?.background = ColorDrawable(android.graphics.Color.TRANSPARENT)
            onPosition(position)
        }
        override fun onScrubMove(timeBar: TimeBar, position: Long) {
            onPosition(position)
        }
        override fun onScrubStop(timeBar: TimeBar, position: Long, canceled: Boolean) {
            controlsBackground?.background = originalBackground
            onPosition(null)
        }
    }
    timeBar.addListener(listener)
    return {
        timeBar.removeListener(listener)
        controlsBackground?.background = originalBackground
        onPosition(null)
    }
}

private fun browserVideoTime(positionMs: Long): String {
    val seconds = positionMs.coerceAtLeast(0) / 1000
    return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
    else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
}

/** Media3 的紧凑布局默认隐藏圆点；控件展开时恢复，并给全屏按钮留出拖动空间。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerView.showProgressScrubber() {
    // 在控件自己的尺寸适配完成后执行，避免紧凑模式再次把圆点隐藏。
    post {
        if (!isAttachedToWindow || !isControllerFullyVisible) return@post
        val timeBar = findViewById<DefaultTimeBar>(androidx.media3.ui.R.id.exo_progress) ?: return@post
        val compactControls = findViewById<View>(androidx.media3.ui.R.id.exo_minimal_controls)
        val endPadding = if (compactControls?.visibility == View.VISIBLE) {
            maxOf(compactControls.width + (8 * resources.displayMetrics.density).toInt(),
                (48 * resources.displayMetrics.density).toInt())
        } else 0
        timeBar.setPaddingRelative(0, 0, endPadding, 0)
        timeBar.showScrubber()
        // 恢复圆点后重新计算两端留白，拖到片头和片尾时也能完整显示圆点。
        timeBar.requestLayout()
    }
}

/** 只接收 PlayerView 自身的画面触摸，进度条与控制按钮继续消费自己的手势。 */
@SuppressLint("ClickableViewAccessibility")
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun PlayerView.enableFiveSecondSwipe(
    player: ExoPlayer,
    allowed: () -> Boolean,
    onSeek: (forward: Boolean) -> Unit,
) {
    val threshold = maxOf(48f * resources.displayMetrics.density,
        ViewConfiguration.get(context).scaledTouchSlop * 2f)
    var downX = 0f
    var downY = 0f
    var tracking = false
    var handled = false
    var ignored = false
    fun seekIfSwiped(event: MotionEvent): Boolean {
        if (handled) return true
        if (!tracking || ignored) return false
        val dx = event.x - downX
        val dy = event.y - downY
        if (abs(dy) >= threshold && abs(dy) > abs(dx)) {
            ignored = true
            return false
        }
        if (abs(dx) < threshold || abs(dx) <= abs(dy) * 1.5f) return false
        val duration = player.duration
        if (!allowed() || !player.isCurrentMediaItemSeekable || duration == C.TIME_UNSET || duration <= 0 ||
            !player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            ignored = true
            return false
        }
        val forward = dx > 0
        val offset = if (forward) VIDEO_SWIPE_SEEK_MS else -VIDEO_SWIPE_SEEK_MS
        val target = (player.currentPosition.coerceAtLeast(0) + offset).coerceIn(0, duration)
        // seekTo 不改变 playWhenReady；一次手势只执行一次固定步长定位。
        player.seekTo(target)
        handled = true
        parent?.requestDisallowInterceptTouchEvent(true)
        val cancel = MotionEvent.obtain(event)
        cancel.action = MotionEvent.ACTION_CANCEL
        onTouchEvent(cancel)
        cancel.recycle()
        showController()
        onSeek(forward)
        return true
    }
    setOnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                tracking = true
                handled = false
                ignored = false
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                ignored = true
                handled
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount > 1) ignored = true
                seekIfSwiped(event)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val consumed = handled || (event.actionMasked == MotionEvent.ACTION_UP && seekIfSwiped(event))
                tracking = false
                handled = false
                ignored = false
                parent?.requestDisallowInterceptTouchEvent(false)
                consumed
            }
            else -> handled
        }
    }
}

private fun Context.inlineVideoActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
