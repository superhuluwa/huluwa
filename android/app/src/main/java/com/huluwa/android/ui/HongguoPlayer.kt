package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
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
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream
import com.huluwa.android.hongguo.HongguoDataSourceFactory
import kotlinx.coroutines.delay

/** 沉浸式播放页：顶部返回，底部仅保留可点击的当前集数与收藏。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun HongguoPlayer(
    playback: HongguoPlayback?, stream: HongguoStream?, request: Long, startPosition: Long,
    title: String, episodeNumber: Int?, episodeCount: Int, favorite: Boolean,
    loading: Boolean, error: String?,
    onBack: () -> Unit, onShowEpisodes: () -> Unit, onToggleFavorite: () -> Unit,
    onProgress: (Long, Long, Boolean) -> Unit, onEnded: () -> Unit, onError: (String) -> Unit, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    HongguoImmersiveBars()
    var controlsVisible by remember(request) { mutableStateOf(true) }
    var interaction by remember(request) { mutableIntStateOf(0) }
    var playing by remember(request) { mutableStateOf(false) }
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

    if (player != null) {
        DisposableEffect(player, lifecycle) {
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    playing = player.isPlaying
                    if (playbackState == Player.STATE_ENDED) { hongguoPosition(player, report, true); ended() }
                }
                override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
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
        LaunchedEffect(player, controlsVisible, playing, interaction) {
            if (controlsVisible && playing) {
                delay(3000)
                controlsVisible = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) key(player) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize(),
                onRelease = { view -> view.player = null },
            )
            Box(Modifier.matchParentSize().pointerInput(player) {
                detectTapGestures {
                    controlsVisible = !controlsVisible
                    interaction++
                }
            })
        }

        when {
            error != null -> Surface(
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
                color = Color.Black.copy(alpha = .8f), contentColor = Color.White,
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

        AnimatedVisibility(
            visible = controlsVisible, modifier = Modifier.align(Alignment.TopCenter),
            enter = fadeIn(), exit = fadeOut(),
        ) {
            Row(
                Modifier.fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = .78f), Color.Transparent)))
                    .statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回选集", tint = Color.White) }
                Text(title, Modifier.weight(1f), color = Color.White.copy(alpha = .9f),
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f, fill = false)
                    .background(Color.Black.copy(alpha = .38f), RoundedCornerShape(50.dp))
                    .clickable(onClick = onShowEpisodes)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    if (episodeNumber != null) "第${episodeNumber}集 · 共${episodeCount}集" else "选择分集 · 共${episodeCount}集",
                    color = Color.White.copy(alpha = .78f), style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
                Text("⌄", color = Color.White.copy(alpha = .55f), style = MaterialTheme.typography.labelMedium)
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onToggleFavorite,
                modifier = Modifier.background(Color.Black.copy(alpha = .38f), RoundedCornerShape(50.dp)),
            ) {
                Icon(
                    if (favorite) Icons.Outlined.Favorite else Icons.Outlined.FavoriteBorder,
                    if (favorite) "取消收藏" else "收藏本剧",
                    tint = if (favorite) Color(0xFFFF6B5E) else Color.White.copy(alpha = .82f),
                )
            }
        }
    }
}

/** 播放页隐藏状态栏与导航栏，退出剧集播放时恢复宿主窗口。 */
@Composable
private fun HongguoImmersiveBars() {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = remember(context) { context.hongguoActivity() }
    val window = activity?.window
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        fun apply() {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        }
        apply()
        val observer = view.viewTreeObserver
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) apply() }
        observer.addOnWindowFocusChangeListener(focus)
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
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
