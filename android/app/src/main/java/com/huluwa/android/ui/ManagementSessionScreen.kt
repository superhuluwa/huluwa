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

@Composable fun ManagementSessionScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val session = ui.chat.session ?: return
    val service = session.managementService ?: return
    val loading = when (service) {
        ManagementService.Frp -> ui.tunnels.loading
        ManagementService.Docker -> ui.containers.loading || ui.resources[ui.dockerSection]?.loading == true
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
                }
                if (ui.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}
