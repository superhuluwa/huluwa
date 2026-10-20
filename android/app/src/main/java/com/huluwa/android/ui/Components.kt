package com.huluwa.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.huluwa.android.R
import com.huluwa.android.data.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun sessionIcon(kind: String): ImageVector = when (kind) {
    "movie" -> Icons.Outlined.LocalMovies
    "dytt" -> Icons.Outlined.PlayCircleOutline
    "magnet" -> Icons.Outlined.CloudDownload
    "video" -> Icons.Outlined.SmartDisplay
    "xunlei", "xunlei-win" -> Icons.Outlined.Bolt
    "upgrade" -> Icons.Outlined.SystemUpdate
    "frp" -> Icons.Outlined.AccountTree
    "docker" -> Icons.Outlined.DirectionsBoat
    "system" -> Icons.Outlined.NotificationsNone
    else -> Icons.AutoMirrored.Outlined.Chat
}
fun sessionColor(kind: String): Color = when (kind) {
    "movie" -> HuluwaColors.Lilac
    "magnet", "docker" -> HuluwaColors.Blue
    "video" -> Color(0xFFAE6B5E)
    "xunlei", "xunlei-win" -> HuluwaColors.Amber
    else -> HuluwaColors.Pine
}

/** 会话品牌图标与网页版 SessionIcon.vue 保持一致；无品牌图标的会话返回 null。 */
private fun sessionBrandIcon(kind: String): Int? = when (kind) {
    "movie" -> R.drawable.session_icon_movie
    "dytt" -> R.drawable.session_icon_dytt
    "magnet" -> R.drawable.session_icon_magnet
    "video" -> R.drawable.session_icon_video
    "xunlei", "xunlei-win" -> R.drawable.session_icon_xunlei
    else -> null
}
@Composable fun Panel(modifier: Modifier = Modifier, color: Color = Color.White, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(20.dp), color = color,
        border = if (color == Color.White) BorderStroke(1.dp, HuluwaColors.Line) else null) {
        Column(Modifier.padding(16.dp), content = content)
    }
}
@Composable fun RoundIcon(icon: ImageVector, color: Color = HuluwaColors.Pine, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(color.copy(alpha = .10f)), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(24.dp), tint = color)
    }
}
@Composable fun SessionIcon(kind: String, modifier: Modifier = Modifier) {
    val brandIcon = sessionBrandIcon(kind)
    if (brandIcon != null) {
        Image(painterResource(brandIcon), null, modifier.size(44.dp), contentScale = ContentScale.Fit)
    } else {
        RoundIcon(sessionIcon(kind), sessionColor(kind), modifier)
    }
}
@Composable fun HeaderAction(icon: ImageVector, label: String, onClick: () -> Unit, enabled: Boolean = true) {
    Surface(shape = RoundedCornerShape(16.dp), color = Color.White, border = BorderStroke(1.dp, HuluwaColors.Line)) {
        IconButton(onClick, enabled = enabled, modifier = Modifier.size(48.dp)) { Icon(icon, label, tint = HuluwaColors.Pine) }
    }
}
/** 各主标签共用同一套标题栏，保证字号、行高和上下留白一致。 */
@Composable fun PageHeader(title: String, background: Color = HuluwaColors.Paper, action: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(background).statusBarsPadding()
            .heightIn(min = 94.dp).padding(start = 22.dp, end = 22.dp, top = 23.dp, bottom = 24.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 与底部导航使用同一套标题色，避免各标签标题观感不一致。
        Text(title, Modifier.weight(1f), color = HuluwaColors.Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        action()
    }
}
@Composable fun SearchBox(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(value, onChange, modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(15.dp),
        leadingIcon = { Icon(Icons.Outlined.Search, null, tint = HuluwaColors.Muted) }, placeholder = { Text(placeholder, style = MaterialTheme.typography.bodySmall) },
        textStyle = MaterialTheme.typography.bodyMedium, colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = Color.White, focusedContainerColor = Color.White, unfocusedBorderColor = HuluwaColors.Line))
}
@Composable fun SectionTitle(title: String, modifier: Modifier = Modifier, action: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f)); action()
    }
}
@Composable fun Tag(text: String, tone: Color = HuluwaColors.Pine, outlined: Boolean = false, modifier: Modifier = Modifier) {
    Surface(modifier, color = if (outlined) Color.Transparent else tone.copy(alpha = .08f), shape = RoundedCornerShape(7.dp), border = if (outlined) BorderStroke(1.dp, HuluwaColors.Line) else null) {
        Text(text, Modifier.padding(horizontal = 7.dp, vertical = 4.dp), color = tone, style = MaterialTheme.typography.labelSmall)
    }
}
@Composable fun StatusTag(text: String, healthy: Boolean = true, alert: Boolean = false) {
    Tag(text, if (alert) HuluwaColors.Danger else if (healthy) HuluwaColors.Pine else HuluwaColors.Muted)
}
@Composable fun ConnectionHero(title: String, subtitle: String, icon: ImageVector, healthy: Boolean = true) {
    Panel(Modifier.fillMaxWidth(), HuluwaColors.SoftGreen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(if (healthy) HuluwaColors.Pine else HuluwaColors.Amber))
                    Text(subtitle, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
            RoundIcon(icon, modifier = Modifier.size(48.dp))
        }
    }
}
@Composable fun FilterRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { index, label -> FilterChip(selected == index, { onSelect(index) }, label = { Text(label) }) }
    }
}
@Composable fun Metric(value: String, title: String, modifier: Modifier = Modifier, color: Color = HuluwaColors.Ink) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp)); Text(title, color = if (color == Color.White) Color.White.copy(alpha = .8f) else HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}
@Composable fun Feedback(loading: Boolean, error: String?, onRetry: () -> Unit) {
    if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
    if (error != null) Panel(Modifier.fillMaxWidth(), Color(0xFFF8F0E4)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Info, null, tint = HuluwaColors.Amber, modifier = Modifier.size(20.dp))
            Text(error, color = HuluwaColors.Amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onRetry) { Text("重试") }
        }
    }
}
@Composable fun EmptyContent(title: String, detail: String, icon: ImageVector = Icons.Outlined.Inbox, loading: Boolean = false, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 46.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.dp)
        else RoundIcon(icon, modifier = Modifier.size(56.dp))
        Text(if (loading) "正在同步…" else title, style = MaterialTheme.typography.titleMedium)
        Text(detail, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable fun Poster(title: String, cover: String, modifier: Modifier = Modifier) {
    Box(modifier.width(70.dp).height(100.dp).clip(RoundedCornerShape(11.dp))
        .background(Brush.linearGradient(listOf(Color(0xFFC7D6B7), Color(0xFF577A62)))), contentAlignment = Alignment.Center) {
        Text(title.take(8), Modifier.padding(10.dp), color = Color.White, style = MaterialTheme.typography.titleMedium)
        if (cover.isNotBlank()) AsyncImage(cover, title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
@Composable fun LabelValue(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        Spacer(Modifier.height(4.dp)); SelectionContainer { Text(value.ifBlank { "—" }, style = MaterialTheme.typography.bodyMedium) }
    }
}
@Composable fun ForwardLink(label: String, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick, enabled = enabled) { Text(label); Spacer(Modifier.width(3.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(15.dp)) }
}
fun timeLabel(raw: String): String = runCatching {
    val value = LocalDateTime.parse(raw.replace(' ', 'T').take(19))
    when (value.toLocalDate()) {
        LocalDate.now() -> value.format(DateTimeFormatter.ofPattern("HH:mm"))
        LocalDate.now().minusDays(1) -> "昨天"
        else -> value.format(DateTimeFormatter.ofPattern("MM-dd"))
    }
}.getOrElse { raw.take(16) }
fun plainText(raw: String): String = raw.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("</(?:p|div|li)>", RegexOption.IGNORE_CASE), "\n")
    .replace(Regex("<[^>]+>"), "").replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").trim()
