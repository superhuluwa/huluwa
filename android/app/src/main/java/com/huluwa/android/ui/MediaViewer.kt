package com.huluwa.android.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable fun MediaViewer(url: String, video: Boolean, dismiss: () -> Unit) {
    val context = LocalContext.current
    var failure by remember(url) { mutableStateOf<String?>(null) }
    val player = remember(url, video) {
        if (video && (url.startsWith("https://") || url.startsWith("http://"))) ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
        } else null
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player?.pause() }
        val listener = object : Player.Listener { override fun onPlayerError(error: PlaybackException) { failure = "视频暂时无法播放，请检查资源线路或网络" } }
        lifecycle.lifecycle.addObserver(observer); player?.addListener(listener)
        onDispose { lifecycle.lifecycle.removeObserver(observer); player?.removeListener(listener); player?.release() }
    }
    Dialog(dismiss, DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            if (video && player != null) AndroidView(factory = { PlayerView(it).apply {
                this.player = player; keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } }, modifier = Modifier.fillMaxSize())
            else if (!video) AsyncImage(url, "消息图片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
            else Text("资源地址无效", color = Color.White, modifier = Modifier.align(Alignment.Center))
            failure?.let { Text(it, color = Color.White, modifier = Modifier.align(Alignment.BottomCenter).padding(24.dp)) }
            IconButton(dismiss, Modifier.align(Alignment.TopEnd).padding(10.dp)) { Icon(Icons.Outlined.Close, "关闭预览", tint = Color.White) }
        }
    }
}
