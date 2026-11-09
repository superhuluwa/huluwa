package com.huluwa.android.hongguo

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.huluwa.android.data.HongguoEpisode
import com.huluwa.android.data.HongguoOfflineEpisode
import com.huluwa.android.data.HongguoOfflineSeries
import com.huluwa.android.data.HongguoOfflineStore
import com.huluwa.android.data.HongguoRepository
import com.huluwa.android.data.HongguoStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 收藏剧的离线状态；episodeIds 是已落盘的分集，供播放命中与界面标记使用。 */
data class HongguoOfflineStatus(
    val seriesId: String, val title: String, val cover: String,
    val total: Int, val done: Int, val failed: Int, val running: Boolean,
    val bytes: Long, val totalBytes: Long, val size: Long, val message: String,
    val episodeIds: Set<String>,
)

/**
 * 整剧离线下载器，挂在应用级生命周期上，退出红果页继续下载。
 * 按分集顺序断点续传密文文件（.part 长度即进度），下满后在本地解密为可直连播放的 MP4；
 * spade 与内容密钥只在下载实例内使用，不写入离线索引或状态。
 */
internal class HongguoOfflineDownloader(context: Context, private val repository: HongguoRepository) {
    private val store = HongguoOfflineStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val statuses = MutableStateFlow(restore())
    val observable = statuses.asStateFlow()

    /** 已离线分集的文件与清晰度，播放时优先命中本地。 */
    fun lookup(seriesId: String, episodeId: String): Pair<File, String>? {
        val entry = store.readAll()[seriesId]?.episodes?.firstOrNull { it.episodeId == episodeId }
            ?: return null
        val file = store.episodeFile(seriesId, entry.number, episodeId)
        return if (file.exists()) file to entry.definition else null
    }

    /** start 幂等且可增量：已落盘的分集直接跳过，因此重试即可补齐失败或新上线的分集。 */
    fun start(seriesId: String, title: String, cover: String) {
        if (jobs[seriesId]?.isActive == true) return
        jobs[seriesId] = scope.launch { run(seriesId, title, cover) }
    }

    /** 取消保留断点；remove 连同文件一起清除。 */
    fun cancel(seriesId: String) {
        jobs.remove(seriesId)?.cancel()
        publish(seriesId) { it.copy(running = false, bytes = 0, totalBytes = 0) }
    }

    fun remove(seriesId: String) {
        jobs.remove(seriesId)?.cancel()
        store.remove(seriesId)
        statuses.update { it - seriesId }
    }

    private suspend fun run(seriesId: String, title: String, cover: String) {
        val job = checkNotNull(kotlin.coroutines.coroutineContext[Job])
        try {
            if (store.root.usableSpace < 200L * 1024 * 1024) throw IOException("存储空间不足，无法离线下载")
            val detail = repository.detail(seriesId)
            val episodes = detail.episodes.filterNot { it.locked }
            check(episodes.isNotEmpty()) { "该剧暂无可下载的分集" }
            var series = store.readAll()[seriesId]?.copy(title = title, cover = cover)
                ?: HongguoOfflineSeries(title, cover)
            series = series.copy(total = maxOf(series.total, episodes.size))
            publish(seriesId) {
                HongguoOfflineStatus(seriesId, title, cover, episodes.size, series.episodes.size, 0,
                    true, 0, 0, series.episodes.sumOf { it.size }, "",
                    series.episodes.mapTo(mutableSetOf()) { it.episodeId })
            }
            var failed = 0
            for (episode in episodes) {
                if (series.episodes.any { it.episodeId == episode.id }) continue
                val downloaded = downloadEpisode(seriesId, series, episodes.size, episode, job)
                if (downloaded != null) series = downloaded else failed += 1
                publish(seriesId) {
                    it.copy(done = series.episodes.size, failed = failed, running = true,
                        bytes = 0, totalBytes = 0, size = series.episodes.sumOf { e -> e.size },
                        episodeIds = series.episodes.mapTo(mutableSetOf()) { e -> e.episodeId })
                }
            }
            publish(seriesId) {
                it.copy(running = false, message = if (failed > 0) "${failed} 集下载失败，可点击重试补齐" else "")
            }
        } catch (error: CancellationException) {
            publish(seriesId) { it.copy(running = false, bytes = 0, totalBytes = 0) }
            throw error
        } catch (error: Exception) {
            publish(seriesId) { it.copy(running = false, message = error.message ?: "离线下载失败，请重试") }
        }
    }

    /** 下载并解密一集；单集失败自动重试三次（重取播放地址，绕开直链过期），仍失败返回 null。 */
    private suspend fun downloadEpisode(
        seriesId: String, series: HongguoOfflineSeries, total: Int, episode: HongguoEpisode, job: Job,
    ): HongguoOfflineSeries? {
        var attempt = 0
        while (true) {
            try {
                val playback = repository.playback(episode.id)
                val stream = playback.streams.filter { it.supported }.maxByOrNull { it.height }
                    ?: throw IOException("该集暂未提供可离线的清晰度")
                val target = store.episodeFile(seriesId, episode.number, episode.id)
                target.parentFile?.mkdirs()
                val part = store.partFile(target)
                download(stream, playback.userAgent, part, job) { bytes, totalBytes ->
                    publish(seriesId) {
                        it.copy(total = total, running = true, bytes = bytes, totalBytes = totalBytes,
                            message = "", episodeIds = series.episodes.mapTo(mutableSetOf()) { e -> e.episodeId })
                    }
                }
                if (stream.encrypted) decrypt(part, target, stream, job)
                else {
                    if (target.exists()) target.delete()
                    check(part.renameTo(target)) { "离线文件保存失败" }
                }
                val episodes = (series.episodes.filterNot { it.episodeId == episode.id } +
                    HongguoOfflineEpisode(episode.id, episode.number, stream.definition, target.length()))
                    .sortedBy { it.number }
                return series.copy(total = maxOf(series.total, total), episodes = episodes)
                    .also { store.write(seriesId, it) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (++attempt >= 3) return null
                delay(1500L * attempt)
            }
        }
    }

    /** 断点续传下载到 .part；CDN 忽略续传范围时自动删除断点从零重下。 */
    @OptIn(UnstableApi::class)
    private fun download(
        stream: HongguoStream, userAgent: String, part: File, job: Job,
        onProgress: (Long, Long) -> Unit,
    ) {
        val factory = DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(mapOf("Accept-Encoding" to "identity"))
            .apply { if (userAgent.isNotBlank()) setUserAgent(userAgent) }
        var start = part.length()
        while (true) {
            val source = factory.createDataSource()
            try {
                val remaining = source.open(DataSpec.Builder().setUri(stream.url).setPosition(start).build())
                val header = source.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }
                    ?.value?.firstOrNull().orEmpty()
                val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header)
                if (start > 0 && (range == null || range.groupValues[1].toLongOrNull() != start)) {
                    check(part.delete()) { "离线缓存文件无法重置" }
                    start = 0
                    continue
                }
                val total = range?.groupValues?.get(3)?.toLongOrNull()
                    ?: remaining.takeIf { it != C.LENGTH_UNSET.toLong() }?.let { start + it } ?: -1L
                if (total > 2L * 1024 * 1024 * 1024) throw IOException("该集文件过大，暂不支持离线")
                RandomAccessFile(part, "rw").use { output ->
                    output.setLength(start)
                    val buffer = ByteArray(512 * 1024)
                    var received = start
                    var publishedAt = 0L
                    while (true) {
                        if (!job.isActive) throw CancellationException("离线下载已取消")
                        val read = source.read(buffer, 0, buffer.size)
                        if (read == C.RESULT_END_OF_INPUT) break
                        output.write(buffer, 0, read)
                        received += read
                        // 下载循环不感知协程，靠节流上报避免状态流高频重组。
                        val now = System.currentTimeMillis()
                        if (now - publishedAt >= 200) {
                            publishedAt = now
                            onProgress(received, if (total > 0) total else received)
                        }
                    }
                    if (total > 0 && received != total) throw IOException("该集下载不完整")
                    onProgress(received, if (total > 0) total else received)
                }
                return
            } finally {
                runCatching { source.close() }
            }
        }
    }

    /** 在本地把密文 MP4 解密为明文：moov 区间换成明文索引，样本区间按 CENC 索引解密，其余字节透传。 */
    private fun decrypt(part: File, target: File, stream: HongguoStream, job: Job) {
        val key = HongguoKey.derive(stream.spadeA)
        RandomAccessFile(part, "r").use { input ->
            val total = input.length()
            var offset = 0L
            var moovOffset = -1L
            var moovLength = 0L
            var count = 0
            val header = ByteArray(16)
            while (offset < total) {
                if (++count > 128) throw IOException("该集视频 box 数量过多")
                input.seek(offset)
                input.readFully(header, 0, minOf(16L, total - offset).toInt())
                if (total - offset < 8) throw IOException("视频 box 头不完整")
                var size = u32(header, 0)
                val type = String(header, 4, 4, Charsets.ISO_8859_1)
                val headerSize = if (size == 1L) 16 else 8
                if (size == 1L) size = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN).getLong(8)
                if (size == 0L) size = total - offset
                if (size < headerSize || size > total - offset) throw IOException("视频 box 长度无效")
                if (offset == 0L && type != "ftyp") throw IOException("离线文件不是 MP4")
                if (type == "moof") throw IOException("该集为分片加密视频，暂不支持离线")
                if (type == "moov") {
                    if (moovOffset >= 0) throw IOException("视频包含多个媒体索引")
                    moovOffset = offset
                    moovLength = size
                }
                offset += size
            }
            if (moovOffset < 0) throw IOException("视频缺少媒体索引")
            if (moovLength > 16L * 1024 * 1024) throw IOException("视频媒体索引超出支持范围")
            val moov = ByteArray(moovLength.toInt()).also { input.seek(moovOffset); input.readFully(it) }
            val layout = CencMp4.parse(moov, moovOffset, total, stream.kid)
            val clear = File(target.parentFile, target.name + ".dec")
            try {
                RandomAccessFile(clear, "rw").use { output ->
                    output.setLength(0)
                    val buffer = ByteArray(512 * 1024)
                    var position = 0L
                    while (position < total) {
                        if (!job.isActive) throw CancellationException("离线下载已取消")
                        val length = minOf(buffer.size.toLong(), total - position).toInt()
                        input.readFully(buffer, 0, length)
                        layout.decrypt(position, buffer, 0, length, key)
                        output.write(buffer, 0, length)
                        position += length
                    }
                }
                if (clear.length() != total) throw IOException("解密输出不完整")
                if (target.exists()) target.delete()
                check(clear.renameTo(target)) { "离线文件保存失败" }
                part.delete()
            } finally {
                if (clear.exists()) clear.delete()
            }
        }
    }

    private fun publish(seriesId: String, build: (HongguoOfflineStatus) -> HongguoOfflineStatus) {
        statuses.update { current ->
            val base = current[seriesId]
                ?: HongguoOfflineStatus(seriesId, "", "", 0, 0, 0, false, 0, 0, 0, "", emptySet())
            current + (seriesId to build(base))
        }
    }

    private fun restore(): Map<String, HongguoOfflineStatus> = store.readAll().mapValues { (id, series) ->
        HongguoOfflineStatus(id, series.title, series.cover, series.total, series.episodes.size, 0, false,
            0, 0, series.episodes.sumOf { it.size }, "", series.episodes.mapTo(mutableSetOf()) { it.episodeId })
    }
}

private fun u32(data: ByteArray, position: Int): Long =
    ((data[position].toLong() and 255) shl 24) or ((data[position + 1].toLong() and 255) shl 16) or
        ((data[position + 2].toLong() and 255) shl 8) or (data[position + 3].toLong() and 255)
