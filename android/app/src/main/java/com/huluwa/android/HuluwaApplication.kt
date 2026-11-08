package com.huluwa.android

import android.app.Application
import android.os.Process
import kotlin.system.exitProcess
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
    }
}
