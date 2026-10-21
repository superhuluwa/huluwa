package com.huluwa.android.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    "android-build" -> Icons.Outlined.Android
    "check-logs" -> Icons.AutoMirrored.Outlined.ListAlt
    "library" -> Icons.Outlined.VideoLibrary
    "subscriptions" -> Icons.Outlined.BookmarkBorder
    "system" -> Icons.Outlined.NotificationsNone
    else -> Icons.AutoMirrored.Outlined.Chat
}
fun sessionColor(kind: String): Color = when (kind) {
    "movie" -> HuluwaColors.Lilac
    "magnet", "docker" -> HuluwaColors.Blue
    "video" -> Color(0xFFAE6B5E)
    "xunlei", "xunlei-win" -> HuluwaColors.Amber
    "check-logs" -> Color(0xFF6E8A4E)
    "library" -> Color(0xFF4E7C6A)
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
/**
 * 各主标签共用同一套标题栏，保证字号、行高和上下留白一致；标题左对齐。
 *
 * onTap 用于隐藏手势入口：关闭水波纹与焦点反馈，点击后外观和操作都与普通标题无异。
 */
@Composable fun PageHeader(title: String, background: Color = HuluwaColors.Paper, onTap: (() -> Unit)? = null, action: @Composable () -> Unit = {}) {
    val tapModifier = if (onTap != null) Modifier.clickable(
        interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTap,
    ) else Modifier
    // 高度为原来的三分之二（94dp → 63dp）：上下留白 15dp + 16dp，标题行高 32sp。
    Row(
        Modifier.fillMaxWidth().background(background).statusBarsPadding()
            .heightIn(min = 63.dp).padding(start = 22.dp, end = 22.dp, top = 15.dp, bottom = 16.dp).then(tapModifier),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 与底部导航使用同一套标题色，避免各标签标题观感不一致。
        Text(title, Modifier.weight(1f), color = HuluwaColors.Ink, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
        action()
    }
}
/** 标题右侧的小菊花加载图标，逐瓣轮转亮度。 */
@Composable fun HeaderLoadingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "标题加载")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 960, easing = LinearEasing)),
        label = "加载轮转",
    )
    val color = HuluwaColors.Muted
    Canvas(modifier.size(20.dp).progressSemantics().semantics { contentDescription = "正在加载" }) {
        val radius = size.minDimension * .42f
        repeat(12) { index ->
            val age = (phase.toInt() - index + 12) % 12
            rotate(degrees = index * 30f) {
                drawLine(
                    color = color.copy(alpha = 1f - age * .065f),
                    start = Offset(center.x, center.y - radius),
                    end = Offset(center.x, center.y - radius * .55f),
                    strokeWidth = size.minDimension * .08f,
                    cap = StrokeCap.Round,
                )
            }
        }
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
/**
 * 下载进度条：自绘一条连续圆角色带。
 *
 * Material3 1.4 的 LinearProgressIndicator 默认会在进度与轨道之间留断口，并在轨道末端
 * 画一个悬浮圆点，0% 时也可见；下载卡片里看起来像是“断成两截”，因此这里统一自绘。
 *
 * @param progress 0..1 的进度；传 null 表示进度未知，显示一段循环扫描的色带
 */
@Composable fun DownloadProgress(
    progress: Float?, modifier: Modifier = Modifier, color: Color = HuluwaColors.Pine,
    trackColor: Color = HuluwaColors.SoftGreen, height: Dp = 6.dp,
) {
    val value by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), animationSpec = tween(320), label = "下载进度")
    // 只在进度未知时启动循环扫描，已知进度不额外刷新。
    val scan = if (progress == null) {
        val transition = rememberInfiniteTransition(label = "等待进度")
        transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = LinearEasing)), label = "进度扫描").value
    } else 0f
    Canvas(modifier.fillMaxWidth().height(height)) {
        val corner = CornerRadius(size.height / 2)
        fun band(start: Float, end: Float, tint: Color) {
            val left = start.coerceIn(0f, 1f) * size.width
            val right = end.coerceIn(0f, 1f) * size.width
            if (right > left) drawRoundRect(tint, Offset(left, 0f), Size(right - left, size.height), corner)
        }
        band(0f, 1f, trackColor)
        if (progress == null) {
            // 进度未知：约轨道三分之一宽的色带从左侧扫到右侧。
            val width = 0.32f
            val start = scan * (1f + width) - width
            band(start, start + width, color)
        } else band(0f, value, color)
    }
}
@Composable fun Feedback(loading: Boolean, error: String?, onRetry: () -> Unit) {
    if (loading) DownloadProgress(null, Modifier.fillMaxWidth(), height = 4.dp)
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
