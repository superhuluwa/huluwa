package com.huluwa.android.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable fun AppUpgradeScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val upgrade = ui.upgrade
    val download = upgrade.download
    val reconnecting = rememberReconnecting(vm)
    val context = LocalContext.current
    var continueInstallation by rememberSaveable { mutableStateOf(false) }
    val installer = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        vm.refreshAppUpdateAfterInstall()
    }
    val installPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.canInstallAppUpdate()) continueInstallation = true
        else vm.appUpdateInstallationError("尚未允许安装应用。授权后点击“继续安装”即可。")
    }
    fun requestInstallation() {
        vm.consumeUpdateInstallRequest()
        if (vm.canInstallAppUpdate()) continueInstallation = true
        else runCatching {
            installPermission.launch(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }.onFailure { vm.appUpdateInstallationError("无法打开安装授权页面，请在系统设置中允许葫芦娃安装应用") }
    }
    LaunchedEffect(download.phase, download.installRequested) {
        if (download.phase == UpgradePhase.Ready && download.installRequested) requestInstallation()
    }
    LaunchedEffect(continueInstallation) {
        if (continueInstallation) {
            try { installer.launch(vm.appUpdateInstallerIntent()) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { vm.appUpdateInstallationError(error.userMessage()) }
            finally { continueInstallation = false }
        }
    }
    val ready = download.phase == UpgradePhase.Ready
    val compatible = upgrade.incompatibleReason == null
    val buttonEnabled = ready || (upgrade.hasUpdate && compatible && !download.active)
    val buttonLabel = when (download.phase) {
        UpgradePhase.Downloading -> download.progress?.let { "正在下载 ${(it * 100).toInt()}%" } ?: "正在下载"
        UpgradePhase.Verifying -> "正在校验安装包"
        UpgradePhase.Ready -> "继续安装"
        UpgradePhase.Failed -> "重新下载更新"
        UpgradePhase.Idle -> when {
            upgrade.checking && !upgrade.checked -> "正在检查更新"
            upgrade.hasUpdate -> "立即升级"
            upgrade.latest == null -> "暂无可用更新"
            upgrade.installed.code > (upgrade.latest?.versionCode ?: 0) -> "当前版本较新"
            else -> "已是最新版本"
        }
    }
    Scaffold(
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::closeChat) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回会话") }
                    RoundIcon(Icons.Outlined.SystemUpdate, modifier = Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 标题按剩余宽度省略，确保右侧的加载菊花与重连徽标始终可见。
                            Text("在线升级", Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium)
                            // 检查更新时的加载状态由标题右侧的菊花承载，刷新按钮保持常显。
                            TitleLoadingIndicator(upgrade.checking)
                            TitleReconnectBadge(reconnecting)
                        }
                        Text("从你的服务器获取最新版本", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                    IconButton(vm::checkAppUpdate, enabled = !upgrade.checking) {
                        Icon(Icons.Outlined.Refresh, "检查更新")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).navigationBarsPadding(),
            contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Panel(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Outlined.PhoneAndroid, null, Modifier.size(18.dp), tint = HuluwaColors.Pine)
                        Text("当前版本", style = MaterialTheme.typography.labelLarge, color = HuluwaColors.Muted)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("v${upgrade.installed.name}", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(5.dp))
                    Text("版本代码 ${upgrade.installed.code}", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                }
            }
            item {
                Panel(Modifier.fillMaxWidth(), if (upgrade.hasUpdate) HuluwaColors.SoftGreen else Color.White) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("服务端最新版本", style = MaterialTheme.typography.labelLarge, color = HuluwaColors.Muted)
                        if (upgrade.hasUpdate) Tag("发现新版本")
                        else if (upgrade.latest?.versionCode == upgrade.installed.code) Tag("已是最新版本")
                    }
                    Spacer(Modifier.height(12.dp))
                    val latest = upgrade.latest
                    if (latest != null) {
                        Text("v${latest.versionName}", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(5.dp))
                        Text("版本代码 ${latest.versionCode} · ${formatBytes(latest.apkSize.toDouble())}",
                            style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                        if (latest.publishedAt.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Text("发布于 ${releaseTime(latest.publishedAt)}", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                        }
                    } else Text(if (upgrade.checking) "正在获取最新版本…" else if (upgrade.checked && upgrade.checkError == null)
                        "服务器尚未发布安装包" else "等待连接服务器", style = MaterialTheme.typography.bodyMedium, color = HuluwaColors.Muted)
                }
            }
            if (upgrade.checkError != null && !upgrade.checkError.isServerUnreachable()) item {
                Panel(Modifier.fillMaxWidth()) {
                    Text(upgrade.checkError, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodyMedium)
                    TextButton(vm::checkAppUpdate, enabled = !upgrade.checking) { Text("重新检查") }
                }
            }
            if (upgrade.hasUpdate && !compatible) item {
                Text(upgrade.incompatibleReason.orEmpty(), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodyMedium)
            }
            item {
                Panel(Modifier.fillMaxWidth()) {
                    if (download.active || ready) {
                        Text(download.message, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(10.dp))
                        if (download.phase == UpgradePhase.Downloading) DownloadProgress(download.progress, Modifier.fillMaxWidth())
                        else if (download.phase == UpgradePhase.Verifying) DownloadProgress(null, Modifier.fillMaxWidth())
                        if (download.total > 0) {
                            Spacer(Modifier.height(7.dp))
                            Text("${formatBytes(download.received.toDouble())} / ${formatBytes(download.total.toDouble())}",
                                style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    if (download.error != null) {
                        Text(download.error, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                    }
                    Button(onClick = { if (ready) requestInstallation() else vm.downloadAppUpdate() },
                        enabled = buttonEnabled, modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp)) {
                        Icon(if (ready) Icons.Outlined.InstallMobile else Icons.Outlined.SystemUpdate, null, Modifier.size(19.dp))
                        Spacer(Modifier.width(8.dp)); Text(buttonLabel)
                    }
                    if (download.active) TextButton(vm::cancelAppUpdate, Modifier.align(Alignment.CenterHorizontally)) { Text("取消下载") }
                    Spacer(Modifier.height(12.dp))
                    Text("下载完成后会打开系统安装页面，由你确认安装。首次升级需要允许葫芦娃安装应用。",
                        style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                    if (upgrade.checkedAt > 0) {
                        Spacer(Modifier.height(10.dp))
                        Text("页面会自动检查最新版本，也可以点击右上角刷新。", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                }
            }
        }
    }
}

private fun releaseTime(raw: String): String = runCatching {
    OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(raw.take(16).replace('T', ' '))
