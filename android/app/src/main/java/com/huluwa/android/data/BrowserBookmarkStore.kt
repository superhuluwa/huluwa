package com.huluwa.android.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString

@Serializable
data class BrowserBookmark(val url: String, val title: String, val addedAt: Long)

/** 用户主动收藏的网址独立保存，不随 WebView 无痕数据清理，两个浏览器入口共用。 */
class BrowserBookmarkStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("huluwa_browser_bookmarks", Context.MODE_PRIVATE)
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(runCatching {
        ApiJson.decodeFromString<List<BrowserBookmark>>(preferences.getString("bookmarks", "[]").orEmpty())
            .mapNotNull { item -> runCatching { item.copy(url = BrowserAddress.normalize(item.url)) }.getOrNull() }
            .distinctBy { it.url }.sortedByDescending { it.addedAt }
    }.getOrDefault(emptyList()))
    val bookmarks = mutable.asStateFlow()

    /** 收藏或取消当前网页，返回操作后的收藏状态。 */
    suspend fun toggle(url: String, title: String): Boolean = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalized = BrowserAddress.normalize(url)
            val saved = mutable.value
            val exists = saved.any { it.url == normalized }
            val next = if (exists) saved.filterNot { it.url == normalized } else listOf(
                BrowserBookmark(normalized, title.trim().ifBlank { normalized }, System.currentTimeMillis()),
            ) + saved
            persist(next)
            !exists
        }
    }

    suspend fun remove(url: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val normalized = BrowserAddress.normalize(url)
            persist(mutable.value.filterNot { it.url == normalized })
        }
    }

    private fun persist(value: List<BrowserBookmark>) {
        check(preferences.edit().putString("bookmarks", ApiJson.encodeToString(value)).commit()) { "收藏保存失败，请重试" }
        mutable.value = value
    }
}
