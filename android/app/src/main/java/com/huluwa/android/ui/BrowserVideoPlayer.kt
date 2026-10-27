package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.huluwa.android.data.BrowserVideo
import com.huluwa.android.VideoCastViewModel

/** 网页视频转到原生 HLS 播放，保留来源请求头，独立控制横屏、系统栏和投屏。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun BrowserVideoPlayer(media: BrowserVideo, onCast: () -> Unit, dismiss: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.browserActivity() }
    var failure by remember(media.url) { mutableStateOf<String?>(null) }
    val player = remember(media) {
        val data = DefaultHttpDataSource.Factory().setDefaultRequestProperties(media.headers)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(data)).build().apply {
            setMediaItem(MediaItem.fromUri(media.url))
            if (media.positionMs > 0) seekTo(media.positionMs)
            prepare()
            playWhenReady = true
        }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player.pause() }
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { failure = "这条线路暂时无法播放，请返回网页切换线路" }
        }
        lifecycle.lifecycle.addObserver(observer)
        player.addListener(listener)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
    }
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val view = LocalView.current
        DisposableEffect(view, activity) {
            val window = (view.parent as? DialogWindowProvider)?.window
            val orientation = activity?.requestedOrientation
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            onDispose {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (orientation != null) activity?.requestedOrientation = orientation
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(factory = { PlayerView(it).apply {
                this.player = player
                keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } }, modifier = Modifier.fillMaxSize())
            Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .5f)).displayCutoutPadding()
                .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(dismiss) { Icon(Icons.Outlined.Close, "退出全屏", tint = Color.White) }
                Text(media.title, Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton({ player.pause(); onCast() }) { Icon(Icons.Outlined.Cast, "投屏", tint = Color.White) }
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

private fun Context.browserActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
