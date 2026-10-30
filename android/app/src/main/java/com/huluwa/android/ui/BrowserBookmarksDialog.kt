package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.data.BrowserBookmark

@Composable fun BrowserBookmarksDialog(bookmarks: List<BrowserBookmark>, busy: Boolean, error: String?,
    onOpen: (BrowserBookmark) -> Unit, onRemove: (BrowserBookmark) -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Outlined.Bookmarks, null) },
        title = { Text("我的收藏（${bookmarks.size}）") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .6f)) {
                HuluwaDialogList(contentPadding = PaddingValues(0.dp)) {
                    error?.let { message ->
                        item(key = "error") { Text(message, Modifier.padding(bottom = 8.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (bookmarks.isEmpty()) item(key = "empty") {
                        Text("还没有收藏。打开网页后，点击右上角菜单中的「收藏当前网页」即可保存。",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    items(bookmarks, key = { "bookmark:${it.url}" }) { bookmark ->
                        Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "打开收藏") { onOpen(bookmark) }
                            .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(bookmark.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(bookmark.url, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            IconButton({ onRemove(bookmark) }, enabled = !busy) {
                                Icon(Icons.Outlined.DeleteOutline, "删除收藏：${bookmark.title}", tint = HuluwaColors.Muted)
                            }
                        }
                        HorizontalDivider(color = HuluwaColors.Line)
                    }
                }
            }
        }, confirmButton = { TextButton(dismiss) { Text("关闭") } })
}
