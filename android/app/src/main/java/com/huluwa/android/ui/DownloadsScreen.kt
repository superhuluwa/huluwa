package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.LocalMovies
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*
import java.text.Collator
import java.util.Locale
import kotlin.math.pow
import kotlinx.coroutines.launch

private enum class DownloadFilter(val title: String) {
    All("全部"), Downloading("下载中"), Completed("已完成"), Failed("异常");

    fun matches(task: DownloadTask): Boolean = when (this) {
        All -> true
        Downloading -> task.isDownloading()
        Completed -> task.completed
        Failed -> task.hasDownloadIssue()
    }
}

@Composable fun DownloadsScreen(ui: AppState, vm: HuluwaViewModel) {
    var search by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf(DownloadFilter.All) }
    var sortByName by rememberSaveable { mutableStateOf(false) }
    val tasks = ui.downloads.data
    val busy = ui.busy == null && !ui.downloads.loading
    val reconnecting = rememberReconnecting(vm)
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val collator = remember { Collator.getInstance(Locale.SIMPLIFIED_CHINESE) }
    val keyword = search.trim()
    val counts = remember(tasks) { DownloadFilter.entries.associateWith { value -> tasks.count(value::matches) } }
    val visibleTasks = remember(tasks, keyword, filter, sortByName) {
        val matches = tasks.filter { task ->
            filter.matches(task) && (keyword.isBlank() || task.title.contains(keyword, ignoreCase = true)
                || task.episode.contains(keyword, ignoreCase = true) || task.source.title.contains(keyword, ignoreCase = true))
        }
        val sorted = if (sortByName) matches.sortedWith(Comparator { first, second -> collator.compare(first.title, second.title) }) else matches
        // 两种排序都优先展示正在下载的任务，组内保留名称或创建时间顺序。
        sorted.sortedByDescending { it.isDownloading() }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 22.dp)) {
        DownloadSummary(tasks, ui.downloads.loaded && ui.downloads.error == null && !reconnecting)
        // 仅在用户修改筛选条件时回到顶部，后台同步及切换页面后返回时保留滚动位置。
        DownloadSearchField(search, {
            search = it
            scope.launch { listState.scrollToItem(0) }
        })
        Spacer(Modifier.height(6.dp))
        DownloadFilters(filter, counts, {
            filter = it
            scope.launch { listState.scrollToItem(0) }
        }, sortByName, {
            sortByName = !sortByName
            scope.launch { listState.scrollToItem(0) }
        })
        HorizontalDivider(color = HuluwaColors.Line)
        // 内容区仅保留业务错误和重试入口，连接异常由共用标题栏提示。
        Feedback(error = ui.downloads.error.takeUnless { it.isServerUnreachable() },
            onRetry = { vm.refreshDownloads() })
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 16.dp)) {
            if (tasks.isEmpty()) item(key = "empty") {
                DownloadEmptyState("这里还没有下载任务", "订阅追更或提交磁力链接后，可在这里查看进度",
                    ui.downloads.loading || !ui.downloads.loaded)
            } else if (visibleTasks.isEmpty()) item(key = "no-matches") {
                DownloadEmptyState("没有符合条件的任务", "试试其他关键词或筛选条件", false) {
                    search = ""
                    filter = DownloadFilter.All
                    scope.launch { listState.scrollToItem(0) }
                }
            }
            itemsIndexed(visibleTasks, key = { _, task -> task.key }) { index, task ->
                DownloadListRow(task, busy, vm)
                if (index < visibleTasks.lastIndex) HorizontalDivider(Modifier.padding(start = 49.dp), color = HuluwaColors.Line)
            }
        }
    }
}

@Composable private fun DownloadSummary(tasks: List<DownloadTask>, online: Boolean) {
    val downloading = remember(tasks) { tasks.filter { it.isDownloading() } }
    val speed = remember(downloading) {
        val speeds = downloading.map { downloadSpeedBytes(it.speed) }
        if (speeds.any { it == null }) "速度待同步" else "${formatBytes(speeds.filterNotNull().sum())}/s"
    }
    Row(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(5.dp).background(if (online) HuluwaColors.Pine else HuluwaColors.Muted, RoundedCornerShape(3.dp)))
        Text("${downloading.size} 项下载中", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
        Text("·", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall)
        Text(speed, Modifier.weight(1f), color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (online) Text("服务器在线", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun DownloadSearchField(value: String, onChange: (String) -> Unit) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(Modifier.fillMaxWidth(), color = HuluwaColors.SoftGreen.copy(alpha = .55f), shape = RoundedCornerShape(12.dp)) {
        Row(Modifier.heightIn(min = 44.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = HuluwaColors.Muted)
            BasicTextField(value, onChange, Modifier.weight(1f).padding(vertical = 12.dp)
                .semantics { contentDescription = "搜索下载任务" }, singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall.copy(color = HuluwaColors.Ink),
                cursorBrush = SolidColor(HuluwaColors.Pine), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) Text("搜索名称或剧集", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                        innerTextField()
                    }
                })
            if (value.isNotEmpty()) IconButton({ onChange("") }, Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Close, "清空搜索", Modifier.size(17.dp), tint = HuluwaColors.Muted)
            } else Spacer(Modifier.width(12.dp))
        }
    }
}

@Composable private fun DownloadFilters(selected: DownloadFilter, counts: Map<DownloadFilter, Int>,
    onSelect: (DownloadFilter) -> Unit, sortByName: Boolean, onSort: () -> Unit) {
    Row(Modifier.fillMaxWidth().selectableGroup(), verticalAlignment = Alignment.CenterVertically) {
        DownloadFilter.entries.forEach { value ->
            val active = selected == value
            Box(Modifier.weight(1f).heightIn(min = 48.dp).selectable(active, role = Role.Tab, onClick = { onSelect(value) }),
                contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value.title, color = if (active) HuluwaColors.Pine else HuluwaColors.Muted,
                        style = MaterialTheme.typography.labelMedium, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                    Text((counts[value] ?: 0).toString(), color = if (active) HuluwaColors.Pine else HuluwaColors.Muted,
                        style = MaterialTheme.typography.labelSmall)
                }
                if (active) Box(Modifier.align(Alignment.BottomCenter).width(24.dp).height(3.dp)
                    .background(HuluwaColors.Pine, RoundedCornerShape(2.dp)))
            }
        }
        IconToggleButton(sortByName, { onSort() }, Modifier.size(48.dp)) {
            Icon(Icons.Outlined.Sort, if (sortByName) "下载中置顶，按名称排序，点击按时间排序" else "下载中置顶，按时间排序，点击按名称排序",
                Modifier.size(19.dp), tint = if (sortByName) HuluwaColors.Pine else HuluwaColors.Muted)
        }
    }
}

/** 下载页标题栏操作，设置入口固定在最右侧。 */
@Composable internal fun DownloadsHeaderActions(ui: AppState, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    val busy = ui.busy == null && !ui.downloads.loading
    TextButton(onClick = { vm.clearAllDownloads() }, enabled = busy && ui.downloads.data.isNotEmpty()) {
        Text("清空记录")
    }
    Box {
        HeaderAction(Icons.Outlined.Settings, "下载设置", { menu = true })
        DropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(text = { Text("离线下载设置") }, onClick = { menu = false; vm.showSettings("dytt") })
            DropdownMenuItem(text = { Text("qBittorrent 设置") }, onClick = { menu = false; vm.showSettings("magnet") })
            DropdownMenuItem(text = { Text("迅雷【Win】设置") }, onClick = { menu = false; vm.showSettings("xunlei-win") })
            DownloadSource.entries.forEach { type -> DropdownMenuItem(text = { Text("清空${type.title}记录") }, enabled = busy,
                onClick = { menu = false; vm.clearDownloads(type) }) }
        }
    }
}

@Composable private fun DownloadEmptyState(title: String, detail: String, loading: Boolean, onReset: (() -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (!loading) RoundIcon(Icons.Outlined.FileDownload, modifier = Modifier.size(62.dp))
        Spacer(Modifier.height(13.dp))
        Text(if (loading) "正在同步…" else title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text(if (loading) "请稍候，正在获取服务器下载任务" else detail, color = HuluwaColors.Muted,
            style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        if (onReset != null) TextButton(onReset, Modifier.padding(top = 10.dp)) { Text("查看全部任务") }
    }
}

@Composable private fun DownloadListRow(task: DownloadTask, enabled: Boolean, vm: HuluwaViewModel) {
    val context = LocalContext.current
    val failed = task.hasDownloadIssue()
    val progress = when {
        task.completed -> 1f
        task.progress != null -> task.progress.div(100f).coerceIn(0f, 1f)
        task.isDownloading() -> null
        else -> 0f
    }
    val progressColor = when {
        failed -> HuluwaColors.Danger
        task.paused || task.status in setOf("paused", "stopped") -> HuluwaColors.Muted
        task.completed -> HuluwaColors.Pine.copy(alpha = .55f)
        else -> HuluwaColors.Pine
    }
    Row(Modifier.fillMaxWidth().clickable { showDownloadDetails(task, vm) }.padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
        Surface(Modifier.padding(top = 3.dp).size(38.dp), color = task.source.tagTone().copy(alpha = .10f), shape = RoundedCornerShape(10.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(when (task.source) {
                    DownloadSource.Hls -> Icons.Outlined.LocalMovies
                    DownloadSource.Magnet -> Icons.Outlined.Link
                    DownloadSource.XunleiWin -> Icons.Outlined.CloudDownload
                }, null, Modifier.size(21.dp), tint = task.source.tagTone())
            }
        }
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth()) {
                Column(Modifier.fillMaxWidth().padding(end = 34.dp)) {
                    Text(task.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(task.source.title, style = MaterialTheme.typography.labelSmall, color = task.source.tagTone())
                        if (task.episode.isNotBlank()) {
                            Text("·", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                            Text(task.episode, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                                color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                DownloadTaskActions(task, enabled, vm, Modifier.align(Alignment.TopEnd).offset(y = (-10).dp))
            }
            Spacer(Modifier.height(8.dp))
            DownloadProgress(progress, if (progress == null) Modifier.progressSemantics() else Modifier.progressSemantics(progress),
                color = progressColor, height = 3.dp)
            Spacer(Modifier.height(7.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (failed && task.message.isNotBlank()) task.message else progressText(task), Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall, color = if (failed) HuluwaColors.Danger else HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                DownloadRowStatus(task)
            }
            Spacer(Modifier.height(8.dp))
            DownloadSavePath(task.savePath) { copyDownloadPath(task.savePath, context, vm) }
        }
    }
}

/** 保存位置作为独立辅助行，单行中间省略，复制时始终使用服务端返回的完整路径。 */
@Composable private fun DownloadSavePath(savePath: String, onCopy: () -> Unit) {
    val hasPath = savePath.isNotBlank()
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
        .background(HuluwaColors.SoftGreen.copy(alpha = .45f))
        .clickable(enabled = hasPath, role = Role.Button, onClickLabel = "复制完整保存位置", onClick = onCopy)
        .heightIn(min = 40.dp).padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Outlined.FolderOpen, null, Modifier.size(14.dp), tint = HuluwaColors.Muted)
        Text("保存至", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
        Text(savePath.ifBlank { "由下载器管理" }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
            color = if (hasPath) HuluwaColors.Ink else HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
        if (hasPath) Icon(Icons.Outlined.ContentCopy, null, Modifier.size(12.dp), tint = HuluwaColors.Muted)
    }
}

private fun copyDownloadPath(savePath: String, context: Context, vm: HuluwaViewModel) {
    if (savePath.isBlank()) return
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("保存路径", savePath))
    vm.notify("保存路径已复制")
}

@Composable private fun DownloadRowStatus(task: DownloadTask) {
    val failed = task.hasDownloadIssue()
    val statusIcon = when {
        failed -> Icons.Outlined.ErrorOutline
        task.completed -> Icons.Outlined.Check
        task.paused || task.status in setOf("paused", "stopped") -> Icons.Outlined.Pause
        !task.isDownloading() -> Icons.Outlined.Schedule
        else -> null
    }
    val tone = when {
        failed -> HuluwaColors.Danger
        task.completed || task.isDownloading() -> HuluwaColors.Pine
        else -> HuluwaColors.Muted
    }
    val label = if (task.isDownloading()) {
        listOfNotNull(task.progress?.let { "${it.toInt()}%" }, task.speed.takeIf { it.isNotBlank() }
            ?: downloadStatus(task)).joinToString(" · ")
    } else downloadStatus(task)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        if (statusIcon != null) Icon(statusIcon, null, Modifier.size(12.dp), tint = tone)
        Text(label, color = tone, style = MaterialTheme.typography.labelSmall,
            fontWeight = if (task.isDownloading()) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable private fun DownloadTaskActions(task: DownloadTask, enabled: Boolean, vm: HuluwaViewModel, modifier: Modifier = Modifier) {
    var menu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Box(modifier) {
        IconButton({ menu = true }, Modifier.size(48.dp)) {
            Icon(Icons.Outlined.MoreHoriz, "${task.title}的更多操作", Modifier.size(19.dp), tint = HuluwaColors.Muted)
        }
        DropdownMenu(menu, { menu = false }) {
            if (task.cancellable) DropdownMenuItem(text = { Text("取消下载") }, enabled = enabled,
                onClick = { menu = false; vm.cancelDownload(task) })
            if (task.retryable) DropdownMenuItem(text = { Text(if (task.status == "skipped") "补下载" else "重新下载") }, enabled = enabled,
                onClick = { menu = false; vm.retryDownload(task) })
            if (task.source == DownloadSource.Hls) DropdownMenuItem(text = { Text("查看日志") },
                onClick = { menu = false; vm.downloadLog(task) })
            DropdownMenuItem(text = { Text("复制保存路径") }, enabled = task.savePath.isNotBlank(),
                leadingIcon = { Icon(Icons.Outlined.FolderOpen, null) }, onClick = {
                    menu = false
                    copyDownloadPath(task.savePath, context, vm)
                })
            DropdownMenuItem(text = { Text("详细信息") }, onClick = { menu = false; showDownloadDetails(task, vm) })
            HorizontalDivider(color = HuluwaColors.Line)
            DropdownMenuItem(text = { Text("删除记录", color = HuluwaColors.Danger) }, enabled = enabled,
                leadingIcon = { Icon(Icons.Outlined.DeleteOutline, null, tint = HuluwaColors.Danger) },
                onClick = { menu = false; vm.deleteDownload(task) })
        }
    }
}

private fun showDownloadDetails(task: DownloadTask, vm: HuluwaViewModel) {
    vm.loadText(task.title) {
        buildString {
            appendLine("来源：${task.source.title}")
            if (task.episode.isNotBlank()) appendLine("剧集 / 规格：${task.episode}")
            appendLine("状态：${downloadStatus(task)}")
            if (task.progress != null) appendLine("进度：${task.progress.toInt()}%")
            appendLine("下载大小：${formatBytes(task.downloaded)} / ${formatBytes(task.total)}")
            appendLine("下载速度：${task.speed.ifBlank { "—" }}")
            appendLine("保存目录：${task.savePath.ifBlank { "由下载器管理" }}")
            appendLine("创建时间：${formatLocalTime(task.createdAt).ifBlank { "—" }}")
            if (task.message.isNotBlank()) appendLine("任务信息：${task.message}")
        }
    }
}

/** 来源配色与现有主题一致，列表只用图标和文字轻量标识。 */
private fun DownloadSource.tagTone(): Color = when (this) {
    DownloadSource.Hls -> HuluwaColors.Pine
    DownloadSource.Magnet -> HuluwaColors.Blue
    DownloadSource.XunleiWin -> HuluwaColors.Lilac
}

// 迅雷也会用 message 返回正常进度，异常筛选以任务状态为准，避免把正常下载误判为失败。
private fun DownloadTask.hasDownloadIssue(): Boolean = status in setOf("failed", "error")

/** 排队和暂停不计入正在下载数量，也不累加下载器遗留的速度。 */
private fun DownloadTask.isDownloading(): Boolean = !paused && status in setOf("downloading", "running", "verifying")

private fun downloadStatus(task: DownloadTask): String = when {
    task.paused -> "订阅已暂停"
    task.hasDownloadIssue() -> if (task.status in setOf("failed", "error")) "下载失败" else "处理异常"
    else -> statusLabel(task.status)
}

private fun progressText(task: DownloadTask): String = when {
    task.total != null || task.downloaded != null -> "${formatBytes(task.downloaded)} / ${formatBytes(task.total)}"
    else -> downloadStatus(task)
}

/** 现有模型把速度统一为文本；仅合计可识别的字节速率，未知时显示待同步，避免低估总速度。 */
private val DownloadSpeedPattern = Regex("""^\s*(\d+(?:\.\d+)?)\s*([KMGT]?I?B)\s*/\s*S\s*$""", RegexOption.IGNORE_CASE)

private fun downloadSpeedBytes(value: String): Double? {
    val match = DownloadSpeedPattern.matchEntire(value) ?: return null
    val amount = match.groupValues[1].toDoubleOrNull() ?: return null
    val unit = when (match.groupValues[2].uppercase(Locale.ROOT)) {
        "B" -> 0
        "KB", "KIB" -> 1
        "MB", "MIB" -> 2
        "GB", "GIB" -> 3
        "TB", "TIB" -> 4
        else -> return null
    }
    return (amount * 1024.0.pow(unit)).takeIf { it.isFinite() }
}
