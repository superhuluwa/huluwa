package com.huluwa.android.ui

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
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
import kotlinx.coroutines.launch

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable internal fun DengtaDetailPage(ui: DengtaState, vm: DengtaViewModel, onCalendar: (String) -> Unit) {
    val detail = ui.detail.data
    val media = detail?.media ?: ui.selected ?: return
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val tabsOffset = with(LocalDensity.current) { 52.dp.roundToPx() }
    var dialog by rememberSaveable(media.id) { mutableStateOf("") }
    var imageUrls by rememberSaveable(media.id) { mutableStateOf(emptyList<String>()) }
    var imageIndex by rememberSaveable(media.id) { mutableIntStateOf(0) }
    var imageTitle by rememberSaveable(media.id) { mutableStateOf("") }
    var videoUrl by rememberSaveable(media.id) { mutableStateOf<String?>(null) }
    val heroColor = remember(detail?.background) {
        runCatching { Color(android.graphics.Color.parseColor(detail?.background.orEmpty())) }.getOrDefault(Color(0xFF493F35))
    }
    val activeTab by remember(list, tabsOffset) {
        derivedStateOf {
            val visible = list.layoutInfo.visibleItemsInfo.filter { it.index >= 3 }
            val anchor = visible.lastOrNull { it.offset <= tabsOffset + 16 }?.index ?: 0
            when { !list.canScrollForward && list.canScrollBackward -> 3; anchor >= 6 -> 3; anchor >= 5 -> 2; anchor >= 4 -> 1; else -> 0 }
        }
    }
    val jumpTo: (Int) -> Unit = { index -> scope.launch { list.animateScrollToItem(intArrayOf(3, 4, 5, 6)[index], -tabsOffset) }; Unit }
    val showImages: (List<String>, Int, String) -> Unit = { urls, index, title ->
        val available = urls.filter(String::isNotBlank)
        if (available.isNotEmpty()) { imageUrls = available; imageIndex = index.coerceIn(0, available.lastIndex); imageTitle = title }
    }
    val openLink: (String) -> Unit = { url ->
        if (url.startsWith("https://") || url.startsWith("http://")) {
            val resource = url.substringBefore('?').lowercase()
            if (listOf(".mp4", ".m3u8", ".webm").any(resource::endsWith)) videoUrl = url
            else runCatching { uriHandler.openUri(url) }.onFailure { Toast.makeText(context, "暂时无法打开该内容", Toast.LENGTH_SHORT).show() }
        }
        Unit
    }
    val showMaterial: (DengtaMaterial, List<DengtaMaterial>) -> Unit = { material, materials ->
        if (material.type == "VIDEO" && material.targetUrl.isNotBlank()) openLink(material.targetUrl)
        else {
            val photos = materials.filter { it.type == material.type && it.preview.isNotBlank() }
            showImages(photos.map { it.preview }, photos.indexOf(material).coerceAtLeast(0), material.title.ifBlank { "${media.name} · 物料" })
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(Color.White), state = list, contentPadding = PaddingValues(bottom = 28.dp)) {
        item(key = "hero") {
            DengtaDetailHero(media, detail, heroColor,
                onCalendar = { detail?.calendarUrl?.takeIf(String::isNotBlank)?.let(onCalendar) },
                onPoster = { showImages(listOf(media.poster), 0, media.name) }, onRules = { dialog = "rules" },
                onHighlights = { dialog = "highlights" }, onPerformance = { jumpTo(0) }, onHeat = { jumpTo(1) })
        }
        stickyHeader(key = "navigation") {
            Surface(Modifier.fillMaxWidth().background(heroColor), color = Color.White, shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)) {
                Row(Modifier.fillMaxWidth().height(52.dp)) {
                    listOf("播放表现", "平台热度", "营销事件", detail?.informationTitle ?: "剧集信息").forEachIndexed { index, title ->
                        Column(Modifier.weight(1f).fillMaxHeight().clickable { jumpTo(index) },
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                            Text(title, color = if (activeTab == index) DengtaDetailColors.Orange else DengtaDetailColors.Ink,
                                fontSize = 13.sp, fontWeight = if (activeTab == index) FontWeight.SemiBold else FontWeight.Normal)
                            Spacer(Modifier.height(6.dp))
                            Box(Modifier.size(18.dp, 3.dp).clip(RoundedCornerShape(2.dp))
                                .background(if (activeTab == index) DengtaDetailColors.Orange else Color.Transparent))
                        }
                    }
                }
            }
        }
        item(key = "status") {
            if (ui.detail.loading || ui.detail.error != null || ui.journalError != null) Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DengtaDetailColors.Orange)
                ui.detail.error?.let { StatusNotice(it, if (detail != null) "正在显示上次获取的数据" else null, onRetry = vm::refreshDetail) }
                ui.journalError?.let { StatusNotice(it, onRetry = vm::loadJournal) }
            }
        }
        item(key = "performance") {
            if (detail != null) DengtaDetailSection {
                DengtaContentHeading(detail.summaryName)
                if (detail.playTrends.isEmpty()) DengtaEmpty("暂无播放走势数据") else DengtaTrendPicker(detail.playTrends)
                Text("当日数据尚未结束时，以虚线表示当日累计。", Modifier.padding(top = 10.dp), color = DengtaDetailColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        item(key = "heat") {
            if (detail != null) DengtaDetailSection {
                if (detail.heats.isEmpty()) { DengtaContentHeading("平台热度"); DengtaEmpty("暂无平台热度数据") }
                detail.heats.forEachIndexed { index, heat ->
                    if (index > 0) HorizontalDivider(Modifier.padding(vertical = 18.dp), color = DengtaDetailColors.Line)
                    DengtaContentHeading(heat.name)
                    Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                        DengtaMetric("今日实时", heat.current.ifBlank { "—" }, Modifier.weight(1f))
                        DengtaMetric("历史最高", heat.maximum.ifBlank { "—" }, Modifier.weight(1f))
                    }
                    if (heat.maximumDate.isNotBlank()) Text("历史峰值日期：${heat.maximumDate}", color = DengtaDetailColors.Muted, fontSize = 11.sp)
                    if (heat.trends.isNotEmpty()) DengtaTrendPicker(heat.trends)
                }
            }
        }
        item(key = "marketing") {
            if (detail != null) DengtaDetailSection {
                DengtaContentHeading("营销事件") { dialog = "marketing" }
                DengtaMarketingTimeline(detail.marketingEvents, onImages = showImages, onLink = openLink)
            }
        }
        item(key = "materials") {
            if (detail != null) DengtaDetailSection {
                DengtaMaterialStrip(detail.information, { dialog = "materials" }) { showMaterial(it, detail.information.materials) }
            }
        }
        item(key = "people") {
            if (detail != null) DengtaDetailSection {
                DengtaContentHeading("演职人员", if (detail.information.people.isNotEmpty()) ({ dialog = "people" }) else null)
                if (detail.information.people.isEmpty()) DengtaEmpty("暂无演职人员资料")
                else LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(detail.information.people) { person -> DengtaPersonCard(person, onImages = showImages) }
                }
            }
        }
        item(key = "companies") {
            if (detail != null) DengtaDetailSection {
                DengtaContentHeading("出品发行", if (detail.information.companies.isNotEmpty() || detail.information.companyGroups.isNotEmpty()) ({ dialog = "companies" }) else null)
                val companies = detail.information.companies.ifEmpty { detail.information.companyGroups.flatMap { it.companies }.distinctBy { it.id to it.name } }
                if (companies.isEmpty()) DengtaEmpty("暂无出品发行资料") else companies.forEach { DengtaCompanyRow(it) }
            }
        }
        item(key = "synopsis") { if (detail != null) DengtaDetailSection { DengtaSynopsis(detail.information.description, media.id) } }
        item(key = "source") { DengtaSourceNote(ui.detail.updatedAt) }
    }
    if (dialog.isNotBlank()) DengtaDetailContentDialog(dialog, ui, vm, { dialog = "" }, showImages, openLink, showMaterial)
    videoUrl?.let { url -> MediaViewer(url, true) { videoUrl = null } }
    if (imageUrls.isNotEmpty()) DengtaDetailImageGallery(imageUrls, imageIndex, imageTitle) { imageUrls = emptyList() }
}

@Composable private fun DengtaDetailSection(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp), content = content)
    HorizontalDivider(Modifier.fillMaxWidth(), thickness = 7.dp, color = Color(0xFFFAFAFB))
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
