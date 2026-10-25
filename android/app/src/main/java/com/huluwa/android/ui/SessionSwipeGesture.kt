package com.huluwa.android.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

/** 拖动直接更新位移，只有松手和主动收起时才启动动画。 */
internal class SessionSwipeState(val actionWidth: Float) {
    var offset by mutableFloatStateOf(0f)
        private set
    var dragging = false
        private set
    private var animation: Job? = null
    private var animationTarget = 0f

    fun beginDrag() {
        dragging = true
        animation?.cancel()
        animation = null
    }

    fun endDrag() {
        dragging = false
    }

    fun dragBy(delta: Float) {
        offset = (offset + delta).coerceIn(-actionWidth, 0f)
    }

    fun settle(open: Boolean, scope: CoroutineScope) {
        val target = if (open) -actionWidth else 0f
        if (animation?.isActive == true && animationTarget == target) return
        animation?.cancel()
        animationTarget = target
        if (offset == target) return
        animation = scope.launch {
            animate(offset, target, animationSpec = tween(180)) { value, _ -> offset = value }
        }
    }
}

/**
 * 在 Initial 阶段锁定横向手势，先于 LazyColumn 和行点击的 Main 阶段消费拖动。
 * 方向尚未确定时不消费事件，先越过纵向阈值则完全交由列表滚动处理。
 */
internal suspend fun PointerInputScope.detectSessionSwipe(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: (velocity: Float, completed: Boolean) -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var movement = Offset.Zero
        var horizontal = false
        var completed = false
        val slop = viewConfiguration.touchSlop
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) break
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!change.pressed) {
                    if (horizontal) {
                        change.consume()
                        completed = true
                    }
                    break
                }
                val delta = change.position - change.previousPosition
                if (horizontal) {
                    change.consume()
                    onDrag(delta.x)
                } else {
                    movement += delta
                    val x = abs(movement.x)
                    val y = abs(movement.y)
                    if (y > slop && y >= x) break
                    if (x > slop && x > y) {
                        horizontal = true
                        change.consume()
                        onDragStart()
                        onDrag(movement.x - sign(movement.x) * slop)
                    }
                }
            }
        } finally {
            if (horizontal) {
                val velocity = if (completed) tracker.calculateVelocity().x else 0f
                onDragEnd(velocity, completed)
            }
        }
    }
}
