package com.huluwa.android.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream
import com.huluwa.android.hongguo.HongguoDataSourceFactory
import kotlinx.coroutines.delay

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun HongguoPlayer(
    playback: HongguoPlayback?, stream: HongguoStream?, request: Long, startPosition: Long,
    loading: Boolean, error: String?,
    onProgress: (Long, Long, Boolean) -> Unit, onEnded: () -> Unit, onError: (String) -> Unit, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    // 此组件在换集的加载期间也保留，自动连播和切换清晰度不会退出全屏。
    var fullscreen by remember { mutableStateOf(false) }
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
    val activeView = remember(player) { arrayOfNulls<PlayerView>(1) }
    // 回调与播放器绑定，旧实例释放时不能把上一集的播放位置记到新一集。
    val report = remember(player) { onProgress }
    val ended = remember(player) { onEnded }
    val failed = remember(player) { onError }
    if (player != null) {
        DisposableEffect(player, lifecycle) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) { hongguoPosition(player, report, true); ended() }
                }
                override fun onPlayerError(error: PlaybackException) {
                    hongguoPosition(player, report, true)
                    val reason = generateSequence<Throwable>(error) { it.cause }.mapNotNull { it.message }
                        .firstOrNull { it.startsWith("红果") || it.startsWith("视频") || it.startsWith("CDN") }
                    failed(reason ?: "视频播放失败，请重试或切换清晰度")
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
                activeView[0]?.player = null
                activeView[0] = null
                player.release()
            }
        }
        LaunchedEffect(player) { while (true) { delay(1000); hongguoPosition(player, report, false) } }
    }
    val surface: @Composable () -> Unit = {
        HongguoVideoSurface(player, activeView, fullscreen, { fullscreen = !fullscreen }, loading, error, onRetry)
    }
    if (!fullscreen) surface()
    else Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = androidx.compose.ui.platform.LocalView.current
        DisposableEffect(view) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
        }
        surface()
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun hongguoPosition(player: ExoPlayer, callback: (Long, Long, Boolean) -> Unit, force: Boolean) {
    callback(player.currentPosition.coerceAtLeast(0), player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L, force)
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun HongguoVideoSurface(
    player: ExoPlayer?, activeView: Array<PlayerView?>, fullscreen: Boolean, toggleFullscreen: () -> Unit,
    loading: Boolean, error: String?, onRetry: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (player != null) key(player) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = true
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        setKeepContentOnPlayerReset(true)
                        keepScreenOn = true
                        PlayerView.switchTargetView(player, activeView[0], this)
                        activeView[0] = this
                    }
                }, modifier = Modifier.fillMaxSize(),
                onRelease = { view ->
                    if (activeView[0] === view) {
                        PlayerView.switchTargetView(player, view, null)
                        activeView[0] = null
                    } else view.player = null
                },
            )
        }
        when {
            error != null -> Column(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .75f)).padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(error, color = Color.White)
                TextButton(onRetry) { Text("重新播放", color = Color.White) }
            }
            loading -> CircularProgressIndicator(color = Color.White)
            player == null -> Text("请选择可播放的分集", color = Color.White)
        }
        IconButton(toggleFullscreen, Modifier.align(Alignment.TopEnd).padding(4.dp)) {
            Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                if (fullscreen) "退出全屏" else "全屏播放", tint = Color.White)
        }
    }
}
