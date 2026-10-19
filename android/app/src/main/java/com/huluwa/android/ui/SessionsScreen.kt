package com.huluwa.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel

@Composable fun SessionsScreen(ui: AppState, vm: HuluwaViewModel) {
    var unreadOnly by rememberSaveable { mutableStateOf(false) }
    val rows = ui.sessions.data.filter { !unreadOnly || it.unread > 0 }
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp)) {
        item {
            SectionTitle("全部会话 ${ui.sessions.data.size}") {
                FilterChip(!unreadOnly, { unreadOnly = false }, label = { Text("全部") })
                Spacer(Modifier.width(6.dp)); FilterChip(unreadOnly, { unreadOnly = true }, label = { Text("未读 ${ui.sessions.data.count { it.unread > 0 }}") })
            }
            Feedback(ui.sessions.loading, ui.sessions.error, { vm.refreshSessions() })
        }
        if (rows.isEmpty()) item {
            EmptyContent(if (unreadOnly) "消息都已读完" else "暂无会话",
                if (ui.sessions.error != null) "连接成功后，会话会自动显示在这里" else "内置服务会由服务器自动提供", loading = !ui.sessions.loaded)
        }
        itemsIndexed(rows, key = { _, row -> row.id }) { index, session ->
            val shape = RoundedCornerShape(topStart = if (index == 0) 20.dp else 0.dp, topEnd = if (index == 0) 20.dp else 0.dp,
                bottomStart = if (index == rows.lastIndex) 20.dp else 0.dp, bottomEnd = if (index == rows.lastIndex) 20.dp else 0.dp)
            Surface(Modifier.fillMaxWidth().clickable { vm.openSession(session) }, shape = shape, color = Color.White) {
                Column {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SessionIcon(session.kind)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(session.preview, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Text(timeLabel(session.last?.createdAt.orEmpty()), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                            if (session.unread > 0) Badge(containerColor = HuluwaColors.Pine) { Text(if (session.unread > 99) "99+" else session.unread.toString()) }
                        }
                    }
                    if (index != rows.lastIndex) HorizontalDivider(Modifier.padding(start = 70.dp, end = 14.dp), color = HuluwaColors.Line.copy(alpha = .65f))
                }
            }
        }
        item { Text("发条消息，让家里的服务为你忙起来", Modifier.fillMaxWidth().padding(vertical = 20.dp), color = HuluwaColors.Muted,
            style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
