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
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import coil3.compose.AsyncImage
import com.huluwa.android.data.HongguoEpisode
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream
import com.huluwa.android.hongguo.HongguoDataSourceFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs

private val HongguoPlayerBackground = Color(0xFF191F1C)
private val HongguoPlayerSurface = Color(0xFF303633)

/**
 * 深色短剧播放页。剧集使用整屏纵向分页，拖动时上一集/下一集会跟随手势露出，
 * 松手后吸附到整集页面并开始播放，交互与抖音的上下翻页一致。
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable internal fun HongguoPlayer(
    playback: HongguoPlayback?, stream: HongguoStream?, request: Long, startPosition: Long,
    title: String, intro: String, seriesCover: String, episodes: List<HongguoEpisode>, currentEpisode: HongguoEpisode?,
    episodeCount: Int, loading: Boolean, error: String?,
    onBack: () -> Unit, onShowEpisodes: () -> Unit, onSelectEpisode: (HongguoEpisode) -> Unit,
    onFirstFrame: () -> Unit, onProgress: (Long, Long, Boolean) -> Unit, onEnded: () -> Unit,
    onError: (String) -> Unit, onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val activity = remember(context) { context.hongguoActivity() }
    val pages = remember(episodes) { episodes.filterNot { it.locked } }
    val currentEpisodeId = currentEpisode?.id
    if (pages.isEmpty()) {
        Box(Modifier.fillMaxSize().background(HongguoPlayerBackground), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("暂无可播放的分集", color = Color.White)
                TextButton(onBack, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("返回") }
            }
        }
        return
    }

    var fullscreen by remember { mutableStateOf(false) }
    var speed by remember { mutableFloatStateOf(1f) }
    var showSpeed by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var introExpanded by remember(title) { mutableStateOf(false) }
    var controlsVisible by remember(request) { mutableStateOf(true) }
    var centerPlayVisible by remember(request) { mutableStateOf(false) }
    var interaction by remember(request) { mutableIntStateOf(0) }
    var playing by remember(request) { mutableStateOf(false) }
    var firstFrameSent by remember(request) { mutableStateOf(false) }
    var position by remember(request) { mutableLongStateOf(startPosition) }
    var duration by remember(request) { mutableLongStateOf(0L) }
    var seekable by remember(request) { mutableStateOf(false) }
    var seekFraction by remember(request) { mutableStateOf<Float?>(null) }
    var videoAspectRatio by remember(request, stream) {
        mutableFloatStateOf(if (stream != null && stream.width > 0 && stream.height > 0) {
            stream.width.toFloat() / stream.height
        } else 16f / 9f)
    }

    val initialPage = pages.indexOfFirst { it.id == currentEpisodeId }.coerceAtLeast(0)
    val pager = rememberPagerState(initialPage = initialPage, pageCount = { pages.size })
    val latestSelectedEpisodeId by rememberUpdatedState(currentEpisodeId)
    val selectEpisode by rememberUpdatedState(onSelectEpisode)
    val episodeLabel = currentEpisode?.number?.let { number -> "第${number}集" } ?: "选择分集"

    LaunchedEffect(currentEpisodeId, pages) {
        val target = pages.indexOfFirst { it.id == currentEpisodeId }
        if (target >= 0 && target != pager.currentPage) {
            if (abs(target - pager.currentPage) == 1) pager.animateScrollToPage(target)
            else pager.scrollToPage(target)
        }
    }
    LaunchedEffect(pager, pages) {
        snapshotFlow { pager.currentPage to pager.isScrollInProgress }
            .distinctUntilChanged()
            .collectLatest { (page, scrolling) ->
                val latest = latestSelectedEpisodeId
                if (scrolling || latest == null) return@collectLatest
                val episode = pages.getOrNull(page) ?: return@collectLatest
                if (episode.id != latest) {
                    controlsVisible = true
                    selectEpisode(episode)
                }
            }
    }

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
        var pausedByUser = false
        player?.let {
            if (it.playWhenReady) {
                it.pause()
                pausedByUser = true
            } else {
                if (it.playbackState == Player.STATE_ENDED) it.seekTo(0)
                it.play()
            }
        }
        centerPlayVisible = pausedByUser
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

    val portraitVideo = videoAspectRatio < 1f && !fullscreen
    val videoBackground = if (portraitVideo) Color.Black else HongguoPlayerBackground
    Box(Modifier.fillMaxSize().background(videoBackground)) {
        VerticalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            userScrollEnabled = !fullscreen,
        ) { page ->
            val pageEpisode = pages[page]
            HongguoEpisodePage(
                pageEpisode = pageEpisode, currentEpisodeId = currentEpisodeId, episodeCount = episodeCount,
                seriesTitle = title, seriesIntro = intro, seriesCover = seriesCover, fullscreen = fullscreen,
                player = player, loading = loading, error = error, position = position, duration = duration,
                seekable = seekable, progress = progress, centerPlayVisible = centerPlayVisible,
                portraitVideo = portraitVideo, videoBackground = videoBackground, introExpanded = introExpanded,
                onTap = togglePlayback,
                onTogglePlayback = togglePlayback, onSeek = seek, onSeekFinished = finishSeek,
                onShowEpisodes = onShowEpisodes, onToggleIntro = { introExpanded = !introExpanded },
                onFullscreen = { fullscreen = true; controlsVisible = true; interaction++ }, onRetry = onRetry,
            )
        }

        AnimatedVisibility(visible = !fullscreen || controlsVisible, modifier = Modifier.align(Alignment.TopCenter), enter = fadeIn(), exit = fadeOut()) {
            Row(Modifier.fillMaxWidth()
                .background(if (fullscreen || portraitVideo) Brush.verticalGradient(listOf(Color.Black.copy(alpha = .7f), Color.Transparent))
                    else Brush.verticalGradient(listOf(HongguoPlayerBackground, HongguoPlayerBackground)))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                .padding(horizontal = 4.dp).height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(leavePlayer) { Icon(Icons.Outlined.ChevronLeft, if (fullscreen) "退出全屏" else "返回", Modifier.size(28.dp), tint = Color.White) }
                Text(episodeLabel, Modifier.weight(1f), color = Color.White, fontSize = 18.sp, lineHeight = 26.sp,
                    fontWeight = FontWeight.Medium, maxLines = 1)
                Box {
                    TextButton({ showSpeed = true }, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                        Icon(Icons.Outlined.AvTimer, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${speed}x", fontSize = 16.sp, fontWeight = FontWeight.Normal)
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

/** 单集整屏页面，上一页和下一页会随手指直接露出。 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable private fun HongguoEpisodePage(
    pageEpisode: HongguoEpisode, currentEpisodeId: String?, episodeCount: Int,
    seriesTitle: String, seriesIntro: String, seriesCover: String, fullscreen: Boolean,
    player: ExoPlayer?, loading: Boolean, error: String?, position: Long, duration: Long,
    seekable: Boolean, progress: Float, centerPlayVisible: Boolean,
    portraitVideo: Boolean, videoBackground: Color, introExpanded: Boolean,
    onTap: () -> Unit, onTogglePlayback: () -> Unit, onSeek: (Float) -> Unit, onSeekFinished: () -> Unit,
    onShowEpisodes: () -> Unit, onToggleIntro: () -> Unit, onFullscreen: () -> Unit, onRetry: () -> Unit,
) {
    val isCurrent = pageEpisode.id == currentEpisodeId
    val episodeLabel = "第${pageEpisode.number}集"
    Column(Modifier.fillMaxSize()
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
        .then(if (!fullscreen && !portraitVideo) Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)) else Modifier)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            if (!isCurrent) {
                HongguoEpisodePlaceholder(seriesCover, episodeLabel, seriesTitle)
            } else {
                if (player != null) {
                    key(player) {
                        AndroidView(
                            factory = { viewContext ->
                                PlayerView(viewContext).apply {
                                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    useController = false
                                    resizeMode = if (portraitVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    setShutterBackgroundColor(videoBackground.toArgb())
                                    setBackgroundColor(videoBackground.toArgb())
                                    keepScreenOn = true
                                    this.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize(),
                            update = { view ->
                                view.resizeMode = if (portraitVideo) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                                view.setShutterBackgroundColor(videoBackground.toArgb())
                                view.setBackgroundColor(videoBackground.toArgb())
                            },
                            onRelease = { view -> view.player = null },
                        )
                        Box(Modifier.matchParentSize().pointerInput(player) {
                            detectTapGestures { onTap() }
                        })
                        androidx.compose.animation.AnimatedVisibility(visible = centerPlayVisible && !loading && error == null,
                            enter = fadeIn(), exit = fadeOut()) {
                            FilledIconButton(onTogglePlayback, Modifier.size(64.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.Black.copy(alpha = .35f), contentColor = Color.White)) {
                                Icon(Icons.Outlined.PlayArrow, "播放", Modifier.size(36.dp))
                            }
                        }
                    }
                } else {
                    HongguoEpisodePlaceholder(seriesCover, episodeLabel, seriesTitle, dimmed = true)
                }
                when {
                    error != null -> Surface(
                        modifier = Modifier.padding(24.dp), color = Color.Black.copy(alpha = .82f), contentColor = Color.White,
                        shape = RoundedCornerShape(14.dp),
                    ) {
                        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(error, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onRetry, colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) { Text("重新播放") }
                        }
                    }
                    loading -> CircularProgressIndicator(color = Color.White)
                    else -> Text("请选择可播放的分集", color = Color.White)
                }
            }
            if (portraitVideo && isCurrent) {
                Column(Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = .24f), Color.Black.copy(alpha = .7f))))
                    .padding(top = 56.dp)) {
                    HongguoPlayerInfo(seriesTitle, seriesIntro, episodeLabel, episodeCount, introExpanded,
                        onToggleIntro = onToggleIntro, onShowEpisodes = onShowEpisodes)
                    Box(Modifier.padding(horizontal = 16.dp)) { HongguoSeekBar(progress, seekable, onSeek, onSeekFinished) }
                }
            }

        }

        if (!fullscreen && !portraitVideo) {
            HongguoPlayerInfo(seriesTitle, seriesIntro, episodeLabel, episodeCount, introExpanded,
                onToggleIntro = onToggleIntro, onShowEpisodes = onShowEpisodes)
        }
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = if (portraitVideo) 4.dp else 0.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(onClick = onShowEpisodes, modifier = Modifier.weight(1f).height(44.dp),
                    color = if (portraitVideo) Color(0xFF151515) else HongguoPlayerSurface,
                    contentColor = Color.White, shape = RoundedCornerShape(10.dp)) {
                    Row(Modifier.padding(horizontal = 16.dp).fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Text("选集 · 全${episodeCount}集", Modifier.weight(1f), fontSize = 15.sp, fontWeight = FontWeight.Normal)
                        Icon(Icons.Outlined.KeyboardArrowUp, null)
                    }
                }
                IconButton(onFullscreen, Modifier.size(40.dp)) {
                    Icon(Icons.Outlined.Fullscreen, "全屏观看",
                        Modifier.size(22.dp).border(1.5.dp, Color.White, RoundedCornerShape(5.dp)).padding(2.dp), tint = Color.White)
                }
            }
        }
    }
}

/** 竖屏和横屏播放页共用的剧集信息。 */
@Composable private fun HongguoPlayerInfo(
    seriesTitle: String, seriesIntro: String, episodeLabel: String, episodeCount: Int, introExpanded: Boolean,
    onToggleIntro: () -> Unit, onShowEpisodes: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClick = onShowEpisodes),
            verticalAlignment = Alignment.CenterVertically) {
            Text(seriesTitle, Modifier.weight(1f, fill = false), color = Color.White,
                fontSize = 18.sp, lineHeight = 26.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.padding(start = 2.dp).size(20.dp), tint = Color.White)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (seriesIntro.isBlank()) "$episodeLabel · 共${episodeCount}集" else "$episodeLabel | $seriesIntro",
                Modifier.weight(1f).padding(vertical = 8.dp), color = Color.White.copy(alpha = .72f),
                fontSize = 14.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal,
                maxLines = if (introExpanded) 4 else 1, overflow = TextOverflow.Ellipsis)
            if (seriesIntro.isNotBlank()) TextButton(onToggleIntro,
                modifier = Modifier.height(40.dp), contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = Color.White)) {
                Text(if (introExpanded) "收起" else "展开", fontSize = 14.sp, fontWeight = FontWeight.Normal)
            }
        }
    }
}

/** 非当前集使用剧集封面承接翻页过程，松手后再进入实际视频页。 */
@Composable private fun HongguoEpisodePlaceholder(
    cover: String, episodeLabel: String, title: String, dimmed: Boolean = false,
) {
    Box(Modifier.fillMaxSize().background(HongguoPlayerBackground), contentAlignment = Alignment.Center) {
        if (cover.isNotBlank()) {
            AsyncImage(cover, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(
            Color.Black.copy(alpha = .36f), Color.Black.copy(alpha = if (dimmed) .62f else .48f), Color.Black.copy(alpha = .5f),
        ))))
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = CircleShape, color = Color.Black.copy(alpha = .38f)) {
                Icon(Icons.Outlined.PlayArrow, null, Modifier.padding(14.dp).size(30.dp), tint = Color.White)
            }
            Text(episodeLabel, color = Color.White, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text("上下滑动切换剧集", color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** 轨道与圆点共用画布坐标，Slider 保留拖动和无障碍交互。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HongguoSeekBar(progress: Float, enabled: Boolean, onSeek: (Float) -> Unit, onSeekFinished: () -> Unit) {
    Box(Modifier.fillMaxWidth().height(24.dp)) {
        Canvas(Modifier.matchParentSize()) {
            val radius = 2.dp.toPx()
            val start = Offset(radius, center.y)
            val end = Offset(size.width - radius, center.y)
            val thumb = Offset(start.x + (end.x - start.x) * progress, center.y)
            val stroke = 1.5.dp.toPx()
            drawLine(Color.White.copy(alpha = .2f), start, end, stroke, StrokeCap.Round)
            drawLine(Color.White.copy(alpha = .65f), start, thumb, stroke, StrokeCap.Round)
            drawCircle(Color.White, radius, thumb)
        }
        Slider(value = progress, onValueChange = onSeek, onValueChangeFinished = onSeekFinished, enabled = enabled,
            modifier = Modifier.fillMaxWidth().height(24.dp).semantics { contentDescription = "播放进度" },
            thumb = { Spacer(Modifier.size(4.dp)) }, track = {},
        )
    }
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
