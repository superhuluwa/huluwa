package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huluwa.android.data.FanqieBook
import com.huluwa.android.data.FanqieReaderCard

/** 搜索结果整张卡片和阅读按钮均可直接打开 App 内阅读页。 */
@Composable fun FanqieResultCard(book: FanqieBook, onRead: () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClickLabel = "阅读${book.title}", onClick = onRead),
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Poster(book.title, book.cover, size = DpSize(64.dp, 90.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) Text("作者：${book.author}",
                    style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(book.meta, style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Pine, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.summary.isNotBlank()) Text(book.summary, style = MaterialTheme.typography.bodySmall,
                    color = HuluwaColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (book.lastChapterTitle.isNotBlank()) Text("最新：${book.lastChapterTitle}", Modifier.weight(1f),
                style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Spacer(Modifier.weight(1f))
            Button(onClick = onRead, contentPadding = PaddingValues(horizontal = 14.dp)) {
                Icon(Icons.Outlined.MenuBook, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("立即阅读", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 兼容会话指令返回的阅读入口。实际位置以服务器的最新进度为准。 */
@Composable fun FanqieReaderResultCard(card: FanqieReaderCard, onOpen: () -> Unit) {
    Column(Modifier.clickable(role = Role.Button, onClick = onOpen), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Poster(card.title, card.cover, size = DpSize(64.dp, 90.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.author.isNotBlank()) Text(card.author, style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("点击在 App 内阅读", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Pine)
            }
        }
        Button(onClick = onOpen, Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.MenuBook, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (card.progressChapter > 0) "继续阅读" else "开始阅读")
        }
    }
}

