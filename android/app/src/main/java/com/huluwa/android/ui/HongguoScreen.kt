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
import com.huluwa.android.HongguoOfflinePicker
import com.huluwa.android.HongguoOfflineTab
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
    when {
        ui.offlineManagerVisible -> HongguoOfflineManagerScreen(ui, vm, back)
        ui.selectedSeries != null -> HongguoDetailContent(ui, vm, back)
        else -> Scaffold(
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
    ui.offlinePicker?.let { picker ->
        HongguoOfflinePickerSheet(picker, recorded = ui.offlineEpisodes[picker.seriesId].orEmpty(),
            onToggle = { vm.toggleOfflineEpisode(it) }, onToggleAll = vm::toggleOfflineAll,
            onConfirm = vm::confirmOfflinePicker, onDismiss = vm::dismissOfflinePicker,
            onRetry = { vm.openOfflinePicker(picker.seriesId) })
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
            HongguoSeriesCard(series, onOpen = { focus.clearFocus(); vm.openSeries(series) })
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("我的收藏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("点右侧「离线」选择分集下载到本机", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                }
                TextButton(vm::showOfflineManager, contentPadding = PaddingValues(start = 8.dp)) {
                    Text("离线管理"); Icon(Icons.Outlined.ChevronRight, null, Modifier.size(18.dp))
                }
            }
        }
        if (ui.favorites.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                Text("还没有收藏，播放短剧时点击右下角爱心即可收藏", color = HuluwaColors.Muted)
            }
        }
        items(ui.favorites, key = { "favorite-" + it.id }) { favorite ->
            val status = ui.offline[favorite.id]
            HongguoSeriesCard(favorite.series(), { vm.openSeries(favorite.series()) }) {
                // 下载中徽标点击进管理页看进度，其余状态打开分集选择。
                HongguoOfflineBadge(status,
                    onClick = { if (status?.running == true) vm.showOfflineManager() else vm.openOfflinePicker(favorite.id) })
            }
        }
    }
}

/** 收藏卡右侧的离线状态徽标：未离线可发起、下载中显示进度、完成后可继续补选。 */
@Composable private fun HongguoOfflineBadge(status: HongguoOfflineStatus?, onClick: () -> Unit) {
    val running = status?.running == true
    val done = status?.done ?: 0
    val failed = status?.failed ?: 0
    Surface(
        onClick = onClick,
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
            val failedVisible = !running && (failed > 0 || (done == 0 && !status?.message.isNullOrEmpty()))
            if (running) {
                val progress = status?.takeIf { it.totalBytes > 0 }
                    ?.let { (it.bytes.toDouble() / it.totalBytes).toFloat().coerceIn(0f, 1f) }
                if (progress != null) CircularProgressIndicator({ progress }, Modifier.size(20.dp), strokeWidth = 2.dp)
                else CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("${status?.done ?: 0}/${status?.selectedIds?.size ?: 0}", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Ink, maxLines = 1)
            } else if (failedVisible) {
                Icon(Icons.Outlined.ErrorOutline, "离线失败", Modifier.size(20.dp), tint = Color(0xFFB3543C))
                Text(if (done > 0) "离线${done}集" else "离线失败", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Ink, maxLines = 1)
            } else if (done > 0) {
                Icon(Icons.Outlined.DownloadDone, "已离线", Modifier.size(20.dp), tint = Color(0xFF3D8B4F))
                Text("已离线${done}集", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Ink, maxLines = 1)
            } else {
                Icon(Icons.Outlined.Download, "选择分集离线", Modifier.size(20.dp), tint = Color(0xFFCA3D32))
                Text("离线", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Ink, maxLines = 1)
            }
        }
    }
}

/** 离线分集选择弹框：数字网格多选（已离线集默认勾选并带标记），支持全选，确认后增量提交。 */
@Composable private fun HongguoOfflinePickerSheet(
    picker: HongguoOfflinePicker, recorded: Set<String>,
    onToggle: (String) -> Unit, onToggleAll: () -> Unit,
    onConfirm: () -> Unit, onDismiss: () -> Unit, onRetry: () -> Unit,
) {
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
                    val selectable = picker.episodes.filterNot { it.locked }
                    val allChecked = selectable.isNotEmpty() && selectable.all { it.id in picker.checked }
                    val fresh = picker.checked.count { it !in recorded }
                    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(picker.title.ifBlank { "选择分集" }, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("共${picker.episodes.size}集 · 已离线${recorded.size}集 · 已选${picker.checked.size}集",
                                color = Color.White.copy(alpha = .6f), style = MaterialTheme.typography.labelMedium)
                        }
                        FilterChip(allChecked, onToggleAll,
                            label = { Text(if (allChecked) "全不选" else "全选") },
                            colors = FilterChipDefaults.filterChipColors(
                                containerColor = Color(0xFF292929), labelColor = Color.White,
                                selectedContainerColor = Color(0xFFE44B40), selectedLabelColor = Color.White))
                        IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭", tint = Color.White) }
                    }
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 380.dp)) {
                        when {
                            picker.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                HongguoLoading("正在加载分集…", dark = true)
                            }
                            picker.error != null -> Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
                                HongguoError(picker.error, onRetry)
                            }
                            else -> {
                                val rows = remember(picker.episodes) { picker.episodes.chunked(5) }
                                LazyColumn(
                                    Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(rows, key = { it.first().id }) { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                            row.forEach { item ->
                                                val checked = item.id in picker.checked
                                                Surface(
                                                    Modifier.weight(1f).height(52.dp)
                                                        .clickable(enabled = !item.locked) { onToggle(item.id) },
                                                    shape = RoundedCornerShape(10.dp),
                                                    color = if (checked) Color(0xFFE44B40) else Color(0xFF292929),
                                                    contentColor = if (checked) Color.White else Color.White.copy(alpha = if (item.locked) .38f else .88f),
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        if (item.locked) Icon(Icons.Outlined.Lock, "需解锁", Modifier.size(18.dp))
                                                        else Text("${item.number}", style = MaterialTheme.typography.titleMedium,
                                                            fontWeight = if (checked) FontWeight.Bold else FontWeight.Medium)
                                                        if (recorded.contains(item.id)) Icon(Icons.Outlined.DownloadDone,
                                                            "已离线", Modifier.align(Alignment.TopEnd).size(12.dp).padding(1.dp),
                                                            tint = if (checked) Color.White else Color(0xFF8CD4A0))
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
                    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 20.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(Modifier.weight(1f))
                        TextButton(onDismiss) { Text("取消", color = Color.White.copy(alpha = .8f)) }
                        Button(onConfirm, enabled = fresh > 0 && !picker.loading,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE44B40))) {
                            Text(if (fresh > 0) "离线新增${fresh}集" else "没有新选择的分集")
                        }
                    }
                }
            }
        }
    }
}

/** 下载任务与本地缓存独立展示：每集落盘后即可在已下载模块离线播放。 */
@Composable private fun HongguoOfflineManagerScreen(ui: HongguoState, vm: HongguoViewModel, onBack: () -> Unit) {
    var pendingRemove by remember { mutableStateOf<String?>(null) }
    val downloading = ui.offline.values
        .filter { it.running || it.failed > 0 || it.message.isNotBlank() }
        .sortedBy { it.title }
    val downloaded = ui.offline.values.filter { it.done > 0 }.sortedBy { it.title }
    val downloadingScroll = rememberLazyListState()
    val downloadedScroll = rememberLazyListState()
    val showDownloading = ui.offlineTab == HongguoOfflineTab.Downloading
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("离线下载", style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("每集缓存完成即可离线播放", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = ui.offlineTab.ordinal, containerColor = Color.White) {
                Tab(selected = showDownloading, onClick = { vm.selectOfflineTab(HongguoOfflineTab.Downloading) },
                    text = { Text("正在下载 (${downloading.size})") })
                Tab(selected = !showDownloading, onClick = { vm.selectOfflineTab(HongguoOfflineTab.Downloaded) },
                    text = { Text("已下载 (${downloaded.size})") })
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(),
                state = if (showDownloading) downloadingScroll else downloadedScroll,
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (showDownloading) {
                    if (downloading.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            Text("暂无下载任务，去收藏页选择分集下载", color = HuluwaColors.Muted)
                        }
                    }
                    items(downloading, key = { "downloading-" + it.seriesId }) { status ->
                        HongguoOfflineDownloadingRow(status,
                            onCancel = { vm.cancelOfflineDownload(status.seriesId) },
                            onPick = { vm.openOfflinePicker(status.seriesId) },
                            onRemove = { pendingRemove = status.seriesId })
                    }
                } else {
                    if (downloaded.isEmpty()) item {
                        Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                            Text("暂无缓存，每集下载完成后会自动显示在这里", color = HuluwaColors.Muted)
                        }
                    }
                    items(downloaded, key = { "downloaded-" + it.seriesId }) { status ->
                        HongguoOfflineDownloadedRow(status,
                            onPlay = { vm.openOfflineSeries(status.seriesId) },
                            onPick = { vm.openOfflinePicker(status.seriesId) },
                            onRemove = { pendingRemove = status.seriesId })
                    }
                }
            }
        }
    }
    if (pendingRemove != null) {
        val removeId = pendingRemove!!
        val status = ui.offline[removeId]
        val size = status?.size ?: 0
        AlertDialog(onDismissRequest = { pendingRemove = null }, title = { Text("删除离线") },
            text = {
                Text("将删除《${status?.title.orEmpty()}》已缓存的 ${status?.done ?: 0} 集" +
                    (if (size > 0) "，释放约 ${hongguoOfflineSizeText(size)} 空间" else "") +
                    (if (status?.running == true) "，并取消剩余下载" else "") +
                    "。删除后可重新下载。")
            },
            confirmButton = { TextButton({ vm.removeOffline(removeId); pendingRemove = null }) { Text("删除") } },
            dismissButton = { TextButton({ pendingRemove = null }) { Text("取消") } })
    }
}

@Composable private fun HongguoOfflineDownloadingRow(
    status: HongguoOfflineStatus, onCancel: () -> Unit, onPick: () -> Unit, onRemove: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), color = Color.White) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(status.cover, status.title, Modifier.width(56.dp).height(76.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(status.title.ifBlank { "短剧" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (status.running) {
                    val fraction = if (status.totalBytes > 0) (status.bytes.toDouble() / status.totalBytes).toFloat().coerceIn(0f, 1f) else null
                    Text(if (status.currentNumber > 0 && fraction != null) "第${status.currentNumber}集 · ${(fraction * 100).toInt()}%" else "准备中…",
                        style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    if (fraction != null) LinearProgressIndicator({ fraction }, Modifier.fillMaxWidth())
                    else LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    Text("下载未完成", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                }
                Text("已缓存 ${status.done} 集" +
                    (if (status.failed > 0) " · ${status.failed} 集失败" else ""),
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                if (status.message.isNotBlank()) Text(status.message, style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFB3543C), maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            if (status.running) TextButton(onCancel) { Text("取消") }
            else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onPick) { Text("重试") }
                IconButton(onRemove) { Icon(Icons.Outlined.DeleteOutline, "删除离线", tint = HuluwaColors.Muted) }
            }
        }
    }
}

@Composable private fun HongguoOfflineDownloadedRow(
    status: HongguoOfflineStatus, onPlay: () -> Unit, onPick: () -> Unit, onRemove: () -> Unit,
) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onPlay), shape = RoundedCornerShape(14.dp), color = Color.White) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(status.cover, status.title, Modifier.width(56.dp).height(76.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(status.title.ifBlank { "短剧" }, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("已缓存 ${status.done} 集" + (if (status.size > 0) " · ${hongguoOfflineSizeText(status.size)}" else ""),
                    style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                if (status.running) Text("后续分集下载中", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Outlined.PlayArrow, null, Modifier.size(16.dp), tint = Color(0xFFE44B40))
                    Text("点击离线播放", color = Color(0xFFE44B40), style = MaterialTheme.typography.labelSmall)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onPick) { Text("补选") }
                IconButton(onRemove) { Icon(Icons.Outlined.DeleteOutline, "删除离线", tint = HuluwaColors.Muted) }
            }
        }
    }
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
                HongguoSeriesCard(series, onOpen = { focus.clearFocus(); vm.openSeries(series) })
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
            onOfflineDownload = { vm.openOfflinePicker(detail.series.id) },
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
        HongguoEpisodeSheet(detail, episode, ui.offlineEpisodes[detail.series.id].orEmpty(), ui.offlineOnly,
            onDismiss = { showEpisodes = false },
            onSelect = { item -> showEpisodes = false; vm.playEpisode(item) })
    }
}

/** 电视端选集样式：大号数字网格、当前集高亮，锁定集保留锁标记，已离线集带下载标记。 */
@Composable private fun HongguoEpisodeSheet(
    detail: HongguoDetail, current: HongguoEpisode?, offlineEpisodes: Set<String>, offlineOnly: Boolean,
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
                            Text(if (offlineOnly) "已缓存${detail.episodes.size}集 · 离线连播" else "共${detail.episodes.size}集 · 自动连播",
                                color = Color.White.copy(alpha = .6f),
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
