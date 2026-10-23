package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppSheet
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*

@Composable fun MineScreen(ui: AppState, vm: HuluwaViewModel) {
    val reconnecting = rememberReconnecting(vm)
    val mine = ui.mine
    val largeFont = LocalDensity.current.fontScale >= 1.5f
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 3.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { AccountCard(ui, reconnecting) }
        if (reconnecting) item {
            StatusNotice("暂时连不上服务器", "已获取的卡片保留上次数据，连接恢复后自动更新。", HuluwaColors.Danger, vm::refreshMine)
        }
        if (!reconnecting && mine.frp.error == null && mine.frp.data.running && (mine.frp.data.failedTunnels ?: 0) > 0) item {
            StatusNotice("${mine.frp.data.failedTunnels} 条隧道需要关注", "进入 FRP 隧道查看错误信息和运行日志。")
        }
        item {
            SectionTitle("我的服务器") {
                Text(statusUpdatedAt(mine.server.updatedAt, reconnecting || mine.server.error != null || mine.server.data.stale),
                    color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall)
            }
            if (!mine.server.loaded && !mine.server.hasSnapshot()) StatusSkeleton()
            else ServerSummaryCard(mine.server, reconnecting, vm::openServerStatus)
        }
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 310.dp || largeFont) Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FrpSummaryCard(mine.frp, reconnecting, Modifier.fillMaxWidth()) { vm.openManagement(ManagementService.Frp) }
                    DockerSummaryCard(mine.docker, reconnecting, Modifier.fillMaxWidth()) { vm.openManagement(ManagementService.Docker) }
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FrpSummaryCard(mine.frp, reconnecting, Modifier.weight(1f)) { vm.openManagement(ManagementService.Frp) }
                    DockerSummaryCard(mine.docker, reconnecting, Modifier.weight(1f)) { vm.openManagement(ManagementService.Docker) }
                }
            }
        }
        item {
            SectionTitle("连接与账户")
            Panel(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                MineEntry(Icons.Outlined.Settings, "连接配置", "${ui.auth.server} · ${ui.auth.username}") { vm.showSheet(AppSheet.Account) }
                HorizontalDivider(color = HuluwaColors.Line)
                MineEntry(Icons.Outlined.Logout, "退出登录 / 切换服务器", "退出后服务器任务继续运行") {
                    vm.confirm("退出登录", "退出后可以切换服务器或账号，服务器任务会继续运行。", "退出") { vm.logout() }
                }
            }
        }
        item { Text("葫芦娃 · 家里的服务，随身就好。", Modifier.fillMaxWidth().padding(top = 4.dp),
            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun AccountCard(ui: AppState, reconnecting: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(HuluwaColors.SoftGreen), contentAlignment = Alignment.Center) {
            Text("葫", color = HuluwaColors.PineDark, fontSize = 24.sp, fontFamily = FontFamily.Serif)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("你好，${ui.auth.username.ifBlank { "未登录" }}", style = MaterialTheme.typography.titleMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(ui.auth.server, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        StatusTag(when { reconnecting -> "同步中断"; ui.realtime -> "实时同步"; else -> "轮询同步" }, ui.realtime && !reconnecting)
    }
}

@Composable private fun ServerSummaryCard(state: LoadState<ServerMetrics>, reconnecting: Boolean, onClick: () -> Unit) {
    val data = state.data
    val stale = reconnecting || state.error != null || data.stale
    val available = state.hasSnapshot() && data.available
    val tone = if (stale) HuluwaColors.Muted else HuluwaColors.Pine
    Panel(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Dns, null, Modifier.size(20.dp), tint = tone)
            Text("当前服务器", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            StatusTag(when { reconnecting -> "连接中断"; state.error != null -> "指标不可用"; data.stale -> "采样过期"; else -> "在线" },
                !stale, reconnecting)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(16.dp), tint = HuluwaColors.Muted)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            SummaryMetric("CPU 使用率", data.cpuPercent.takeIf { available },
                listOfNotNull(data.cores?.let { "$it 核" }, data.load1?.let { "负载 ${statusNumber(it)}" }).joinToString(" · ").ifBlank { "等待采样" },
                tone, Modifier.weight(1f))
            SummaryMetric("内存使用率", data.memoryPercent.takeIf { available },
                if (available) "${statusBytes(data.memoryUsed)} / ${statusBytes(data.memoryTotal)}" else "等待采样",
                if (stale) HuluwaColors.Muted else Color(0xFF78A27B), Modifier.weight(1f))
        }
        HorizontalDivider(color = HuluwaColors.Line)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(when {
                state.error != null -> if (state.hasSnapshot() && available) "上次数据 · ${statusTime(state.updatedAt)}" else "资源指标暂不可用"
                data.stale -> "采样于 ${statusTime(data.sampledAt ?: state.updatedAt)}"
                reconnecting -> if (state.hasSnapshot()) "上次数据 · ${statusTime(state.updatedAt)}" else "连接中断"
                else -> "资源指标"
            }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            Text("查看详情 →", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Pine)
        }
    }
}

@Composable private fun SummaryMetric(title: String, value: Double?, subtitle: String, color: Color, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
        Text(statusPercent(value), color = HuluwaColors.Ink, fontSize = 27.sp, fontWeight = FontWeight.SemiBold)
        StatusProgress(value, color)
        Text(subtitle, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun FrpSummaryCard(state: LoadState<FrpRuntime>, reconnecting: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val data = state.data
    val known = state.hasSnapshot() && data.configAvailable
    val stale = reconnecting || state.error != null
    val status = when {
        reconnecting -> if (state.hasSnapshot()) "上次数据 · ${statusTime(state.updatedAt)}" else "连接中断"
        state.error != null -> "无法获取 · 点击查看"
        !state.loaded -> "正在获取"
        !data.running -> "frpc 未运行"
        (data.failedTunnels ?: 0) > 0 -> "${data.failedTunnels} 条隧道异常"
        else -> "frpc 运行中"
    }
    ServiceSummaryCard("FRP 隧道", Icons.Outlined.AccountTree,
        if (known) data.runningTunnels?.toString() ?: "—" else "—",
        if (known) "/ ${data.total ?: "—"} 运行中" else "隧道状态",
        if (known) "${data.disabledTunnels ?: "—"} 条已停用" else if (state.loaded) "状态暂不可用" else "等待连接",
        status, when { stale || !state.loaded -> HuluwaColors.Muted; !data.running || (data.failedTunnels ?: 0) > 0 -> HuluwaColors.Amber; else -> HuluwaColors.Pine },
        modifier, onClick)
}

@Composable private fun DockerSummaryCard(state: LoadState<DockerSummary>, reconnecting: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val data = state.data
    val known = state.hasSnapshot() && data.available
    ServiceSummaryCard("Docker", Icons.Outlined.DirectionsBoat,
        if (known) data.running?.toString() ?: "—" else "—",
        if (known) "/ ${data.total ?: "—"} 运行中" else "容器状态",
        if (known) "${data.notRunning ?: "—"} 个未运行" else if (state.loaded) "状态暂不可用" else "等待连接",
        when {
            reconnecting -> if (state.hasSnapshot()) "上次数据 · ${statusTime(state.updatedAt)}" else "连接中断"
            state.error != null -> "Docker 无法连接"
            !state.loaded -> "正在获取"
            else -> "Engine 已连接"
        }, if (reconnecting || state.error != null || !known) HuluwaColors.Muted else HuluwaColors.Pine,
        modifier, onClick)
}

@Composable private fun ServiceSummaryCard(
    title: String, icon: ImageVector, count: String, countLabel: String, description: String,
    status: String, tone: Color, modifier: Modifier, onClick: () -> Unit,
) {
    Panel(modifier.clip(RoundedCornerShape(20.dp)).clickable(role = Role.Button, onClick = onClick), contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(18.dp), tint = HuluwaColors.Pine)
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Icon(Icons.Outlined.ChevronRight, null, Modifier.size(14.dp), tint = HuluwaColors.Muted)
        }
        Text(count, Modifier.padding(top = 12.dp), fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
        Text(countLabel, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        Text(description, Modifier.padding(top = 5.dp, bottom = 12.dp), style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        HorizontalDivider(color = HuluwaColors.Line)
        Text(status, Modifier.padding(top = 10.dp), color = tone, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable private fun MineEntry(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
        .heightIn(min = 56.dp).padding(horizontal = 4.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icon, null, Modifier.size(20.dp), tint = HuluwaColors.Pine)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(subtitle, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = HuluwaColors.Muted)
    }
}
