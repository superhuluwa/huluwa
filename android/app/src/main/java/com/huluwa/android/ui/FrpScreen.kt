package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun FrpScreen(ui: AppState, vm: HuluwaViewModel) {
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val tunnels = ui.tunnels
    val reconnecting = rememberReconnecting(vm)
    val snapshot = tunnels.hasSnapshot()
    val known = snapshot && tunnels.error == null && ui.runtime.configAvailable && !reconnecting
    val enabled = ui.busy == null && !tunnels.loading && known
    val running = if (ui.runtime.running) tunnels.data.count { it.running } else 0
    val failed = tunnels.data.count { it.enabled && it.failed }
    val disabled = tunnels.data.count { !it.enabled }
    val filtered = tunnels.data.filter { when (filter) {
        1 -> ui.runtime.running && it.running
        2 -> it.enabled && it.failed
        3 -> !it.enabled
        else -> true
    } }
    val status = when {
        reconnecting -> "连接中断"
        tunnels.error != null -> "无法获取"
        !snapshot -> if (tunnels.loading) "获取中" else "等待连接"
        !ui.runtime.running -> "frpc 未运行"
        else -> "frpc 运行中"
    }
    // 关闭边缘拉伸回弹，避免隧道列表触底时反复晃动。
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp), overscrollEffect = null) {
        if (reconnecting || tunnels.error != null) item {
            StatusNotice(if (reconnecting) "暂时连不上服务器" else "无法获取 FRP 状态",
                if (snapshot) "保留上次数据 · ${statusTime(tunnels.updatedAt)}" else tunnels.error,
                if (reconnecting) HuluwaColors.Danger else HuluwaColors.Amber) { vm.refreshFrp() }
        }
        item {
            Panel(Modifier.fillMaxWidth(), HuluwaColors.PineDark) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Outlined.AccountTree, null, Modifier.size(28.dp), tint = HuluwaColors.Lime)
                    Text("FRP 隧道服务", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
                    Tag(status, tone = if (!known || !ui.runtime.running) Color(0xFFFFD7BD) else HuluwaColors.Lime)
                }
                Text("容器 ${ui.runtime.container} · ${statusUpdatedAt(tunnels.updatedAt, !known)}", Modifier.padding(top = 9.dp),
                    color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium)
                Row(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 18.dp)) {
                    Metric(if (snapshot) tunnels.data.size.toString() else "—", "全部隧道", Modifier.weight(1f), Color.White)
                    Metric(if (snapshot) running.toString() else "—", "运行中", Modifier.weight(1f), Color.White)
                    Metric(if (snapshot) failed.toString() else "—", "异常", Modifier.weight(1f), Color.White)
                }
                HorizontalDivider(color = Color.White.copy(alpha = .18f))
                Text(if (snapshot) "$disabled 条已停用 · 不参与运行告警" else "连接后获取隧道状态",
                    Modifier.padding(top = 12.dp), style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
            }
        }
        if (known && ui.runtime.message.isNotBlank()) item { StatusNotice("FRP 服务提示", ui.runtime.message) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(vm::frpLogs, Modifier.weight(1f), enabled = !reconnecting && ui.busy == null) {
                    Icon(Icons.Outlined.Subject, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("运行日志")
                }
                Button({ vm.showSheet(AppSheet.TunnelEdit()) }, Modifier.weight(1f), enabled = enabled) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("新增隧道")
                }
            }
            SectionTitle("我的隧道") {
                Text(if (snapshot) "${tunnels.data.size} 条" else "—", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
            }
            FilterRow(listOf("全部", "运行中", "异常", "已停用"), filter, { filter = it })
        }
        if (!tunnels.loaded && !snapshot) item { StatusSkeleton() }
        else if (known && filtered.isEmpty()) item {
            EmptyContent(if (tunnels.data.isEmpty()) "还没有隧道" else "该筛选下没有隧道",
                if (tunnels.data.isEmpty()) "点击“新增隧道”，连接你的第一个内网服务" else "试试查看其他状态", Icons.Outlined.AccountTree)
        }
        items(filtered, key = { it.name }) { tunnel -> TunnelCard(tunnel, enabled, known && ui.runtime.running, vm) }
        item { Text("隧道运行与本地端口可达分别展示，公网访问仍取决于实际连接。",
            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun TunnelCard(tunnel: Tunnel, enabled: Boolean, current: Boolean, vm: HuluwaViewModel) {
    var menu by remember { mutableStateOf(false) }
    val status = when {
        !tunnel.enabled -> "已停用"
        !current -> "状态待查询"
        tunnel.failed -> "启动异常"
        tunnel.running -> "运行中"
        else -> tunnel.statusText.ifBlank { "状态待查询" }
    }
    Panel(Modifier.fillMaxWidth(), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (tunnel.draft.proxyType in setOf("http", "https")) Icons.Outlined.Language else Icons.Outlined.AccountTree,
                null, Modifier.size(21.dp), tint = if (tunnel.enabled) HuluwaColors.Pine else HuluwaColors.Muted)
            Column(Modifier.weight(1f)) {
                Text(tunnel.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${tunnel.draft.proxyType.uppercase()} · ${if (tunnel.enabled) "已启用" else "已停用"}",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            }
            StatusTag(status, current && tunnel.running, current && tunnel.failed)
            Box {
                IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "隧道操作") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("查看详情") }, onClick = { menu = false; vm.tunnelDetail(tunnel) })
                    DropdownMenuItem(text = { Text("编辑隧道") }, enabled = enabled,
                        onClick = { menu = false; vm.showSheet(AppSheet.TunnelEdit(tunnel)) })
                    DropdownMenuItem(text = { Text(if (tunnel.enabled) "停用隧道" else "启用隧道") }, enabled = enabled,
                        onClick = { menu = false; vm.toggleTunnel(tunnel) })
                    DropdownMenuItem(text = { Text("删除隧道", color = HuluwaColors.Danger) }, enabled = enabled,
                        onClick = { menu = false; vm.deleteTunnel(tunnel) })
                }
            }
        }
        Panel(Modifier.fillMaxWidth().padding(top = 10.dp), HuluwaColors.Paper, contentPadding = 10.dp) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("本地服务", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    Text(tunnel.localAddress.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                }
                Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = HuluwaColors.Muted)
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text(if (tunnel.draft.proxyType in setOf("http", "https")) "远程域名" else "云端端口",
                        style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    Text(tunnel.remoteAddress.ifBlank { "—" }, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val probe = when {
                !tunnel.enabled -> "已停用 · 不参与运行告警"
                !current -> "上次探测 · ${tunnel.probeText.ifBlank { "未探测" }}"
                tunnel.draft.proxyType in setOf("udp", "sudp") -> "${tunnel.probeText.ifBlank { "未探测" }} · TCP 探测"
                else -> tunnel.probeText.ifBlank { "未探测" }
            }
            Text(probe, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall,
                color = if (current && tunnel.enabled && tunnel.probe == "closed") HuluwaColors.Amber else HuluwaColors.Muted)
            TextButton({ vm.tunnelDetail(tunnel) }) { Text("查看详情") }
        }
        if (tunnel.enabled && tunnel.error.isNotBlank()) Text(tunnel.error, color = if (current) HuluwaColors.Danger else HuluwaColors.Muted,
            style = MaterialTheme.typography.bodySmall)
    }
}
