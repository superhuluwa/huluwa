package com.huluwa.android.data

import android.os.Build
import kotlinx.serialization.json.*

const val UPGRADE_SESSION_ID = "local-app-upgrade"

data class InstalledAppVersion(val name: String = "未知", val code: Long = 0, val signatures: Set<String> = emptySet())

data class AppRelease(
    val taskId: Long, val versionName: String, val versionCode: Long, val packageName: String,
    val apkSize: Long, val sha256: String, val signatureSha256: String, val minSdk: Int,
    val publishedAt: String,
) {
    fun toJson(): JsonObject = jsonBody {
        put("taskId", taskId); put("versionName", versionName); put("versionCode", versionCode)
        put("packageName", packageName); put("apkSize", apkSize); put("sha256", sha256)
        put("signatureSha256", signatureSha256); put("minSdk", minSdk); put("publishedAt", publishedAt)
    }
    companion object {
        fun from(raw: JsonObject): AppRelease {
            val release = AppRelease(raw["taskId"]?.jsonPrimitive?.longOrNull ?: 0,
                raw.text("versionName"), raw["versionCode"]?.jsonPrimitive?.longOrNull ?: 0,
                raw.text("packageName"), raw["apkSize"]?.jsonPrimitive?.longOrNull ?: 0,
                raw.text("sha256").lowercase(), raw.text("signatureSha256").lowercase(),
                raw["minSdk"]?.jsonPrimitive?.intOrNull ?: 26, raw.text("publishedAt"))
            require(release.taskId > 0 && release.versionCode > 0 && release.apkSize > 0
                && release.versionName.isNotBlank() && release.packageName.isNotBlank()
                && release.sha256.matches(Regex("[0-9a-f]{64}"))
                && release.signatureSha256.matches(Regex("[0-9a-f]{64}"))) { "服务端版本信息不完整，请稍后重试" }
            return release
        }
    }
}

enum class UpgradePhase { Idle, Downloading, Verifying, Ready, Failed }

data class UpgradeDownload(
    val phase: UpgradePhase = UpgradePhase.Idle, val release: AppRelease? = null,
    val received: Long = 0, val total: Long = 0, val message: String = "", val error: String? = null,
    val installRequested: Boolean = false,
) {
    val active get() = phase == UpgradePhase.Downloading || phase == UpgradePhase.Verifying
    val progress get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null
}

data class AppUpgradeState(
    val installed: InstalledAppVersion = InstalledAppVersion(), val latest: AppRelease? = null,
    val checking: Boolean = false, val checked: Boolean = false, val checkError: String? = null,
    val checkedAt: Long = 0, val download: UpgradeDownload = UpgradeDownload(),
) {
    val hasUpdate get() = latest?.versionCode?.let { it > installed.code } == true
    val incompatibleReason: String? get() = latest?.let { release -> when {
        release.minSdk > Build.VERSION.SDK_INT -> "当前安卓系统版本较低，暂不支持安装此更新"
        installed.signatures.isEmpty() || release.signatureSha256 !in installed.signatures -> "服务端安装包的签名与当前 App 不一致，无法覆盖升级"
        else -> null
    } }
    val sessionPreview: String get() = when {
        download.active -> download.message.ifBlank { "正在下载应用更新" }
        download.phase == UpgradePhase.Ready -> "更新已下载，点击继续安装"
        hasUpdate -> "发现新版本 ${latest?.versionName}（${latest?.versionCode}），点击升级"
        checking && !checked -> "正在检查服务端最新版本"
        checkError != null -> "暂时无法检查更新，点击重试"
        latest != null && installed.code > latest.versionCode -> "当前版本 ${installed.name}（${installed.code}），高于服务端已发布版本"
        latest != null -> "当前版本 ${installed.name}（${installed.code}），已是最新版本"
        else -> "当前版本 ${installed.name}（${installed.code}），点击检查更新"
    }
}
