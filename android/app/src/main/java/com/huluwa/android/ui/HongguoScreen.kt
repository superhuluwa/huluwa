package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.huluwa.android.HongguoFeedTab
import com.huluwa.android.HongguoState
import com.huluwa.android.HongguoViewModel
import com.huluwa.android.data.*
import com.huluwa.android.hongguo.HongguoOfflineStatus

@Composable fun HongguoScreen(vm: HongguoViewModel, onClose: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val back = { if (!vm.back()) onClose() }
    BackHandler(onBack = back)
    LaunchedEffect(Unit) { vm.loadDiscover() }
    LaunchedEffect(ui.tab) { if (ui.tab == HongguoFeedTab.Ranking) vm.loadRanking() }
    if (ui.selectedSeries != null) {
        HongguoDetailContent(ui, vm, back)
    } else {
        Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                Surface(color = Color.White) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                        Column(Modifier.weight(1f)) {
                            Text("红果短视频", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("推荐 · 排行榜 · 想看的短剧", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                        }
                        IconButton(vm::showSettings) { Icon(Icons.Outlined.Settings, "连接设置") }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding).navigationBarsPadding().fillMaxSize()) { HongguoHome(ui, vm) }
        }
    }
    if (ui.settingsVisible) HongguoSettingsDialog(ui, vm)
}

@Composable private fun HongguoHome(ui: HongguoState, vm: HongguoViewModel) {
    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = ui.tab.ordinal, containerColor = Color.White) {
            listOf(HongguoFeedTab.Recommend to "推荐", HongguoFeedTab.Ranking to "排行榜", HongguoFeedTab.Search to "搜索", HongguoFeedTab.Favorites to "收藏")
                .forEachIndexed { index, (tab, label) ->
                    Tab(selected = ui.tab == tab, onClick = { vm.selectTab(tab) },
                        text = { Text(label, fontWeight = if (ui.tab == tab) FontWeight.SemiBold else FontWeight.Normal) })
                }
        }
        when (ui.tab) {
            HongguoFeedTab.Recommend -> HongguoDiscover(ui, vm)
            HongguoFeedTab.Ranking -> HongguoRanking(ui, vm)
            HongguoFeedTab.Search -> HongguoSearch(ui, vm)
            HongguoFeedTab.Favorites -> HongguoFavorites(ui, vm)
        }
    }
}

@Composable private fun HongguoDiscover(ui: HongguoState, vm: HongguoViewModel) {
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("为你推荐", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("短剧频道精选，点开即可选集观看", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
        }
        if (ui.discoverLoading) item {
            if (ui.discover.isEmpty()) HongguoLoading("正在加载推荐…")
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (ui.discoverError != null) item { HongguoError(ui.discoverError) { vm.loadDiscover() } }
        if (!ui.discoverLoading && ui.discover.isEmpty() && ui.discoverError == null) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                Text("暂无推荐，稍后再来看看", color = HuluwaColors.Muted)
            }
        }
        items(ui.discover, key = { "discover-${it.id}" }) { series ->
            HongguoSeriesCard(series) { focus.clearFocus(); vm.openSeries(series) }
        }
    }
}

@Composable private fun HongguoRanking(ui: HongguoState, vm: HongguoViewModel) {
    val focus = LocalFocusManager.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("短剧排行榜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("真人剧、漫剧、演员榜实时热度", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
        }
        if (ui.rankContents.isNotEmpty()) item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.rankContents.forEach { content ->
                        FilterChip(ui.rankContent == content.id, { vm.selectRankContent(content.id) },
                            label = { Text(content.name) })
                    }
                }
                if (ui.currentRankBoards.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ui.currentRankBoards.forEach { board ->
                        AssistChip(onClick = { vm.selectRankBoard(board.id) },
                            label = { Text(board.name) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = if (ui.rankBoard == board.id) Color(0xFFFFEAE6) else Color.White,
                                labelColor = if (ui.rankBoard == board.id) Color(0xFFCA3D32) else HuluwaColors.Ink))
                    }
                }
            }
        }
        if (ui.rankLoading) item {
            if (ui.rankItems.isEmpty()) HongguoLoading("正在加载排行榜…")
            else LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        if (ui.rankError != null) item { HongguoError(ui.rankError) { vm.loadRanking() } }
        if (!ui.rankLoading && ui.rankItems.isEmpty() && ui.rankError == null) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                Text("榜单暂无数据，稍后再来看看", color = HuluwaColors.Muted)
            }
        }
        itemsIndexed(ui.rankItems, key = { _, item -> "rank-${item.kind}-${item.id}" }) { index, item ->
            if (item.actor) HongguoActorRankRow(index, item)
            else HongguoRankRow(index, item, ui.rankItems) { focus.clearFocus(); vm.openSeries(
                HongguoSeries(item.id, item.title, item.cover, item.intro, item.episodeCount)) }
        }
        if (ui.rankMore) item { HongguoLoading("正在加载更多…") }
        else if (ui.rankHasMore && ui.rankItems.isNotEmpty()) item {
            TextButton({ vm.loadRanking(more = true) }, Modifier.fillMaxWidth()) { Text("加载更多") }
        }
    }
}

@Composable private fun HongguoRankRow(index: Int, item: HongguoRankItem, all: List<HongguoRankItem>, onOpen: () -> Unit) {
    val position = item.rankDisplayPosition(all, index)
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(14.dp), color = Color.White) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(item.rankLabel(all, index), Modifier.width(34.dp), fontWeight = FontWeight.Bold,
                color = if (position in 1..3) Color(0xFFE44B40) else HuluwaColors.Muted)
            AsyncImage(item.cover, item.title, Modifier.width(62.dp).height(84.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(item.score.ifBlank { if (item.episodeCount > 0) "共 ${item.episodeCount} 集" else "红果短剧" },
                    style = MaterialTheme.typography.labelMedium, color = if (item.promoted) HuluwaColors.Muted else Color(0xFFE44B40))
                if (item.episodeCount > 0 && item.score.isNotBlank()) Text("共 ${item.episodeCount} 集",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            }
            Icon(Icons.Outlined.ChevronRight, null, tint = HuluwaColors.Muted)
        }
    }
}

@Composable private fun HongguoActorRankRow(index: Int, item: HongguoRankItem) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${index + 1}", Modifier.width(34.dp), fontWeight = FontWeight.Bold,
                color = if (index < 3) Color(0xFFE44B40) else HuluwaColors.Muted)
            AsyncImage(item.cover, item.title, Modifier.size(56.dp).clip(RoundedCornerShape(28.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(item.title, fontWeight = FontWeight.SemiBold)
                Text(item.score, style = MaterialTheme.typography.labelMedium, color = Color(0xFFE44B40))
                if (item.details.isNotEmpty()) Text(item.details.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

private fun HongguoRankItem.rankDisplayPosition(all: List<HongguoRankItem>, index: Int): Int {
    if (!promoted) return index + 1
    return all.drop(index + 1).firstOrNull { !it.promoted }?.let { all.indexOf(it) + 1 } ?: (index + 1)
}
private fun HongguoRankItem.rankLabel(all: List<HongguoRankItem>, index: Int): String =
    if (promoted) "推荐" else rankDisplayPosition(all, index).toString()

@Composable private fun HongguoSeriesCard(
    series: HongguoSeries, onOpen: () -> Unit, trailing: (@Composable () -> Unit)? = null,
) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onOpen), shape = RoundedCornerShape(16.dp), color = Color.White) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AsyncImage(series.cover, series.title, Modifier.width(78.dp).height(108.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f).heightIn(min = 108.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(series.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (series.episodeCount > 0) "共 ${series.episodeCount} 集" else "查看分集",
                    style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                if (series.intro.isNotBlank()) Text(series.intro, style = MaterialTheme.typography.bodySmall,
                    color = HuluwaColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("开始观看", color = Color(0xFFE44B40), style = MaterialTheme.typography.labelLarge)
            }
            if (trailing != null) trailing() else Icon(Icons.Outlined.ChevronRight, null, tint = HuluwaColors.Muted)
        }
    }
}

@Composable private fun HongguoFavorites(ui: HongguoState, vm: HongguoViewModel) {
    var pendingManage by remember { mutableStateOf<String?>(null) }
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("我的收藏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("收藏保存在当前账号下，点右侧「离线」按集序下载最高清晰度到本机", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
        }
        if (ui.favorites.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                Text("还没有收藏，播放短剧时点击右下角爱心即可收藏", color = HuluwaColors.Muted)
            }
        }
        items(ui.favorites, key = { "favorite-" + it.id }) { favorite ->
            val status = ui.offline[favorite.id]
            HongguoSeriesCard(favorite.series(), { vm.openSeries(favorite.series()) }) {
                HongguoOfflineBadge(status,
                    onStart = { vm.startOfflineDownload(favorite.id) },
                    onCancel = { vm.cancelOfflineDownload(favorite.id) },
                    onManage = { pendingManage = favorite.id })
            }
        }
    }
    if (pendingManage != null) {
        val manageId = pendingManage!!
        val status = ui.offline[manageId]
        if (status != null) HongguoOfflineManageDialog(status,
            onComplete = { pendingManage = null },
            onRetry = { vm.startOfflineDownload(manageId); pendingManage = null },
            onRemove = { pendingManage = null; pendingRemove = manageId })
    }
    if (pendingRemove != null) {
        val removeId = pendingRemove!!
        val status = ui.offline[removeId]
        val size = status?.size ?: 0
        AlertDialog(onDismissRequest = { pendingRemove = null }, title = { Text("删除离线") },
            text = {
                Text("将删除《${status?.title.orEmpty()}》已离线的 ${status?.done ?: 0} 集" +
                    (if (size > 0) "，释放约 ${hongguoOfflineSizeText(size)} 空间" else "") +
                    "。删除后可重新下载。")
            },
            confirmButton = { TextButton({ vm.removeOffline(removeId); pendingRemove = null }) { Text("删除") } },
            dismissButton = { TextButton({ pendingRemove = null }) { Text("取消") } })
    }
}

/** 收藏卡右侧的离线状态徽标：未离线可发起、下载中显示进度、完成后进入管理。 */
@Composable private fun HongguoOfflineBadge(
    status: HongguoOfflineStatus?,
    onStart: () -> Unit, onCancel: () -> Unit, onManage: () -> Unit,
) {
    val running = status?.running == true
    val done = status?.done ?: 0
    val failed = status?.failed ?: 0
    Surface(
        onClick = when { running -> onCancel; done > 0 -> onManage; else -> onStart },
        shape = RoundedCornerShape(10.dp),
        color = when {
            running -> Color(0xFFFFF3E0)
            done > 0 && failed > 0 -> Color(0xFFFFEDEA)
            done > 0 -> Color(0xFFE9F5EC)
            else -> Color(0xFFFFEAE6)
        },
    ) {
        Column(Modifier.widthIn(min = 58.dp).padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (running) {
                val progress = status?.takeIf { it.totalBytes > 0 }
                    ?.let { (it.bytes.toDouble() / it.totalBytes).toFloat().coerceIn(0f, 1f) }
                if (progress != null) CircularProgressIndicator({ progress }, Modifier.size(20.dp), strokeWidth = 2.dp)
                else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("${status?.done ?: 0}/${status?.total ?: 0}", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Ink, maxLines = 1)
            } else if (done > 0 && failed > 0) {
                Icon(Icons.Outlined.ErrorOutline, "部分集离线失败", Modifier.size(20.dp), tint = Color(0xFFB3543C))
                Text("离线$done/${status?.total ?: 0}", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Ink, maxLines = 1)
            } else if (done > 0) {
                Icon(Icons.Outlined.DownloadDone, "已离线", Modifier.size(20.dp), tint = Color(0xFF3D8B4F))
                Text("已离线$done集", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Ink, maxLines = 1)
            } else {
                Icon(Icons.Outlined.Download, "离线下载全剧", Modifier.size(20.dp), tint = Color(0xFFCA3D32))
                Text("离线", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Ink, maxLines = 1)
            }
        }
    }
}

@Composable private fun HongguoOfflineManageDialog(
    status: HongguoOfflineStatus,
    onComplete: () -> Unit, onRetry: () -> Unit, onRemove: () -> Unit,
) {
    AlertDialog(onDismissRequest = onComplete, title = { Text("离线管理") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(status.title.ifBlank { "该短剧" }, fontWeight = FontWeight.SemiBold)
                Text("已离线 ${status.done}/${status.total} 集" +
                    (if (status.size > 0) " · 占用 ${hongguoOfflineSizeText(status.size)}" else "") +
                    (if (status.failed > 0) " · ${status.failed} 集失败" else ""),
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                if (status.message.isNotBlank()) Text(status.message, style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB3543C))
                if (status.done < status.total || status.failed > 0) TextButton(onRetry) { Text("补齐缺失分集") }
                TextButton(onRemove) { Text("删除离线文件", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onComplete) { Text("完成") } })
}

private fun hongguoOfflineSizeText(bytes: Long): String = when {
    bytes >= 1024L * 1024 * 1024 -> "%.1fGB".format(bytes.toDouble() / (1024 * 1024 * 1024))
    bytes >= 1024L * 1024 -> "%.0fMB".format(bytes.toDouble() / (1024 * 1024))
    else -> "${bytes / 1024}KB"
}

@Composable private fun HongguoSearch(ui: HongguoState, vm: HongguoViewModel) {
    val focus = LocalFocusManager.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(ui.query, vm::setQuery, Modifier.weight(1f), singleLine = true,
                placeholder = { Text("搜索剧名") }, leadingIcon = { Icon(Icons.Outlined.Search, null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focus.clearFocus(); vm.search() }))
            Button({ focus.clearFocus(); vm.search() }, enabled = ui.query.isNotBlank() && !ui.searching) { Text("搜索") }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.settings?.configured == false) item {
                Panel {
                    Text("红果服务暂不可用", fontWeight = FontWeight.SemiBold)
                    Text(ui.settings.message.ifBlank { "请检查签名服务的连接状态。" }, color = HuluwaColors.Muted)
                    TextButton(vm::showSettings) { Text("连接设置") }
                }
            }
            if (ui.settingsError != null && !ui.searched) item { HongguoError(ui.settingsError, vm::showSettings) }
            if (ui.searching) item { HongguoLoading("正在搜索…") }
            if (ui.searchError != null) item { HongguoError(ui.searchError) { vm.search(ui.results.isNotEmpty()) } }
            if (!ui.searching && ui.results.isEmpty() && ui.searchError == null) item {
                Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                    Text(if (ui.searched) "没有找到相关短剧，试试其他剧名" else "输入剧名，找到想看的短剧", color = HuluwaColors.Muted)
                }
            }
            items(ui.results, key = { "search-${it.id}" }) { series ->
                HongguoSeriesCard(series) { focus.clearFocus(); vm.openSeries(series) }
            }
            if (ui.loadingMore) item { HongguoLoading("正在加载更多…") }
            else if (ui.hasMore && ui.results.isNotEmpty()) item {
                TextButton({ vm.search(true) }, Modifier.fillMaxWidth()) { Text("加载更多") }
            }
        }
    }
}

@Composable private fun HongguoDetailContent(ui: HongguoState, vm: HongguoViewModel, onBack: () -> Unit) {
    if (ui.detailLoading) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) { HongguoLoading("正在加载剧集…", dark = true) }
        return
    }
    if (ui.detailError != null) {
        Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            HongguoError(ui.detailError) { ui.selectedSeries?.let(vm::openSeries) }
        }
        return
    }
    val detail = ui.detail ?: return
    val episode = ui.episode
    val episodes = detail.episodes
    var showEpisodes by remember(detail.series.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HongguoPlayer(
            playback = ui.playback, stream = ui.stream, request = ui.playRequest, startPosition = ui.startPositionMs,
            episodePreloads = ui.episodePreloads,
            title = detail.series.title, intro = detail.series.intro, seriesCover = detail.series.cover,
            episodes = episodes, currentEpisode = episode, episodeCount = episodes.size,
            loading = ui.playLoading, error = ui.playError, favorite = ui.favoriteSelected,
            offlineRunning = ui.offline[detail.series.id]?.running == true,
            playingOffline = ui.playingOffline,
            onOfflineDownload = { vm.startOfflineDownload(detail.series.id) },
            onBack = onBack, onShowEpisodes = { showEpisodes = true },
            onToggleFavorite = vm::toggleFavorite,
            onSelectEpisode = vm::playEpisode, onFirstFrame = { vm.prefetchNextEpisode(ui.playRequest) },
            onProgress = { position, duration, force ->
                ui.episode?.let { vm.progress(detail.series.id, it, position, duration, force) }
            },
            onEnded = { vm.nextEpisode(ui.playRequest) },
            onError = { vm.playerError(ui.playRequest, it) }, onRetry = vm::retryPlayback,
        )
    }
    if (showEpisodes) {
        HongguoEpisodeSheet(detail, episode, ui.offlineEpisodes[detail.series.id].orEmpty(),
            onDismiss = { showEpisodes = false },
            onSelect = { item -> showEpisodes = false; vm.playEpisode(item) })
    }
}

/** 电视端选集样式：大号数字网格、当前集高亮，锁定集保留锁标记，已离线集带下载标记。 */
@Composable private fun HongguoEpisodeSheet(
    detail: HongguoDetail, current: HongguoEpisode?, offlineEpisodes: Set<String>,
    onDismiss: () -> Unit, onSelect: (HongguoEpisode) -> Unit,
) {
    // 每行 5 个数字按钮；弹框打开时定位到当前播放集所在行。
    val rows = remember(detail.episodes) { detail.episodes.chunked(5) }
    val currentRow = remember(rows, current?.id) {
        rows.indexOfFirst { row -> row.any { it.id == current?.id } }.coerceAtLeast(0)
    }
    // 留出上方两行作上下文，当前行大致落在可见区域中部；首行附近自然贴顶。
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentRow - 2).coerceAtLeast(0))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .78f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter) {
            Surface(
                Modifier.fillMaxWidth().navigationBarsPadding()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
                color = Color(0xFF171717), contentColor = Color.White,
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            ) {
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(detail.series.title, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("共${detail.episodes.size}集 · 自动连播", color = Color.White.copy(alpha = .6f),
                                style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭分集", tint = Color.White) }
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().heightIn(max = 380.dp),
                        state = listState,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(rows, key = { it.first().id }) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                row.forEach { item ->
                                    val selected = item.id == current?.id
                                    Surface(
                                        Modifier.weight(1f).height(52.dp)
                                            .clickable(enabled = !item.locked) { onSelect(item) },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selected) Color(0xFFE44B40) else Color(0xFF292929),
                                        contentColor = if (selected) Color.White else Color.White.copy(alpha = if (item.locked) .38f else .88f),
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            if (item.locked) Icon(Icons.Outlined.Lock, "需解锁", Modifier.size(18.dp))
                                            else Text("${item.number}", style = MaterialTheme.typography.titleMedium,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
                                            if (offlineEpisodes.contains(item.id)) Icon(Icons.Outlined.DownloadDone,
                                                "已离线", Modifier.align(Alignment.TopEnd).size(12.dp).padding(1.dp),
                                                tint = if (selected) Color.White else Color(0xFF8CD4A0))
                                        }
                                    }
                                }
                                repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun HongguoLoading(message: String, dark: Boolean = false) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp,
            color = if (dark) Color.White else MaterialTheme.colorScheme.primary)
        Text(message, color = if (dark) Color.White.copy(alpha = .72f) else HuluwaColors.Muted)
    }
}
@Composable private fun HongguoError(message: String, retry: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 4.dp), color = Color(0xFFFFF1ED), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = Color(0xFF9B4336))
            TextButton(retry) { Text("重试") }
        }
    }
}

@Composable private fun HongguoSettingsDialog(ui: HongguoState, vm: HongguoViewModel) {
    var url by remember(ui.settings?.signUrl) { mutableStateOf(ui.settings?.signUrl.orEmpty()) }
    AlertDialog(onDismissRequest = vm::hideSettings, title = { Text("红果连接设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("自动使用签名服务当前的设备，视频在手机上直接播放。", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, label = { Text("签名服务地址") }, singleLine = true, enabled = !ui.settingsSaving)
                Text(if (ui.settings?.configured == true) "当前设备：${ui.settings?.deviceModel.orEmpty()}" else "设备信息会自动获取",
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                if (ui.settingsLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
                val error = ui.settingsError ?: ui.settings?.message?.takeIf { it.isNotBlank() }
                if (error != null) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton({ vm.saveSettings(url) }, enabled = !ui.settingsSaving && url.isNotBlank()) {
                Text(if (ui.settingsSaving) "保存中…" else "保存")
            }
        },
        dismissButton = { TextButton(vm::hideSettings, enabled = !ui.settingsSaving) { Text("取消") } })
}
