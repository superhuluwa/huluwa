package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun DownloadsScreen(ui: AppState, vm: HuluwaViewModel) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var source by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val all = ui.downloads.data
    val selectedSource = DownloadSource.entries.getOrNull(source - 1)
    val tasks = all.filter { (selectedSource == null || it.source == selectedSource) && when (filter) { 0 -> it.active; 1 -> it.completed; else -> true } }
    val busy = ui.busy == null && !ui.downloads.loading
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(10.dp))
            DownloadHero(all)
            Spacer(Modifier.height(14.dp))
            FilterRow(listOf("进行中 ${all.count { it.active }}", "已完成 ${all.count { it.completed }}", "全部任务 ${all.size}"), filter, { filter = it })
            Spacer(Modifier.height(8.dp))
            SourceFilterRow(source, { source = it })
            SectionTitle("下载任务", Modifier.padding(top = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
            }
            Feedback(ui.downloads.loading, ui.downloads.error, { vm.refreshDownloads() })
        }
        if (tasks.isEmpty()) item {
            DownloadEmptyState(if (all.isEmpty()) "这里还没有下载任务" else "当前筛选下暂无任务",
                if (all.isEmpty()) "订阅追更或提交磁力链接后，可在这里查看进度" else "可以切换状态或来源查看其他下载任务",
                !ui.downloads.loaded)
        }
        items(tasks, key = { it.key }) { task -> DownloadCard(task, busy, vm) }
    }
}

@Composable private fun DownloadHero(tasks: List<DownloadTask>) {
    val active = tasks.count { it.active }
    val waiting = tasks.count { it.active && (it.progress == null || it.progress <= 0f) }
    val completed = tasks.count { it.completed }
    val totalSpeed = tasks.filter { it.active }.sumOf { speedBytes(it.speed) }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), color = HuluwaColors.Pine,
        contentColor = Color.White, shadowElevation = 2.dp) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF174E3D), HuluwaColors.Pine, Color(0xFF2A785F))))) {
            Box(Modifier.size(170.dp).align(Alignment.TopEnd).offset(x = 52.dp, y = (-82).dp)
                .clip(RoundedCornerShape(100.dp)).background(Color.White.copy(alpha = .07f)))
            Column(Modifier.padding(horizontal = 18.dp, vertical = 17.dp)) {
                Text("当前下载速度", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Lime)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(if (totalSpeed > 0) formatBytes(totalSpeed) else "待同步", fontSize = 31.sp, lineHeight = 34.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = (-0.8).sp)
                    if (totalSpeed > 0) Text("/s", Modifier.padding(start = 5.dp, bottom = 4.dp),
                        color = Color.White.copy(alpha = .68f), style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(15.dp))
                HorizontalDivider(color = Color.White.copy(alpha = .14f))
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    HeroMetric("$active", "个任务正在下载")
                    HeroMetric("$waiting", "个等待中")
                    HeroMetric("$completed", "个已完成")
                }
            }
        }
    }
}

@Composable private fun HeroMetric(value: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(label, color = Color.White.copy(alpha = .72f), style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun SourceFilterRow(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf("全部来源") + DownloadSource.entries.map { it.title }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        labels.forEachIndexed { index, label ->
            val active = selected == index
            Column(Modifier.clickable { onSelect(index) }.padding(vertical = 2.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(label, color = if (active) HuluwaColors.Pine else HuluwaColors.Muted,
                    style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.width(20.dp).height(2.dp).clip(RoundedCornerShape(2.dp))
                    .background(if (active) HuluwaColors.Pine else Color.Transparent))
            }
        }
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

private fun speedBytes(raw: String): Double {
    val match = Regex("([0-9]+(?:\\.[0-9]+)?)\\s*([kmgt]?b)(?:/s)?").find(raw.trim().lowercase()) ?: return 0.0
    val amount = match.groupValues[1].toDoubleOrNull() ?: return 0.0
    val multiplier = when (match.groupValues[2]) {
        "kb" -> 1024.0
        "mb" -> 1024.0 * 1024
        "gb" -> 1024.0 * 1024 * 1024
        "tb" -> 1024.0 * 1024 * 1024 * 1024
        else -> 1.0
    }
    return amount * multiplier
}

@Composable private fun DownloadCard(task: DownloadTask, enabled: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    val failed = task.status == "failed"
    val done = task.completed
    Panel(Modifier.fillMaxWidth(), if (failed) Color(0xFFFFFDFD) else if (done) Color(0xFFFBFCFA) else Color.White) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
            RoundIcon(if (task.source == DownloadSource.Hls) Icons.Outlined.InsertDriveFile else Icons.Outlined.CloudDownload,
                if (task.source == DownloadSource.Hls) HuluwaColors.Pine else HuluwaColors.Blue, Modifier.size(38.dp))
            Column(Modifier.weight(1f)) {
                Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Text(listOf(task.episode, task.source.title).filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
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
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(progressText(task), Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = if (failed) HuluwaColors.Danger else HuluwaColors.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            task.progress?.let { Text("${it.toInt()}%", style = MaterialTheme.typography.labelMedium,
                color = if (failed) HuluwaColors.Danger else HuluwaColors.Pine, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.height(7.dp))
        if (task.progress != null) DownloadProgress(task.progress / 100f, failed)
        else if (task.active) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
            trackColor = HuluwaColors.SoftGreen, color = HuluwaColors.Pine)
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
    }
}

@Composable private fun DownloadProgress(progress: Float, failed: Boolean) {
    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(5.dp)),
        trackColor = HuluwaColors.SoftGreen, color = if (failed) HuluwaColors.Danger else HuluwaColors.Pine)
}

private fun progressText(task: DownloadTask): String = when {
    task.total != null || task.downloaded != null -> "${formatBytes(task.downloaded)} / ${formatBytes(task.total)}"
    task.paused -> "订阅已暂停"
    else -> statusLabel(task.status)
}
