package com.huluwa.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

/**
 * 我的订阅（系统会话，对齐安卓 ManagementSessionScreen + 网页端「我的订阅」）：
 * 由原底部「订阅」标签迁入系统会话，卡片展示封面、来源与已收录条数。
 * 剧名下方一行「来源 · 已收录 N 条」，不展示年份 / 类型与巡检时间（只保留失败原因）。
 * 卡片右上角为「取消订阅」（取消即删除记录，已下载 / 已归档文件保留），
 * 原「…」菜单里的立即检查与资源列表改为卡片底部按钮，不再提供暂停 / 恢复追更。
 * 空状态与加载 / 错误反馈沿用系统会话列表的同一套组件。
 */
@Composable fun SubscriptionsScreen(ui: AppState, vm: HuluwaViewModel) {
    val state = ui.subscriptions
    val rows = state.data
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        overscrollEffect = rememberBounceOverscroll()) {
        // 加载状态统一显示在标题栏菊花上，内容区只保留错误反馈。
        if (state.error != null && !state.error.isServerUnreachable()) item {
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
    // 紧凑卡片：内边距 12、海报 56×80、按钮 32 高，一屏能多放一张
    Panel(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(11.dp), verticalAlignment = Alignment.Top) {
            Poster(sub.title, sub.cover, size = DpSize(56.dp, 80.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(sub.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    // 右上角「取消订阅」：取消即删除订阅记录，已下载文件保留；已暂停的残留项显示「删除订阅」
                    TextButton(
                        onClick = { vm.deleteSubscription(sub) }, enabled = enabled,
                        modifier = Modifier.height(28.dp), contentPadding = PaddingValues(horizontal = 6.dp),
                    ) {
                        Text(if (sub.enabled) "取消订阅" else "删除订阅", color = HuluwaColors.Danger,
                            style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                // 来源 + 收录条数合并成一行（原年份 / 类型与下方标签行一并移除），追更中的状态不再标注
                Text(listOf(sub.source.title, "已收录 ${sub.count} 条", if (sub.enabled) "" else "已暂停").filter(String::isNotBlank).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // 失败原因与两个操作按钮都跟在信息列里，不再横跨整张卡片底部
                if (sub.lastError.isNotBlank()) Text(sub.lastError, Modifier.padding(top = 5.dp), color = HuluwaColors.Danger,
                    style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    SubscriptionAction("立即检查", Icons.Outlined.Refresh, enabled && sub.enabled, Modifier.weight(1f)) { vm.checkSubscription(sub) }
                    SubscriptionAction("资源列表", Icons.Outlined.List, true, Modifier.weight(1f)) { vm.filmResources(sub.asFilmCard()) }
                }
            }
        }
    }
}

/** 订阅卡片信息列里的次要操作按钮：白底细边框，文字与图标统一用松柏绿。 */
@Composable private fun SubscriptionAction(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(
        onClick, modifier.height(32.dp), enabled = enabled, shape = RoundedCornerShape(9.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HuluwaColors.Pine),
        border = BorderStroke(1.dp, HuluwaColors.Line), contentPadding = PaddingValues(horizontal = 8.dp),
    ) {
        Icon(icon, null, Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
fun Subscription.asFilmCard() = FilmCard(raw.text("resId"), title, cover, raw.text("detailUrl"), if (source == SubscriptionSource.Hls) "dytt" else "movie", year, genre,
    raw.text("origin"), raw.text("apiUrl"), emptyList(), enabled)
