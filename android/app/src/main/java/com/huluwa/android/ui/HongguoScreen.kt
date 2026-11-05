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

@Composable private fun HongguoSeriesCard(series: HongguoSeries, onOpen: () -> Unit) {
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
            Icon(Icons.Outlined.ChevronRight, null, tint = HuluwaColors.Muted)
        }
    }
}

@Composable private fun HongguoFavorites(ui: HongguoState, vm: HongguoViewModel) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("我的收藏", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("收藏的短剧会保存在当前账号下", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
        }
        if (ui.favorites.isEmpty()) item {
            Box(Modifier.fillMaxWidth().padding(vertical = 64.dp), contentAlignment = Alignment.Center) {
                Text("还没有收藏，播放短剧时点击右下角爱心即可收藏", color = HuluwaColors.Muted)
            }
        }
        items(ui.favorites, key = { "favorite-" + it.id }) { favorite ->
            HongguoSeriesCard(favorite.series()) { vm.openSeries(favorite.series()) }
        }
    }
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
            title = detail.series.title, intro = detail.series.intro, seriesCover = detail.series.cover,
            episodes = episodes, currentEpisode = episode, episodeCount = episodes.size,
            loading = ui.playLoading, error = ui.playError,
            onBack = onBack, onShowEpisodes = { showEpisodes = true },
            onSelectEpisode = vm::playEpisode, onFirstFrame = { vm.prefetchNextEpisode(ui.playRequest) },
            onProgress = { position, duration, force ->
                ui.episode?.let { vm.progress(detail.series.id, it, position, duration, force) }
            },
            onEnded = { vm.nextEpisode(ui.playRequest) },
            onError = { vm.playerError(ui.playRequest, it) }, onRetry = vm::retryPlayback,
        )
    }
    if (showEpisodes) {
        HongguoEpisodeSheet(detail, episode, onDismiss = { showEpisodes = false },
            onSelect = { item -> showEpisodes = false; vm.playEpisode(item) })
    }
}

/** 电视端选集样式：大号数字网格、当前集高亮，锁定集保留锁标记。 */
@Composable private fun HongguoEpisodeSheet(
    detail: HongguoDetail, current: HongguoEpisode?, onDismiss: () -> Unit, onSelect: (HongguoEpisode) -> Unit,
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
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(detail.episodes.chunked(5), key = { it.first().id }) { row ->
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
