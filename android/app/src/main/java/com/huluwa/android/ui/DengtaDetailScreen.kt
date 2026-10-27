package com.huluwa.android.ui

import android.text.Html
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.DengtaState
import com.huluwa.android.DengtaViewModel
import com.huluwa.android.data.*
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.roundToInt

@Composable internal fun DengtaDetailPage(ui: DengtaState, vm: DengtaViewModel, onCalendar: (String) -> Unit) {
    val detail = ui.detail.data
    val media = detail?.media ?: ui.selected ?: return
    var tab by rememberSaveable(media.id) { mutableIntStateOf(0) }
    val heroColor = remember(detail?.background) {
        runCatching { Color(android.graphics.Color.parseColor(detail?.background.orEmpty())) }.getOrDefault(HuluwaColors.PineDark)
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.fillMaxWidth().background(heroColor).padding(22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    DengtaPoster(media.poster, media.name, Modifier.size(90.dp, 136.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text(media.name, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(detail?.genres?.ifBlank { media.typeName } ?: media.typeName, color = Color(0xFFF0EBDD), style = MaterialTheme.typography.bodySmall)
                        Text(listOfNotNull(media.episodeCount?.let { "共${it}集" }, media.updateText.takeIf(String::isNotBlank)).joinToString(" · "), color = Color(0xFFF0EBDD), style = MaterialTheme.typography.bodySmall)
                        Text(media.platforms.ifBlank { "播出平台待公布" }, color = Color(0xFFF0EBDD), style = MaterialTheme.typography.bodySmall)
                        detail?.openDay?.takeIf(String::isNotBlank)?.let { Text("$it 首播", color = Color(0xFFF0EBDD), style = MaterialTheme.typography.labelMedium) }
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(detail?.rankNo?.let { "${detail?.rankName.orEmpty().ifBlank { "全网日榜" }} 第${it}名" } ?: media.typeName.ifBlank { "影视作品" },
                        Modifier.weight(1f), color = HuluwaColors.Lime, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    FilledTonalButton({ vm.toggleFollow(media) }, enabled = !ui.journalBusy && !ui.journalLoading,
                        colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color.White.copy(alpha = .15f), contentColor = Color.White)) {
                        Icon(if (ui.selectedFollow?.following == true) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd, null, Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (ui.selectedFollow?.following == true) "已追剧" else "加入追剧", fontSize = 12.sp)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (ui.detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HuluwaColors.Pine)
                ui.detail.error?.let { StatusNotice(it, if (detail != null) "正在显示上次获取的数据" else null, onRetry = vm::refreshDetail) }
                ui.journalError?.let { StatusNotice(it, onRetry = vm::loadJournal) }
                if (detail != null) {
                    Panel(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("今日正片播放量", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                            Text("最新快照", color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium)
                        }
                        Text(detail.today.label, Modifier.padding(top = 4.dp), fontSize = 34.sp, fontWeight = FontWeight.Bold)
                        HorizontalDivider(Modifier.padding(vertical = 15.dp), color = HuluwaColors.Line)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DengtaMetric("播放市占", detail.share.label, Modifier.weight(1f))
                            DengtaMetric("累计播放量", detail.total.label, Modifier.weight(1f))
                            DengtaMetric("集均播放量", detail.average.label, Modifier.weight(1f))
                        }
                        if (detail.statisticsDate.isNotBlank()) Text(detail.statisticsDate, Modifier.padding(top = 12.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        if (detail.honors.isNotEmpty()) {
                            val honor = remember(detail.honors) { Html.fromHtml(detail.honors.first(), Html.FROM_HTML_MODE_LEGACY).toString().trim() }
                            Text("高光时刻 · $honor", Modifier.padding(top = 14.dp), color = HuluwaColors.Amber, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                if (detail != null && detail.calendarUrl.isNotBlank()) {
                    Surface(onClick = { onCalendar(detail.calendarUrl) }, color = HuluwaColors.SoftGreen, shape = MaterialTheme.shapes.medium) {
                        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Outlined.CalendarMonth, null, tint = HuluwaColors.Pine)
                            Column(Modifier.weight(1f)) {
                                Text(detail.calendarTitle, fontWeight = FontWeight.SemiBold)
                                Text("点击查看更新排期", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                            }
                            Icon(Icons.AutoMirrored.Outlined.ArrowForward, "查看${detail.calendarTitle}图片", Modifier.size(19.dp), tint = HuluwaColors.Pine)
                        }
                    }
                }
                if (detail != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("播放表现", "平台热度", "剧集信息").forEachIndexed { index, text ->
                            TextButton({ tab = index }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Text(text, color = if (tab == index) HuluwaColors.Pine else HuluwaColors.Muted, fontWeight = if (tab == index) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                    when (tab) {
                        0 -> Panel(Modifier.fillMaxWidth()) {
                            Text("正片播放走势", style = MaterialTheme.typography.titleMedium)
                            if (detail.playTrends.isEmpty()) DengtaEmpty("暂无播放走势数据")
                            else DengtaTrendPicker(detail.playTrends)
                            Text("当日数据尚未结束时，以虚线表示当日累计，避免与全天数据混淆。", Modifier.padding(top = 10.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                        1 -> {
                            if (detail.heats.isEmpty()) Panel(Modifier.fillMaxWidth()) { DengtaEmpty("暂无平台热度数据") }
                            detail.heats.forEach { heat ->
                                Panel(Modifier.fillMaxWidth()) {
                                    Text(heat.name, style = MaterialTheme.typography.titleMedium)
                                    Row(Modifier.fillMaxWidth().padding(top = 15.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                                        DengtaMetric("最新热度", heat.current.ifBlank { "—" }, Modifier.weight(1f))
                                        DengtaMetric("历史最高", heat.maximum.ifBlank { "—" }, Modifier.weight(1f))
                                    }
                                    if (heat.maximumDate.isNotBlank()) Text("历史峰值日期：${heat.maximumDate}", Modifier.padding(top = 8.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                                    if (heat.trends.isNotEmpty()) DengtaTrendPicker(heat.trends)
                                    Text("不同平台热度口径独立，不直接跨平台比较。", Modifier.padding(top = 12.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                        else -> {
                            Panel(Modifier.fillMaxWidth()) {
                                Text("剧集信息", style = MaterialTheme.typography.titleMedium)
                                listOf("作品类型" to media.typeName, "题材" to detail.genres, "播出平台" to media.platforms,
                                    "剧集进度" to media.updateText, "总集数" to media.episodeCount?.let { "${it}集" }.orEmpty(),
                                    "首播日期" to detail.openDay, "上线时间" to detail.playTime,
                                    "单集时长" to detail.duration.takeIf(String::isNotBlank)?.let { "约${it}分钟" }.orEmpty()).forEach { (label, value) ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                        Text(label, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                                        Text(value.ifBlank { "暂未提供" }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            DengtaRules(ui, vm)
                        }
                    }
                }
                DengtaSourceNote(ui.detail.updatedAt)
            }
        }
    }
}

@Composable private fun DengtaMetric(title: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable internal fun DengtaRules(ui: DengtaState, vm: DengtaViewModel) {
    if (ui.detail.data?.hasPlayRule != true) return
    Panel(Modifier.fillMaxWidth()) {
        Text("平台更新规则", style = MaterialTheme.typography.titleMedium)
        if (ui.rules.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 12.dp), color = HuluwaColors.Pine)
        ui.rules.error?.let { StatusNotice(it, onRetry = vm::retryRules) }
        ui.rules.data.forEach { rule ->
            Text("${rule.platform} · ${rule.update}", Modifier.padding(top = 14.dp), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
            Text(rule.rule.ifBlank { "暂无详细更新规则" }, Modifier.padding(top = 5.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        if (ui.rules.loaded && ui.rules.data.isEmpty()) Text("暂无更新规则", Modifier.padding(top = 12.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable private fun DengtaTrendPicker(series: List<DengtaSeries>) {
    var selected by rememberSaveable(series.map { it.name }) { mutableIntStateOf(0) }
    val current = series.getOrElse(selected) { series.first() }
    if (series.size > 1) DengtaDropdown(current.name, series.map { it.name }) { name -> selected = series.indexOfFirst { it.name == name }.coerceAtLeast(0) }
    DengtaTrend(current)
}

@Composable private fun DengtaTrend(series: DengtaSeries) {
    val points = series.points
    var selected by rememberSaveable(series.name, points.lastOrNull()?.date) { mutableIntStateOf(points.lastIndex) }
    if (points.isEmpty()) return
    val point = points[selected.coerceIn(points.indices)]
    val max = points.mapNotNull { it.value.raw }.maxOrNull()?.takeIf { it > 0 } ?: 1.0
    val today = LocalDate.now(ZoneId.of("Asia/Shanghai")).toString()
    val isToday = points.last().date == today
    val percent = points.first().value.unit == "%"
    fun axis(value: Double) = if (percent) "${(value * 100).roundToInt()}%" else dengtaNumber(value)
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        DengtaDropdown(point.date, points.map { it.date }) { date -> selected = points.indexOfFirst { it.date == date }.coerceAtLeast(0) }
        Text(point.value.label, color = HuluwaColors.Pine, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
    Row(Modifier.fillMaxWidth().height(152.dp)) {
        Column(Modifier.width(43.dp).fillMaxHeight().padding(vertical = 8.dp), verticalArrangement = Arrangement.SpaceBetween) {
            listOf(max, max / 2, 0.0).forEach { Text(axis(it), color = HuluwaColors.Muted, fontSize = 10.sp) }
        }
        Canvas(Modifier.weight(1f).fillMaxHeight().semantics { contentDescription = "${series.name}走势，共${points.size}个日期。选择下拉日期可查看具体数值。" }
            .pointerInput(points) { detectTapGestures { offset ->
                selected = if (points.size == 1) 0 else ((offset.x / size.width) * points.lastIndex).roundToInt().coerceIn(points.indices)
            } }) {
            val margin = 8.dp.toPx()
            val height = size.height - margin * 2
            fun position(index: Int) = Offset(if (points.size == 1) size.width / 2 else margin + (size.width - margin * 2) * index / points.lastIndex,
                margin + height * (1 - ((points[index].value.raw ?: 0.0) / max).toFloat()))
            repeat(3) { i -> drawLine(HuluwaColors.Line, Offset(0f, margin + height * i / 2), Offset(size.width, margin + height * i / 2), 1.dp.toPx()) }
            for (i in 1..points.lastIndex) {
                val unfinished = isToday && i == points.lastIndex
                drawLine(if (unfinished) HuluwaColors.Amber else HuluwaColors.Pine, position(i - 1), position(i), 2.5.dp.toPx(), StrokeCap.Round,
                    pathEffect = if (unfinished) PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 5.dp.toPx())) else null)
            }
            val selectedPosition = position(selected.coerceIn(points.indices))
            drawCircle(HuluwaColors.Pine, 4.dp.toPx(), selectedPosition)
        }
    }
    Row(Modifier.fillMaxWidth().padding(start = 43.dp, top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        listOf(points.first().date, points[points.lastIndex / 2].date, points.last().date).distinct().forEach {
            Text(it.takeLast(5), fontSize = 11.sp, color = HuluwaColors.Muted)
        }
    }
}
