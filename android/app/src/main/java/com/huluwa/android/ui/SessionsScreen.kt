package com.huluwa.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel

@Composable fun SessionsScreen(ui: AppState, vm: HuluwaViewModel) {
    val rows = ui.sessions.data
    LazyColumn(Modifier.fillMaxSize()) {
        if (ui.sessions.error != null) item {
            Feedback(false, ui.sessions.error, { vm.refreshSessions() })
        }
        if (rows.isEmpty()) item {
            EmptyContent("暂无会话", "内置服务会由服务器自动提供", loading = !ui.sessions.loaded)
        }
        itemsIndexed(rows, key = { _, row -> row.id }) { index, session ->
            Column(Modifier.fillMaxWidth().clickable { vm.openSession(session) }) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SessionIcon(session.kind)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(session.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(session.preview, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(Modifier.height(44.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                        Text(timeLabel(session.last?.createdAt.orEmpty()), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                        if (session.unread > 0) Badge(containerColor = HuluwaColors.Pine) { Text(if (session.unread > 99) "99+" else session.unread.toString()) }
                    }
                }
                if (index != rows.lastIndex) HorizontalDivider(Modifier.padding(start = 72.dp), color = HuluwaColors.Line.copy(alpha = .65f))
            }
        }
        item { Text("发条消息，让家里的服务为你忙起来", Modifier.fillMaxWidth().padding(vertical = 20.dp), color = HuluwaColors.Muted,
            style = MaterialTheme.typography.labelMedium, textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
}
