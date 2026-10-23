@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*

@Composable fun AccountSheet(ui: AppState, vm: HuluwaViewModel) {
    var changePassword by remember { mutableStateOf(false) }
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    LabelValue("服务器地址", ui.auth.server)
    LabelValue("当前账号", ui.auth.username)
    val reconnecting = rememberReconnecting(vm)
    StatusTag(when { reconnecting -> "消息同步中断"; ui.realtime -> "实时消息已连接"; else -> "消息自动轮询同步" }, ui.realtime && !reconnecting)
    OutlinedButton({ changePassword = !changePassword }, Modifier.fillMaxWidth(), enabled = ui.busy == null) { Text(if (changePassword) "收起修改密码" else "修改密码") }
    if (changePassword) {
        OutlinedTextField(old, { old = it }, Modifier.fillMaxWidth(), label = { Text("原密码") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = ui.busy == null, singleLine = true)
        OutlinedTextField(new, { new = it }, Modifier.fillMaxWidth(), label = { Text("新密码（6–64 位）") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = ui.busy == null, singleLine = true)
        OutlinedTextField(confirm, { confirm = it }, Modifier.fillMaxWidth(), label = { Text("再次输入新密码") }, visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), enabled = ui.busy == null, singleLine = true)
        Button({ vm.changePassword(old, new, confirm) }, Modifier.fillMaxWidth(), enabled = ui.busy == null && !reconnecting && old.isNotBlank() && new.isNotBlank() && confirm.isNotBlank()) { Text("确认修改") }
    }
    TextButton({ vm.confirm("退出登录", "退出后可以切换服务器或账号，服务器任务会继续运行。", "退出") { vm.logout() } }, Modifier.fillMaxWidth(), enabled = ui.busy == null) { Text("退出登录 / 切换服务器", color = HuluwaColors.Danger) }
}
@Composable fun NewDownloadSheet(busy: Boolean, vm: HuluwaViewModel) {
    var title by rememberSaveable { mutableStateOf("") }
    var magnet by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    Text("下载任务由家庭服务器执行，文件保存到服务器配置的目录。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth(), label = { Text("任务名称（可选）") }, enabled = !busy, singleLine = true)
    OutlinedTextField(magnet, { magnet = it; error = null }, Modifier.fillMaxWidth().heightIn(min = 130.dp), label = { Text("磁力链接") }, placeholder = { Text("magnet:?xt=urn:btih:…") },
        enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri), isError = error != null)
    error?.let { Text(it, color = HuluwaColors.Danger) }
    Text("默认下载器可在“我的订阅 → 电影港 → 下载设置”中选择。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    Button({ if (!magnet.trim().startsWith("magnet:?", true)) error = "请粘贴有效的磁力链接" else vm.newDownload(title, magnet.trim()) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && magnet.isNotBlank()) { Text("添加下载任务") }
}
@Composable fun TunnelEditor(tunnel: Tunnel?, busy: Boolean, vm: HuluwaViewModel) {
    val original = tunnel?.draft ?: TunnelDraft()
    var name by rememberSaveable(original.name) { mutableStateOf(original.name) }
    var type by rememberSaveable(original.name) { mutableStateOf(original.proxyType) }
    var localIp by rememberSaveable(original.name) { mutableStateOf(original.localIp) }
    var localPort by rememberSaveable(original.name) { mutableStateOf(original.localPort?.toString().orEmpty()) }
    var remotePort by rememberSaveable(original.name) { mutableStateOf(original.remotePort?.toString().orEmpty()) }
    var domains by rememberSaveable(original.name) { mutableStateOf(original.customDomains) }
    var subdomain by rememberSaveable(original.name) { mutableStateOf(original.subdomain) }
    var remark by rememberSaveable(original.name) { mutableStateOf(original.remark) }
    var encryption by rememberSaveable(original.name) { mutableStateOf(original.encryption) }
    var compression by rememberSaveable(original.name) { mutableStateOf(original.compression) }
    var enabled by rememberSaveable(original.name) { mutableStateOf(original.enabled) }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("隧道名称") }, enabled = !busy && tunnel == null, singleLine = true)
    FilterRow(listOf("TCP", "UDP", "HTTP", "HTTPS"), listOf("tcp", "udp", "http", "https").indexOf(type), { type = listOf("tcp", "udp", "http", "https")[it] })
    OutlinedTextField(localIp, { localIp = it }, Modifier.fillMaxWidth(), label = { Text("本地服务地址") }, enabled = !busy, singleLine = true)
    OutlinedTextField(localPort, { localPort = it }, Modifier.fillMaxWidth(), label = { Text("本地端口") }, enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    if (type in listOf("tcp", "udp")) OutlinedTextField(remotePort, { remotePort = it }, Modifier.fillMaxWidth(), label = { Text("云端端口") }, enabled = !busy, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    else {
        OutlinedTextField(domains, { domains = it }, Modifier.fillMaxWidth(), label = { Text("自定义域名") }, supportingText = { Text("多个域名用英文逗号分隔") }, enabled = !busy, singleLine = true)
        OutlinedTextField(subdomain, { subdomain = it }, Modifier.fillMaxWidth(), label = { Text("子域名（可选）") }, enabled = !busy, singleLine = true)
    }
    OutlinedTextField(remark, { remark = it }, Modifier.fillMaxWidth(), label = { Text("备注（可选）") }, enabled = !busy, singleLine = true)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("启用隧道", Modifier.weight(1f)); Switch(enabled, { enabled = it }, enabled = !busy) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("传输加密", Modifier.weight(1f)); Switch(encryption, { encryption = it }, enabled = !busy) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text("数据压缩", Modifier.weight(1f)); Switch(compression, { compression = it }, enabled = !busy) }
    error?.let { Text(it, color = HuluwaColors.Danger) }
    Button({
        val draft = TunnelDraft(name.trim(), type, localIp.trim(), localPort.toIntOrNull(), remotePort.toIntOrNull(), domains.trim(), subdomain.trim(), remark.trim(), encryption, compression, enabled)
        val failure = draft.validationError()
        if (failure != null) error = failure else vm.saveTunnel(tunnel?.name, draft)
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("保存并应用") }
}
@Composable fun CreateResource(sheet: AppSheet.CreateResource, busy: Boolean, vm: HuluwaViewModel) {
    var name by rememberSaveable(sheet.section) { mutableStateOf("") }
    OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(if (sheet.section == DockerSection.Images) "镜像名称" else "资源名称") },
        placeholder = { Text(if (sheet.section == DockerSection.Images) "nginx:stable" else "例如：huluwa_data") }, enabled = !busy, singleLine = true)
    Text(if (sheet.section == DockerSection.Images) "镜像拉取可能需要数分钟，请等待服务端完成。" else if (sheet.section == DockerSection.Networks) "创建一个使用 bridge 驱动的网络。" else "创建一个使用 local 驱动的存储卷。",
        style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
    Button({ vm.createResource(sheet.section, name) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy && name.isNotBlank()) { Text(if (sheet.section == DockerSection.Images) "拉取镜像" else "创建资源") }
}
