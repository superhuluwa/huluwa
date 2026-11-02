package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huluwa.android.AppSheet
import com.huluwa.android.HuluwaViewModel
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

@Composable fun FanqieShelfSheet(sheet: AppSheet.FanqieShelf, vm: HuluwaViewModel) {
    HuluwaBottomSheet(title = "我的书架", onDismissRequest = vm::dismissSheet,
        titleExtra = { IconButton(vm::showFanqieShelf, enabled = !sheet.data.loading) {
            Icon(Icons.Outlined.Refresh, "刷新书架")
        } }) {
        when {
            sheet.data.loading -> Box(Modifier.fillMaxWidth().height(180.dp), Alignment.Center) {
                CircularProgressIndicator()
            }
            sheet.data.error != null -> Column(Modifier.fillMaxWidth().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Text(sheet.data.error, color = HuluwaColors.Danger)
                TextButton(vm::showFanqieShelf) { Text("重试") }
            }
            sheet.data.data.isEmpty() -> Text("还没有读过的小说，发送书名或作者搜索，点击书目即可开始阅读。",
                Modifier.padding(24.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.bodyMedium)
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp),
                contentPadding = PaddingValues(bottom = 20.dp)) {
                items(sheet.data.data, key = { it.bookId }) { book ->
                    Row(Modifier.fillMaxWidth().clickable {
                        vm.openReader(book.bookId, book.title, book.author, book.cover)
                    }.padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Poster(book.title, book.cover, size = DpSize(48.dp, 66.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleSmall,
                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (book.author.isNotBlank()) Text(book.author, color = HuluwaColors.Muted,
                                style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(book.progressLabel, color = HuluwaColors.Pine, style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(Icons.Outlined.MenuBook, "继续阅读", tint = HuluwaColors.Pine)
                    }
                }
            }
        }
    }
}
