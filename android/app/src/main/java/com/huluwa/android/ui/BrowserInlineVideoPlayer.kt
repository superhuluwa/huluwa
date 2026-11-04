package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.LayoutInflater
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
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.MediaItem
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.huluwa.android.R
import com.huluwa.android.data.BrowserVideo

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
    val player = remember(media) {
        val data = DefaultHttpDataSource.Factory().setDefaultRequestProperties(media.headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(data)).build().apply {
            setMediaItem(MediaItem.fromUri(media.url))
            if (media.positionMs > 0) seekTo(media.positionMs)
            playWhenReady = true
            prepare()
        }
    }
    val views = remember(player) { arrayOfNulls<PlayerView>(2) }
    val activeView = remember(player) { arrayOfNulls<PlayerView>(1) }
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
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        setFullscreenButtonClickListener { fullscreen = it }
                        setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                            if (activeView[0] === this) controlsVisible = visibility == View.VISIBLE
                        })
                        views[slot] = this
                        if (inFullscreen || !fullscreen) attach(this)
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onReset = null,
                onRelease = { view ->
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
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_USER
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

private fun Context.inlineVideoActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
