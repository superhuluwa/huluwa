package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.LibraryEntry
import com.huluwa.android.data.formatBytes
import com.huluwa.android.data.isServerUnreachable
import com.huluwa.android.data.libraryEpisodeCount
import kotlinx.coroutines.delay

/** 按作品归类，再按明确的季号、集数和文件版本展开；归类不移动磁盘文件。 */
@Composable fun LibraryScreen(ui: AppState, vm: HuluwaViewModel) {
    val scanState = ui.library.scan
    val scan = scanState.data
    var keyword by rememberSaveable { mutableStateOf("") }
    var expanded by rememberSaveable { mutableStateOf(listOf<String>()) }
    var expandedEpisodes by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selectedSeasons = remember { mutableStateMapOf<String, Int>() }
    val allGroups = remember(scan.entries) {
        scan.entries.groupBy { it.groupingKey }.map { (key, rows) -> LibraryGroup(key, rows) }
            .sortedWith(compareBy<LibraryGroup> { !it.allEntries.first().seriesRecognized }.thenBy { it.title })
    }
    val groups = remember(allGroups, keyword) {
        val query = keyword.trim()
        allGroups.mapNotNull { group ->
            val rows = group.allEntries.filter { entry ->
                query.isBlank() || "${entry.seriesTitle} ${entry.fileName} ${entry.relativePath}".contains(query, true)
            }
            if (rows.isEmpty()) null else group.copy(entries = rows)
        }
    }
    val pendingCount = scan.entries.count { it.videoInfo.status == "pending" }
    // 元数据更新不改变扫描 ID；退出页面即停止轮询，后台探测结果仍会缓存。
    LaunchedEffect(scan.scannedAt, scan.entries.firstOrNull()?.id) {
        while (vm.refreshLibraryVideoInfo()) delay(2000)
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryStat("已下载作品", scan.workCount.toString(),
                    "${allGroups.sumOf { it.episodeCount }} 集 · ${scan.entries.size} 个文件", Modifier.weight(1f))
                LibraryStat("占用空间", formatBytes(scan.totalBytes.toDouble()),
                    "本次扫描的视频文件", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Panel(Modifier.fillMaxWidth()) {
                Text("下载目录", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                Spacer(Modifier.height(4.dp))
                Text(scan.mediaRoot.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (scan.writable) "目录可访问" else "目录不可写，无法删除文件",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (scan.writable) HuluwaColors.Muted else HuluwaColors.Danger)
            }
            if (scan.warning.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(scan.warning, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Amber)
            }
            if (pendingCount > 0) {
                Spacer(Modifier.height(8.dp))
                Text("正在读取 $pendingCount 个文件的清晰度与视频信息…",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            }
            Spacer(Modifier.height(10.dp))
            SearchBox(keyword, { keyword = it }, "搜索剧名、文件名或路径")
            Spacer(Modifier.height(8.dp))
            Feedback(error = scanState.error.takeUnless { it.isServerUnreachable() }, onRetry = { vm.refreshLibrary() })
        }
        if (scan.entries.isEmpty() && scanState.loaded) item {
            EmptyContent("下载目录里还没有可管理的剧集", "支持 MP4、MKV、AVI、TS 等常见格式；隐藏目录会被跳过", Icons.Outlined.FolderOpen)
        } else if (groups.isEmpty() && scan.entries.isNotEmpty()) item {
            EmptyContent("没有匹配的剧集", "试试其他剧名或清空搜索词", Icons.Outlined.Search)
        }
        items(groups, key = { it.key }) { group ->
            val open = group.key in expanded
            val toggle = { expanded = if (open) expanded - group.key else expanded + group.key }
            val selected = selectedSeasons[group.key]?.takeIf { value -> group.entries.any { it.season == value } } ?: 0
            val rows = group.entries.filter { !group.showSeasons || selected == 0 || it.season == selected }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(toggle) {
                        Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                            if (open) "收起剧集" else "展开剧集")
                    }
                    Column(Modifier.weight(1f).clickable(onClick = toggle)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text(group.summary, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                    // 搜索与季度筛选后的删除范围只覆盖当前展示的文件。
                    TextButton({ vm.deleteLibrary(rows) }, enabled = !ui.library.deleting) {
                        Text(when {
                            selected != 0 -> "删除本季"
                            keyword.isNotBlank() -> "删除匹配"
                            else -> "删除全部"
                        }, color = HuluwaColors.Danger)
                    }
                }
                if (open) {
                    if (group.showSeasons) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected == 0, { selectedSeasons[group.key] = 0 }, label = { Text("全部") })
                            group.entries.map { it.season }.distinct().sorted().forEach { season ->
                                FilterChip(selected == season, { selectedSeasons[group.key] = season },
                                    label = { Text("第 $season 季") })
                            }
                        }
                    }
                    if (!group.entries.first().seriesRecognized) {
                        Text("暂未识别出剧名，保留原文件名便于查找", Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    rows.groupBy { it.season }.toSortedMap().forEach { (season, seasonRows) ->
                        if (group.showSeasons) {
                            Text("第 $season 季", Modifier.padding(top = 12.dp, bottom = 6.dp),
                                style = MaterialTheme.typography.titleSmall, color = HuluwaColors.Pine)
                        }
                        seasonRows.sortedWith(compareBy<LibraryEntry> {
                            if (it.episodeRecognized) it.episode else Int.MAX_VALUE
                        }.thenBy { it.episodeEnd }.thenBy { it.fileName }).groupBy { it.episodeKey }.forEach { (episodeKey, versions) ->
                            HorizontalDivider(Modifier.padding(top = 8.dp), color = HuluwaColors.Line)
                            if (versions.size == 1) {
                                LibraryFileRow(versions.first(), showEpisode = true, deleting = ui.library.deleting,
                                    onDelete = { vm.deleteLibrary(listOf(versions.first())) })
                            } else {
                                val key = "${group.key}|$episodeKey"
                                val versionsOpen = key in expandedEpisodes
                                Row(Modifier.fillMaxWidth().clickable {
                                    expandedEpisodes = if (versionsOpen) expandedEpisodes - key else expandedEpisodes + key
                                }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Text(versions.first().episodeLabel, Modifier.weight(1f),
                                        style = MaterialTheme.typography.titleMedium)
                                    Text("${versions.size} 个版本", style = MaterialTheme.typography.labelMedium,
                                        color = HuluwaColors.Muted)
                                    Icon(if (versionsOpen) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight,
                                        if (versionsOpen) "收起版本" else "展开版本", tint = HuluwaColors.Muted)
                                }
                                if (versionsOpen) versions.sortedWith(compareByDescending<LibraryEntry> {
                                    when (it.videoInfo.resolution) {
                                        "8K" -> 8; "4K" -> 7; "1440P" -> 6; "1080P" -> 5; "720P" -> 4; else -> 0
                                    }
                                }.thenByDescending { it.videoInfo.dynamicRange == "杜比视界" }.thenBy { it.fileName }).forEach { entry ->
                                    LibraryFileRow(entry, showEpisode = false, deleting = ui.library.deleting,
                                        onDelete = { vm.deleteLibrary(listOf(entry)) })
                                }
                            }
                        }
                    }
                }
            }
        }
        if (scan.scannedAt.isNotBlank()) item {
            Text("上次扫描：${scan.scannedAt}", Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

private data class LibraryGroup(val key: String, val allEntries: List<LibraryEntry>, val entries: List<LibraryEntry> = allEntries) {
    val title get() = allEntries.first().seriesTitle
    // 用整部作品判断，搜索命中第一季时仍保留多季结构；不因默认补出的 1 显示第一季。
    val showSeasons get() = allEntries.any { it.seasonExplicit && it.season > 1 }
    val episodeCount get() = libraryEpisodeCount(entries)
    val summary get() = buildList {
        if (episodeCount > 0) add("$episodeCount 集")
        add("${entries.size} 个文件")
        add(formatBytes(entries.sumOf { it.size }.toDouble()))
    }.joinToString(" · ")
}

@Composable private fun LibraryFileRow(entry: LibraryEntry, showEpisode: Boolean, deleting: Boolean, onDelete: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            if (showEpisode && entry.episodeRecognized) Text(entry.episodeLabel, style = MaterialTheme.typography.titleMedium)
            val info = entry.videoInfo
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(info.resolution.ifBlank { if (info.status == "pending") "识别中" else "清晰度未知" },
                    tone = if (info.resolution.isBlank()) HuluwaColors.Muted else HuluwaColors.Pine)
                if (info.dynamicRange.isNotBlank()) Tag(info.dynamicRange, tone = HuluwaColors.Amber)
                if (info.source == "filename") Tag("文件名标注", tone = HuluwaColors.Muted, outlined = true)
            }
            Text(entry.fileName, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(buildList {
                if (info.width > 0 && info.height > 0) add("${info.width}×${info.height}")
                add(formatBytes(entry.size.toDouble()))
                if (entry.modifiedAt.isNotBlank()) add(entry.modifiedAt)
            }.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            if (entry.relativePath != entry.fileName) Text(entry.relativePath, style = MaterialTheme.typography.labelSmall,
                color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onDelete, enabled = !deleting) { Text("删除", color = HuluwaColors.Danger) }
    }
}

@Composable private fun LibraryStat(title: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(value, color = HuluwaColors.Ink, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
    }
}
