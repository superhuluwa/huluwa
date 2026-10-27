package com.huluwa.android

import android.app.Application
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
}
