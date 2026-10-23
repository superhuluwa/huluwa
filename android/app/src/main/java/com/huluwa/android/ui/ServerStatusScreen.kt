package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel

@Composable fun ServerStatusScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val state = ui.mine.server
    val server = state.data
    val reconnecting = rememberReconnecting(vm)
    val available = state.hasSnapshot() && server.available
    val stale = reconnecting || state.error != null || server.stale
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(vm::closeServerStatus) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回我的") }
                Text("服务器状态", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TitleLoadingIndicator(state.loading)
                TitleReconnectBadge(reconnecting)
                IconButton({ vm.refreshServer() }, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "刷新服务器状态") }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            LazyColumn(Modifier.widthIn(max = 660.dp).fillMaxSize().align(Alignment.TopCenter),
                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                if (reconnecting) item {
                    StatusNotice("暂时连不上服务器", if (available) "显示上次获取的数据 · ${statusTime(state.updatedAt)}" else "连接恢复后自动获取资源指标。",
                        HuluwaColors.Danger) { vm.refreshServer() }
                } else if (state.error != null) item {
                    StatusNotice("资源指标暂不可用", state.error) { vm.refreshServer() }
                } else if (server.stale) item {
                    StatusNotice("资源采样已过期", server.message.ifBlank { "暂时显示上次采集的数据。" }) { vm.refreshServer() }
                }
                item {
                    Panel(Modifier.fillMaxWidth(), HuluwaColors.PineDark) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Dns, null, Modifier.size(26.dp), tint = HuluwaColors.Lime)
                            Text("当前服务器", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = Color.White)
                            Tag(when { reconnecting -> "连接中断"; !state.loaded -> "获取中"; state.error != null -> "指标不可用"; server.stale -> "采样过期"; else -> "在线" },
                                tone = if (stale) Color(0xFFFFD7BD) else HuluwaColors.Lime)
                        }
                        Text(ui.auth.server, Modifier.padding(top = 10.dp), color = Color.White.copy(alpha = .8f),
                            style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (stale && available) "上次数据" else "资源概览", Modifier.padding(top = 22.dp, bottom = 18.dp),
                            color = Color.White, style = MaterialTheme.typography.headlineMedium)
                        HorizontalDivider(color = Color.White.copy(alpha = .18f))
                        Text(statusUpdatedAt(state.updatedAt, stale), Modifier.padding(top = 12.dp),
                            color = Color.White.copy(alpha = .75f), style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!state.loaded && !state.hasSnapshot()) item { StatusSkeleton() }
                else {
                    item {
                        SectionTitle("资源用量")
                        Panel(Modifier.fillMaxWidth()) {
                            ResourceReading("CPU 使用率", server.cpuPercent.takeIf { available },
                                server.cores?.let { "$it 核" } ?: "核心数未知", "当前 CPU 占用", "整机百分比口径",
                                if (stale) HuluwaColors.Muted else HuluwaColors.Pine)
                            HorizontalDivider(Modifier.padding(vertical = 20.dp), color = HuluwaColors.Line)
                            ResourceReading("内存使用率", server.memoryPercent.takeIf { available },
                                "总计 ${statusBytes(server.memoryTotal.takeIf { available })}",
                                "已用 ${statusBytes(server.memoryUsed.takeIf { available })}",
                                "可用 ${statusBytes(server.memoryAvailable.takeIf { available })}",
                                if (stale) HuluwaColors.Muted else Color(0xFF78A27B))
                        }
                    }
                    item {
                        SectionTitle("系统平均负载")
                        Panel(Modifier.fillMaxWidth()) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf("1 分钟" to server.load1, "5 分钟" to server.load5, "15 分钟" to server.load15).forEach { (label, value) ->
                                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text(statusNumber(value.takeIf { available }), fontSize = 23.sp, fontWeight = FontWeight.Medium)
                                        Text(label, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                                    }
                                }
                            }
                            Text(server.cores?.let { "结合 $it 核观察负载，数值越低表示等待越少。" } ?: "系统平均负载与 CPU 使用率采用不同口径。",
                                Modifier.padding(top = 18.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    item {
                        Text(if (server.sampledAt != null) "采样于 ${statusTime(server.sampledAt)}" else "当前服务未提供采集时间，以获取时间为参考。",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        Text("资源范围由服务器部署环境决定，与 Docker 容器用量分开展示。", Modifier.padding(top = 6.dp, bottom = 14.dp),
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable private fun ResourceReading(title: String, value: Double?, label: String, left: String, right: String, tone: Color) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        Text(label, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
    Text(statusPercent(value), Modifier.padding(top = 10.dp, bottom = 14.dp), fontSize = 36.sp, fontWeight = FontWeight.SemiBold)
    StatusProgress(value, tone)
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(left, Modifier.weight(1f), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
        Text(right, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
    }
}
