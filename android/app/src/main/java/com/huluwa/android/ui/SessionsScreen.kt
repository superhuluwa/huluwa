package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.Session
import com.huluwa.android.data.isServerUnreachable
import kotlin.math.roundToInt

internal val SessionsBackground: Color
    get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFFF9FBF8) else HuluwaColors.Paper

private object SessionsColors {
    val Ink get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFF20382C) else HuluwaColors.Ink
    val Muted get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFF657367) else HuluwaColors.Muted
    val Function get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFF2B6A49) else HuluwaColors.Pine
    val System get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFF4B6A9A) else HuluwaColors.Pine
    val SystemSoft get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFFEAF0FA) else HuluwaColors.SoftGreen
    val Line get() = if (HuluwaColors.skin == AppSkin.Pine) Color(0xFFE9EEE7) else HuluwaColors.Line
}

@Composable fun SessionsScreen(ui: AppState, vm: HuluwaViewModel, system: Boolean = false) {
    val rows = ui.sessions.data.filter { it.isSystemSession == system }
    val listState = rememberLazyListState()
    var revealedSessionId by remember(system, ui.auth.server, ui.auth.username) { mutableStateOf<String?>(null) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) revealedSessionId = null
    }
    BackHandler(revealedSessionId != null) { revealedSessionId = null }

    LazyColumn(Modifier.fillMaxSize().skinBackground(SessionsBackground), state = listState,
        overscrollEffect = rememberBounceOverscroll()) {
        // 加载状态显示在标题右侧，列表只保留错误反馈，避免切换标签时挤动内容。
        // 「无法连接服务器」由标题栏的「重连中」徽标统一承载，这里不再重复渲染成错误卡片。
        if (ui.sessions.error != null && !ui.sessions.error.isServerUnreachable()) item {
            Feedback(error = ui.sessions.error, onRetry = { vm.refreshSessions() })
        }
        if (rows.isEmpty()) item {
            EmptyContent(if (system) "暂无系统会话" else "暂无功能会话", "", loading = !ui.sessions.loaded)
        }
        itemsIndexed(rows, key = { _, session -> session.id }) { index, session ->
            SwipeableSessionListRow(
                session = session,
                pinned = session.id in ui.pinnedSessionIds,
                showDivider = index != rows.lastIndex,
                revealed = revealedSessionId == session.id,
                onRevealChange = { open ->
                    if (open) revealedSessionId = session.id
                    else if (revealedSessionId == session.id) revealedSessionId = null
                },
                onTogglePin = { revealedSessionId = null; vm.toggleSessionPin(session.id) },
                onClick = {
                    if (revealedSessionId != null) revealedSessionId = null else vm.openSession(session)
                },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

/** 固定的整行容器优先识别横向拖动，内容跟随手指移动，右侧操作区始终留在原位。 */
@Composable private fun SwipeableSessionListRow(
    session: Session,
    pinned: Boolean,
    showDivider: Boolean,
    revealed: Boolean,
    onRevealChange: (Boolean) -> Unit,
    onTogglePin: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val actionWidth = 88.dp
    val density = LocalDensity.current
    val actionWidthPx = with(density) { actionWidth.toPx() }
    val tone = if (session.isSystemSession) SessionsColors.System else SessionsColors.Function
    val actionLabel = if (pinned) "取消置顶" else "置顶"
    val swipeState = remember(session.id, actionWidthPx) { SessionSwipeState(actionWidthPx) }
    val scope = rememberCoroutineScope()
    val updateReveal by rememberUpdatedState(onRevealChange)
    val velocityThreshold = with(density) { 600.dp.toPx() }

    LaunchedEffect(revealed, swipeState) {
        if (!revealed && !swipeState.dragging) swipeState.settle(false, scope)
    }

    Box(
        modifier.fillMaxWidth().clipToBounds().pointerInput(swipeState, velocityThreshold) {
            detectSessionSwipe(
                onDragStart = {
                    swipeState.beginDrag()
                    updateReveal(true)
                },
                onDrag = swipeState::dragBy,
                onDragEnd = { velocity, completed ->
                    swipeState.endDrag()
                    val open = completed && when {
                        velocity < -velocityThreshold -> true
                        velocity > velocityThreshold -> false
                        else -> swipeState.offset < -actionWidthPx / 2f
                    }
                    updateReveal(open)
                    swipeState.settle(open, scope)
                },
            )
        },
    ) {
        val menuVisible = swipeState.offset < -0.5f
        // 背景菜单始终占据同一个布局节点，拖动过程中只移动前景内容。
        Box(
            Modifier.matchParentSize().then(if (menuVisible) Modifier else Modifier.clearAndSetSemantics {}),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Column(
                Modifier.fillMaxHeight().width(actionWidth).background(tone)
                    .clickable(enabled = menuVisible, role = Role.Button, onClick = onTogglePin),
                verticalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(Icons.Outlined.PushPin, null, Modifier.size(20.dp), tint = Color.White)
                Text(actionLabel, color = Color.White, fontSize = 13.sp)
            }
        }
        SessionListRow(
            session = session,
            pinned = pinned,
            showDivider = showDivider,
            modifier = Modifier.absoluteOffset { IntOffset(swipeState.offset.roundToInt(), 0) }
                .semantics {
                    if (pinned) stateDescription = "已置顶"
                    customActions = listOf(CustomAccessibilityAction(actionLabel) { onTogglePin(); true })
                },
            onClick = {
                if (revealed || menuVisible) {
                    onRevealChange(false)
                    swipeState.settle(false, scope)
                } else onClick()
            },
        )
    }
}

@Composable private fun SessionListRow(
    session: Session,
    pinned: Boolean,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val tone = if (session.isSystemSession) SessionsColors.System else SessionsColors.Function
    val background = if (pinned) tone.copy(alpha = 0.06f).compositeOver(SessionsBackground) else Color.Transparent
    Box(modifier.fillMaxWidth().background(background).clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 66.4.dp).padding(horizontal = 21.dp, vertical = 9.2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SessionListIcon(session)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        session.title, Modifier.weight(1f, fill = false), color = SessionsColors.Ink,
                        fontSize = 15.sp, lineHeight = 23.sp, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(13.dp), tint = tone)
                }
                Text(
                    session.preview, color = SessionsColors.Muted, fontSize = 12.sp, lineHeight = 18.sp,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier.widthIn(min = 28.dp).height(44.dp), horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                if (session.managementService != null) {
                    Text(
                        "管理", Modifier.clip(RoundedCornerShape(5.dp)).background(SessionsColors.SystemSoft)
                            .padding(horizontal = 7.dp, vertical = 3.dp),
                        color = tone, fontSize = 10.sp, lineHeight = 14.sp,
                    )
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(15.dp), tint = tone.copy(alpha = .7f))
                } else {
                    Text(
                        timeLabel(session.updatedAt.ifBlank { session.last?.createdAt.orEmpty() }),
                        color = SessionsColors.Muted, fontSize = 10.sp, lineHeight = 14.sp,
                    )
                    if (session.unread > 0) SessionUnreadBadge(session.unread, tone)
                }
            }
        }
        if (showDivider) HorizontalDivider(
            Modifier.align(Alignment.BottomCenter).padding(start = 78.dp, end = 21.dp), color = SessionsColors.Line,
        )
    }
}

@Composable private fun SessionListIcon(session: Session) {
    if (!session.isSystemSession) {
        SessionIcon(session.kind)
    } else {
        val background = when (session.kind) {
            "upgrade" -> Color(0xFFE4EDE8)
            "docker" -> Color(0xFFE4EBF4)
            "android-build", "terminalbuddy-build" -> Color(0xFFE4F0E7)
            "check-logs" -> Color(0xFFEDF1E5)
            "library" -> Color(0xFFE8EFE9)
            "subscriptions" -> Color(0xFFF0EBF6)
            else -> Color(0xFFE9E5F1)
        }
        val tone = when (session.kind) {
            "upgrade" -> Color(0xFF5F8D77)
            "docker" -> Color(0xFF6A87B2)
            "android-build", "terminalbuddy-build" -> Color(0xFF4E8C68)
            "check-logs" -> Color(0xFF6E8A4E)
            "library" -> Color(0xFF4E7C6A)
            "subscriptions" -> Color(0xFF7E6AA0)
            else -> Color(0xFF8E7BAC)
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(13.dp)).background(background),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sessionIcon(session.kind), null, Modifier.size(24.dp), tint = tone)
        }
    }
}

@Composable private fun SessionUnreadBadge(unread: Int, tone: Color) {
    val label = if (unread > 99) "99+" else unread.toString()
    Box(
        Modifier.height(17.dp).widthIn(min = 17.dp).clip(CircleShape).background(tone).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label, Modifier.semantics { contentDescription = "$unread 条未读消息" }, color = Color.White,
            fontSize = 9.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1,
        )
    }
}
