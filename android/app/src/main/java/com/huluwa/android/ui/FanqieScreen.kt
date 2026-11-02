package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huluwa.android.data.FanqieBook

/**
 * 番茄小说搜索结果卡：封面 + 书名 / 作者 / 分类字数评分 + 简介摘要。
 *
 * 番茄小说会话只做搜索，不接入订阅与下载链路，因此卡片底部只提供「复制书名」，
 * 方便用户把书名带到其他下载入口。
 */
@Composable fun FanqieResultCard(book: FanqieBook) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Poster(book.title, book.cover, size = DpSize(64.dp, 90.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(book.title, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.author.isNotBlank()) {
                    Text("作者：${book.author}", style = MaterialTheme.typography.labelMedium,
                        color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(book.meta, style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Pine, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (book.summary.isNotBlank()) {
                    Text(book.summary, style = MaterialTheme.typography.bodySmall,
                        color = HuluwaColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        if (book.lastChapterTitle.isNotBlank() || book.bookId.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (book.lastChapterTitle.isNotBlank()) {
                    Text("最新：${book.lastChapterTitle}", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    { copyText(context, book.title, "已复制书名") },
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(Icons.Outlined.ContentCopy, null, Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("复制书名", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

private fun copyText(context: Context, text: String, toast: String) {
    val manager = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    manager.setPrimaryClip(ClipData.newPlainText("番茄小说书名", text))
    Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
}
