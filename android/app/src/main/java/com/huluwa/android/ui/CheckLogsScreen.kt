package com.huluwa.android.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.CheckLog
import com.huluwa.android.data.isServerUnreachable

/**
 * 巡检记录（系统会话，网页端「巡检记录」的原生版本）：
 * 电影港与电影天堂两个来源共用一条时间线，卡片展示结果、来源、新增 / 提交条数与明细。
 */
@Composable fun CheckLogsScreen(ui: AppState, vm: HuluwaViewModel) {
    val logs = ui.checkLogs
    var source by rememberSaveable { mutableStateOf("all") }
    val rows = if (source == "all") logs.data else logs.data.filter { it.source == source }
    val updated = logs.data.count { it.status == "updated" }
    val failed = logs.data.count { it.status == "failed" }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LogStat("巡检记录", logs.data.size.toString(), "最近 200 条", Modifier.weight(1f))
                LogStat("有更新", updated.toString(), "发现新资源", Modifier.weight(1f))
                LogStat("失败", failed.toString(), "站点或解析异常", Modifier.weight(1f), HuluwaColors.Danger)
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("all" to "全部来源", "dygangs" to "电影港", "dytt" to "电影天堂").forEach { (key, label) ->
                    FilterChip(source == key, { source = key }, label = { Text(label) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Feedback(logs.loading, logs.error.takeUnless { it.isServerUnreachable() }, { vm.refreshCheckLogs() })
        }
        if (rows.isEmpty() && logs.loaded) item {
            EmptyContent("还没有巡检记录", "添加订阅后，每个巡检周期都会在这里留一条结果", Icons.AutoMirrored.Outlined.ListAlt)
        }
        items(rows, key = { it.id }) { log -> CheckLogCard(log) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun LogStat(title: String, value: String, hint: String, modifier: Modifier = Modifier,
                               tone: Color = HuluwaColors.Ink) {
    Panel(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        Spacer(Modifier.height(4.dp))
        Text(value, color = tone, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted, maxLines = 2,
            textAlign = TextAlign.Start)
    }
}

@Composable private fun CheckLogCard(log: CheckLog) {
    var expanded by remember(log.id) { mutableStateOf(false) }
    val tone = when (log.status) {
        "updated" -> HuluwaColors.Pine
        "failed" -> HuluwaColors.Danger
        "baseline" -> HuluwaColors.Amber
        else -> HuluwaColors.Muted
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(log.seriesTitle.ifBlank { "未知订阅" }, Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Tag(log.statusLabel, tone = tone, outlined = log.status == "ok")
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Tag(log.sourceLabel, tone = if (log.source == "dytt") HuluwaColors.Lilac else HuluwaColors.Pine)
            Tag(log.triggerLabel, tone = HuluwaColors.Muted, outlined = true)
            if (log.hasUpdate) Tag("新增 ${log.foundCount} · 提交 ${log.submittedCount}")
        }
        Spacer(Modifier.height(9.dp))
        Text("${log.createdAt}${if (log.durationMs > 0) " · 耗时 ${log.durationText}" else ""}",
            style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        if (log.message.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(log.message, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Ink)
        }
        if (log.details.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = HuluwaColors.Line)
            Spacer(Modifier.height(8.dp))
            val visible = if (expanded) log.details else log.details.take(4)
            visible.forEach { line ->
                Text(line, Modifier.padding(bottom = 3.dp), style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Muted)
            }
            if (log.details.size > 4) TextButton({ expanded = !expanded }) {
                Text(if (expanded) "收起明细" else "展开其余 ${log.details.size - 4} 条")
            }
        }
    }
}
