package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.ManagementService
import com.huluwa.android.data.formatBytes

@Composable fun ManagementSessionScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val session = ui.chat.session ?: return
    val service = session.managementService ?: return
    val loading = when (service) {
        ManagementService.Frp -> ui.tunnels.loading
        ManagementService.Docker -> ui.containers.loading || ui.resources[ui.dockerSection]?.loading == true
        ManagementService.AppBuild -> ui.androidBuild.tasks.loading
        ManagementService.CheckLogs -> ui.checkLogs.loading
        ManagementService.Library -> ui.library.scan.loading
        ManagementService.Subscriptions -> ui.subscriptions.loading
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::closeChat) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回会话") }
                    SessionIcon(session.kind, Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Text(service.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(service.subtitle, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ vm.refreshCurrent() }, enabled = !loading) {
                        if (loading) CircularProgressIndicator(Modifier.size(19.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Outlined.Refresh, "刷新管理会话")
                    }
                    when (service) {
                        ManagementService.Frp -> IconButton(vm::showFrpConfig, enabled = ui.busy == null) {
                            Icon(Icons.Outlined.Description, "查看或编辑 Frp 配置文件")
                        }
                        ManagementService.Docker -> IconButton({ vm.loadText("Docker 主机") {
                            val current = vm.state.value
                            val overview = current.overview
                            val connection = when {
                                current.containers.error != null -> "连接失败"
                                !current.containers.loaded -> "等待连接"
                                overview.online -> "在线"
                                else -> "不可用"
                            }
                            "连接状态：$connection\n服务端版本：${overview.version.ifBlank { "未知" }}\n${current.containers.error ?: overview.message}"
                        } }) { Icon(Icons.Outlined.Dns, "Docker 主机信息") }
                        ManagementService.AppBuild -> IconButton({ vm.loadText("构建宿主信息") {
                            val current = vm.state.value
                            val status = current.androidBuild.status
                            "构建服务：${if (status.enabled) "已启用" else "未启用"}\n构建容器：${if (status.workerOnline) "在线" else "离线"}" +
                                "\n签名配置：${if (status.signingReady) "已配置" else "未配置"}\n分支：${status.branch}"
                        } }) { Icon(Icons.Outlined.Dns, "构建宿主信息") }
                        ManagementService.CheckLogs -> IconButton(vm::clearCheckLogs, enabled = ui.checkLogs.data.isNotEmpty() && ui.busy == null) {
                            Icon(Icons.Outlined.DeleteSweep, "清空巡检记录")
                        }
                        ManagementService.Library -> IconButton({ vm.loadText("下载目录") {
                            val scan = vm.state.value.library.scan.data
                            "剧集目录：${scan.mediaRoot.ifBlank { "尚未扫描" }}\n可写：${if (scan.writable) "是" else "否"}" +
                                "\n视频文件：${scan.entries.size} 个\n占用空间：${formatBytes(scan.totalBytes.toDouble())}" +
                                "\n上次扫描：${scan.scannedAt.ifBlank { "—" }}"
                        } }) { Icon(Icons.Outlined.Info, "下载目录信息") }
                        // 订阅管理没有额外工具入口，刷新按钮已足够
                        ManagementService.Subscriptions -> Unit
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().navigationBarsPadding()) {
            Box(Modifier.widthIn(max = 660.dp).fillMaxSize().align(Alignment.TopCenter)) {
                when (service) {
                    ManagementService.Frp -> FrpScreen(ui, vm)
                    ManagementService.Docker -> DockerScreen(ui, vm)
                    ManagementService.AppBuild -> AppBuildScreen(ui, vm, snackbar)
                    ManagementService.CheckLogs -> CheckLogsScreen(ui, vm)
                    ManagementService.Library -> LibraryScreen(ui, vm)
                    ManagementService.Subscriptions -> SubscriptionsScreen(ui, vm)
                }
                if (ui.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}
