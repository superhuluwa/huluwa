package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun FrpScreen(ui: AppState, vm: HuluwaViewModel) {
    val tunnels = ui.tunnels
    val known = tunnels.loaded && tunnels.error == null
    val enabled = ui.busy == null && !tunnels.loading && known
    val status = when {
        tunnels.loading && !tunnels.loaded -> "连接中"
        tunnels.error != null -> "连接异常"
        !tunnels.loaded -> "等待连接"
        ui.runtime.running -> "运行中"
        else -> "未运行"
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Panel(Modifier.fillMaxWidth(), HuluwaColors.Pine) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AccountTree, null, Modifier.size(32.dp), tint = HuluwaColors.Lime)
                    Column(Modifier.weight(1f)) {
                        Text("Frp 隧道服务", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(if (known) ui.runtime.container else "连接你的内网服务", style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = .8f))
                    }
                    Tag(status, tone = if (tunnels.error != null || known && !ui.runtime.running) Color(0xFFFFD7BD) else HuluwaColors.Lime)
                }
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 16.dp)) {
                    Metric(if (known) tunnels.data.size.toString() else "—", "全部隧道", Modifier.weight(1f), Color.White)
                    Metric(if (known) tunnels.data.count { it.running }.toString() else "—", "运行中", Modifier.weight(1f), Color.White)
                    Metric(if (known) tunnels.data.count { !it.enabled }.toString() else "—", "已停用", Modifier.weight(1f), Color.White)
                }
                HorizontalDivider(color = Color.White.copy(alpha = .2f))
                Text("将家里的服务连接到外网", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = .8f))
            }
        }
        item {
            Feedback(tunnels.loading, tunnels.error.takeUnless { it.isServerUnreachable() }, { vm.refreshFrp() })
            if (known && ui.runtime.message.isNotBlank()) Text(ui.runtime.message, style = MaterialTheme.typography.bodySmall,
                color = HuluwaColors.Muted, modifier = Modifier.padding(bottom = 8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(vm::reloadFrp, Modifier.weight(1f), enabled = enabled) { Text("热加载") }
                OutlinedButton(vm::frpLogs, Modifier.weight(1f)) { Text("日志") }
                OutlinedButton(vm::restartFrp, Modifier.weight(1f), enabled = enabled) { Text("重启") }
            }
            SectionTitle("我的隧道", Modifier.padding(top = 8.dp)) {
                TextButton({ vm.showSheet(AppSheet.TunnelEdit()) }, enabled = enabled) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(17.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("新增隧道")
                }
            }
        }
        if (known && !tunnels.loading && tunnels.data.isEmpty()) item {
            EmptyContent("还没有隧道", "点击“新增隧道”，连接你的第一个内网服务", Icons.Outlined.AccountTree)
        }
        items(tunnels.data, key = { it.name }) { tunnel -> TunnelCard(tunnel, enabled, vm) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
@Composable private fun TunnelCard(tunnel: Tunnel, enabled: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    val status = if (!tunnel.enabled) "已停用" else if (tunnel.failed) "异常" else if (tunnel.running) "运行中" else tunnel.statusText.ifBlank { "状态待查询" }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (tunnel.draft.proxyType in setOf("http", "https")) Icons.Outlined.Language else Icons.Outlined.AccountTree, null, Modifier.size(21.dp), tint = HuluwaColors.Pine)
            Text(tunnel.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            StatusTag(status, healthy = tunnel.running, alert = tunnel.failed)
            Switch(tunnel.enabled, { vm.toggleTunnel(tunnel) }, enabled = enabled)
        }
        Panel(Modifier.fillMaxWidth().padding(top = 9.dp), HuluwaColors.Paper) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("本地服务", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    Text(tunnel.localAddress.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(17.dp), tint = HuluwaColors.Muted)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(if (tunnel.draft.proxyType in setOf("http", "https")) "远程域名" else "云端端口", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    Text(tunnel.remoteAddress.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Tag(tunnel.draft.proxyType.uppercase(), outlined = true)
            Text(tunnel.draft.remark, Modifier.weight(1f).padding(start = 7.dp), style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton({ vm.showSheet(AppSheet.TunnelEdit(tunnel)) }, enabled = enabled) { Text("编辑") }
            Box {
                IconButton({ menu = true }, enabled = enabled) { Icon(Icons.Outlined.MoreHoriz, "隧道操作") }
                DropdownMenu(menu, { menu = false }) { DropdownMenuItem(text = { Text("删除隧道") }, onClick = { menu = false; vm.deleteTunnel(tunnel) }) }
            }
        }
    }
}
