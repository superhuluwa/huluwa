package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 单屏构建工作台：固定展示节点、版本和操作，日志使用剩余空间独立滚动。
 * 横屏时左右分栏；完整源码、签名与错误信息通过详情弹层查看。
 */
@Composable fun AppBuildScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val build = ui.androidBuild
    val context = LocalContext.current
    var continueInstallation by rememberSaveable { mutableStateOf(false) }
    // 安装授权页返回后继续安装；授权失败则把原因显示在下载卡片里。
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.canInstallAndroidBuild()) continueInstallation = true
        else vm.androidBuildInstallError("尚未允许安装应用。授权后点击「安装」即可。")
    }
    LaunchedEffect(continueInstallation) {
        if (!continueInstallation) return@LaunchedEffect
        try {
            vm.androidBuildInstallerIntent()?.let(context::startActivity)
                ?: vm.androidBuildInstallError("安装包文件已不存在，请重新下载")
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { vm.androidBuildInstallError(error.userMessage()) }
        finally { continueInstallation = false }
    }
    fun install() {
        if (vm.canInstallAndroidBuild()) continueInstallation = true
        else runCatching {
            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }.onFailure { vm.androidBuildInstallError("无法打开安装授权页面，请在系统设置中允许葫芦娃安装应用") }
    }
    // 构建页可见时的轮询：先发布首屏请求，再按任务状态决定节奏；
    // 有任务执行或日志未拉完时每秒刷新，空闲时改为 5 秒。
    LaunchedEffect(Unit) {
        vm.refreshAndroidBuild()
        while (true) {
            kotlinx.coroutines.delay(1000)
            val current = vm.state.value.androidBuild
            vm.refreshAndroidBuild(silent = true)
            if (current.download.active) vm.pollAndroidDownload()
            if (current.status.activeTask == null && current.logComplete && !current.download.active) {
                kotlinx.coroutines.delay(4000)
            }
        }
    }
    val fontScale = LocalDensity.current.fontScale
    BoxWithConstraints(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp)) {
        val compact = maxHeight < 520.dp || fontScale > 1.2f
        if (maxWidth >= 480.dp && maxHeight < 520.dp) {
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BuildControls(build, ui.clock, ui.busy != null, vm, compact, Modifier.weight(1f))
                BuildOutput(build, ui.clock, vm, snackbar, ::install, Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                BuildControls(build, ui.clock, ui.busy != null, vm, compact)
                BuildOutput(build, ui.clock, vm, snackbar, ::install, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

@Composable private fun BuildControls(
    build: AndroidBuildState, clock: Long, busy: Boolean, vm: HuluwaViewModel,
    compact: Boolean, modifier: Modifier = Modifier,
) {
    val status = build.status
    val active = status.activeTask
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        BuildServerSelector(build, vm)
        BuildOverview(build, compact, vm)
        BuildActions(build, vm)
        val notice = when {
            build.connectionError -> "连接中断，正在重连 · 服务端任务将继续执行"
            !build.tasks.error.isNullOrBlank() -> build.tasks.error.orEmpty()
            !build.tasks.loaded -> "正在获取构建状态…"
            !status.enabled -> "构建服务未启用，请先配置构建节点"
            !status.workerOnline -> "节点离线 · 已生成的安装包仍可下载"
            active != null || build.submitting != null -> ""
            !status.hasSource -> "请先更新代码，再编译安装包"
            !status.signingReady -> "请先配置 Release 签名"
            else -> ""
        }
        if (notice.isNotBlank()) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val tone = if (build.tasks.loaded) HuluwaColors.Amber else HuluwaColors.Muted
                Icon(Icons.Outlined.Info, null, Modifier.size(16.dp), tint = tone)
                Text(notice, style = MaterialTheme.typography.labelMedium, color = tone,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (active != null) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                Column(Modifier.weight(1f)) {
                    Text(active.stageLabel, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Text("#${active.id} · ${active.elapsedLabel(clock)}", style = MaterialTheme.typography.labelSmall,
                        color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton({ vm.cancelAndroidTask(active) }, enabled = active.status != "CANCELLING" && !busy) {
                    Text(if (active.status == "CANCELLING") "正在取消" else "取消任务",
                        color = if (active.status == "CANCELLING" || busy) HuluwaColors.Muted else HuluwaColors.Danger,
                        maxLines = 1)
                }
            }
        }
    }
}

@Composable private fun BuildServerSelector(build: AndroidBuildState, vm: HuluwaViewModel) {
    var expanded by remember { mutableStateOf(false) }
    val status = build.status
    val server = status.servers.firstOrNull { it.id == build.serverId }
    Box(Modifier.fillMaxWidth()) {
        OutlinedButton({ expanded = true }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = build.submitting == null && status.servers.isNotEmpty(),
            shape = RoundedCornerShape(14.dp), border = BorderStroke(1.dp, HuluwaColors.Line),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.White, contentColor = HuluwaColors.Ink),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
            Icon(Icons.Outlined.Dns, null, Modifier.size(18.dp), tint = HuluwaColors.Muted)
            Text(server?.name ?: "默认服务器", Modifier.weight(1f).padding(horizontal = 8.dp),
                style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp))
                .background(if (status.workerOnline) HuluwaColors.Pine else HuluwaColors.Amber))
            Text(if (status.workerOnline) "在线" else "离线", Modifier.padding(start = 5.dp, end = 6.dp),
                style = MaterialTheme.typography.labelSmall,
                color = if (status.workerOnline) HuluwaColors.Pine else HuluwaColors.Amber)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(18.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            status.servers.forEach { node ->
                DropdownMenuItem(text = { Text("${node.name} · ${if (node.online) "在线" else "离线"}") },
                    leadingIcon = { if (node.id == build.serverId) Icon(Icons.Outlined.Check, null) },
                    onClick = { expanded = false; if (node.id != build.serverId) vm.selectAndroidServer(node.id) })
            }
        }
    }
}

@Composable private fun BuildOverview(build: AndroidBuildState, compact: Boolean, vm: HuluwaViewModel) {
    val status = build.status
    val apk = status.latestApk
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(1.dp, HuluwaColors.Line)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("当前源码", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    Icon(Icons.Outlined.VerifiedUser,
                        if (status.signingReady) "Release 签名已配置" else "Release 签名未配置",
                        Modifier.size(13.dp), tint = if (status.signingReady) HuluwaColors.Pine else HuluwaColors.Amber)
                }
                Text(status.source.commit.take(8).ifBlank { "尚未更新" }, fontSize = 17.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace,
                    color = HuluwaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text(status.source.commitSubject.ifBlank { status.branch },
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            VerticalDivider(Modifier.height(if (compact) 36.dp else 52.dp), color = HuluwaColors.Line)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("最新安装包", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Text(apk?.let { "v${it.versionName}" } ?: "暂无安装包", fontSize = 17.sp, lineHeight = 23.sp,
                    fontWeight = FontWeight.SemiBold, color = HuluwaColors.Ink,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!compact) Text(apk?.let { formatBytes(it.apkSize.toDouble()) } ?: "编译成功后可下载",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton({ apk?.let { vm.downloadAndroidArtifact(it) } },
                enabled = status.canDownload && !build.download.active) {
                Icon(Icons.Outlined.Download, "下载最新 APK")
            }
        }
    }
}

@Composable private fun BuildActions(build: AndroidBuildState, vm: HuluwaViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton({ vm.submitAndroidBuild("UPDATE") }, Modifier.weight(1f).heightIn(min = 48.dp),
            enabled = build.status.canUpdate && build.submitting == null, shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            if (build.submitting == "UPDATE") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            else Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("更新代码", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Button({ vm.submitAndroidBuild("BUILD") }, Modifier.weight(1f).heightIn(min = 48.dp),
            enabled = build.status.canBuild && build.submitting == null, shape = RoundedCornerShape(14.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)) {
            if (build.submitting == "BUILD") CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
            else Icon(Icons.Outlined.Build, null, Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("编译 APK", maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun DownloadStrip(download: AndroidDownload, vm: HuluwaViewModel, install: () -> Unit) {
    val failed = download.phase == UpgradePhase.Failed
    Surface(Modifier.fillMaxWidth(), color = if (failed) HuluwaColors.Amber.copy(alpha = .08f) else HuluwaColors.SoftGreen,
        shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable {
                    vm.loadText(if (download.logFile) "日志下载详情" else "安装包下载详情") {
                        listOf(download.filename, download.message, download.error.orEmpty()).filter { it.isNotBlank() }.joinToString("\n")
                    }
                }.padding(vertical = 4.dp)) {
                    val title = when (download.phase) {
                        UpgradePhase.Ready -> if (download.logFile) "日志已保存到 Download 目录" else "安装包已就绪"
                        UpgradePhase.Failed -> "下载失败，点击查看详情"
                        UpgradePhase.Verifying -> "正在校验安装包"
                        else -> if (download.logFile) "正在下载日志" else "正在下载安装包"
                    }
                    Text(title, style = MaterialTheme.typography.labelLarge,
                        color = if (failed) HuluwaColors.Amber else HuluwaColors.Pine,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val detail = if (download.active) {
                        val received = formatBytes(download.received.toDouble())
                        if (download.total > 0) "$received / ${formatBytes(download.total.toDouble())}" else received
                    } else download.error ?: download.filename.ifBlank { download.message }
                    Text(detail,
                        style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (download.phase == UpgradePhase.Ready && !download.logFile) TextButton(install) {
                    Text("安装", maxLines = 1)
                }
                IconButton(vm::cancelAndroidDownload) {
                    Icon(Icons.Outlined.Close, if (download.active) "取消下载" else "清除下载状态", Modifier.size(18.dp),
                        tint = HuluwaColors.Muted)
                }
            }
            if (download.active) DownloadProgress(download.progress)
        }
    }
}

@Composable private fun BuildOutput(
    build: AndroidBuildState, clock: Long, vm: HuluwaViewModel, snackbar: SnackbarHostState,
    install: () -> Unit, modifier: Modifier = Modifier,
) {
    val task = build.selectedTask
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var menuExpanded by remember { mutableStateOf(false) }
    // 同时观察测量后的滚动范围，确保新增日志已排版后仍能跟随到底部。
    LaunchedEffect(build.selectedId, build.logs.length, build.follow, scroll.maxValue) {
        if (build.follow && build.logs.isNotEmpty()) scroll.scrollTo(scroll.maxValue)
    }
    Surface(modifier, shape = RoundedCornerShape(16.dp), color = Color.White,
        border = BorderStroke(1.dp, HuluwaColors.Line)) {
        Column(Modifier.fillMaxSize().padding(horizontal = 12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("执行日志", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (task != null) Text("#${task.id} · ${task.statusLabel}",
                    style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    color = when (task.status) {
                        "SUCCESS" -> HuluwaColors.Pine
                        "FAILED", "TIMED_OUT", "INTERRUPTED" -> HuluwaColors.Danger
                        else -> HuluwaColors.Muted
                    })
                Box {
                    IconButton({ menuExpanded = true }) { Icon(Icons.Outlined.MoreHoriz, "日志操作") }
                    DropdownMenu(menuExpanded, { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text(if (build.follow) "暂停自动滚动" else "开启自动滚动") },
                            leadingIcon = { Icon(if (build.follow) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null) },
                            onClick = { menuExpanded = false; vm.toggleAndroidFollow() })
                        DropdownMenuItem(text = { Text("复制日志") }, enabled = build.logs.isNotBlank(),
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = {
                                menuExpanded = false
                                context.getSystemService(ClipboardManager::class.java)
                                    .setPrimaryClip(ClipData.newPlainText("构建日志", build.logs))
                                scope.launch { snackbar.showSnackbar("日志已复制") }
                            })
                        DropdownMenuItem(text = { Text("下载完整日志") },
                            enabled = task != null && build.logs.isNotBlank() && !build.download.active,
                            leadingIcon = { Icon(Icons.Outlined.Download, null) },
                            onClick = { menuExpanded = false; task?.let { vm.downloadAndroidArtifact(it, logFile = true) } })
                    }
                }
            }
            if (build.download.phase != UpgradePhase.Idle) {
                DownloadStrip(build.download, vm, install)
                Spacer(Modifier.height(8.dp))
            }
            task?.error?.takeIf { it.isNotBlank() }?.let { error ->
                Text(error, Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable {
                    vm.loadText("任务错误") { error }
                }.padding(bottom = 6.dp), color = HuluwaColors.Danger,
                    style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HuluwaColors.Paper)) {
                if (build.logs.isBlank()) {
                    Column(Modifier.align(Alignment.Center).padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.Subject, null, Modifier.size(26.dp), tint = HuluwaColors.Muted.copy(alpha = .6f))
                        Text(if (task == null) "开始构建后，日志将在这里显示" else "等待任务输出日志…",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                    }
                } else SelectionContainer(Modifier.fillMaxSize()) {
                    Text(build.logs, Modifier.fillMaxSize().verticalScroll(scroll).padding(12.dp),
                        color = HuluwaColors.Ink, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 18.sp)
                }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(
                    if (build.connectionError) HuluwaColors.Amber else if (task?.active == true) HuluwaColors.Pine else HuluwaColors.Muted))
                Text(when {
                    build.connectionError -> "正在重连"
                    task == null -> "等待任务"
                    !build.follow -> "已暂停自动滚动"
                    build.logComplete -> "日志已全部加载"
                    else -> "实时更新 · 自动跟随"
                }, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                task?.let { Text(it.elapsedLabel(clock), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                    maxLines = 1, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}
