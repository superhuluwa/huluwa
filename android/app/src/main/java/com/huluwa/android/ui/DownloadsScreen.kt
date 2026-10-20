package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun DownloadsScreen(ui: AppState, vm: HuluwaViewModel) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var source by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val all = ui.downloads.data
    val selectedSource = DownloadSource.entries.getOrNull(source - 1)
    val tasks = all.filter { (selectedSource == null || it.source == selectedSource) && when (filter) { 0 -> it.active; 1 -> it.completed; else -> true } }
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            FilterRow(listOf("进行中 ${all.count { it.active }}", "已完成 ${all.count { it.completed }}", "全部任务 ${all.size}"), filter, { filter = it }, Modifier.padding(top = 10.dp))
            FilterRow(listOf("全部来源") + DownloadSource.entries.map { it.title }, source, { source = it })
            SectionTitle("下载任务") {
                IconButton({ vm.refreshDownloads() }, enabled = !ui.downloads.loading) { Icon(Icons.Outlined.Refresh, "刷新下载") }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "下载操作") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("离线下载设置") }, onClick = { menu = false; vm.showSettings("dytt") })
                        DropdownMenuItem(text = { Text("qBittorrent 设置") }, onClick = { menu = false; vm.showSettings("magnet") })
                        DropdownMenuItem(text = { Text("迅雷【Win】设置") }, onClick = { menu = false; vm.showSettings("xunlei-win") })
                        DownloadSource.entries.forEach { type -> DropdownMenuItem(text = { Text("清空${type.title}记录") }, enabled = ui.busy == null && !ui.downloads.loading,
                            onClick = { menu = false; vm.clearDownloads(type) }) }
                    }
                }
            }
            Feedback(ui.downloads.loading, ui.downloads.error, { vm.refreshDownloads() })
        }
        if (tasks.isEmpty()) item { EmptyContent("这里还没有下载任务", "订阅追更或提交磁力链接后，可在这里查看进度", Icons.Outlined.FileDownload, !ui.downloads.loaded) }
        items(tasks, key = { it.key }) { task -> DownloadCard(task, ui.busy == null && !ui.downloads.loading, vm) }
    }
}
@Composable private fun DownloadCard(task: DownloadTask, enabled: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RoundIcon(Icons.Outlined.InsertDriveFile, if (task.source == DownloadSource.Hls) HuluwaColors.Pine else HuluwaColors.Blue)
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(task.episode, task.source.title).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
            }
            Box {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "任务操作") }
                DropdownMenu(menu, { menu = false }) {
                    if (task.cancellable) DropdownMenuItem(text = { Text("取消下载") }, enabled = enabled, onClick = { menu = false; vm.cancelDownload(task) })
                    if (task.retryable) DropdownMenuItem(text = { Text(if (task.status == "skipped") "补下载" else "重新下载") }, enabled = enabled, onClick = { menu = false; vm.retryDownload(task) })
                    if (task.source == DownloadSource.Hls) DropdownMenuItem(text = { Text("查看日志") }, onClick = { menu = false; vm.downloadLog(task) })
                    DropdownMenuItem(text = { Text("详细信息") }, onClick = {
                        menu = false; vm.loadText(task.title) { "状态：${statusLabel(task.status)}\n保存目录：${task.savePath.ifBlank { "由下载器管理" }}\n创建时间：${task.createdAt}\n${task.message}" }
                    })
                }
            }
        }
        Spacer(Modifier.height(13.dp))
        if (task.progress != null) LinearProgressIndicator(progress = { task.progress / 100f }, modifier = Modifier.fillMaxWidth(), trackColor = HuluwaColors.SoftGreen)
        else if (task.active) LinearProgressIndicator(Modifier.fillMaxWidth(), trackColor = HuluwaColors.SoftGreen)
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(if (task.total != null || task.downloaded != null) "${formatBytes(task.downloaded)} / ${formatBytes(task.total)}" else if (task.paused) "订阅已暂停" else statusLabel(task.status),
                style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, modifier = Modifier.weight(1f))
            if (task.speed.isNotBlank()) Text(task.speed, color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium)
            task.progress?.let { Text("${it.toInt()}%", Modifier.padding(start = 10.dp), style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted) }
        }
        if (task.message.isNotBlank()) Text(task.message, Modifier.padding(top = 9.dp), color = if (task.status == "failed") HuluwaColors.Danger else HuluwaColors.Muted,
            style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}
