package com.huluwa.android

import android.app.Application
import com.huluwa.android.data.*

class HuluwaApplication : Application() {
    val session by lazy { SessionStore(this) }
    val repository by lazy { HuluwaRepository(ApiClient(session)) }
    val appUpdates by lazy { AppUpdateManager(this, repository.api) }
}
