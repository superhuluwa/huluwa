package com.huluwa.android.hongguo

import android.os.SystemClock
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async

/** 只在当前播放会话的内存中保留相邻集，不持久化播放地址和解密信息。 */
data class HongguoEpisodePreload(
    val episodeId: String,
    val playback: HongguoPlayback,
    val stream: HongguoStream,
    val startPositionMs: Long,
)

/** 前台播放和后台预加载共用请求，滑到正在准备的分集时继续等待同一个请求。 */
internal class HongguoPrefetchCache(
    private val scope: CoroutineScope,
    private val loadPlayback: suspend (String) -> HongguoPlayback,
) {
    private data class Entry(val playback: HongguoPlayback, val createdAt: Long)
    private val entries = mutableMapOf<String, Entry>()
    private val requests = mutableMapOf<String, Deferred<HongguoPlayback>>()

    fun peek(episodeId: String): HongguoPlayback? {
        val entry = entries[episodeId] ?: return null
        val expires = entry.playback.expireTime.let { if (it > 1_000_000_000_000L) it / 1000 else it }
        if (SystemClock.elapsedRealtime() - entry.createdAt > 10 * 60_000L ||
            (expires > 0 && expires <= System.currentTimeMillis() / 1000 + 60)) {
            entries.remove(episodeId)
            return null
        }
        return entry.playback
    }

    suspend fun get(episodeId: String): HongguoPlayback {
        peek(episodeId)?.let { return it }
        if (requests[episodeId]?.isCancelled == true) requests.remove(episodeId)
        val request = requests.getOrPut(episodeId) {
            scope.async {
                val playback = loadPlayback(episodeId)
                val available = playback.streams.filter { it.supported }.sortedByDescending { it.height }
                check(available.isNotEmpty()) { "该集暂未提供手机支持的视频编码，请稍后重试" }
                playback.copy(streams = available).also {
                    entries[episodeId] = Entry(it, SystemClock.elapsedRealtime())
                }
            }
        }
        return try {
            request.await()
        } finally {
            if (request.isCompleted && requests[episodeId] === request) requests.remove(episodeId)
        }
    }

    fun invalidate(episodeId: String) {
        entries.remove(episodeId)
        requests.remove(episodeId)?.cancel()
    }

    fun retain(episodeIds: Set<String>) {
        entries.keys.retainAll(episodeIds)
        requests.keys.filter { it !in episodeIds }.forEach { requests.remove(it)?.cancel() }
    }

    fun clear() {
        entries.clear()
        requests.values.forEach { it.cancel() }
        requests.clear()
    }
}
