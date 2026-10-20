package com.huluwa.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.OverscrollEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.LayoutModifierNode
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * iOS 风格的列表回弹：拖到顶部/底部边界外时内容跟随手指移动（带阻尼），松手后弹回原位。
 *
 * Android 默认的 overscroll 在 12+ 是拉伸、旧版本是边缘发光，观感不统一；这里统一替换为位移回弹。
 * 用法：`LazyColumn(overscrollEffect = rememberBounceOverscroll())`。
 */
@Composable fun rememberBounceOverscroll(): OverscrollEffect {
    val scope = rememberCoroutineScope()
    return remember(scope) { BounceOverscrollEffect(scope) }
}

private class BounceOverscrollEffect(private val scope: CoroutineScope) : OverscrollEffect {
    /** 内容相对边界的位移（px）：正数表示向下拖出顶部，负数表示向上拖出底部。 */
    private val displacement = Animatable(0f)

    /** 列表可视高度，用于按比例限制最大拉伸并计算阻尼；布局前为 0，退回默认值。 */
    private var containerHeight = 0f

    private val dimension: Float get() = if (containerHeight > 0f) containerHeight else 600f
    private val maxPull: Float get() = dimension * 0.4f

    override fun applyToScroll(
        delta: Offset,
        source: NestedScrollSource,
        performScroll: (Offset) -> Offset,
    ): Offset {
        if (delta.y == 0f) return performScroll(delta)
        val current = displacement.value
        // 正在回弹的方向与拖动方向相反时，先把位移收回去，退回边界后再正常滚动。
        val sameDirection = current == 0f || sign(delta.y) == sign(current)
        val consumedByPreScroll = if (abs(current) > 0.5f && !sameDirection) {
            val next = current + delta.y
            if (sign(next) != sign(current)) {
                // 越过边界：本次拖动先把位移收回到 0，剩余部分留给列表滚动。
                scope.launch { displacement.snapTo(0f) }
                Offset(0f, -current)
            } else {
                scope.launch { displacement.snapTo(next) }
                Offset(0f, delta.y)
            }
        } else Offset.Zero

        val leftForScroll = delta - consumedByPreScroll
        val consumedByScroll = performScroll(leftForScroll)
        val leftover = leftForScroll - consumedByScroll
        // 只有手指拖动才拉出边界；惯性滑动到边界后由 applyToFling 处理。
        if (source == NestedScrollSource.UserInput && abs(leftover.y) > 0.5f) {
            val pulled = displacement.value + resist(leftover.y, displacement.value)
            scope.launch { displacement.snapTo(pulled.coerceIn(-maxPull, maxPull)) }
        }
        return consumedByPreScroll + consumedByScroll
    }

    override suspend fun applyToFling(
        velocity: Velocity,
        performFling: suspend (Velocity) -> Velocity,
    ) {
        val consumed = performFling(velocity)
        val leftover = velocity - consumed
        if (displacement.value == 0f && leftover.y == 0f) return
        // 带着松手时的速度弹回，接近边界时有一点自然的回弹感。
        displacement.animateTo(
            targetValue = 0f,
            initialVelocity = leftover.y.coerceIn(-MAX_RELEASE_VELOCITY, MAX_RELEASE_VELOCITY),
            animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        )
    }

    override val isInProgress: Boolean get() = displacement.value != 0f

    /** 拖得越远阻尼越大，避免列表被整屏拉走。 */
    private fun resist(delta: Float, current: Float): Float =
        delta * 0.55f / (1f + 3f * abs(current) / dimension)

    override val node: DelegatableNode = object : Modifier.Node(), LayoutModifierNode {
        override fun MeasureScope.measure(measurable: androidx.compose.ui.layout.Measurable, constraints: Constraints): MeasureResult {
            val placeable = measurable.measure(constraints)
            containerHeight = placeable.height.toFloat()
            return layout(placeable.width, placeable.height) {
                placeable.placeWithLayer(0, displacement.value.roundToInt())
            }
        }
    }

    private companion object {
        /** 回弹初始速度上限（px/s），防止快速滑动时弹得过猛。 */
        const val MAX_RELEASE_VELOCITY = 3200f
    }
}
