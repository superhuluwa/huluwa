package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun DownloadsScreen(ui: AppState, vm: HuluwaViewModel) {
    // 默认展示全部任务：去掉顶部汇总卡片和多余标题，列表直接从筛选条下方开始
    var filter by rememberSaveable { mutableIntStateOf(2) }
    var menu by remember { mutableStateOf(false) }
    val all = ui.downloads.data
    // 三个来源合并成一个列表：不再按来源分区，来源改由卡片上的标签区分
    val tasks = all.filter { when (filter) { 0 -> it.active; 1 -> it.completed; else -> true } }
    val busy = ui.busy == null && !ui.downloads.loading
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(10.dp))
            // 筛选与刷新、更多操作收在同一行，节省列表上方空间
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterRow(listOf("进行中 ${all.count { it.active }}", "已完成 ${all.count { it.completed }}", "全部 ${all.size}"),
                    filter, { filter = it }, Modifier.weight(1f))
                DownloadToolButton(Icons.Outlined.Refresh, "刷新下载", !ui.downloads.loading) { vm.refreshDownloads() }
                Box {
                    DownloadToolButton(Icons.Outlined.MoreHoriz, "下载操作") { menu = true }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("离线下载设置") }, onClick = { menu = false; vm.showSettings("dytt") })
                        DropdownMenuItem(text = { Text("qBittorrent 设置") }, onClick = { menu = false; vm.showSettings("magnet") })
                        DropdownMenuItem(text = { Text("迅雷【Win】设置") }, onClick = { menu = false; vm.showSettings("xunlei-win") })
                        DownloadSource.entries.forEach { type -> DropdownMenuItem(text = { Text("清空${type.title}记录") }, enabled = busy,
                            onClick = { menu = false; vm.clearDownloads(type) }) }
                    }
                }
            }
            // 连不上服务器时统一显示标题栏的「重连中」徽标，列表里不再叠一张错误卡片。
            Feedback(ui.downloads.loading, ui.downloads.error.takeUnless { it.isServerUnreachable() }, { vm.refreshDownloads() })
        }
        if (tasks.isEmpty()) item {
            DownloadEmptyState(if (all.isEmpty()) "这里还没有下载任务" else "当前筛选下暂无任务",
                if (all.isEmpty()) "订阅追更或提交磁力链接后，可在这里查看进度" else "可以切换状态查看其他下载任务",
                !ui.downloads.loaded)
        }
        items(tasks, key = { it.key }) { task -> DownloadCard(task, busy, vm) }
    }
}

@Composable private fun DownloadToolButton(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(13.dp), color = Color.White, border = BorderStroke(1.dp, HuluwaColors.Line)) {
        IconButton(onClick, enabled = enabled, modifier = Modifier.size(40.dp)) { Icon(icon, label, tint = if (enabled) HuluwaColors.Pine else HuluwaColors.Muted) }
    }
}

@Composable private fun DownloadEmptyState(title: String, detail: String, loading: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        else RoundIcon(Icons.Outlined.FileDownload, modifier = Modifier.size(62.dp))
        Spacer(Modifier.height(13.dp))
        Text(if (loading) "正在同步…" else title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(if (loading) "请稍候，正在获取服务器下载任务" else detail, color = HuluwaColors.Muted,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
    }
}

@Composable private fun DownloadCard(task: DownloadTask, enabled: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    val failed = task.status == "failed"
    val done = task.completed
    val context = LocalContext.current
    Panel(Modifier.fillMaxWidth(), if (failed) Color(0xFFFFFDFD) else if (done) Color(0xFFFBFCFA) else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            RoundIcon(if (task.source == DownloadSource.Hls) Icons.Outlined.InsertDriveFile else Icons.Outlined.CloudDownload,
                task.source.tagTone(), Modifier.size(38.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                // 来源统一用标签标注，不再和集数拼成一行文字
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    Tag(task.source.title, task.source.tagTone())
                    if (task.episode.isNotBlank()) Text(task.episode, Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Box {
                IconButton({ menu = true }, Modifier.size(36.dp)) { Icon(Icons.Outlined.MoreHoriz, "任务操作") }
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
        Spacer(Modifier.height(12.dp))
        DownloadProgress(
            when {
                task.completed -> 1f
                !task.active -> 0f
                else -> task.progress?.div(100f)
            },
            Modifier.fillMaxWidth(),
            color = if (failed) HuluwaColors.Danger else HuluwaColors.Pine,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(progressText(task), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = if (failed) HuluwaColors.Danger else HuluwaColors.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.progress?.let { Text("${it.toInt()}%", style = MaterialTheme.typography.labelMedium,
                color = if (failed) HuluwaColors.Danger else HuluwaColors.Pine, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (task.paused) "订阅已暂停" else if (task.message.isNotBlank()) "处理异常" else statusLabel(task.status), Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (task.speed.isNotBlank()) Text(task.speed, color = HuluwaColors.Pine, style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold)
        }
        if (task.message.isNotBlank()) {
            Spacer(Modifier.height(8.dp))
            Text(task.message, color = if (failed) HuluwaColors.Danger else HuluwaColors.Muted,
                style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        // 保存路径：三种来源都由服务端返回 savePath，留空表示下载器自行决定目录。
        // 点按整行复制完整路径，路径较长时单行省略显示。
        Spacer(Modifier.height(8.dp))
        val path = task.savePath.ifBlank { "由下载器管理" }
        Row(Modifier.fillMaxWidth().clickable(enabled = task.savePath.isNotBlank()) {
            context.getSystemService(ClipboardManager::class.java)
                ?.setPrimaryClip(ClipData.newPlainText("保存路径", task.savePath))
            vm.notify("保存路径已复制")
        }, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(Icons.Outlined.FolderOpen, null, Modifier.size(14.dp), tint = HuluwaColors.Muted)
            Text(path, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = if (task.savePath.isBlank()) HuluwaColors.Muted else HuluwaColors.Ink,
                maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        }
    }
}

/** 来源标签的配色：离线剧集松柏绿、磁力蓝、迅雷【Win】紫，与卡片左侧图标保持一致。 */
private fun DownloadSource.tagTone(): Color = when (this) {
    DownloadSource.Hls -> HuluwaColors.Pine
    DownloadSource.Magnet -> HuluwaColors.Blue
    DownloadSource.XunleiWin -> HuluwaColors.Lilac
}

private fun progressText(task: DownloadTask): String = when {
    task.total != null || task.downloaded != null -> "${formatBytes(task.downloaded)} / ${formatBytes(task.total)}"
    task.paused -> "订阅已暂停"
    else -> statusLabel(task.status)
}
