package com.huluwa.android.ui

import android.text.Html
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.huluwa.android.data.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

internal object DengtaDetailColors {
    val Orange = Color(0xFFFF6335)
    val Ink = Color(0xFF242733)
    val Muted = Color(0xFF989AA2)
    val Line = Color(0xFFF0F1F4)
    val Gold = Color(0xFFEAD39C)
}

@Composable internal fun DengtaDetailHero(
    media: DengtaMedia, detail: DengtaDetail?, background: Color,
    onCalendar: () -> Unit, onPoster: () -> Unit, onRules: () -> Unit,
    onHighlights: () -> Unit, onPerformance: () -> Unit, onHeat: () -> Unit,
) {
    val muted = Color.White.copy(alpha = .65f)
    Column(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(background, androidx.compose.ui.graphics.lerp(background, Color.Black, .25f))))
        .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            DengtaContentImage(media.poster, "${media.name} 海报", Modifier.size(76.dp, 114.dp).clip(RoundedCornerShape(3.dp)).clickable(onClick = onPoster))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(media.name, Modifier.weight(1f, fill = false), color = Color.White, fontSize = 21.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (media.typeName.isNotBlank()) Surface(color = Color.Transparent, border = BorderStroke(.5.dp, muted), shape = RoundedCornerShape(2.dp)) {
                        Text(media.typeName, Modifier.padding(horizontal = 3.dp, vertical = 1.dp), color = muted, fontSize = 9.sp)
                    }
                }
                if (!detail?.englishName.isNullOrBlank()) Text(detail?.englishName.orEmpty(), color = muted, fontSize = 10.sp, maxLines = 1)
                Text(detail?.genres.orEmpty().ifBlank { media.typeName }, color = muted, fontSize = 11.sp)
                val episodes = listOfNotNull(media.episodeCount?.let { "${it}集" },
                    detail?.duration?.takeIf(String::isNotBlank)?.let { "每集${it}分钟" }).joinToString(" / ")
                Text(episodes + media.updateText.takeIf(String::isNotBlank)?.let { "（$it）" }.orEmpty(), color = muted, fontSize = 10.sp)
                detail?.openDay?.takeIf(String::isNotBlank)?.let {
                    Text(listOf(it, detail?.playTime.orEmpty()).filter(String::isNotBlank).joinToString("  ") + " 上线", color = muted, fontSize = 10.sp)
                }
                Row(Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onRules).padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("播放平台", color = muted, fontSize = 10.sp)
                    if (detail?.platformIcons?.isNotEmpty() == true) detail.platformIcons.forEachIndexed { index, url ->
                        AsyncImage(url, media.platforms.split(" / ").getOrNull(index) ?: "播放平台", Modifier.size(15.dp), contentScale = ContentScale.Fit)
                    } else Text(media.platforms.ifBlank { "待公布" }, color = muted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(Icons.Outlined.ChevronRight, "查看播放平台与更新规则", Modifier.size(13.dp), tint = muted)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(9.dp)) {
                if (!detail?.calendarUrl.isNullOrBlank()) Surface(onClick = onCalendar,
                    color = Color.Black.copy(alpha = .16f), shape = RoundedCornerShape(4.dp)) {
                    Row(Modifier.padding(horizontal = 5.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(12.dp), tint = muted)
                        Text(detail?.calendarTitle.orEmpty(), color = muted, fontSize = 10.sp)
                    }
                }
                detail?.rankNo?.let { rank ->
                    Column(Modifier.width(67.dp).clip(RoundedCornerShape(4.dp))
                        .background(Brush.linearGradient(listOf(Color(0xFFFFE8AD), Color(0xFFF3C46E))))
                        .padding(horizontal = 4.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(detail?.rankName.orEmpty().ifBlank { "全网正片播放量\n日榜" }, color = Color(0xFF493515), fontSize = 9.sp, lineHeight = 12.sp, maxLines = 3)
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text("TOP ", color = Color(0xFF493515), fontSize = 10.sp)
                            Text(rank.toString(), color = Color(0xFF493515), fontSize = 24.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                        }
                    }
                }
            }
        }
        if (detail != null) {
            if (detail.highlights.isNotEmpty() || detail.honors.isNotEmpty()) DengtaHeroPanel(onClick = onHighlights) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Icon(Icons.Outlined.EmojiEvents, null, Modifier.size(17.dp), tint = DengtaDetailColors.Gold)
                    Text("高光时刻", color = DengtaDetailColors.Gold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(Modifier.padding(vertical = 9.dp), color = Color.White.copy(alpha = .05f))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val first = detail.highlights.firstOrNull()
                    Text(dengtaPlainText(first?.html ?: detail.honors.firstOrNull().orEmpty()), Modifier.weight(1f),
                        color = DengtaDetailColors.Gold, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (first != null && first.timestamp > 0) Text(dengtaHighlightTime(first.timestamp), color = muted, fontSize = 10.sp)
                    Text("${detail.highlightCount.coerceAtLeast(detail.highlights.size)}条动态", color = DengtaDetailColors.Gold, fontSize = 10.sp)
                    Icon(Icons.Outlined.ChevronRight, "查看全部高光动态", Modifier.size(13.dp), tint = DengtaDetailColors.Gold)
                }
            }
            DengtaHeroPanel(onClick = onPerformance) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(detail.summaryName, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                    Icon(Icons.Outlined.ChevronRight, "查看播放表现", Modifier.size(15.dp), tint = muted)
                }
                HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = .05f))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    DengtaHeroMetric(detail.todayName, detail.today, "累计播放量:  ${detail.total.label}", Modifier.weight(1f), true)
                    DengtaHeroMetric("实时播放市占", detail.share, "历史最高:  ${detail.shareMaximum.label}", Modifier.weight(1f))
                    DengtaHeroMetric("今日舆情热度", detail.opinion, "历史最高:  ${detail.opinionMaximum.label}", Modifier.weight(1f))
                }
                Spacer(Modifier.height(13.dp))
                DengtaHeroMetric("集均播放量", detail.average, detail.statisticsDate, Modifier.fillMaxWidth())
            }
            detail.heats.forEach { heat ->
                DengtaHeroPanel(onClick = onHeat) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(heat.name, Modifier.weight(1f), color = Color.White, fontSize = 13.sp)
                        Icon(Icons.Outlined.ChevronRight, "查看${heat.name}走势", Modifier.size(15.dp), tint = muted)
                    }
                    HorizontalDivider(Modifier.padding(vertical = 10.dp), color = Color.White.copy(alpha = .05f))
                    Row(Modifier.fillMaxWidth()) {
                        DengtaHeroHeat("今日实时", heat.current, Modifier.weight(1f))
                        DengtaHeroHeat("历史最高", heat.maximum, Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable private fun DengtaHeroPanel(onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(onClick = onClick, color = Color.Black.copy(alpha = .23f), shape = RoundedCornerShape(5.dp)) {
        Column(Modifier.fillMaxWidth().padding(12.dp), content = content)
    }
}

@Composable private fun DengtaHeroMetric(label: String, value: DengtaValue, note: String, modifier: Modifier, accent: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(label, color = Color.White.copy(alpha = .65f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value.value.ifBlank { "—" }, color = if (accent) DengtaDetailColors.Orange else Color.White,
                fontSize = if (value.value.length > 6) 18.sp else 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            if (value.value.isNotBlank()) Text(value.unit, color = if (accent) DengtaDetailColors.Orange else Color.White,
                fontSize = 12.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        if (note.isNotBlank()) Text(note, color = Color.White.copy(alpha = .5f), fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun DengtaHeroHeat(title: String, value: String, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Color.White.copy(alpha = .6f), fontSize = 10.sp)
        Text(value.ifBlank { "—" }, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable internal fun DengtaContentHeading(title: String, onAll: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, Modifier.weight(1f), color = DengtaDetailColors.Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        if (onAll != null) Row(Modifier.clip(RoundedCornerShape(4.dp)).clickable(onClick = onAll).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("查看全部", color = DengtaDetailColors.Muted, fontSize = 11.sp)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(14.dp), tint = DengtaDetailColors.Muted)
        }
    }
}

@Composable internal fun DengtaMarketingTimeline(
    events: List<DengtaMarketingEvent>, expanded: Boolean = false,
    onImages: (List<String>, Int, String) -> Unit, onLink: (String) -> Unit,
) {
    if (events.isEmpty()) DengtaEmpty("暂无营销事件", "定档、预告、海报等动态将在发布后显示")
    events.forEachIndexed { index, event ->
        Row(Modifier.fillMaxWidth().drawBehind {
            if (index < events.lastIndex) drawLine(DengtaDetailColors.Line, Offset(9.dp.toPx(), 14.dp.toPx()), Offset(9.dp.toPx(), size.height), 1.dp.toPx())
        }) {
            Column(Modifier.width(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.padding(top = 6.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(DengtaDetailColors.Orange))
            }
            Column(Modifier.weight(1f).padding(start = 5.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    if (event.type.isNotBlank()) Text(event.type, Modifier.background(Color(0xFFF2F3F5), RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp), color = DengtaDetailColors.Muted, fontSize = 10.sp)
                    Text(event.title, Modifier.weight(1f), color = DengtaDetailColors.Ink, fontSize = 13.sp, lineHeight = 21.sp,
                        maxLines = if (expanded) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis)
                }
                if (expanded && event.images.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(event.images.size) { imageIndex ->
                        Box(Modifier.size(if (event.images.size == 1) 168.dp else 100.dp, 128.dp).clip(RoundedCornerShape(5.dp))
                            .clickable { if (event.sourceType == "VIDEO" && event.targetUrl.isNotBlank()) onLink(event.targetUrl)
                                else onImages(event.images, imageIndex, event.title) }) {
                            DengtaContentImage(event.images[imageIndex], event.title, Modifier.fillMaxSize())
                            if (event.sourceType == "VIDEO") Icon(Icons.Outlined.PlayCircle, "播放视频", Modifier.size(34.dp).align(Alignment.Center), tint = Color.White)
                        }
                    }
                }
                if (expanded && event.targetUrl.isNotBlank()) TextButton({ onLink(event.targetUrl) }, contentPadding = PaddingValues(0.dp)) {
                    Text(if (event.sourceType == "VIDEO") "播放视频" else "查看详情", color = DengtaDetailColors.Orange, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (event.dateText.isNotBlank()) Text(event.dateText, color = DengtaDetailColors.Orange, fontSize = 11.sp)
                    Text(event.date, color = DengtaDetailColors.Muted, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable internal fun DengtaMaterialStrip(info: DengtaInformation, onAll: () -> Unit, onMaterial: (DengtaMaterial) -> Unit) {
    DengtaContentHeading("物料", if (info.materials.isNotEmpty()) onAll else null)
    if (info.materials.isEmpty()) DengtaEmpty("暂无物料")
    else LazyRow(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        items(info.materials) { material -> DengtaMaterialThumbnail(material, Modifier.size(if (material.type == "VIDEO") 160.dp else 78.dp, 116.dp)) { onMaterial(material) } }
        item {
            Box(Modifier.size(78.dp, 116.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFF1F2F4)).clickable(onClick = onAll), contentAlignment = Alignment.Center) {
                Text("查看全部\n${info.materials.maxOfOrNull { it.count }?.takeIf { it > 0 }?.let { "${it}个" }.orEmpty()}",
                    color = DengtaDetailColors.Muted, fontSize = 11.sp, lineHeight = 20.sp)
            }
        }
    }
}

@Composable internal fun DengtaMaterialThumbnail(material: DengtaMaterial, modifier: Modifier, onClick: () -> Unit) {
    Box(modifier.clip(RoundedCornerShape(5.dp)).clickable(onClick = onClick)) {
        DengtaContentImage(material.preview, material.title.ifBlank { "剧集物料" }, Modifier.fillMaxSize())
        if (material.type == "VIDEO") Icon(Icons.Outlined.PlayCircle, "播放视频", Modifier.size(32.dp).align(Alignment.Center), tint = Color.White)
        if (material.title.isNotBlank()) Text(material.title, Modifier.fillMaxWidth().align(Alignment.BottomCenter)
            .background(Color.Black.copy(alpha = .45f)).padding(5.dp), color = Color.White, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun DengtaPersonCard(person: DengtaPerson, modifier: Modifier = Modifier, onImages: (List<String>, Int, String) -> Unit) {
    Column(modifier.width(82.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.fillMaxWidth().height(116.dp)) {
            DengtaContentImage(person.portrait, person.name, Modifier.fillMaxSize().clip(RoundedCornerShape(5.dp))
                .clickable(enabled = person.portrait.isNotBlank()) { onImages(listOf(person.portrait), 0, person.name) })
            if (person.rolePortrait.isNotBlank()) Surface(Modifier.size(31.dp, 40.dp).align(Alignment.BottomEnd)
                .clickable { onImages(listOf(person.rolePortrait), 0, person.roleLabel) }, border = BorderStroke(1.dp, Color.White), shape = RoundedCornerShape(3.dp)) {
                DengtaContentImage(person.rolePortrait, person.roleLabel, Modifier.fillMaxSize())
            }
        }
        Text(person.name, color = DengtaDetailColors.Ink, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(person.roleLabel, color = DengtaDetailColors.Muted, fontSize = 10.sp, maxLines = 2, lineHeight = 14.sp, overflow = TextOverflow.Ellipsis)
    }
}

@Composable internal fun DengtaCompanyRow(company: DengtaCompany) {
    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
        DengtaContentImage(company.logo, company.name, Modifier.size(31.dp).clip(RoundedCornerShape(4.dp)), ContentScale.Fit)
        Text(company.name, color = DengtaDetailColors.Ink, fontSize = 13.sp)
    }
}

@Composable internal fun DengtaSynopsis(text: String, mediaId: String) {
    var expanded by rememberSaveable(mediaId) { mutableStateOf(false) }
    DengtaContentHeading("影片简介")
    if (text.isBlank()) DengtaEmpty("暂无简介") else {
        Text(dengtaPlainText(text), color = DengtaDetailColors.Ink, fontSize = 13.sp, lineHeight = 23.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4, overflow = TextOverflow.Ellipsis)
        if (text.length > 80) TextButton({ expanded = !expanded }, Modifier, contentPadding = PaddingValues(0.dp)) {
            Text(if (expanded) "收起" else "展开", color = Color(0xFF4884FF), fontSize = 12.sp)
            Icon(if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, null, Modifier.size(16.dp), tint = Color(0xFF4884FF))
        }
    }
}

@Composable internal fun DengtaContentImage(url: String, label: String, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.background(Color(0xFFF2F3F5)), contentAlignment = Alignment.Center) {
        if (url.isNotBlank() && !failed) AsyncImage(url, label, Modifier.fillMaxSize(), contentScale = scale, onError = { failed = true })
        else Icon(Icons.Outlined.Image, "$label 暂无图片", Modifier.size(22.dp), tint = DengtaDetailColors.Muted)
    }
}

internal fun dengtaPlainText(html: String): String = Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim()
internal fun dengtaHighlightTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate()
    val days = ChronoUnit.DAYS.between(date, LocalDate.now(ZoneId.of("Asia/Shanghai")))
    return when (days) { 0L -> "今天"; 1L -> "1天前"; in 2L..6L -> "${days}天前"; else -> date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")) }
}
