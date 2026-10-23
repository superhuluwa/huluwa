package com.huluwa.android.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.NorthEast
import androidx.compose.material.icons.outlined.SouthWest
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.data.AiSession
import java.util.Locale
import kotlin.math.roundToInt

/** 卡片收起后保留独立展开入口，同一会话内记住展开状态。 */
@Composable internal fun AiConversationUsageCard(
    session: AiSession, modelLabel: String, effortLabel: String, details: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(session.id) { mutableStateOf(true) }
    AnimatedContent(
        targetState = expanded,
        modifier = modifier,
        contentAlignment = Alignment.BottomEnd,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 60)) +
                slideInVertically(tween(300, easing = FastOutSlowInEasing)) { it / 4 }) togetherWith
                (fadeOut(tween(120)) +
                    slideOutVertically(tween(200, easing = FastOutSlowInEasing)) { it / 4 }) using
                SizeTransform(clip = true, sizeAnimationSpec = { _, _ -> tween(300, easing = FastOutSlowInEasing) })
        },
        label = "模型与用量收展",
    ) { showing ->
        if (showing) {
            AiExpandedUsageCard(session, modelLabel, effortLabel, details, { expanded = false }, Modifier.fillMaxWidth())
        } else {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Surface(color = AiColors.SoftGreen, shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, AiColors.Line)) {
                    IconButton(
                        onClick = { expanded = true },
                        modifier = Modifier.size(48.dp).semantics { stateDescription = "已收起模型与用量" },
                    ) {
                        Icon(Icons.Outlined.KeyboardArrowUp, "展开模型与用量", Modifier.size(22.dp), tint = HuluwaColors.Pine)
                    }
                }
            }
        }
    }
}

/** 模型信息与真实用量共用一张轻卡片；缺失统计不按零用量展示。 */
@Composable private fun AiExpandedUsageCard(
    session: AiSession, modelLabel: String, effortLabel: String, details: () -> Unit,
    collapse: () -> Unit, modifier: Modifier,
) {
    val usage = session.usage
    val contextUsage = usage?.context
    val used = contextUsage?.usedTokens?.takeIf { it >= 0 }
    val capacity = contextUsage?.windowTokens?.takeIf { it > 0 }
    val ratio = contextUsage?.percentage?.div(100)
    val percent = ratio?.let { (it * 100).roundToInt().toString() } ?: "—"
    val estimate = if (contextUsage?.estimated == true && ratio != null) "≈" else ""
    val context = if (contextUsage?.updating == true) "更新中(${formatAiTokensK(used)}/${formatAiTokensK(capacity)})"
        else "$estimate$percent%(${formatAiTokensK(used)}/${formatAiTokensK(capacity)})"
    val tokens = usage?.lastTurn ?: usage?.sessionTotal
    val fontScale = LocalDensity.current.fontScale
    Surface(modifier, color = AiColors.Background, shape = RoundedCornerShape(15.dp), border = BorderStroke(1.dp, AiColors.Line)) {
        Column {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "查看模型与用量详情", onClick = details)
                    .heightIn(min = 48.dp).padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                    AiAgentLogo(session.provider, Modifier.size(22.dp))
                    Text("${session.providerLabel} · $modelLabel", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Surface(color = AiColors.SoftGreen, shape = RoundedCornerShape(7.dp)) {
                        Text("思考 $effortLabel", Modifier.padding(horizontal = 8.dp, vertical = 5.dp), color = HuluwaColors.Pine,
                            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal))
                    }
                }
                Box(Modifier.width(1.dp).height(18.dp).background(AiColors.Line))
                IconButton(
                    onClick = collapse,
                    modifier = Modifier.size(48.dp).semantics { stateDescription = "已展开模型与用量" },
                ) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "收起模型与用量", Modifier.size(20.dp), tint = HuluwaColors.Pine)
                }
            }
            AiDivider()
            BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val stackedContext = maxWidth < 280.dp || fontScale > 1.15f
                val stackedAmounts = maxWidth < 220.dp || fontScale > 1.4f
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (stackedContext) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            AiContextLabel()
                            AiContextValue(context)
                        }
                        AiContextProgress(ratio?.toFloat(), context, Modifier.fillMaxWidth())
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AiContextLabel()
                            AiContextProgress(ratio?.toFloat(), context, Modifier.weight(1f))
                            AiContextValue(context)
                        }
                    }
                    if (stackedAmounts) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            AiTokenAmount("输入", tokens?.inputTokens, Icons.Outlined.SouthWest)
                            AiTokenAmount("输出", tokens?.outputTokens, Icons.Outlined.NorthEast)
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            AiTokenAmount("输入", tokens?.inputTokens, Icons.Outlined.SouthWest)
                            AiTokenAmount("输出", tokens?.outputTokens, Icons.Outlined.NorthEast)
                        }
                    }
                }
            }
        }
    }
}

@Composable private fun AiContextLabel() {
    Text("上下文长度", color = AiColors.Muted,
        style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal))
}

@Composable private fun AiContextValue(value: String) {
    Text(value, style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum"))
}

@Composable private fun AiContextProgress(ratio: Float?, description: String, modifier: Modifier = Modifier) {
    val progress by animateFloatAsState(targetValue = ratio?.coerceIn(0f, 1f) ?: 0f, animationSpec = tween(180), label = "上下文占比")
    val color = when {
        ratio == null -> AiColors.Muted
        ratio >= .9f -> HuluwaColors.Danger
        ratio >= .75f -> HuluwaColors.Amber
        else -> HuluwaColors.Pine
    }
    Box(modifier.height(4.dp).clip(RoundedCornerShape(4.dp)).background(AiColors.Line)
        .then(if (ratio != null) Modifier.progressSemantics(ratio.coerceIn(0f, 1f)) else Modifier)
        .semantics {
            contentDescription = "上下文占用"
            stateDescription = if (ratio == null) "暂无完整上下文统计，$description" else description
        }) {
        if (ratio != null && progress > 0f) Box(Modifier.fillMaxWidth(progress).fillMaxHeight().background(color, RoundedCornerShape(4.dp)))
    }
}

@Composable private fun AiTokenAmount(label: String, tokens: Long?, icon: ImageVector) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(12.dp), tint = AiColors.Muted)
        Text(label, color = AiColors.Muted,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, fontWeight = FontWeight.Normal))
        Text(formatAiTokensK(tokens), style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontFeatureSettings = "tnum"))
    }
}

/** 统一使用十进制 K；小于 1K 时保留到单个 Token，避免非零用量被显示为零。 */
private fun formatAiTokensK(tokens: Long?): String {
    if (tokens == null || tokens < 0) return "—K"
    if (tokens == 0L) return "0K"
    val precision = if (tokens < 1_000) 3 else 1
    return String.format(Locale.ROOT, "%.${precision}f", tokens / 1_000.0).trimEnd('0').trimEnd('.') + "K"
}
