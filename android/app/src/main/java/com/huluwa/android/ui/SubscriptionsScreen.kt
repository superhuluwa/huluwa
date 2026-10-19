package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun SubscriptionsScreen(ui: AppState, vm: HuluwaViewModel) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    var source by rememberSaveable { mutableIntStateOf(0) }
    var keyword by rememberSaveable { mutableStateOf("") }
    val all = ui.subscriptions.data
    val rows = all.filter { (filter == 0 || if (filter == 1) it.enabled else !it.enabled) && (source == 0 || it.source == SubscriptionSource.entries[source - 1]) && it.title.contains(keyword.trim(), true) }
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            ConnectionHero("好故事，持续追更", "发现更新，自动下载到家里的 NAS", Icons.Outlined.BookmarkBorder)
            Row(Modifier.fillMaxWidth().padding(vertical = 18.dp)) {
                Metric(all.size.toString(), "全部订阅", Modifier.weight(1f))
                Metric(all.count { it.enabled }.toString(), "追更中", Modifier.weight(1f), HuluwaColors.Pine)
                Metric(all.count { !it.enabled }.toString(), "已暂停", Modifier.weight(1f))
            }
            SearchBox(keyword, { keyword = it }, "搜索订阅的剧集")
            FilterRow(listOf("全部订阅", "追更中", "已暂停"), filter, { filter = it })
            FilterRow(listOf("全部来源") + SubscriptionSource.entries.map { it.title }, source, { source = it })
            SectionTitle("我的订阅") {
                TextButton({ vm.showSettings(if (source == 2) "movie" else "dytt") }) { Text("下载设置") }
                IconButton({ vm.refreshSubscriptions() }, enabled = !ui.subscriptions.loading) { Icon(Icons.Outlined.Refresh, "刷新订阅") }
            }
            Feedback(ui.subscriptions.loading, ui.subscriptions.error, { vm.refreshSubscriptions() })
        }
        if (rows.isEmpty()) item {
            EmptyContent(if (keyword.isNotBlank()) "没有匹配的订阅" else "还没有订阅内容", "在会话里搜索剧名，找到后即可订阅追更", Icons.Outlined.BookmarkBorder, !ui.subscriptions.loaded)
        }
        items(rows, key = { it.key }) { sub -> SubscriptionCard(sub, ui.busy == null && !ui.subscriptions.loading, vm) }
        item { OutlinedButton({ vm.selectTab(MainTab.Sessions); vm.notify("在电影港或电影天堂会话中搜索剧名，即可添加订阅") }, Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 20.dp)) {
            Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(7.dp)); Text("去会话里发现新内容")
        } }
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
