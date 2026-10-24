package com.huluwa.android

import android.app.Application
import com.huluwa.android.data.*

class HuluwaApplication : Application() {
    val session by lazy { SessionStore(this) }
    val chatCache by lazy { ChatCache(this) }
    val sessionListCache by lazy { SessionListCache(this, session) }
    val repository by lazy { HuluwaRepository(ApiClient(session), chatCache) }
    val aiRepository by lazy { AiRepository(this) }
    val appUpdates by lazy { AppUpdateManager(this, repository.api) }
    val buildDownloads by lazy { AndroidBuildDownloader(this, repository.api) }
}
