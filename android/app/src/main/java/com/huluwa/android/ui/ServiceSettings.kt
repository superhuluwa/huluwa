package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*
import kotlinx.serialization.json.*

fun settingsTitle(kind: String) = when (kind) {
    "magnet" -> "qBittorrent 连接设置"
    "video" -> "视频解析服务设置"
    "xunlei" -> "迅雷【飞牛】连接设置"
    "xunlei-win" -> "迅雷【Win】连接设置"
    "movie" -> "$MOVIE_SESSION_TITLE 下载设置"
    else -> "离线下载设置"
}
private data class SettingField(val key: String, val label: String, val secret: Boolean = false)
@Composable fun ServiceSettings(sheet: AppSheet.Settings, busy: Boolean, vm: HuluwaViewModel) {
    Feedback(sheet.data.error, { vm.showSettings(sheet.kind) })
    if (!sheet.data.loaded || sheet.data.error != null) return
    val raw = sheet.data.data
    val settings = (raw["settings"] as? JsonObject) ?: raw
    var error by remember(sheet.kind, settings) { mutableStateOf<String?>(null) }
    when (sheet.kind) {
        "movie" -> {
            val tools = listOf("qb", "xunlei", "xunlei-win")
            var tool by remember(settings) { mutableStateOf(settings.text("downloader", fallback = "qb")) }
            Text("默认下载器", style = MaterialTheme.typography.titleMedium)
            FilterRow(listOf("qBittorrent", "迅雷【飞牛】", "迅雷【Win】"), tools.indexOf(tool), { tool = tools[it] })
            LabelValue("服务器下载根目录", settings.text("saveRoot"))
            Text("此设置用于新建电影港订阅和手动磁力下载，已有订阅保留各自的下载器。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
            Button({ vm.saveSettings(sheet.kind, jsonBody { put("downloader", tool) }) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("保存设置") }
        }
        "dytt" -> HlsSettings(settings, busy, vm)
        else -> {
            val fields = when (sheet.kind) {
                "magnet" -> listOf(SettingField("url", "服务地址"), SettingField("username", "账号"), SettingField("password", "密码", true), SettingField("savePath", "保存目录（可选）"))
                "video" -> listOf(SettingField("url", "解析服务地址"), SettingField("username", "账号（可选）"), SettingField("password", "密码（可选）", true))
                "xunlei" -> listOf(SettingField("url", "飞牛 / 迅雷服务地址"), SettingField("fnosUsername", "飞牛账号"), SettingField("fnosPassword", "飞牛密码", true),
                    SettingField("twofaCode", "两步验证码（需要时填写）", true), SettingField("token", "手动认证值（可选）", true), SettingField("savePath", "保存目录（可选）"))
                else -> listOf(SettingField("url", "xunlei-win-api 服务地址"), SettingField("savePath", "Windows 保存目录（可选）"))
            }
            val values = remember(sheet.kind, settings) { mutableStateMapOf<String, String>().apply { fields.forEach { put(it.key, if (it.secret) "" else settings.text(it.key)) } } }
            var useAuth by remember(settings) { mutableStateOf(settings.flag("useAuth", true)) }
            if (sheet.kind == "magnet") Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("使用账号密码认证", Modifier.weight(1f)); Switch(useAuth, { useAuth = it }, enabled = !busy)
            }
            fields.forEach { field ->
                if (sheet.kind != "magnet" || useAuth || field.key !in setOf("username", "password")) OutlinedTextField(values[field.key].orEmpty(), { values[field.key] = it },
                    Modifier.fillMaxWidth(), label = { Text(field.label) }, singleLine = true, enabled = !busy,
                    visualTransformation = if (field.secret) PasswordVisualTransformation() else VisualTransformation.None,
                    keyboardOptions = KeyboardOptions(keyboardType = if (field.secret) KeyboardType.Password else if (field.key == "url") KeyboardType.Uri else KeyboardType.Text),
                    supportingText = { if (field.secret && field.key in setOf("password", "fnosPassword", "token")) Text("留空沿用服务器已保存的值") })
            }
            error?.let { Text(it, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
            Button({
                if (values["url"].isNullOrBlank()) error = "请填写服务地址"
                else vm.saveSettings(sheet.kind, jsonBody {
                    fields.forEach { field -> put(field.key, if (sheet.kind == "magnet" && !useAuth && field.key in setOf("username", "password")) "" else values[field.key].orEmpty().let { if (field.secret) it else it.trim() }) }
                    if (sheet.kind == "magnet") put("useAuth", useAuth)
                })
            }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("保存连接设置") }
        }
    }
}
@Composable private fun HlsSettings(settings: JsonObject, busy: Boolean, vm: HuluwaViewModel) {
    var threads by remember(settings) { mutableStateOf(settings.text("threadCount", fallback = "8")) }
    var attempts by remember(settings) { mutableStateOf(settings.text("maxAttempts", fallback = "3")) }
    var timeout by remember(settings) { mutableStateOf(settings.text("timeoutMinutes", fallback = "60")) }
    var format by remember(settings) { mutableStateOf(settings.text("format", fallback = "mp4")) }
    var speed by remember(settings) { mutableStateOf(settings.text("maxSpeed")) }
    var error by remember { mutableStateOf<String?>(null) }
    OutlinedTextField(threads, { threads = it }, Modifier.fillMaxWidth(), label = { Text("下载线程数") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(attempts, { attempts = it }, Modifier.fillMaxWidth(), label = { Text("最大重试次数") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(timeout, { timeout = it }, Modifier.fillMaxWidth(), label = { Text("任务超时（分钟）") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Text("封装格式", style = MaterialTheme.typography.bodyMedium)
    FilterRow(listOf("mp4", "mkv"), listOf("mp4", "mkv").indexOf(format), { format = listOf("mp4", "mkv")[it] })
    OutlinedTextField(speed, { speed = it }, Modifier.fillMaxWidth(), label = { Text("限速（留空表示不限速）") }, placeholder = { Text("例如：10M") }, singleLine = true, enabled = !busy)
    error?.let { Text(it, color = HuluwaColors.Danger) }
    Button({
        val count = threads.toIntOrNull(); val retry = attempts.toIntOrNull(); val minutes = timeout.toIntOrNull()
        if (count == null || retry == null || minutes == null || count !in 1..32 || retry !in 1..10 || minutes !in 5..1440) error = "线程应为 1–32，重试应为 1–10，超时应为 5–1440 分钟"
        else vm.saveSettings("dytt", jsonBody { put("threadCount", count); put("maxAttempts", retry); put("timeoutMinutes", minutes); put("format", format); put("maxSpeed", speed.trim()) })
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("保存下载设置") }
}
