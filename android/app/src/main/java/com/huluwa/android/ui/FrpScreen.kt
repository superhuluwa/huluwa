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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun FrpScreen(ui: AppState, vm: HuluwaViewModel) {
    val enabled = ui.busy == null && !ui.tunnels.loading && ui.tunnels.loaded
    LazyColumn(contentPadding = PaddingValues(horizontal = 22.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            SectionTitle("我的隧道") {
                TextButton(onClick = { vm.refreshFrp() }, enabled = !ui.tunnels.loading) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("刷新")
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(vm::reloadFrp, Modifier.weight(1f), enabled = enabled) { Text("热加载") }
                OutlinedButton(vm::frpLogs, Modifier.weight(1f)) { Text("日志") }
                OutlinedButton(vm::restartFrp, Modifier.weight(1f), enabled = enabled) { Text("重启") }
            }
        }
        items(ui.tunnels.data, key = { it.name }) { tunnel -> TunnelCard(tunnel, enabled, vm) }
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
