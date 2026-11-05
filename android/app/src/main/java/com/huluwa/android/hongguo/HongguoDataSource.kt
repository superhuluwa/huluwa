package com.huluwa.android.hongguo

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import com.huluwa.android.data.HongguoStream
import java.io.EOFException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** 每个播放清晰度拥有独立索引；Seek 重开数据源时复用索引，媒体字节始终直接来自 CDN。 */
@OptIn(UnstableApi::class)
class HongguoDataSourceFactory(private val stream: HongguoStream, userAgent: String) : DataSource.Factory {
    private val upstream = DefaultHttpDataSource.Factory().setConnectTimeoutMs(15_000).setReadTimeoutMs(30_000)
        .setDefaultRequestProperties(mapOf("Accept-Encoding" to "identity"))
        .apply { if (userAgent.isNotBlank()) setUserAgent(userAgent) }
    private var layout: CencMp4? = null
    private var key: ByteArray? = null
    private var totalLength = 0L

    override fun createDataSource(): DataSource = if (stream.encrypted) DecryptingSource() else upstream.createDataSource()

    @Synchronized private fun index(): Pair<CencMp4, ByteArray> {
        layout?.let { return it to key!! }
        if (stream.encryptionMethod != "cenc-aes-ctr") throw IOException("暂不支持该视频的加密方式")
        val contentKey = HongguoKey.derive(stream.spadeA)
        var offset = 0L
        var metadata: ByteArray? = null
        var metadataOffset = 0L
        var boxCount = 0
        while (offset == 0L || offset < totalLength) {
            if (++boxCount > 128) throw IOException("视频顶层 box 数量过多")
            val header = remote(offset, 16)
            val bb = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
            var size = bb.int.toLong() and 0xffffffffL
            val type = String(header, 4, 4, Charsets.ISO_8859_1)
            val headerSize = if (size == 1L) 16 else 8
            if (size == 1L) size = bb.getLong(8)
            if (size == 0L) size = totalLength - offset
            if (size < headerSize || size > totalLength - offset) throw IOException("视频 box 长度无效")
            if (offset == 0L && type != "ftyp") throw IOException("CDN 未返回 MP4 文件")
            if (type == "moof") throw IOException("暂不支持该分片加密视频，请更换清晰度")
            if (type == "moov") {
                if (metadata != null || size > 16L * 1024 * 1024) throw IOException("视频媒体索引超出支持范围")
                metadata = remote(offset, size.toInt())
                metadataOffset = offset
            }
            offset += size
        }
        val parsed = CencMp4.parse(metadata ?: throw IOException("视频缺少媒体索引"), metadataOffset, totalLength, stream.kid)
        key = contentKey
        layout = parsed
        return parsed to contentKey
    }

    private fun remote(start: Long, requested: Int): ByteArray {
        val source = upstream.createDataSource()
        val length = if (totalLength > 0) minOf(requested.toLong(), totalLength - start).toInt() else requested
        if (length < 8) throw IOException("视频 box 头不完整")
        try {
            source.open(DataSpec.Builder().setUri(stream.url).setPosition(start).setLength(length.toLong()).build())
            val header = source.responseHeaders.entries.firstOrNull { it.key.equals("Content-Range", true) }?.value?.firstOrNull().orEmpty()
            val range = Regex("bytes (\\d+)-(\\d+)/(\\d+)").matchEntire(header)
                ?: throw IOException("视频 CDN 不支持分段读取")
            val from = range.groupValues[1].toLongOrNull()
            val end = range.groupValues[2].toLongOrNull()
            val total = range.groupValues[3].toLongOrNull() ?: throw IOException("视频总长度无效")
            if (source.responseCode != 206 || from != start || end != start + length - 1 ||
                total < 16 || total > 2L * 1024 * 1024 * 1024 || (totalLength > 0 && total != totalLength)) {
                throw IOException("视频 CDN 返回的分段范围不正确")
            }
            totalLength = total
            val result = ByteArray(length)
            var read = 0
            while (read < length) {
                if (Thread.currentThread().isInterrupted) throw IOException("视频加载已取消")
                val n = source.read(result, read, length - read)
                if (n == C.RESULT_END_OF_INPUT) throw EOFException("视频媒体索引下载不完整")
                read += n
            }
            return result
        } finally { source.close() }
    }

    private inner class DecryptingSource : BaseDataSource(true) {
        private val source = upstream.createDataSource()
        private var parsed: CencMp4? = null
        private var contentKey: ByteArray? = null
        private var position = 0L
        private var opened = false
        override fun open(dataSpec: DataSpec): Long {
            transferInitializing(dataSpec)
            try {
                val (metadata, key) = index()
                parsed = metadata; contentKey = key; position = dataSpec.position
                val length = source.open(dataSpec)
                opened = true
                transferStarted(dataSpec)
                return length
            } catch (error: Exception) {
                source.close()
                throw if (error is IOException) error else IOException("红果视频解密初始化失败", error)
            }
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            val count = source.read(buffer, offset, length)
            if (count > 0) {
                try { parsed!!.decrypt(position, buffer, offset, count, contentKey!!) }
                catch (error: Exception) { throw if (error is IOException) error else IOException("红果视频解密失败", error) }
                position += count
                bytesTransferred(count)
            }
            return count
        }
        override fun getUri(): Uri? = source.uri
        override fun getResponseHeaders(): Map<String, List<String>> = source.responseHeaders
        override fun close() {
            try { source.close() } finally {
                parsed = null; contentKey = null
                if (opened) { opened = false; transferEnded() }
            }
        }
    }
}
