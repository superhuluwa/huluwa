package com.huluwa.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.Session

internal val SessionsBackground = Color(0xFFF9FBF8)

private object SessionsColors {
    val Ink = Color(0xFF20382C)
    val Muted = Color(0xFF657367)
    val Function = Color(0xFF2B6A49)
    val System = Color(0xFF4B6A9A)
    val SystemSoft = Color(0xFFEAF0FA)
    val Line = Color(0xFFE9EEE7)
}

@Composable fun SessionsScreen(ui: AppState, vm: HuluwaViewModel, system: Boolean = false) {
    val rows = ui.sessions.data.filter { it.isSystemSession == system }
    val listState = rememberLazyListState()

    LazyColumn(Modifier.fillMaxSize().background(SessionsBackground), state = listState) {
        if (ui.sessions.loading || ui.sessions.error != null) item {
            Feedback(ui.sessions.loading, ui.sessions.error, { vm.refreshSessions() })
        }
        if (rows.isEmpty()) item {
            EmptyContent(if (system) "暂无系统会话" else "暂无功能会话", "", loading = !ui.sessions.loaded)
        }
        itemsIndexed(rows, key = { _, session -> session.id }) { index, session ->
            SessionListRow(session, index != rows.lastIndex) { vm.openSession(session) }
        }
    }
}

@Composable private fun SessionListRow(session: Session, showDivider: Boolean, onClick: () -> Unit) {
    val tone = if (session.isSystemSession) SessionsColors.System else SessionsColors.Function
    Box(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 83.dp).padding(horizontal = 21.dp, vertical = 17.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
        ) {
            SessionListIcon(session)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    session.title, color = SessionsColors.Ink, fontSize = 15.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
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
            "android-build" -> Color(0xFFE4F0E7)
            else -> Color(0xFFE9E5F1)
        }
        val tone = when (session.kind) {
            "upgrade" -> Color(0xFF5F8D77)
            "docker" -> Color(0xFF6A87B2)
            "android-build" -> Color(0xFF4E8C68)
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
