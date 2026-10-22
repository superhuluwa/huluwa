package com.huluwa.android.ui

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.huluwa.android.data.AiMessage
import com.huluwa.android.data.AiSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first

/** Reveal state lives outside lazy items so scrolling away never restarts a reply. */
@Stable
internal class AiAnimatedMessage(message: AiMessage, animate: Boolean) {
    val id = message.id
    val role = message.role
    var text by mutableStateOf(if (animate && role == "assistant") "" else message.text)
        private set
    private var targetText by mutableStateOf(message.text)
    var entered by mutableStateOf(!animate)
    val isRevealing get() = text.length < targetText.length

    fun update(message: AiMessage) {
        // Replacements, shortened replies and tool output must stay faithful to the server.
        if (role != "assistant" || !message.text.startsWith(text)) text = message.text
        targetText = message.text
    }

    fun revealNext() {
        if (!isRevealing) return
        // Catch up with large chunks without accumulating a long animation backlog.
        val step = ((targetText.length - text.length) / 12).coerceAtLeast(1)
        var end = (text.length + step).coerceAtMost(targetText.length)
        if (end < targetText.length && Character.isHighSurrogate(targetText[end - 1]) && Character.isLowSurrogate(targetText[end])) end++
        text = targetText.substring(0, end)
    }
}

@Stable
internal class AiConversationMotion {
    val messages = mutableStateListOf<AiAnimatedMessage>()
    var initialized by mutableStateOf(false)
        private set

    fun update(incoming: List<AiMessage>) {
        val previous = messages.associateBy { it.id }
        val next = incoming.map { message ->
            previous[message.id]?.also { it.update(message) } ?: AiAnimatedMessage(message, animate = initialized)
        }
        if (next != messages) {
            messages.clear()
            messages.addAll(next)
        }
        initialized = true
    }
}

@Composable
internal fun rememberAiConversationMotion(session: AiSession, loading: Boolean): AiConversationMotion {
    val motion = remember(session.id) { AiConversationMotion() }
    LaunchedEffect(motion, session.messages, loading) {
        if (!loading) motion.update(session.messages)
    }
    LaunchedEffect(motion) {
        snapshotFlow { motion.messages.any { it.isRevealing } }.collect { pending ->
            if (pending) {
                while (true) {
                    val revealing = motion.messages.filter { it.isRevealing }
                    if (revealing.isEmpty()) break
                    revealing.forEach { it.revealNext() }
                    delay(24)
                }
            }
        }
    }
    return motion
}

@Stable
internal class AiConversationScroll(val list: LazyListState) {
    var following by mutableStateOf(true)
    var positioned by mutableStateOf(false)
    val gestures = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (source == NestedScrollSource.UserInput && available.y != 0f) following = false
            return Offset.Zero
        }
    }
}

@Composable
internal fun rememberAiConversationScroll(sessionId: String, ready: Boolean, messageCount: Int): AiConversationScroll {
    val scroll = remember(sessionId) { AiConversationScroll(LazyListState()) }
    val list = scroll.list
    LaunchedEffect(scroll, ready) {
        if (ready && !scroll.positioned) {
            val count = snapshotFlow { list.layoutInfo.totalItemsCount }.first { it >= messageCount + 1 }
            // Only opening history needs immediate positioning. Updates follow with animation.
            list.scrollToItem(count - 1)
            scroll.positioned = true
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { list.isScrollInProgress }.collect { moving ->
            if (!moving && !list.canScrollForward) scroll.following = true
        }
    }
    LaunchedEffect(scroll) {
        snapshotFlow { scroll.positioned && scroll.following }.collectLatest { follow ->
            if (follow) {
                snapshotFlow { list.canScrollForward }.collect { canScroll ->
                    if (canScroll) {
                        // Finish each short movement before measuring again; token updates do not restart it.
                        while (list.canScrollForward) {
                            val layout = list.layoutInfo
                            val lastIndex = layout.totalItemsCount - 1
                            val bottom = layout.visibleItemsInfo.lastOrNull()?.takeIf { it.index == lastIndex }
                            if (bottom == null) {
                                list.animateScrollToItem(lastIndex)
                            } else {
                                val distance = (bottom.offset + bottom.size + layout.afterContentPadding - layout.viewportEndOffset).coerceAtLeast(1)
                                list.animateScrollBy(distance.toFloat(), tween(220, easing = LinearOutSlowInEasing))
                            }
                            withFrameNanos { }
                        }
                    }
                }
            }
        }
    }
    return scroll
}
