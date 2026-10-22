package com.huluwa.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke

/** 页面级错误条：左侧图标 + 错误文本 + 关闭按钮；限高避免把整页顶出屏幕。 */
@Composable internal fun AiError(message: String, dismiss: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(HuluwaColors.Danger.copy(alpha = .08f)).padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(16.dp), tint = HuluwaColors.Danger)
        Text(
            message, Modifier.weight(1f).padding(horizontal = 8.dp, vertical = 8.dp).heightIn(max = 80.dp),
            color = HuluwaColors.Danger,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        IconButton(dismiss) { Icon(Icons.Outlined.Close, "关闭提示", Modifier.size(18.dp), tint = HuluwaColors.Danger) }
    }
}

/** 根界面中的固定悬浮提示，不参与页面内容的测量和排版。 */
@Composable internal fun AiConnectingOverlay(modifier: Modifier = Modifier) {
    Surface(
        modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = RoundedCornerShape(24.dp), color = Color.White,
        border = BorderStroke(1.dp, HuluwaColors.Line), shadowElevation = 6.dp,
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AiSpinner(HuluwaColors.Pine, Modifier.size(16.dp))
            Text("连接中", color = HuluwaColors.Ink, fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1)
        }
    }
}

/** 与标题栏重连徽标同一套十二瓣画法，逐瓣轮转亮度。 */
@Composable internal fun AiSpinner(color: Color, modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "AI 连接")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 960, easing = LinearEasing)),
        label = "连接轮转",
    )
    Canvas(modifier) {
        val radius = size.minDimension * .42f
        repeat(12) { index ->
            val age = (phase.toInt() - index + 12) % 12
            rotate(degrees = index * 30f) {
                drawLine(
                    color = color.copy(alpha = 1f - age * .065f),
                    start = androidx.compose.ui.geometry.Offset(center.x, center.y - radius),
                    end = androidx.compose.ui.geometry.Offset(center.x, center.y - radius * .55f),
                    strokeWidth = size.minDimension * .08f,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

/** 细分割线，颜色统一走 AI 页的浅绿分隔色。 */
@Composable internal fun AiDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier, color = AiColors.Line)
}

/** 状态小圆点：绿色表示在线 / 执行中，红色表示失败，灰色表示空闲。 */
@Composable internal fun AiStatusDot(color: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(5.dp).background(color, CircleShape))
}

/** 带描边的卡片容器，AI 首页和会话页的卡片都复用它。 */
@Composable internal fun AiCard(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier, color = Color.White, shape = shape, border = BorderStroke(1.dp, AiColors.Line)) {
        Column(content = content)
    }
}
