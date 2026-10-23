package com.huluwa.android.ui

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage

/** 聊天气泡默认只展示封面，用户点击后才创建播放器并开始加载。 */
@Composable internal fun InlineVideoBubble(
    url: String, poster: String, active: Boolean, onPlay: () -> Unit, onStop: () -> Unit,
) {
    Box(Modifier.width(240.dp).aspectRatio(4f / 3f).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
        if (active) {
            InlineVideoPlayer(url, onStop)
        } else {
            Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "播放视频", onClick = onPlay), contentAlignment = Alignment.Center) {
                if (poster.isNotBlank()) AsyncImage(poster, "视频封面", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                Column(Modifier.background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(18.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(42.dp), tint = Color.White)
                    Text("点击播放", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun InlineVideoPlayer(url: String, onStop: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val stopPlayback by rememberUpdatedState(onStop)
    var failure by remember(url) { mutableStateOf<String?>(null) }
    var isPlaying by remember(url) { mutableStateOf(false) }
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
        }
    }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: PlaybackException) {
                failure = "视频暂时无法播放，链接可能已过期，请重试或重新解析"
            }
        }
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        // 此组件只在用户点击封面后挂载，首次收到视频消息时不会加载或播放视频。
        player.play()
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                player.pause()
                stopPlayback()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    Box(Modifier.fillMaxSize()) {
        AndroidView(
            factory = { PlayerView(it).apply {
                this.player = player
                useController = true
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } },
            modifier = Modifier.fillMaxSize(),
            update = { view -> view.player = player; view.keepScreenOn = isPlaying },
            onRelease = { view -> view.player = null; view.keepScreenOn = false },
        )
        failure?.let { message ->
            Column(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(message, color = Color.White, style = MaterialTheme.typography.bodySmall)
                TextButton({ failure = null; player.prepare(); player.play() }) { Text("重试", color = Color.White) }
            }
        }
    }
}
