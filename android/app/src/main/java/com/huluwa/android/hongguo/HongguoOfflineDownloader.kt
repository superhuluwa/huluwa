package com.huluwa.android.hongguo

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.huluwa.android.data.AppLog
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

/** 收藏剧的离线状态；episodeIds 是已落盘分集，selectedIds 是已选目标（已落盘 + 待下载）。 */
data class HongguoOfflineStatus(
    val seriesId: String, val title: String, val cover: String,
    val done: Int, val failed: Int, val running: Boolean,
    val bytes: Long, val totalBytes: Long, val currentNumber: Int,
    val size: Long, val message: String,
    val episodeIds: Set<String>, val selectedIds: Set<String>,
)

/**
 * 整剧离线下载器，挂在应用级生命周期上，退出红果页继续下载。
 * 由分集选择驱动：start 提交选中的分集（episodeId to 集号），已落盘的自动跳过，
 * 重复提交即增量补下。每集断点续传密文文件（.part 长度即进度），
 * 下满后在本地解密为可直连播放的 MP4；spade 与内容密钥不写入离线索引或状态。
 */
internal class HongguoOfflineDownloader(context: Context, private val repository: HongguoRepository) {
    /** 单剧下载账本：recorded 已落盘、pending 待下载、numbers 为集号。 */
    private class Ledger(
        var title: String, var cover: String,
        val recorded: LinkedHashMap<String, HongguoOfflineEpisode> = LinkedHashMap(),
        val pending: LinkedHashSet<String> = LinkedHashSet(),
        val numbers: MutableMap<String, Int> = mutableMapOf(),
    ) {
        fun poll(): Pair<String, Int>? {
            val id = pending.firstOrNull() ?: return null
            pending.remove(id)
            return id to (numbers[id] ?: 0)
        }
    }

    private val store = HongguoOfflineStore(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<String, Job>()
    private val ledgers: MutableMap<String, Ledger> = mutableMapOf<String, Ledger>().also { map ->
        store.readAll().forEach { (id, saved) ->
            val ledger = Ledger(saved.title, saved.cover)
            saved.episodes.forEach { entry ->
                ledger.recorded[entry.episodeId] = entry
                ledger.numbers[entry.episodeId] = entry.number
            }
            map[id] = ledger
        }
    }
    private val statuses = MutableStateFlow(buildStatuses())
    val observable = statuses.asStateFlow()

    /** 已离线分集的文件与清晰度，播放时优先命中本地。 */
    @Synchronized fun lookup(seriesId: String, episodeId: String): Pair<File, String>? {
        val entry = ledgers[seriesId]?.recorded[episodeId] ?: return null
        val file = store.episodeFile(seriesId, entry.number, episodeId)
        return if (file.exists()) file to entry.definition else null
    }

    /** 提交一次分集选择：已落盘的自动跳过，追加到待下载队列；下载中重复提交同样有效。 */
    fun start(seriesId: String, title: String, cover: String, selection: List<Pair<String, Int>>) {
        val launch = synchronized(this) {
            val ledger = ledgers.getOrPut(seriesId) { Ledger(title, cover) }
            ledger.title = title
            ledger.cover = cover
            for ((episodeId, number) in selection) {
                ledger.numbers[episodeId] = number
                if (!ledger.recorded.containsKey(episodeId)) ledger.pending.add(episodeId)
            }
            publishLocked(seriesId)
            // 只有该剧没有注册中的任务才启动新任务；任务跑空时会在锁内注销自己。
            ledger.pending.isNotEmpty() && !jobs.containsKey(seriesId)
        }
        if (launch) jobs[seriesId] = scope.launch { run(seriesId) }
    }

    /** 取消下载：保留已落盘分集与断点，清空待下载队列。 */
    fun cancel(seriesId: String) {
        jobs.remove(seriesId)?.cancel()
        synchronized(this) {
            ledgers[seriesId]?.pending?.clear()
            publishLocked(seriesId)
        }
    }

    /** 删除一部剧的全部离线内容（含未完成的临时文件）。 */
    fun remove(seriesId: String) {
        jobs.remove(seriesId)?.cancel()
        synchronized(this) {
            ledgers.remove(seriesId)
            store.remove(seriesId)
        }
        statuses.update { it - seriesId }
    }

    private suspend fun run(seriesId: String) {
        val job = checkNotNull(kotlin.coroutines.coroutineContext[Job])
        val failures = mutableListOf<String>()
        try {
            while (true) {
                // 取集与注销在同一把锁里完成：队列跑空前 start 追加的新集不会被落下。
                val next = synchronized(this) {
                    val pair = ledgers[seriesId]?.poll()
                    if (pair == null && jobs[seriesId] === job) jobs.remove(seriesId)
                    pair
                } ?: break
                val (episodeId, number) = next
                downloadEpisode(seriesId, episodeId, number, job)?.let { failures.add(it) }
                publish(seriesId)
            }
            publish(seriesId) {
                it.copy(message = if (failures.isEmpty()) ""
                else "下载失败：${failures.joinToString("；").take(120)}")
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            AppLog.e("红果离线", "下载任务异常终止", error)
            publish(seriesId) { it.copy(message = error.message ?: "离线下载失败，请重试") }
        } finally {
            synchronized(this) { if (jobs[seriesId] === job) jobs.remove(seriesId) }
            publish(seriesId) { it.copy(running = false, bytes = 0, totalBytes = 0, currentNumber = 0) }
        }
    }

    /** 下载并解密一集；单集失败自动重试三次（重取播放地址，绕开直链过期），仍失败返回错误描述。 */
    private suspend fun downloadEpisode(
        seriesId: String, episodeId: String, number: Int, job: Job,
    ): String? {
        var attempt = 0
        var lastError = "下载失败"
        while (true) {
            try {
                val playback = repository.playback(episodeId)
                val stream = playback.streams.filter { it.supported }.maxByOrNull { it.height }
                    ?: throw IOException("该集暂未提供可离线的清晰度")
                // 按本集落盘峰值检查剩余空间：加密集解密前 .part 与 .dec 并存，需要双倍空间；
                // 目录首次下载前尚不存在，usableSpace 会误报 0，availableBytes 内部会先建目录。
                val needed = stream.size * (if (stream.encrypted) 2L else 1L) + 50L * 1024 * 1024
                val available = store.availableBytes()
                if (stream.size > 0 && available < needed) {
                    throw IOException("存储空间不足（可用${available / 1024 / 1024}MB，需${needed / 1024 / 1024}MB）")
                }
                val target = store.episodeFile(seriesId, number, episodeId)
                target.parentFile?.mkdirs()
                val part = store.partFile(target)
                AppLog.d("红果离线", "第${number}集开始下载（${stream.definition}，${stream.size / 1024 / 1024}MB）")
                download(stream, playback.userAgent, part, job) { bytes, totalBytes ->
                    publish(seriesId) {
                        it.copy(bytes = bytes, totalBytes = totalBytes, currentNumber = number, message = "")
                    }
                }
                if (stream.encrypted) {
                    AppLog.d("红果离线", "第${number}集下载完成，正在解密")
                    decrypt(part, target, stream, job)
                } else {
                    if (target.exists()) target.delete()
                    check(part.renameTo(target)) { "离线文件保存失败" }
                }
                record(seriesId, episodeId, number, stream, target)
                AppLog.d("红果离线", "第${number}集离线完成")
                publish(seriesId)
                return null
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error.message ?: error.javaClass.simpleName
                AppLog.e("红果离线", "第${number}集第${attempt + 1}次尝试失败", error)
                if (++attempt >= 3) return "第${number}集：$lastError"
                delay(1500L * attempt)
            }
        }
    }

    /**
     * 分块断点续传下载到 .part。与播放数据源相同的有界 Range 模式（每块 4MB）：
     * CDN 对短请求稳定，长连接整文件拉取会在接近完成时被切断；块失败只重试该块，
     * 并在主/备 CDN 地址间轮换。文件长度即已完成位置，天然记录断点。
     */
    @OptIn(UnstableApi::class)
    private fun download(
        stream: HongguoStream, userAgent: String, part: File, job: Job,
        onProgress: (Long, Long) -> Unit,
    ) {
        val factory = DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
            .setDefaultRequestProperties(mapOf("Accept-Encoding" to "identity"))
            .apply { if (userAgent.isNotBlank()) setUserAgent(userAgent) }
        val total = if (stream.size > 0) stream.size else probeTotal(factory, stream)
        require(total > 0) { "该集缺少文件大小信息，无法离线" }
        if (total > 2L * 1024 * 1024 * 1024) throw IOException("该集文件过大，暂不支持离线")
        val urls = listOf(stream.url, stream.backupUrl).filter { it.isNotBlank() }.distinct()
        val chunk = 4L * 1024 * 1024
        // 断点异常（比文件还长，比如上次 size 变了）时从头重下。
        var position = if (part.length() in 0..total) part.length() else 0L
        if (part.exists() && part.length() > total) part.delete()
        val buffer = ByteArray(512 * 1024)
        while (position < total) {
            if (!job.isActive) throw CancellationException("离线下载已取消")
            val length = minOf(chunk, total - position)
            var failure: Exception? = null
            var done = false
            // 单块最多 6 次尝试，主备地址轮换；任一次成功即继续下一块。
            for (attempt in 0 until 6) {
                if (!job.isActive) throw CancellationException("离线下载已取消")
                val source = factory.createDataSource()
                try {
                    val candidate = urls[(attempt + position / chunk).toInt().mod(urls.size)]
                    source.open(DataSpec.Builder().setUri(candidate)
                        .setPosition(position).setLength(length).build())
                    val header = source.responseHeaders.entries
                        .firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull().orEmpty()
                    val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header)
                    if (source.responseCode != 206 || range == null ||
                        range.groupValues[1].toLongOrNull() != position ||
                        range.groupValues[2].toLongOrNull() != position + length - 1 ||
                        range.groupValues[3].toLongOrNull() != total
                    ) throw IOException("CDN 分段响应不正确（${source.responseCode}）")
                    RandomAccessFile(part, "rw").use { output ->
                        output.seek(position)
                        var received = 0L
                        var publishedAt = 0L
                        while (received < length) {
                            val read = source.read(buffer, 0, minOf(buffer.size.toLong(), length - received).toInt())
                            // 有界请求不应提前结束；提前 EOF 视为本块失败，重试。
                            if (read == C.RESULT_END_OF_INPUT) throw IOException("本块提前结束（${received}/${length}）")
                            output.write(buffer, 0, read)
                            received += read
                            val now = System.currentTimeMillis()
                            if (now - publishedAt >= 200) {
                                publishedAt = now
                                onProgress(position + received, total)
                            }
                        }
                    }
                    done = true
                    break
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failure = error
                } finally {
                    runCatching { source.close() }
                }
            }
            if (!done) {
                throw IOException("分块下载失败（$position-${position + length - 1}/$total）：${failure?.message ?: "未知错误"}")
            }
            position += length
            onProgress(position, total)
        }
        onProgress(total, total)
    }

    /** 流信息缺少 size 时探测一次 16 字节，从 Content-Range 读总长。 */
    @OptIn(UnstableApi::class)
    private fun probeTotal(factory: DefaultHttpDataSource.Factory, stream: HongguoStream): Long {
        val source = factory.createDataSource()
        try {
            source.open(DataSpec.Builder().setUri(stream.url).setPosition(0).setLength(15).build())
            val header = source.responseHeaders.entries
                .firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull().orEmpty()
            return Regex("bytes \\d+-\\d+/(\\d+)").matchEntire(header)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { source.close() }
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

    private fun record(seriesId: String, episodeId: String, number: Int, stream: HongguoStream, target: File) {
        val entry = HongguoOfflineEpisode(episodeId, number, stream.definition, target.length())
        synchronized(this) {
            val ledger = ledgers.getOrPut(seriesId) { Ledger("", "") }
            ledger.recorded[episodeId] = entry
            ledger.numbers[episodeId] = number
            val total = ledger.recorded.size + ledger.pending.size
            store.write(seriesId, HongguoOfflineSeries(ledger.title, ledger.cover, total,
                ledger.recorded.values.sortedBy { it.number }))
        }
    }

    /** 持锁发布状态：帐本与任务存活状态合成 running、done、selected。 */
    private fun publishLocked(seriesId: String, mutate: (HongguoOfflineStatus) -> HongguoOfflineStatus = { it }) {
        val ledger = ledgers[seriesId] ?: return
        val done = ledger.recorded.keys.toSet()
        val running = ledger.pending.isNotEmpty() || jobs[seriesId]?.isActive == true
        val base = statuses.value[seriesId]
            ?: HongguoOfflineStatus(seriesId, ledger.title, ledger.cover, 0, 0, false, 0, 0, 0, 0, "", emptySet(), emptySet())
        statuses.update { map ->
            map + (seriesId to mutate(base.copy(
                title = ledger.title, cover = ledger.cover, done = done.size, running = running,
                size = ledger.recorded.values.sumOf { it.size },
                episodeIds = done, selectedIds = done + ledger.pending,
            )))
        }
    }

    private fun publish(seriesId: String, mutate: (HongguoOfflineStatus) -> HongguoOfflineStatus = { it }) =
        synchronized(this) { publishLocked(seriesId, mutate) }

    private fun buildStatuses(): Map<String, HongguoOfflineStatus> = ledgers.mapValues { (id, ledger) ->
        val done = ledger.recorded.keys.toSet()
        HongguoOfflineStatus(id, ledger.title, ledger.cover, done.size, 0, false, 0, 0, 0,
            ledger.recorded.values.sumOf { it.size }, "", done, done)
    }
}

private fun u32(data: ByteArray, position: Int): Long =
    ((data[position].toLong() and 255) shl 24) or ((data[position + 1].toLong() and 255) shl 16) or
        ((data[position + 2].toLong() and 255) shl 8) or (data[position + 3].toLong() and 255)
