package com.huluwa.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.*

/**
 * 把 AppLog 中 W/E 级别的待上报日志批量送到服务端 app_error_log 表，
 * 便于在服务端直接读取手机上的失败原因；上报失败时条目放回队列等下次重试。
 */
class AppLogReporter(private val api: ApiClient) {

    suspend fun flush(version: String, device: String, username: String) {
        val entries = AppLog.drainPending()
        if (entries.isEmpty()) return
        try {
            api.post("app-logs", body = jsonBody {
                put("version", version)
                put("device", device)
                put("username", username)
                put("entries", buildJsonArray {
                    for (entry in entries) addJsonObject {
                        put("at", entry.at)
                        put("level", entry.level)
                        put("tag", entry.tag)
                        put("message", entry.message)
                    }
                })
            })
        } catch (error: CancellationException) {
            AppLog.restorePending(entries)
            throw error
        } catch (error: Exception) {
            // 失败不打 W/E（否则自我放大），放回队列下次再试
            AppLog.restorePending(entries)
        }
    }
}
