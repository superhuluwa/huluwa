package com.huluwa.android.hongguo

import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import com.huluwa.android.data.HongguoPlayback
import com.huluwa.android.data.HongguoStream
import java.io.IOException
import kotlin.math.min

/**
 * 当前集播放期间预取下一集的解密索引和开头媒体段。
 * 只保留一条内存记录，不落地播放地址或密钥；真正切换时仍用同一清晰度重新创建播放器。
 */
@UnstableApi
internal object HongguoPrefetchCache {
    private val lock = Any()
    data class Entry(
        val episodeId: String, val playback: HongguoPlayback, val stream: HongguoStream,
        val hasData: Boolean,
    )

    @Volatile private var entry: Entry? = null

    fun get(episodeId: String): Entry? = synchronized(lock) { entry?.takeIf { it.episodeId == episodeId } }

    fun put(value: Entry) { synchronized(lock) { entry = value } }

    fun clear(episodeId: String? = null) {
        synchronized(lock) {
            if (episodeId == null || entry?.episodeId == episodeId) entry = null
        }
    }

    fun prefetch(playback: HongguoPlayback, stream: HongguoStream, bytes: Int): Boolean {
        val source = HongguoDataSourceFactory(stream, playback.userAgent).createDataSource()
        var opened = false
        return try {
            source.open(DataSpec.Builder().setUri(stream.url).setPosition(0)
                .setLength(bytes.toLong().coerceAtLeast(1)).build())
            opened = true
            val buffer = ByteArray(64 * 1024)
            var remaining = bytes
            while (remaining > 0) {
                val read = source.read(buffer, 0, min(buffer.size, remaining))
                if (read <= 0) break
                remaining -= read
            }
            true
        } catch (_: IOException) {
            false
        } finally {
            if (opened) runCatching { source.close() }
        }
    }
}
