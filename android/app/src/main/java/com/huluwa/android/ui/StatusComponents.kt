package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.huluwa.android.data.LoadState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

internal fun statusPercent(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.1f%%", it) } ?: "—"
internal fun statusNumber(value: Double?): String = value?.let { String.format(Locale.ROOT, "%.2f", it) } ?: "—"
internal fun statusBytes(value: Double?): String {
    if (value == null || !value.isFinite() || value < 0) return "—"
    val units = listOf("B", "KiB", "MiB", "GiB", "TiB")
    var size: Double = value
    var unit = 0
    while (size >= 1024 && unit < units.lastIndex) { size /= 1024; unit++ }
    return String.format(Locale.ROOT, if (unit == 0) "%.0f %s" else "%.1f %s", size, units[unit])
}
internal fun statusTime(timestamp: Long): String {
    if (timestamp <= 0) return "尚未获取"
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())
    return date.format(DateTimeFormatter.ofPattern(if (date.toLocalDate() == LocalDate.now()) "HH:mm:ss" else "MM-dd HH:mm"))
}
internal fun statusUpdatedAt(timestamp: Long, stale: Boolean = false): String = when {
    timestamp <= 0 -> "尚未获取"
    stale -> "上次获取 · ${statusTime(timestamp)}"
    System.currentTimeMillis() - timestamp in 0L..9_999L -> "刚刚获取"
    else -> "获取于 ${statusTime(timestamp)}"
}
internal fun <T> LoadState<T>.hasSnapshot(): Boolean = updatedAt > 0

@Composable internal fun StatusNotice(
    title: String, description: String? = null, tone: Color = HuluwaColors.Amber, onRetry: (() -> Unit)? = null,
) {
    Panel(Modifier.fillMaxWidth(), tone.copy(alpha = .08f), contentPadding = 12.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, Modifier.padding(top = 2.dp).size(18.dp), tint = tone)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = tone, style = MaterialTheme.typography.bodySmall)
                description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                }
            }
            if (onRetry != null) TextButton(onRetry, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("重试") }
        }
    }
}

@Composable internal fun StatusProgress(percent: Double?, color: Color = HuluwaColors.Pine, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(4.dp)).background(HuluwaColors.Line)
        .semantics { contentDescription = if (percent == null) "尚无用量数据" else statusPercent(percent) }) {
        if (percent != null && percent > 0) Box(Modifier.fillMaxHeight().fillMaxWidth((percent / 100).toFloat().coerceIn(0f, 1f)).background(color))
    }
}

@Composable internal fun StatusSkeleton() {
    Panel(Modifier.fillMaxWidth().semantics { contentDescription = "正在获取状态" }) {
        Box(Modifier.width(120.dp).height(15.dp).clip(RoundedCornerShape(5.dp)).background(HuluwaColors.Line))
        Spacer(Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            repeat(2) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.width(70.dp).height(30.dp).clip(RoundedCornerShape(6.dp)).background(HuluwaColors.Line))
                    Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(HuluwaColors.Paper))
                }
            }
        }
    }
}
