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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * App 构建管理：迁移网页端「安卓 App」页面。
 * 概览卡片（源码 / 最近安装包 / 当前任务 / 签名）+ 构建操作 + 增量执行日志 + 任务历史。
 */
@Composable fun AppBuildScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val build = ui.androidBuild
    val status = build.status
    val download = build.download
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
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            BuildOverview(build, ui.clock)
            // 加载状态统一显示在标题栏菊花上，内容区只保留错误反馈。
            Feedback(loading = false, error = build.tasks.error.takeUnless { it.isServerUnreachable() }, onRetry = { vm.refreshAndroidBuild() })
            val notice = when {
                build.connectionError -> "暂时无法连接构建服务，正在自动重连。服务器上的任务会继续执行。"
                build.tasks.loaded && !status.enabled -> "安卓构建服务尚未启用，请配置并启动构建容器。"
                build.tasks.loaded && !status.workerOnline -> "构建容器暂时离线，恢复后即可更新代码和编译。已生成的 APK 仍可下载。"
                else -> ""
            }
            if (notice.isNotBlank()) Text(notice, Modifier.padding(top = 8.dp),
                color = if (build.connectionError || status.enabled) HuluwaColors.Amber else HuluwaColors.Muted,
                style = MaterialTheme.typography.bodySmall)
        }
        item { BuildActions(build, ui.busy != null, vm) }
        if (download.phase != UpgradePhase.Idle) item { DownloadCard(download, vm, ::install) }
        item { LogPanel(build, ui.clock, vm, snackbar) }
        item { SectionTitle("任务历史", Modifier.padding(top = 4.dp)) }
        if (build.tasks.loaded && build.tasks.data.isEmpty()) item {
            EmptyContent("暂无任务记录", "先更新代码，再编译安装包", Icons.Outlined.Android)
        }
        items(build.tasks.data, key = { it.id }) { task ->
            TaskRow(task, task.id == build.selectedId, ui.clock) { vm.selectAndroidTask(task) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable private fun BuildOverview(build: AndroidBuildState, clock: Long) {
    val status = build.status
    val active = status.activeTask
    Panel(Modifier.fillMaxWidth(), HuluwaColors.Pine) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Outlined.Android, null, Modifier.size(32.dp), tint = HuluwaColors.Lime)
            Column(Modifier.weight(1f)) {
                Text("App 构建服务", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Text((if (status.workerOnline) "构建容器在线 · " else "构建容器离线 · ") + status.branch,
                    style = MaterialTheme.typography.labelMedium, color = Color.White.copy(alpha = .8f))
            }
            Tag(if (status.workerOnline) "在线" else "离线", tone = if (status.workerOnline) HuluwaColors.Lime else Color(0xFFFFD7BD))
        }
        HorizontalDivider(Modifier.padding(top = 16.dp), color = Color.White.copy(alpha = .2f))
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OverviewColumn("当前源码", status.source.commit.take(8).ifBlank { "尚未更新" },
                status.source.commitSubject.ifBlank { "点击更新代码获取最新源码" }, Modifier.weight(1f))
            OverviewColumn("最近构建", status.latestApk?.let { "v${it.versionName}" } ?: "暂无安装包",
                status.latestApk?.let { "版本代码 ${it.versionCode} · ${formatBytes(it.apkSize.toDouble())}" } ?: "编译成功后即可下载", Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            OverviewColumn("当前任务", active?.stageLabel ?: "空闲",
                active?.let { "${it.typeLabel} #${it.id} · ${it.elapsedLabel(clock)}" } ?: "就绪后可以开始新的任务", Modifier.weight(1f))
            OverviewColumn("签名配置", if (status.signingReady) "已配置" else "未配置",
                if (status.signingReady) "可以编译 Release 安装包" else "需先恢复 Release 签名文件", Modifier.weight(1f))
        }
    }
}

@Composable private fun OverviewColumn(title: String, value: String, detail: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .75f))
        Text(value, color = Color.White, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(detail, style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = .75f), maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable private fun BuildActions(build: AndroidBuildState, busy: Boolean, vm: HuluwaViewModel) {
    val status = build.status
    val active = status.activeTask
    val submitting = build.submitting
    Panel(Modifier.fillMaxWidth()) {
        Text("构建操作", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(5.dp))
        Text("先更新代码，再编译 APK；每次编译固定使用当前源码版本。",
            style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
        Spacer(Modifier.height(14.dp))
        Button({ vm.submitAndroidBuild("UPDATE") }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = status.canUpdate && submitting == null) {
            if (submitting == "UPDATE") { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
            else { Icon(Icons.Outlined.CloudDownload, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text("更新代码")
        }
        Spacer(Modifier.height(9.dp))
        Button({ vm.submitAndroidBuild("BUILD") }, Modifier.fillMaxWidth().heightIn(min = 48.dp),
            enabled = status.canBuild && submitting == null) {
            if (submitting == "BUILD") { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White); Spacer(Modifier.width(8.dp)) }
            else { Icon(Icons.Outlined.Build, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)) }
            Text("编译 APK")
        }
        if (submitting == null && active == null && !status.canBuild) {
            val reason = when {
                !status.enabled -> "安卓构建服务尚未启用"
                !status.workerOnline -> "构建容器离线，暂时无法编译"
                !status.hasSource -> "请先更新代码，获取最新安卓源码"
                !status.signingReady -> "尚未配置 Release 签名，暂时无法编译"
                else -> ""
            }
            Text(reason, Modifier.padding(top = 8.dp), color = HuluwaColors.Amber, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = HuluwaColors.Line)
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ status.latestApk?.let { vm.downloadAndroidArtifact(it) } },
                enabled = status.canDownload && !build.download.active) {
                Icon(Icons.Outlined.Download, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("下载 APK")
            }
            Spacer(Modifier.weight(1f))
            if (active != null) TextButton({ vm.cancelAndroidTask(active) }, enabled = active.status != "CANCELLING" && !busy) {
                Text(if (active.status == "CANCELLING") "正在取消" else "取消任务", color = HuluwaColors.Danger)
            }
        }
    }
}

@Composable private fun DownloadCard(download: AndroidDownload, vm: HuluwaViewModel, install: () -> Unit) {
    Panel(Modifier.fillMaxWidth(), if (download.phase == UpgradePhase.Failed) Color(0xFFF8F0E4) else HuluwaColors.SoftGreen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(if (download.logFile) Icons.Outlined.Subject else Icons.Outlined.Android, null, Modifier.size(19.dp),
                tint = if (download.phase == UpgradePhase.Failed) HuluwaColors.Amber else HuluwaColors.Pine)
            Text(if (download.logFile) "构建日志" else "构建安装包", Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            when (download.phase) {
                UpgradePhase.Ready -> Tag(if (download.logFile) "已下载" else "可安装")
                UpgradePhase.Failed -> Tag("失败", tone = HuluwaColors.Danger)
                else -> Tag("下载中")
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(download.filename.ifBlank { download.message }, style = MaterialTheme.typography.bodySmall,
            color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (download.active) {
            Spacer(Modifier.height(9.dp))
            DownloadProgress(download.progress)
            Spacer(Modifier.height(6.dp))
            Text("${download.message}${if (download.total > 0) " · ${formatBytes(download.received.toDouble())} / ${formatBytes(download.total.toDouble())}" else ""}",
                style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        }
        download.error?.let { Text(it, Modifier.padding(top = 8.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when (download.phase) {
                UpgradePhase.Downloading -> TextButton(vm::cancelAndroidDownload) { Text("取消下载") }
                UpgradePhase.Ready -> if (download.logFile) {
                    Text("已保存到手机 Download 目录", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                } else TextButton(install) {
                    Icon(Icons.Outlined.InstallMobile, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("安装")
                }
                UpgradePhase.Failed -> Text("可以重新点击下载按钮再试一次", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                else -> Unit
            }
            Spacer(Modifier.weight(1f))
            TextButton(vm::cancelAndroidDownload) { Text("清除") }
        }
    }
}

@Composable private fun LogPanel(build: AndroidBuildState, clock: Long, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val task = build.selectedTask
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val scroll = rememberScrollState()
    // 自动滚动：跟随开关打开且日志变长时贴到底部。
    LaunchedEffect(build.logs.length, build.follow) {
        if (build.follow && build.logs.isNotEmpty()) scroll.scrollTo(scroll.maxValue)
    }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("执行日志", style = MaterialTheme.typography.titleMedium)
            if (task != null) Text("${task.typeLabel} #${task.id}", Modifier.weight(1f),
                style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Spacer(Modifier.weight(1f))
            if (task != null) StatusTag(task.statusLabel, healthy = task.status == "SUCCESS",
                alert = task.status in setOf("FAILED", "TIMED_OUT", "INTERRUPTED"))
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(vm::toggleAndroidFollow) { Text(if (build.follow) "暂停滚动" else "自动滚动") }
            TextButton({
                if (build.logs.isBlank()) return@TextButton
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("构建日志", build.logs))
                scope.launch { snackbar.showSnackbar("日志已复制") }
            }, enabled = build.logs.isNotBlank()) { Text("复制") }
            TextButton({ task?.let { vm.downloadAndroidArtifact(it, logFile = true) } },
                enabled = task != null && build.logs.isNotBlank() && !build.download.active) { Text("下载日志") }
            Spacer(Modifier.weight(1f))
            task?.let { Text(it.elapsedLabel(clock), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted) }
        }
        task?.error?.takeIf { it.isNotBlank() }?.let {
            Text(it, Modifier.padding(top = 6.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(260.dp)
            .clip(RoundedCornerShape(12.dp)).background(Color(0xFF0A1220))) {
            when {
                task == null -> Text("开始更新代码或编译后，这里会显示执行日志。", Modifier.padding(14.dp),
                    color = Color(0xFF7A8DA3), style = MaterialTheme.typography.bodySmall)
                build.logs.isBlank() -> Text("等待任务输出日志…", Modifier.padding(14.dp),
                    color = Color(0xFF7A8DA3), style = MaterialTheme.typography.bodySmall)
                else -> SelectionContainer {
                    Text(build.logs, Modifier.fillMaxSize().verticalScroll(scroll).padding(14.dp),
                        color = Color(0xFFC7D9CA), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 17.sp)
                }
            }
        }
        if (task != null) Text(if (build.logComplete) "日志已全部加载，完整内容可下载" else "日志每秒增量更新，完整内容可下载",
            Modifier.padding(top = 7.dp), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
    }
}

@Composable private fun TaskRow(task: AndroidBuildTask, selected: Boolean, clock: Long, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
        color = if (selected) HuluwaColors.SoftGreen else Color.White, shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (selected) HuluwaColors.Pine.copy(alpha = .35f) else HuluwaColors.Line)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("#${task.id}", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                Text(task.typeLabel, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusTag(task.statusLabel, healthy = task.status == "SUCCESS",
                    alert = task.status in setOf("FAILED", "TIMED_OUT", "INTERRUPTED"))
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Commit, null, Modifier.size(14.dp), tint = HuluwaColors.Muted)
                Text(task.commit.take(8).ifBlank { "—" }, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                if (task.building && task.versionName.isNotBlank()) Text("v${task.versionName}（${task.versionCode ?: "—"}）",
                    style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.weight(1f))
                if (task.downloadable) Tag(formatBytes(task.apkSize.toDouble()), outlined = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(task.startedLabel, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Text(task.elapsedLabel(clock), style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Spacer(Modifier.weight(1f))
                Text(if (selected) "正在查看日志" else "点击查看日志", style = MaterialTheme.typography.labelSmall,
                    color = if (selected) HuluwaColors.Pine else HuluwaColors.Muted)
            }
            if (task.active) LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = HuluwaColors.Pine)
        }
    }
}
