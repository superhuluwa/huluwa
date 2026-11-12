package com.huluwa.android

import android.app.Application
import android.os.Build
import android.os.Process
import kotlin.system.exitProcess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import com.huluwa.android.data.*

class HuluwaApplication : Application() {
    val session by lazy { SessionStore(this) }
    val browserBookmarks by lazy { BrowserBookmarkStore(this) }
    val chatCache by lazy { ChatCache(this) }
    val sessionListCache by lazy { SessionListCache(this, session) }
    val repository by lazy { HuluwaRepository(ApiClient(session), chatCache) }
    val appUpdates by lazy { AppUpdateManager(this, repository.api) }
    val terminalBuddyBuildDownloads by lazy { AndroidBuildDownloader(this, repository.api, AndroidBuildProject.TerminalBuddy) }
    val buildDownloads by lazy { AndroidBuildDownloader(this, repository.api) }
    private val logReportScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val logReporter by lazy { AppLogReporter(repository.api) }

    override fun onCreate() {
        super.onCreate()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            // 同步落盘后交还系统处理，重启应用仍可读取原始异常。
            runCatching { CrashLog.record(this, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
        // 手机上的 W/E 日志定期批量上报服务端，远程排查失败原因不再依赖连接电脑。
        logReportScope.launch {
            while (isActive) {
                delay(30_000)
                runCatching {
                    if (session.loggedIn) {
                        logReporter.flush(appVersion(), "${Build.MANUFACTURER} ${Build.MODEL}", session.username)
                    }
                }
            }
        }
    }

    private fun appVersion(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
}
