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

/**
 * 剧集管理（系统会话，网页端「剧集管理」的原生版本）：
 * 扫描共享下载目录，按「剧名 + 季度」分组展开，支持单集删除与整组删除。
 * 删除只提交本次扫描返回的短期 ID，后端会再校验文件类型与大小。
 */
@Composable fun LibraryScreen(ui: AppState, vm: HuluwaViewModel) {
    val scanState = ui.library.scan
    val scan = scanState.data
    var keyword by rememberSaveable { mutableStateOf("") }
    var season by rememberSaveable { mutableStateOf(0) }
    var expanded by remember { mutableStateOf(setOf<String>()) }

    val seasons = scan.entries.map { it.season }.distinct().sorted()
    val filtered = scan.entries.filter { entry ->
        (season == 0 || entry.season == season) && (keyword.isBlank() ||
            "${entry.seriesTitle} ${entry.fileName} ${entry.relativePath}".contains(keyword.trim(), true))
    }
    // 分组：剧名 + 季度；保留后端给出的顺序（后端已按剧名 / 季度 / 集数排序）
    val groups = filtered.groupBy { "${it.seriesTitle}|${it.season}" }.values
        .map { rows -> Group(key = "${rows.first().seriesTitle}|${rows.first().season}", rows) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LibraryStat("已下载视频", scan.entries.size.toString(),
                    "${scan.seriesCount} 部作品 · ${seasons.size} 个季度", Modifier.weight(1f))
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
            Spacer(Modifier.height(10.dp))
            SearchBox(keyword, { keyword = it }, "搜索剧名、文件名或路径")
            Spacer(Modifier.height(10.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(season == 0, { season = 0 }, label = { Text("全部季度") })
                seasons.forEach { value ->
                    FilterChip(season == value, { season = value }, label = { Text("第 $value 季") })
                }
            }
            Spacer(Modifier.height(8.dp))
            // 加载状态统一显示在标题栏菊花上，内容区只保留错误反馈。
            Feedback(loading = false, error = scanState.error.takeUnless { it.isServerUnreachable() }, onRetry = { vm.refreshLibrary() })
        }
        if (scan.entries.isEmpty() && scanState.loaded) item {
            EmptyContent("下载目录里还没有可管理的剧集", "支持 MP4、MKV、AVI、TS 等常见格式；隐藏目录会被跳过", Icons.Outlined.FolderOpen)
        } else if (groups.isEmpty() && scan.entries.isNotEmpty()) item {
            EmptyContent("没有匹配的剧集", "试试清空搜索词或季度筛选", Icons.Outlined.Search)
        }
        items(groups, key = { it.key }) { group ->
            val open = group.key in expanded
            val toggle = { expanded = if (open) expanded - group.key else expanded + group.key }
            Panel(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(toggle) {
                        Icon(if (open) Icons.Outlined.ExpandMore else Icons.Outlined.ChevronRight, if (open) "收起剧集" else "展开剧集")
                    }
                    Column(Modifier.weight(1f).clickable(onClick = toggle)) {
                        Text(group.title, style = MaterialTheme.typography.titleMedium, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                        Text("第 ${group.season} 季 · ${group.entries.size} 集 · ${formatBytes(group.bytes.toDouble())}",
                            style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                    TextButton({ vm.deleteLibrary(group.entries) }, enabled = !ui.library.deleting) {
                        Text("删除全部", color = HuluwaColors.Danger)
                    }
                }
                if (open) {
                    Spacer(Modifier.height(6.dp))
                    HorizontalDivider(color = HuluwaColors.Line)
                    group.entries.forEach { entry ->
                        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(entry.episodeLabel, style = MaterialTheme.typography.titleMedium)
                                Text(entry.fileName, style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${formatBytes(entry.size.toDouble())} · ${entry.modifiedAt}",
                                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                            }
                            TextButton({ vm.deleteLibrary(listOf(entry)) }, enabled = !ui.library.deleting) {
                                Text("删除", color = HuluwaColors.Danger)
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

private data class Group(val key: String, val entries: List<LibraryEntry>) {
    val title get() = entries.first().seriesTitle
    val season get() = entries.first().season
    val bytes: Long get() = entries.sumOf { it.size }
}

@Composable private fun LibraryStat(title: String, value: String, hint: String, modifier: Modifier = Modifier) {
    Panel(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(value, color = HuluwaColors.Ink, fontSize = 21.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted, maxLines = 2)
    }
}
