package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.huluwa.android.HongguoState
import com.huluwa.android.HongguoViewModel
import com.huluwa.android.data.*

@Composable fun HongguoScreen(vm: HongguoViewModel, onClose: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val back = { if (!vm.back()) onClose() }
    BackHandler(onBack = back)
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text(ui.detail?.series?.title ?: ui.selectedSeries?.title ?: "红果短视频",
                            style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (ui.selectedSeries == null) "搜索短剧，接着上次继续看" else "选集播放 · 自动连播",
                            style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                    IconButton(vm::showSettings) { Icon(Icons.Outlined.Settings, "连接设置") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).navigationBarsPadding().fillMaxSize()) {
            if (ui.selectedSeries == null) HongguoSearch(ui, vm) else HongguoDetailContent(ui, vm)
        }
    }
    if (ui.settingsVisible) HongguoSettingsDialog(ui, vm)
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
            if (ui.settingsLoading && !ui.searched) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
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
            items(ui.results, key = { it.id }) { series ->
                Surface(Modifier.fillMaxWidth().clickable { focus.clearFocus(); vm.openSeries(series) },
                    shape = RoundedCornerShape(16.dp), color = Color.White) {
                    Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        AsyncImage(series.cover, series.title, Modifier.width(78.dp).height(108.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Column(Modifier.weight(1f).heightIn(min = 108.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(series.title, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(if (series.episodeCount > 0) "共 ${series.episodeCount} 集" else "查看分集", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                            if (series.intro.isNotBlank()) Text(series.intro, style = MaterialTheme.typography.bodySmall,
                                color = HuluwaColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("开始观看", color = Color(0xFFE44B40), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            if (ui.loadingMore) item { HongguoLoading("正在加载更多…") }
            else if (ui.hasMore && ui.results.isNotEmpty()) item {
                TextButton({ vm.search(true) }, Modifier.fillMaxWidth()) { Text("加载更多") }
            }
        }
    }
}

@Composable private fun HongguoDetailContent(ui: HongguoState, vm: HongguoViewModel) {
    if (ui.detailLoading) { HongguoLoading("正在加载剧集…"); return }
    if (ui.detailError != null) { HongguoError(ui.detailError) { ui.selectedSeries?.let(vm::openSeries) }; return }
    val detail = ui.detail ?: return
    Column(Modifier.fillMaxSize()) {
        val episode = ui.episode
        val playback = ui.playback
        val stream = ui.stream
        Box(Modifier.fillMaxWidth().height(300.dp).background(Color.Black), contentAlignment = Alignment.Center) {
            HongguoPlayer(
                playback, stream, ui.playRequest, ui.startPositionMs, ui.playLoading, ui.playError,
                onProgress = { position, duration, force ->
                    if (episode != null) vm.progress(detail.series.id, episode, position, duration, force)
                },
                onEnded = { vm.nextEpisode(ui.playRequest) },
                onError = { vm.playerError(ui.playRequest, it) }, onRetry = vm::retryPlayback,
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(episode?.title ?: "选择分集", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            val episodeIndex = detail.episodes.indexOfFirst { it.id == episode?.id }
            TextButton(vm::previousEpisode, enabled = episodeIndex > 0 && detail.episodes.getOrNull(episodeIndex - 1)?.locked == false) { Text("上一集") }
            TextButton({ vm.nextEpisode() }, enabled = detail.episodes.getOrNull(episodeIndex + 1)?.locked == false) { Text("下一集") }
            if (playback != null) {
                var menu by remember { mutableStateOf(false) }
                Box {
                    TextButton({ menu = true }) { Text(stream?.definition ?: "清晰度") }
                    DropdownMenu(menu, { menu = false }) {
                        playback.streams.forEachIndexed { index, variant ->
                            DropdownMenuItem(text = { Text(variant.definition + if (index == ui.streamIndex) " ✓" else "") },
                                onClick = { menu = false; vm.selectStream(index) })
                        }
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (ui.playError != null) item { HongguoError(ui.playError, vm::retryPlayback) }
            item {
                Text(detail.series.title, style = MaterialTheme.typography.titleMedium)
                Text("共 ${detail.episodes.size} 集", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                if (detail.series.intro.isNotBlank()) {
                    var expanded by remember(detail.series.id) { mutableStateOf(false) }
                    Text(detail.series.intro, Modifier.padding(top = 8.dp).clickable { expanded = !expanded },
                        style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted,
                        maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                }
            }
            items(detail.episodes.chunked(5), key = { it.first().id }) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { item ->
                        val selected = episode?.id == item.id
                        OutlinedButton({ vm.playEpisode(item) }, Modifier.weight(1f), enabled = !item.locked,
                            shape = RoundedCornerShape(10.dp), contentPadding = PaddingValues(horizontal = 2.dp, vertical = 10.dp),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selected) Color(0xFFFFEAE6) else Color.White,
                                contentColor = if (selected) Color(0xFFCA3D32) else HuluwaColors.Ink)) {
                            if (item.locked) Icon(Icons.Outlined.Lock, "需解锁", Modifier.size(12.dp))
                            Text("${item.number}", maxLines = 1)
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable private fun HongguoLoading(message: String) {
    Row(Modifier.fillMaxWidth().padding(24.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
        Text(message, color = HuluwaColors.Muted)
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
