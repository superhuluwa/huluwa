package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.AndroidBuildProject
import com.huluwa.android.data.ManagementService
import com.huluwa.android.data.formatBytes

@Composable fun ManagementSessionScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val session = ui.chat.session ?: return
    val service = session.managementService ?: return
    val buildProject = when (service) {
        ManagementService.TerminalBuddyBuild -> AndroidBuildProject.TerminalBuddy
        else -> AndroidBuildProject.Huluwa
    }
    val reconnecting = rememberReconnecting(vm)
    var frpMenu by remember { mutableStateOf(false) }
    val loading = when (service) {
        ManagementService.Frp -> ui.tunnels.loading
        ManagementService.Docker -> ui.containers.loading || ui.resources[ui.dockerSection]?.loading == true
        ManagementService.AppBuild, ManagementService.TerminalBuddyBuild -> ui.build(buildProject).tasks.loading
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 标题按剩余宽度省略，确保右侧的重连徽标始终可见。
                            Text(service.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TitleReconnectBadge(reconnecting)
                        }
                        Text(service.subtitle, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton({ vm.refreshCurrent() }, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, "刷新管理会话")
                    }
                    when (service) {
                        ManagementService.Frp -> Box {
                            IconButton({ frpMenu = true }) { Icon(Icons.Outlined.MoreHoriz, "FRP 更多操作") }
                            DropdownMenu(frpMenu, { frpMenu = false }) {
                                DropdownMenuItem(text = { Text("查看配置文件") }, enabled = !reconnecting && ui.busy == null,
                                    onClick = { frpMenu = false; vm.showFrpConfig() })
                                val canOperate = !reconnecting && ui.busy == null && !ui.tunnels.loading &&
                                    ui.tunnels.error == null && ui.runtime.running && ui.runtime.configAvailable
                                DropdownMenuItem(text = { Text("热加载配置") }, enabled = canOperate,
                                    onClick = { frpMenu = false; vm.reloadFrp() })
                                DropdownMenuItem(text = { Text("重启 frpc", color = HuluwaColors.Danger) }, enabled = !reconnecting && ui.busy == null,
                                    onClick = { frpMenu = false; vm.restartFrp() })
                            }
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
                        ManagementService.AppBuild, ManagementService.TerminalBuddyBuild -> IconButton({ vm.loadText("构建详情") {
                            val build = vm.state.value.build(buildProject)
                            val status = build.status
                            val server = status.servers.firstOrNull { it.id == build.serverId }
                            buildString {
                                appendLine("构建服务：${if (status.enabled) "已启用" else "未启用"}")
                                appendLine("构建节点：${server?.name ?: "默认服务器"}")
                                appendLine("节点状态：${if (status.workerOnline) "在线" else "离线"}")
                                appendLine("分支：${status.branch}")
                                appendLine("Release 签名：${if (status.signingReady) "已配置" else "未配置"}")
                                appendLine("源码版本：${status.source.commit.ifBlank { "尚未更新" }}")
                                appendLine("提交说明：${status.source.commitSubject.ifBlank { "—" }}")
                                appendLine("更新时间：${status.source.updatedAt.ifBlank { "—" }}")
                                status.latestApk?.let {
                                    appendLine("\n最近安装包：v${it.versionName}（${it.versionCode ?: "—"}）")
                                    appendLine("文件大小：${formatBytes(it.apkSize.toDouble())}")
                                }
                                build.selectedTask?.let {
                                    appendLine("\n查看任务：${it.typeLabel} #${it.id} · ${it.statusLabel}")
                                    appendLine("任务阶段：${it.stageLabel}")
                                    appendLine("任务源码：${it.commit.ifBlank { "—" }}")
                                    it.error.takeIf { error -> error.isNotBlank() }?.let { error -> appendLine("任务错误：$error") }
                                }
                                status.activeTask?.let { appendLine("当前任务：${it.typeLabel} #${it.id} · ${it.stageLabel}") }
                                build.tasks.error?.takeIf { it.isNotBlank() }?.let { appendLine("\n服务提示：$it") }
                            }.trim()
                        } }) { Icon(Icons.Outlined.Dns, "查看构建详情") }
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
                    ManagementService.AppBuild, ManagementService.TerminalBuddyBuild -> AppBuildScreen(ui, vm, snackbar, buildProject)
                    ManagementService.CheckLogs -> CheckLogsScreen(ui, vm)
                    ManagementService.Library -> LibraryScreen(ui, vm)
                    ManagementService.Subscriptions -> SubscriptionsScreen(ui, vm)
                }
            }
        }
    }
}
