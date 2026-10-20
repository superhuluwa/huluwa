package com.huluwa.android.data

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import kotlinx.serialization.json.put
import java.io.File

/**
 * 构建产物下载：服务端只下发短时下载票据，这里交给系统 DownloadManager 拉取。
 * 下载在 App 退到后台后仍会继续，完成后可通过 FileProvider 交给系统安装器。
 */
class AndroidBuildDownloader(private val context: Context, private val api: ApiClient) {
    private val downloads = context.getSystemService(DownloadManager::class.java)
    private val operations = Mutex()
    private val mutable = MutableStateFlow(AndroidDownload())
    val state = mutable.asStateFlow()
    @Volatile private var downloadId = -1L
    @Volatile private var pendingFile: File? = null

    /** 申请下载票据并提交系统下载；logFile 为 true 时下载完整日志。 */
    suspend fun download(task: AndroidBuildTask, logFile: Boolean = false) = withContext(Dispatchers.IO) {
        operations.withLock {
            val current = mutable.value
            if (current.active) return@withLock
            removeDownload()
            val ticket = api.post("android", "tasks", task.id.toString(), "download-ticket",
                body = jsonBody { put("logFile", logFile) }).asObject()
            val path = ticket.text("path")
            require(path.startsWith("/api/public/android-downloads/")) { "下载地址无效，请刷新页面后重试" }
            val filename = ticket.text("filename").ifBlank {
                if (logFile) "android-task-${task.id}.log" else "huluwa-${task.versionName}-${task.id}.apk"
            }
            val directory = if (logFile) "logs" else "builds"
            val target = File(buildRoot(), "$directory/$filename")
            check(target.parentFile?.isDirectory == true || target.parentFile?.mkdirs() == true) { "无法创建下载目录，请检查手机存储空间" }
            if (target.exists()) check(target.delete()) { "无法清理旧文件，请稍后重试" }
            val request = DownloadManager.Request(Uri.parse(ServerAddress.publicUrl(api.store.server, path).toString()))
                .setTitle(if (logFile) "葫芦娃构建日志" else "葫芦娃构建安装包")
                .setDescription("${task.typeLabel} #${task.id} · ${filename}")
                .setMimeType(if (logFile) "text/plain" else "application/vnd.android.package-archive")
                .setAllowedOverMetered(true).setAllowedOverRoaming(false)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "$directory/$filename")
            val id = downloads.enqueue(request)
            downloadId = id
            pendingFile = target
            mutable.value = AndroidDownload(taskId = task.id, phase = UpgradePhase.Downloading, logFile = logFile,
                filename = filename, total = task.apkSize, message = if (logFile) "正在下载构建日志…" else "正在下载安装包…")
        }
    }

    /** 轮询系统下载进度；没有进行中的下载时直接返回。 */
    suspend fun poll() = withContext(Dispatchers.IO) {
        val pending = mutable.value
        val id = downloadId
        if (id <= 0 || pending.phase != UpgradePhase.Downloading) return@withContext
        try {
            downloads.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
                check(cursor != null && cursor.moveToFirst()) { "系统下载记录已移除，请重新下载" }
                val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val received = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: pending.total
                val reason = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> mutable.update { it.copy(phase = UpgradePhase.Ready,
                        received = received, total = total, message = if (pending.logFile) "构建日志已下载" else "安装包已下载，可以安装") }
                    DownloadManager.STATUS_FAILED -> mutable.update { it.copy(phase = UpgradePhase.Failed, error = when (reason) {
                        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "手机存储空间不足，请清理后重试"
                        404 -> "下载链接已失效，请重新点击下载"
                        401 -> "登录已过期，请重新登录后下载"
                        else -> "下载失败，请检查网络后重试（$reason）"
                    }) }
                    else -> mutable.update { it.copy(received = received, total = total, message = when (status) {
                        DownloadManager.STATUS_PAUSED -> "网络中断，等待自动重试"
                        DownloadManager.STATUS_PENDING -> "等待开始下载"
                        else -> pending.message
                    }) }
                }
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { mutable.update { it.copy(phase = UpgradePhase.Failed, error = error.userMessage()) } }
    }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        operations.withLock { removeDownload(); mutable.value = AndroidDownload() }
    }

    /** 清理下载状态（用于退出登录）。 */
    @Synchronized fun reset() {
        removeDownload()
        mutable.value = AndroidDownload()
    }

    /** 安装已下载的构建包；文件缺失时返回 null。 */
    fun installIntent(): Intent? {
        val file = pendingFile?.takeIf { it.isFile } ?: return null
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /** 安装授权或安装启动失败时，把错误显示在下载卡片上。 */
    fun installationError(message: String) {
        mutable.update { it.copy(error = message) }
    }

    private fun removeDownload() {
        val id = downloadId
        downloadId = -1
        if (id > 0) runCatching { downloads.remove(id) }
        pendingFile?.let { runCatching { it.delete() } }
        pendingFile = null
    }

    private fun buildRoot(): File = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        ?: throw IllegalStateException("手机存储暂时不可用，请稍后重试")
}
