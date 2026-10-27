package com.huluwa.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

/** 观看记录与追剧收藏按葫芦娃服务器、账号隔离，退出登录不删除用户的记录。 */
class DengtaJournalStore(context: Context, server: String, username: String) {
    private val preferences = context.applicationContext.getSharedPreferences("huluwa_dengta_journal", Context.MODE_PRIVATE)
    private val key = MessageDigest.getInstance("SHA-256").digest("$server\u0000$username".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    private val mutex = Mutex()
    private var cached: DengtaJournalData? = null

    suspend fun read(): DengtaJournalData = withContext(Dispatchers.IO) { mutex.withLock { load() } }
    suspend fun update(transform: (DengtaJournalData) -> DengtaJournalData): DengtaJournalData = withContext(Dispatchers.IO) {
        mutex.withLock {
            val next = transform(load())
            check(preferences.edit().putString(key, ApiJson.encodeToString(next)).commit()) { "追剧记录保存失败，请重试" }
            cached = next
            next
        }
    }
    private fun load(): DengtaJournalData {
        cached?.let { return it }
        val content = preferences.getString(key, null)
        val value = if (content == null) DengtaJournalData() else try {
            ApiJson.decodeFromString<DengtaJournalData>(content)
        } catch (_: Exception) { throw IllegalStateException("追剧记录读取失败，请重试") }
        cached = value
        return value
    }
}
