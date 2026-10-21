package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

/**
 * 我的订阅（系统会话，对齐安卓 ManagementSessionScreen + 网页端「我的订阅」）：
 * 由原底部「订阅」标签迁入系统会话，卡片展示封面、来源、追更状态与巡检信息，
 * 「…」菜单提供立即检查、暂停 / 恢复、资源列表与删除。
 * 空状态与加载 / 错误反馈沿用系统会话列表的同一套组件。
 */
@Composable fun SubscriptionsScreen(ui: AppState, vm: HuluwaViewModel) {
    val state = ui.subscriptions
    val rows = state.data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.error != null) item {
            Feedback(loading = false, error = state.error, onRetry = { vm.refreshSubscriptions() })
        }
        if (rows.isEmpty()) item {
            EmptyContent(
                "还没有订阅", "在「功能 → 电影港 / 电影天堂」搜索片名，在结果卡片上订阅即可追更",
                Icons.Outlined.BookmarkBorder, loading = !state.loaded,
            )
        }
        items(rows, key = { it.key }) { sub -> SubscriptionCard(sub, ui.busy == null && !state.loading, vm) }
    }
}

@Composable private fun SubscriptionCard(sub: Subscription, enabled: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    Panel(Modifier.fillMaxWidth()) {
        Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.Top) {
            Poster(sub.title, sub.cover)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Box {
                        IconButton({ menu = true }, Modifier.size(40.dp)) { Icon(Icons.Outlined.MoreHoriz, "管理${sub.title}") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("立即检查") }, enabled = sub.enabled && enabled, onClick = { menu = false; vm.checkSubscription(sub) })
                            DropdownMenuItem(text = { Text(if (sub.enabled) "暂停追更" else "恢复追更") }, enabled = enabled, onClick = { menu = false; vm.toggleSubscription(sub) })
                            DropdownMenuItem(text = { Text("资源列表") }, onClick = { menu = false; vm.filmResources(sub.asFilmCard()) })
                            DropdownMenuItem(text = { Text("删除订阅") }, enabled = enabled, onClick = { menu = false; vm.deleteSubscription(sub) })
                        }
                    }
                }
                Text(listOf(sub.year, sub.genre, sub.source.title).filter(String::isNotBlank).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Tag(if (sub.source == SubscriptionSource.Hls) "已收录 ${sub.count} 集" else "${sub.count} 条磁力"); Tag(if (sub.enabled) "追更中" else "已暂停", outlined = true) }
            }
        }
        if (sub.lastCheck.isNotBlank()) Text("上次检查：${sub.lastCheck}", Modifier.padding(top = 11.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
        if (sub.enabled && sub.nextCheck.isNotBlank()) Text("下次检查：${sub.nextCheck}", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
        if (sub.lastError.isNotBlank()) Text(sub.lastError, Modifier.padding(top = 7.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
    }
}
fun Subscription.asFilmCard() = FilmCard(raw.text("resId"), title, cover, raw.text("detailUrl"), if (source == SubscriptionSource.Hls) "dytt" else "movie", year, genre,
    raw.text("origin"), raw.text("apiUrl"), emptyList(), enabled)
