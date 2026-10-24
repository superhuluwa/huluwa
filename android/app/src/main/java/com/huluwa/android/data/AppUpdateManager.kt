package com.huluwa.android.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** System downloads survive backgrounding; installation always requires Android's confirmation. */
class AppUpdateManager(private val context: Context, private val api: ApiClient) {
    private val preferences = context.getSharedPreferences("huluwa_app_updates", Context.MODE_PRIVATE)
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val checks = Mutex()
    private val operations = Mutex()
    private val mutable = MutableStateFlow(AppUpgradeState(installed = installedVersion()))
    val state = mutable.asStateFlow()
    @Volatile private var epoch = 0
    @Volatile private var downloadId = -1L

    init {
        val release = runCatching { AppRelease.from(ApiJson.parseToJsonElement(preferences.getString("release", "").orEmpty()).asObject()) }.getOrNull()
        val id = preferences.getLong("downloadId", -1)
        if (api.store.loggedIn && preferences.getString("server", "") == api.store.server
            && preferences.getString("username", "") == api.store.username
            && release != null && release.versionCode > mutable.value.installed.code && id > 0) {
            downloadId = id
            mutable.update { it.copy(download = UpgradeDownload(UpgradePhase.Downloading, release,
                total = release.apkSize, message = "恢复上次更新下载", installRequested = preferences.getBoolean("installRequested", false))) }
        } else {
            if (id > 0) runCatching { downloads.remove(id) }
            preferences.edit().clear().apply()
        }
    }

    suspend fun checkLatest() {
        if (!api.store.loggedIn || !checks.tryLock()) return
        val current = epoch
        mutable.update { it.copy(checking = true) }
        try {
            val body = api.get("android", "releases", "latest").asObject()
            val release = if (body.flag("available")) AppRelease.from(body.child("release")) else null
            require(release == null || release.packageName == context.packageName) { "服务端安装包不属于葫芦娃 App" }
            updateIfCurrent(current) { it.copy(latest = release, checking = false,
                checked = true, checkError = null, checkedAt = System.currentTimeMillis()) }
        } catch (error: CancellationException) {
            updateIfCurrent(current) { it.copy(checking = false) }
            throw error
        } catch (error: Throwable) {
            updateIfCurrent(current) { it.copy(checking = false, checked = true,
                checkError = if (error is ApiException && error.code == 404) "服务器暂未启用在线升级，请先更新服务端" else error.userMessage()) }
            if (error is ApiException && error.code == 401 && current == epoch) throw error
        } finally { checks.unlock() }
    }

    suspend fun downloadLatest() = withContext(Dispatchers.IO) {
        operations.withLock {
            val snapshot = mutable.value
            if (snapshot.download.active) return@withLock
            val release = snapshot.latest ?: throw IllegalArgumentException("请先检查最新版本")
            require(release.versionCode > snapshot.installed.code) { "当前版本无需升级" }
            require(snapshot.incompatibleReason == null) { snapshot.incompatibleReason.orEmpty() }
            require(api.store.loggedIn) { "请先登录服务器" }
            val current = epoch
            val sourceServer = api.store.server
            val sourceUsername = api.store.username
            val sourceToken = api.store.token
            removeDownload()
            val file = apkFile(release)
            check(file.parentFile?.isDirectory == true || file.parentFile?.mkdirs() == true) { "无法创建更新目录，请检查手机存储空间" }
            if (file.exists()) check(file.delete()) { "无法清理旧的安装包，请稍后重试" }
            val endpoint = ServerAddress.endpoint(sourceServer, listOf("android", "tasks", release.taskId.toString(), "apk"))
            val request = DownloadManager.Request(Uri.parse(endpoint.toString()))
                .addRequestHeader("Authorization", "Bearer $sourceToken")
                .addRequestHeader("Accept", "application/vnd.android.package-archive")
                .setTitle("葫芦娃应用更新")
                .setDescription("下载版本 ${release.versionName}（${release.versionCode}）")
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "updates/${file.name}")
            val id = downloads.enqueue(request)
            synchronized(this@AppUpdateManager) {
                if (current != epoch) { downloads.remove(id); return@withLock }
                downloadId = id
                preferences.edit().putLong("downloadId", id).putString("release", release.toJson().toString())
                    .putString("server", sourceServer).putString("username", sourceUsername)
                    .putBoolean("installRequested", true).apply()
                mutable.update { it.copy(download = UpgradeDownload(UpgradePhase.Downloading, release,
                    total = release.apkSize, message = "等待开始下载", installRequested = true)) }
            }
        }
    }

    suspend fun pollDownload() = withContext(Dispatchers.IO) {
        operations.withLock {
            val id = downloadId
            val pending = mutable.value.download
            if (id <= 0 || pending.phase != UpgradePhase.Downloading) return@withLock
            val release = pending.release ?: return@withLock
            val current = epoch
            try {
                downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                    check(cursor != null && cursor.moveToFirst()) { "系统下载记录已移除，请重新下载更新" }
                    val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                    val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: release.apkSize
                    val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                    if (current != epoch) return@withLock
                    when (status) {
                        DownloadManager.STATUS_SUCCESSFUL -> {
                            updateIfCurrent(current) { it.copy(download = it.download.copy(phase = UpgradePhase.Verifying,
                                received = received, total = total, message = "下载完成，正在校验安装包")) }
                            verify(apkFile(release), release)
                            updateIfCurrent(current) { it.copy(download = it.download.copy(
                                phase = UpgradePhase.Ready, received = release.apkSize, total = release.apkSize,
                                message = "安装包已准备好，可以继续安装", error = null)) }
                        }
                        DownloadManager.STATUS_FAILED -> {
                            if (reason == 401) throw ApiException(401, "登录已过期，请重新登录后下载更新")
                            throw IllegalStateException(when (reason) {
                                DownloadManager.ERROR_INSUFFICIENT_SPACE -> "手机存储空间不足，请清理后重试"
                                404 -> "此安装包已被服务端清理，请重新检查最新版本"
                                else -> "更新下载失败，请检查网络后重试（$reason）"
                            })
                        }
                        else -> updateIfCurrent(current) { it.copy(download = it.download.copy(received = received, total = total,
                            message = when (status) {
                                DownloadManager.STATUS_PAUSED -> if (reason == DownloadManager.PAUSED_WAITING_TO_RETRY) "网络中断，等待自动重试" else "等待可用网络"
                                DownloadManager.STATUS_PENDING -> "等待开始下载"
                                else -> "正在下载版本 ${release.versionName}（${release.versionCode}）"
                            })) }
                    }
                }
            } catch (error: CancellationException) {
                // A stopped foreground monitor leaves the system download intact.
                if (current == epoch && mutable.value.download.phase == UpgradePhase.Verifying)
                    mutable.update { it.copy(download = it.download.copy(phase = UpgradePhase.Downloading)) }
                throw error
            } catch (error: Throwable) {
                if (current == epoch) failDownload(error.userMessage())
                if (error is ApiException && error.code == 401 && current == epoch) throw error
            }
        }
    }

    suspend fun cancelDownload() = withContext(Dispatchers.IO) {
        operations.withLock { removeDownload(); mutable.update { it.copy(download = UpgradeDownload()) } }
    }

    fun consumeInstallRequest() {
        preferences.edit().putBoolean("installRequested", false).apply()
        mutable.update { it.copy(download = it.download.copy(installRequested = false, error = null)) }
    }

    fun downloadError(message: String) = failDownload(message)

    fun installationError(message: String) { mutable.update { it.copy(download = it.download.copy(error = message)) } }
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    suspend fun installerIntent(): Intent = withContext(Dispatchers.IO) {
        operations.withLock {
            val release = mutable.value.download.release ?: throw IllegalStateException("请先下载更新")
            check(mutable.value.download.phase == UpgradePhase.Ready) { "安装包尚未准备好" }
            val file = apkFile(release)
            try {
                // Recheck immediately before handing a read-only URI to Android's installer.
                verify(file, release)
            } catch (error: Throwable) {
                failDownload(error.userMessage())
                throw error
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            // 覆盖安装会结束当前 App 进程，安装器必须使用独立任务栈。
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun refreshInstalledVersion() {
        val installed = installedVersion()
        if (mutable.value.download.release?.versionCode?.let { it <= installed.code } == true) removeDownload()
        mutable.update { it.copy(installed = installed, download = if (it.download.release?.versionCode?.let { code -> code <= installed.code } == true) UpgradeDownload() else it.download) }
    }

    @Synchronized fun reset() {
        epoch++
        removeDownload()
        mutable.value = AppUpgradeState(installed = installedVersion())
    }

    private fun updateIfCurrent(current: Int, transform: (AppUpgradeState) -> AppUpgradeState) {
        synchronized(this) { if (current == epoch) mutable.update(transform) }
    }

    private fun failDownload(message: String) {
        removeDownload()
        mutable.update { it.copy(download = UpgradeDownload(UpgradePhase.Failed, error = message)) }
    }

    private fun removeDownload() {
        val id = downloadId
        downloadId = -1
        if (id > 0) runCatching { downloads.remove(id) }
        mutable.value.download.release?.let { runCatching { apkFile(it).delete() } }
        preferences.edit().clear().apply()
    }

    private fun apkFile(release: AppRelease): File {
        val root = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: throw IllegalStateException("手机存储暂时不可用，请稍后重试")
        return File(root, "updates/huluwa-${release.taskId}-${release.versionCode}.apk")
    }

    private fun verify(file: File, release: AppRelease) {
        check(file.isFile && file.length() == release.apkSize) { "安装包不完整，请重新下载" }
        val hash = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(65536)
            while (true) { val read = input.read(buffer); if (read < 0) break; hash.update(buffer, 0, read) }
        }
        check(hex(hash.digest()) == release.sha256) { "安装包校验失败，请重新下载" }
        val info = context.packageManager.getPackageArchiveInfo(file.absolutePath, signatureFlags())
            ?: throw IllegalStateException("无法识别安装包，请重新下载")
        check(info.packageName == context.packageName && info.packageName == release.packageName) { "安装包不属于葫芦娃 App" }
        check(versionCode(info) == release.versionCode && versionCode(info) > installedVersion().code) { "安装包版本不符合升级要求，请重新检查更新" }
        check((info.applicationInfo?.minSdkVersion ?: release.minSdk) <= Build.VERSION.SDK_INT) { "当前安卓系统版本不支持此更新" }
        val signers = signatures(info)
        check(release.signatureSha256 in signers && signers.any { it in mutable.value.installed.signatures }) { "安装包签名与当前 App 不一致，已停止安装" }
    }

    private fun installedVersion(): InstalledAppVersion {
        val info = context.packageManager.getPackageInfo(context.packageName, signatureFlags())
        return InstalledAppVersion(info.versionName ?: "未知", versionCode(info), signatures(info))
    }
    @Suppress("DEPRECATION")
    private fun signatureFlags(): Int = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun signatures(info: PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures)
        .orEmpty().map { hex(MessageDigest.getInstance("SHA-256").digest(it.toByteArray())) }.toSet()
    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
