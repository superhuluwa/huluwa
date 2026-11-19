package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import coil3.ImageLoader
import coil3.compose.AsyncImage
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive
import kotlin.math.abs

private val HuangguoAccent = Color(0xFFF4B942)
private val HuangguoInk = Color(0xFF171411)
private val HuangguoPaper = Color(0xFFF7F2E8)
private val HuangguoMuted = Color(0xFF746B5F)

/** 竖版黄果：独立于聊天会话的全屏模块，从功能页进入。 */
@Composable fun HuangguoScreen(ui: AppState, vm: HuluwaViewModel) {
    val context = LocalContext.current
    val imageLoader = remember(context) { huangguoImageLoader(context) }
    DisposableEffect(imageLoader) { onDispose { imageLoader.shutdown() } }
    val state = ui.huangguo
    BackHandler(state.visible) {
        when {
            state.playerVisible -> vm.closeHuangguoPlayer()
            state.detail != null -> vm.closeHuangguoDetail()
            else -> vm.closeHuangguo()
        }
    }
    var query by remember { mutableStateOf("") }
    val detail = state.detail
    when {
        state.playerVisible && detail != null -> HuangguoPlayer(state, vm)
        state.detailLoading -> Box(Modifier.fillMaxSize().background(HuangguoPaper), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HuangguoAccent)
        }
        state.detailError != null && detail == null -> Column(Modifier.fillMaxSize().background(HuangguoPaper),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(state.detailError, color = HuluwaColors.Danger)
            Spacer(Modifier.height(12.dp))
            Button(vm::closeHuangguoDetail) { Text("返回") }
        }
        detail != null -> HuangguoDetailPage(state, vm, imageLoader)
        else -> HuangguoBrowsePage(state, vm, imageLoader, query, { query = it })
    }
}

/** 黄果首页：一级频道 + 二级题材筛选，结构与红果 TV 排行榜页一致。 */
@Composable private fun HuangguoBrowsePage(
    state: HuangguoState, vm: HuluwaViewModel, imageLoader: ImageLoader,
    query: String, onQueryChange: (String) -> Unit,
) {
    val grid = rememberLazyGridState()
    val searching = state.searchKeyword.isNotBlank()
    val cards = if (searching) state.search else state.browse
    val selectedGroup = state.tagGroups.data.firstOrNull { it.code == state.selectedTagGroup }
    val visibleTags = selectedGroup?.tags.orEmpty()
    // 滚动到底部附近时续页；用末项可见位置判断，避免首屏只显示少量卡片时反复触发。
    LaunchedEffect(grid, state.browse.data.size, state.browsePage) {
        snapshotFlow { grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { last ->
                val lastIndex = state.browse.data.lastIndex
                if (!searching && lastIndex >= 0 && last != null && last >= lastIndex - 4) vm.loadMoreHuangguoBrowse()
            }
    }
    Column(Modifier.fillMaxSize().background(HuangguoPaper).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(vm::closeHuangguo) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = HuangguoInk) }
            Text("黄果短剧", Modifier.weight(1f), color = HuangguoInk, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            IconButton({ if (searching) vm.searchHuangguo(state.searchKeyword) else vm.refreshHuangguoBrowse() }) {
                Icon(Icons.Outlined.Refresh, "刷新", tint = HuangguoMuted)
            }
            if (searching) IconButton(vm::clearHuangguoSearch) {
                Icon(Icons.Outlined.Close, "退出搜索", tint = HuangguoMuted)
            }
        }
        OutlinedTextField(
            value = query, onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            singleLine = true,
            placeholder = { Text("搜索剧名，例如：任意门") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = {
                TextButton(onClick = { vm.searchHuangguo(query) }, enabled = query.isNotBlank()) { Text("搜索") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { vm.searchHuangguo(query) }),
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val categories = state.categories.data.ifEmpty { listOf(HuangguoCategory("全部", "")) }
            categories.forEach { category ->
                HuangguoFilterChip(if (category.available) category.name else "${category.name} · 暂不可用",
                    state.selectedTag.isBlank() && state.selectedCategory == category.route,
                    { if (category.available) vm.selectHuangguoCategory(category.route) },
                    enabled = category.available)
            }
        }
        if (!searching) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HuangguoFilterChip("全部题材", selectedGroup == null, { vm.clearHuangguoTag() })
                state.tagGroups.data.forEach { group ->
                    if (group.name.startsWith("成人")) return@forEach
                    HuangguoFilterChip(group.name, selectedGroup?.code == group.code,
                        { vm.selectHuangguoTagGroup(group.code) })
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 8.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(selectedGroup?.name ?: "题材分类", color = HuangguoMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text("第 ${state.browsePage}/${state.browsePages} 页", color = HuangguoMuted, fontSize = 12.sp)
            }
            if (visibleTags.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 7.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    visibleTags.forEach { tag ->
                        if (tag.name != "萝莉") HuangguoFilterChip(tag.name, state.selectedTag == tag.slug, { vm.selectHuangguoTag(tag.slug) })
                    }
                }
            }
        }
        if (cards.loading && cards.data.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = HuangguoAccent)
        } else if (cards.error != null && cards.data.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text(cards.error.orEmpty(), color = HuluwaColors.Danger)
                Spacer(Modifier.height(12.dp))
                Button({ if (searching) vm.searchHuangguo(state.searchKeyword) else vm.refreshHuangguoBrowse() }) { Text("重试") }
            }
        } else LazyVerticalGrid(
            state = grid,
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards.data, key = { it.id }) { card ->
                HuangguoCardView(card, imageLoader,
                    onClick = { vm.openHuangguoDetail(card) },
                    onPlay = { vm.openHuangguoDetail(card, playImmediately = true) })
            }
            if (!searching && state.browsePage >= state.browsePages && cards.data.isNotEmpty()) item(span = { GridItemSpan(2) }) {
                Text("已经到底了", Modifier.fillMaxWidth().padding(vertical = 16.dp), color = HuangguoMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontSize = 12.sp)
            }
            if (!searching && state.browseLoadingMore) item(span = { GridItemSpan(2) }) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 12.dp), color = HuangguoAccent)
            }
            if (cards.data.isEmpty() && !cards.loading) item(span = { GridItemSpan(2) }) {
                Text("暂无匹配内容", Modifier.fillMaxWidth().padding(32.dp), color = HuangguoMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
    }
}

@Composable private fun HuangguoFilterChip(text: String, selected: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp),
        enabled = enabled,
        color = when { !enabled -> Color(0xFFE9E1D5); selected -> HuangguoAccent; else -> Color.White },
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) HuangguoAccent else Color(0xFFE2D8C8))) {
        Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp), color = if (enabled) HuangguoInk else HuangguoMuted,
            fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable private fun HuangguoCardView(card: HuangguoCard, imageLoader: ImageLoader, onClick: () -> Unit, onPlay: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.72f).background(Color(0xFFE8DED0), RoundedCornerShape(14.dp))) {
            AsyncImage(card.cover, card.title, imageLoader, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Surface(Modifier.align(Alignment.Center).size(48.dp), color = Color.Black.copy(alpha = .48f), shape = RoundedCornerShape(50)) {
                IconButton(onPlay) { Icon(Icons.Outlined.PlayArrow, "立即播放", tint = Color.White) }
            }
            Surface(Modifier.align(Alignment.BottomStart).padding(8.dp), color = Color.Black.copy(alpha = .68f), shape = RoundedCornerShape(20.dp)) {
                Text("▶ ${card.episodeCount}集", Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(card.title, color = HuangguoInk, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(card.meta, color = HuangguoMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun HuangguoDetailPage(state: HuangguoState, vm: HuluwaViewModel, imageLoader: ImageLoader) {
    val detail = state.detail ?: return
    val card = detail.card
    Column(Modifier.fillMaxSize().background(HuangguoPaper).statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(vm::closeHuangguoDetail) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回", tint = HuangguoInk) }
            Text(card.title, Modifier.weight(1f), color = HuangguoInk, fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AsyncImage(card.cover, card.title, imageLoader, Modifier.width(128.dp).height(178.dp), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(card.meta, color = HuangguoMuted, fontSize = 12.sp)
                Text(card.description, color = HuangguoInk, fontSize = 13.sp, maxLines = 7, overflow = TextOverflow.Ellipsis)
                Button({ vm.playHuangguo(detail.episodes.firstOrNull()?.ep ?: 1) },
                    Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = HuangguoAccent, contentColor = HuangguoInk)) {
                    Icon(Icons.Outlined.PlayArrow, null); Spacer(Modifier.width(5.dp)); Text("立即播放")
                }
            }
        }
        Text("选集 · 全${detail.episodes.size}集", Modifier.padding(start = 18.dp, top = 22.dp, bottom = 10.dp),
            color = HuangguoInk, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp),
            contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp), userScrollEnabled = false) {
            items(detail.episodes, key = { it.ep }) { episode ->
                Surface(onClick = { vm.playHuangguo(episode.ep) }, shape = RoundedCornerShape(10.dp),
                    color = if (episode.ep == state.selectedEp) HuangguoAccent else Color.White) {
                    Text("${episode.ep}", Modifier.fillMaxWidth().padding(vertical = 12.dp), color = HuangguoInk,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun HuangguoPlayer(state: HuangguoState, vm: HuluwaViewModel) {
    val detail = state.detail ?: return
    val context = LocalContext.current
    val activity = remember(context) { context.huangguoActivity() }
    val url = state.playback?.url.orEmpty()
    var fullscreen by remember { mutableStateOf(false) }
    var playing by remember(url) { mutableStateOf(false) }
    var buffering by remember(url) { mutableStateOf(true) }
    var position by remember(url) { mutableLongStateOf(0L) }
    var duration by remember(url) { mutableLongStateOf(0L) }
    var failure by remember(url) { mutableStateOf<String?>(null) }
    var controls by remember { mutableStateOf(true) }
    var showEpisodes by remember { mutableStateOf(false) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var dragSeeking by remember { mutableStateOf(false) }
    var resumeApplied by remember(url) { mutableStateOf(false) }
    val resumeMs = state.resumePositionMs
    val player = remember(url) {
        if (url.isBlank()) null else {
            val source = DefaultHttpDataSource.Factory()
                .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/131 Mobile Safari/537.36")
                .setDefaultRequestProperties(mapOf("Referer" to "https://huangguoai.com/"))
            ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(source)).build().apply {
                setMediaItem(MediaItem.fromUri(url)); prepare(); playWhenReady = true
            }
        }
    }
    fun refresh() {
        player ?: return
        position = player.currentPosition.coerceAtLeast(0)
        duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
        playing = player.isPlaying
        buffering = player.playbackState == Player.STATE_BUFFERING
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(player, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) player?.pause() }
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { refresh() }
            override fun onPlaybackStateChanged(playbackState: Int) {
                refresh()
                if (playbackState == Player.STATE_ENDED) vm.switchHuangguoEpisode(1)
            }
            override fun onPlayerError(error: PlaybackException) { failure = "播放失败：${error.errorCodeName}" }
        }
        lifecycle.lifecycle.addObserver(observer); player?.addListener(listener)
        onDispose {
            if (player != null) {
                val end = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
                if (end > 0) vm.saveHuangguoProgress(player.currentPosition, end)
            }
            lifecycle.lifecycle.removeObserver(observer); player?.removeListener(listener); player?.release()
        }
    }
    LaunchedEffect(player) { while (isActive && player != null) { refresh(); delay(350) } }
    LaunchedEffect(player, resumeMs) {
        val target = player ?: return@LaunchedEffect
        if (resumeApplied || resumeMs <= 0L) return@LaunchedEffect
        while (isActive) {
            val ready = target.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
            if (ready > 0) { target.seekTo(resumeMs.coerceIn(0L, ready)); resumeApplied = true; break }
            delay(200)
        }
    }
    Dialog(vm::closeHuangguoPlayer, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        // 全屏播放器必须操作 Dialog 自己的 window；Activity.window 属于下层页面，改它会把页面状态弄乱。
        val view = LocalView.current
        val window = (view.parent as? DialogWindowProvider)?.window
        DisposableEffect(window, view, fullscreen) {
            val controller = window?.let { WindowCompat.getInsetsController(it, view) }
            val original = activity?.requestedOrientation
            window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            controller?.isAppearanceLightStatusBars = false
            controller?.isAppearanceLightNavigationBars = false
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullscreen) {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                controller?.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                controller?.show(WindowInsetsCompat.Type.systemBars())
            }
            onDispose {
                controller?.show(WindowInsetsCompat.Type.systemBars())
                if (original != null) activity?.requestedOrientation = original
            }
        }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (player != null) AndroidView(factory = { PlayerView(it).apply {
                this.player = player; useController = false; keepScreenOn = true
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            } }, modifier = Modifier.fillMaxSize())
            Box(Modifier.matchParentSize()
                .pointerInput(player, fullscreen) { detectTapGestures(onTap = {
                    if (fullscreen) controls = !controls
                    player?.let { p -> if (p.isPlaying) p.pause() else p.play() }
                }) }
                .pointerInput(player) {
                    detectVerticalDragGestures(
                        onDragStart = { dragSeeking = true },
                        onVerticalDrag = { change, amount ->
                            dragY += amount
                            if (dragSeeking && fullscreen) {
                                val total = duration
                                if (total > 0) player?.seekTo((player.currentPosition - amount * 25).toLong().coerceIn(0L, total))
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (!fullscreen && abs(dragY) > 70f) vm.switchHuangguoEpisode(if (dragY < 0) 1 else -1)
                            dragY = 0f
                            dragSeeking = false
                        },
                        onDragCancel = { dragY = 0f; dragSeeking = false },
                    )
                })
            if (state.playerLoading || (buffering && failure == null)) CircularProgressIndicator(Modifier.align(Alignment.Center), color = HuangguoAccent)
            if (!playing && !state.playerLoading && failure == null) Icon(Icons.Outlined.PlayArrow, null,
                Modifier.align(Alignment.Center).size(68.dp), tint = Color.White.copy(alpha = .78f))
            if (failure != null || state.playerError != null) Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(failure ?: state.playerError.orEmpty(), color = Color.White)
                TextButton({ vm.playHuangguo(state.selectedEp) }) { Text("重新解析", color = HuangguoAccent) }
            }
            if (controls) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Black.copy(.78f), Color.Transparent)))
                    .then(if (fullscreen) Modifier else Modifier.statusBarsPadding())
                    .padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::closeHuangguoPlayer) { Icon(Icons.Outlined.Close, "关闭", tint = Color.White) }
                    Text(if (fullscreen) "${detail.card.title} · 第${state.selectedEp}集" else "第${state.selectedEp}集",
                        Modifier.weight(1f), color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton({ fullscreen = !fullscreen }) { Icon(if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen, "全屏", tint = Color.White) }
                }
                if (!fullscreen) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.9f))))
                    .navigationBarsPadding().padding(start = 16.dp, end = 16.dp, top = 42.dp, bottom = 18.dp)) {
                    Text(detail.card.title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    Text("第${state.selectedEp}集 | ${detail.episodes.firstOrNull { it.ep == state.selectedEp }?.title.orEmpty()}",
                        color = Color.White.copy(alpha = .82f), fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(12.dp))
                    val progress = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                    Box(Modifier.fillMaxWidth().height(18.dp).pointerInput(duration) {
                        detectTapGestures { offset ->
                            if (duration > 0) player?.seekTo((offset.x / size.width).coerceIn(0f, 1f).times(duration).toLong())
                        }
                    }, contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .32f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(Color.White))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(onClick = { showEpisodes = true }, modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp), color = Color.White.copy(alpha = .18f)) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.List, null, tint = Color.White, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("选集 · 全${detail.episodes.size}集", color = Color.White)
                            }
                        }
                        IconButton({ player?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                            Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "播放", tint = Color.White)
                        }
                    }
                }
                if (fullscreen) Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(.82f))))
                    .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    IconButton({ player?.let { if (it.isPlaying) it.pause() else it.play() } }) {
                        Icon(if (playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, "播放", tint = Color.White)
                    }
                    Text(clockLabel(position, duration), color = Color.White.copy(alpha = .8f), fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    TextButton({ showEpisodes = true }) { Text("选集 · 全${detail.episodes.size}集", color = Color.White) }
                    IconButton({ fullscreen = false }) { Icon(Icons.Outlined.FullscreenExit, "退出全屏", tint = Color.White) }
                }
            }
        }
    }
    if (showEpisodes) ModalBottomSheet(onDismissRequest = { showEpisodes = false }, containerColor = Color(0xFF171717)) {
        Text("选集 · 全${detail.episodes.size}集", Modifier.padding(18.dp), color = Color.White, fontSize = 18.sp)
        LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
            contentPadding = PaddingValues(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(detail.episodes, key = { it.ep }) { episode ->
                Surface(onClick = { showEpisodes = false; vm.playHuangguo(episode.ep) }, shape = RoundedCornerShape(9.dp),
                    color = if (episode.ep == state.selectedEp) Color(0xFFE44B40) else Color(0xFF292929)) {
                    Text("${episode.ep}", Modifier.fillMaxWidth().padding(vertical = 13.dp),
                        color = Color.White, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

private fun Context.huangguoActivity(): Activity? {
    var value = this
    while (value is ContextWrapper) {
        if (value is Activity) return value
        value = value.baseContext
    }
    return null
}


private fun clockLabel(position: Long, duration: Long): String {
    fun part(value: Long): String {
        val seconds = (value / 1000).coerceAtLeast(0)
        return "%d:%02d".format(seconds / 60, seconds % 60)
    }
    return if (duration > 0) "${part(position)} / ${part(duration)}" else "--:--"
}
