package com.huluwa.android.hongguo

import java.io.IOException
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** 普通 MP4 的 CENC 索引；保持所有 box 长度和 chunk 偏移不变，供 Media3 随机读取。 */
internal class CencMp4 private constructor(
    val moovOffset: Long,
    private val clearMoov: ByteArray,
    private val samples: List<Sample>,
) {
    private data class Region(val offset: Int, val length: Int, val cipherOffset: Long)
    private data class Sample(val offset: Long, val size: Int, val iv: ByteArray, val regions: List<Region>) {
        val end get() = offset + size
    }
    private data class Box(val start: Int, val end: Int, val header: Int, val type: String) {
        val payload get() = start + header
    }
    private data class Protection(val ivSize: Int, val constantIv: ByteArray?)

    fun decrypt(position: Long, buffer: ByteArray, offset: Int, length: Int, key: ByteArray) {
        val end = position + length
        val from = maxOf(position, moovOffset)
        val to = minOf(end, moovOffset + clearMoov.size)
        if (from < to) clearMoov.copyInto(buffer, offset + (from - position).toInt(),
            (from - moovOffset).toInt(), (to - moovOffset).toInt())
        var lo = 0
        var hi = samples.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (samples[mid].end <= position) lo = mid + 1 else hi = mid
        }
        while (lo < samples.size && samples[lo].offset < end) {
            val sample = samples[lo++]
            for (region in sample.regions) {
                val regionStart = sample.offset + region.offset
                val begin = maxOf(position, regionStart)
                val finish = minOf(end, regionStart + region.length)
                if (begin >= finish) continue
                val cryptoOffset = region.cipherOffset + begin - regionStart
                val iv = ByteArray(16)
                sample.iv.copyInto(iv)
                // 以样本自身 IV 为起点，按已跳过的密文字节推进 128 位 CTR。
                var blocks = cryptoOffset / 16
                for (i in iv.lastIndex downTo 0) {
                    val sum = (iv[i].toInt() and 255) + (blocks and 255).toInt()
                    iv[i] = sum.toByte()
                    blocks = (blocks ushr 8) + (sum ushr 8)
                }
                val cipher = Cipher.getInstance("AES/CTR/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(iv))
                val skip = (cryptoOffset % 16).toInt()
                if (skip > 0) cipher.update(ByteArray(skip))
                val destination = offset + (begin - position).toInt()
                val count = (finish - begin).toInt()
                val clear = cipher.doFinal(buffer, destination, count)
                if (clear.size != count) fail("解密数据长度不正确")
                clear.copyInto(buffer, destination)
            }
        }
    }

    companion object {
        private const val MAX_SAMPLES = 250_000
        private fun fail(message: String): Nothing = throw IOException("红果视频：$message")
        private fun requireMp4(value: Boolean, message: String) { if (!value) fail(message) }
        private fun u8(data: ByteArray, p: Int): Int {
            requireMp4(p in data.indices, "媒体信息不完整")
            return data[p].toInt() and 255
        }
        private fun u16(data: ByteArray, p: Int) = (u8(data, p) shl 8) or u8(data, p + 1)
        private fun u32(data: ByteArray, p: Int): Long =
            (u8(data, p).toLong() shl 24) or (u8(data, p + 1).toLong() shl 16) or
                (u8(data, p + 2).toLong() shl 8) or u8(data, p + 3).toLong()
        private fun u64(data: ByteArray, p: Int): Long {
            val high = u32(data, p)
            requireMp4(high <= 0x7fffffffL, "媒体偏移超出支持范围")
            return (high shl 32) or u32(data, p + 4)
        }
        private fun count(data: ByteArray, p: Int): Int {
            val n = u32(data, p)
            requireMp4(n <= MAX_SAMPLES, "媒体索引过大")
            return n.toInt()
        }
        private fun type(data: ByteArray, p: Int): String {
            requireMp4(p >= 0 && p + 4 <= data.size, "媒体 box 不完整")
            return String(data, p, 4, Charsets.ISO_8859_1)
        }
        private fun patchType(data: ByteArray, p: Int, value: String) { value.toByteArray(Charsets.ISO_8859_1).copyInto(data, p) }
        private fun boxes(data: ByteArray, start: Int, end: Int): List<Box> {
            requireMp4(start in 0..end && end <= data.size, "媒体 box 范围无效")
            val result = ArrayList<Box>()
            var p = start
            while (p < end) {
                requireMp4(end - p >= 8, "媒体 box 头不完整")
                var size = u32(data, p)
                val header = if (size == 1L) 16 else 8
                if (size == 1L) size = u64(data, p + 8)
                if (size == 0L) size = (end - p).toLong()
                requireMp4(size >= header && size <= end - p, "媒体 box 长度无效")
                result.add(Box(p, p + size.toInt(), header, type(data, p + 4)))
                requireMp4(result.size <= MAX_SAMPLES, "媒体 box 数量过多")
                p += size.toInt()
            }
            return result
        }
        private fun children(data: ByteArray, box: Box) = boxes(data, box.payload, box.end)
        private fun child(data: ByteArray, box: Box, type: String): Box =
            children(data, box).firstOrNull { it.type == type } ?: fail("缺少 $type 媒体索引")
        private fun bounded(box: Box, p: Int, bytes: Long) {
            requireMp4(p >= box.payload && bytes >= 0 && p.toLong() + bytes <= box.end, "${box.type} 索引不完整")
        }

        fun parse(moov: ByteArray, moovOffset: Long, total: Long, expectedKid: String): CencMp4 {
            val data = moov.copyOf()
            val root = boxes(data, 0, data.size).singleOrNull { it.type == "moov" } ?: fail("缺少 moov")
            val allSamples = ArrayList<Sample>()
            for (track in children(data, root).filter { it.type == "trak" }) {
                val mdia = child(data, track, "mdia")
                val stbl = child(data, child(data, mdia, "minf"), "stbl")
                val tables = children(data, stbl)
                val stsd = tables.firstOrNull { it.type == "stsd" } ?: continue
                bounded(stsd, stsd.payload, 8)
                val entries = boxes(data, stsd.payload + 8, stsd.end)
                requireMp4(entries.size.toLong() == u32(data, stsd.payload + 4), "sample description 数量不正确")
                val protectedEntries = entries.withIndex().filter { it.value.type == "encv" || it.value.type == "enca" }
                if (protectedEntries.isEmpty()) continue
                requireMp4(protectedEntries.size == 1, "暂不支持同轨多套加密参数")
                val entry = protectedEntries.single().value
                val descriptionIndex = protectedEntries.single().index + 1
                var childOffset = entry.start + if (entry.type == "encv") 86 else 36
                if (entry.type == "enca") {
                    val version = u16(data, entry.start + 16)
                    childOffset += when (version) { 0 -> 0; 1 -> 16; 2 -> 36; else -> fail("暂不支持该音频格式") }
                }
                val entryChildren = boxes(data, childOffset, entry.end)
                val sinf = entryChildren.firstOrNull { it.type == "sinf" } ?: fail("缺少加密描述")
                val original = child(data, sinf, "frma")
                bounded(original, original.payload, 4)
                val originalType = type(data, original.payload)
                requireMp4(originalType in setOf("avc1", "avc3", "hvc1", "hev1", "mp4a", "ac-3", "ec-3", "Opus", "fLaC"),
                    "暂不支持该视频编码，请更换清晰度")
                val scheme = child(data, sinf, "schm")
                bounded(scheme, scheme.payload, 8)
                requireMp4(type(data, scheme.payload + 4) == "cenc", "暂不支持该加密方式")
                val tenc = child(data, child(data, sinf, "schi"), "tenc")
                bounded(tenc, tenc.payload, 24)
                val p = tenc.payload
                val version = u8(data, p)
                requireMp4(version == 0 || (version == 1 && u8(data, p + 5) == 0), "暂不支持分块模式加密")
                requireMp4(u8(data, p + 6) == 1, "加密轨道状态无效")
                val ivSize = u8(data, p + 7)
                requireMp4(ivSize in setOf(0, 8, 16), "不支持该 IV 长度")
                val kid = data.copyOfRange(p + 8, p + 24).joinToString("") { "%02x".format(it.toInt() and 255) }
                if (expectedKid.isNotBlank()) requireMp4(kid.equals(expectedKid.replace("-", ""), true), "播放模型与视频不匹配，请刷新播放地址")
                val constant = if (ivSize == 0) {
                    val length = u8(data, p + 24)
                    requireMp4(length == 8 || length == 16, "无效的固定 IV")
                    bounded(tenc, p + 25, length.toLong())
                    data.copyOfRange(p + 25, p + 25 + length)
                } else null
                val protection = Protection(ivSize, constant)
                val stsz = tables.firstOrNull { it.type == "stsz" } ?: fail("缺少样本大小")
                bounded(stsz, stsz.payload, 12)
                val uniformSize = u32(data, stsz.payload + 4)
                val n = count(data, stsz.payload + 8)
                requireMp4(allSamples.size + n <= MAX_SAMPLES, "媒体样本数量超出范围")
                if (uniformSize == 0L) bounded(stsz, stsz.payload + 12, n.toLong() * 4)
                val sizes = IntArray(n) { index ->
                    val size = if (uniformSize == 0L) u32(data, stsz.payload + 12 + index * 4) else uniformSize
                    requireMp4(size <= Int.MAX_VALUE, "样本大小超出范围")
                    size.toInt()
                }
                val stco = tables.firstOrNull { it.type == "stco" || it.type == "co64" } ?: fail("缺少 chunk 偏移")
                bounded(stco, stco.payload, 8)
                val chunkCount = count(data, stco.payload + 4)
                val stride = if (stco.type == "co64") 8 else 4
                bounded(stco, stco.payload + 8, chunkCount.toLong() * stride)
                val offsets = LongArray(chunkCount) { i ->
                    val pos = stco.payload + 8 + i * stride
                    if (stride == 8) u64(data, pos) else u32(data, pos)
                }
                val stsc = tables.firstOrNull { it.type == "stsc" } ?: fail("缺少样本到 chunk 的映射")
                bounded(stsc, stsc.payload, 8)
                val mappingCount = count(data, stsc.payload + 4)
                bounded(stsc, stsc.payload + 8, mappingCount.toLong() * 12)
                val mapping = List(mappingCount) { i ->
                    val at = stsc.payload + 8 + i * 12
                    Triple(count(data, at), count(data, at + 4), count(data, at + 8))
                }
                requireMp4(n == 0 || (mapping.isNotEmpty() && mapping.first().first == 1), "首个 chunk 映射无效")
                mapping.forEachIndexed { i, row ->
                    requireMp4(row.first in 1..chunkCount && row.second > 0 && row.third == descriptionIndex &&
                        (i == 0 || mapping[i - 1].first < row.first), "chunk 映射无效或使用了多套加密参数")
                }
                val senc = tables.firstOrNull { it.type == "senc" } ?: fail("暂不支持外置加密索引")
                bounded(senc, senc.payload, 8)
                val flags = u32(data, senc.payload).toInt() and 0xffffff
                requireMp4(flags and 1 == 0 && flags and 0xfffffc == 0 && count(data, senc.payload + 4) == n, "样本加密参数不匹配")
                var encryptionPosition = senc.payload + 8
                var sampleIndex = 0
                var mappingIndex = 0
                for (chunkIndex in offsets.indices) {
                    while (mappingIndex + 1 < mapping.size && mapping[mappingIndex + 1].first <= chunkIndex + 1) mappingIndex++
                    var fileOffset = offsets[chunkIndex]
                    val perChunk = mapping.getOrNull(mappingIndex)?.second ?: fail("缺少 chunk 映射")
                    repeat(perChunk) {
                        requireMp4(sampleIndex < n, "样本数量与 chunk 不匹配")
                        val size = sizes[sampleIndex++]
                        requireMp4(fileOffset >= 0 && fileOffset <= total - size, "媒体样本偏移无效")
                        val iv = if (protection.ivSize == 0) protection.constantIv!! else {
                            bounded(senc, encryptionPosition, protection.ivSize.toLong())
                            data.copyOfRange(encryptionPosition, encryptionPosition + protection.ivSize).also { encryptionPosition += protection.ivSize }
                        }
                        val regions = ArrayList<Region>()
                        if (flags and 2 != 0) {
                            bounded(senc, encryptionPosition, 2)
                            val subCount = u16(data, encryptionPosition); encryptionPosition += 2
                            bounded(senc, encryptionPosition, subCount.toLong() * 6)
                            var within = 0L
                            var encryptedOffset = 0L
                            repeat(subCount) {
                                val clear = u16(data, encryptionPosition)
                                val encrypted = u32(data, encryptionPosition + 2)
                                encryptionPosition += 6
                                within += clear
                                requireMp4(within + encrypted <= size, "局部加密样本范围无效")
                                if (encrypted > 0) regions.add(Region(within.toInt(), encrypted.toInt(), encryptedOffset))
                                within += encrypted; encryptedOffset += encrypted
                            }
                            requireMp4(within == size.toLong(), "局部加密样本大小不匹配")
                        } else if (size > 0) regions.add(Region(0, size, 0))
                        if (size > 0) allSamples.add(Sample(fileOffset, size, iv, regions))
                        fileOffset += size
                    }
                }
                requireMp4(sampleIndex == n && encryptionPosition == senc.end, "样本加密索引不完整")
                // 使用等长 free box 隐藏 CENC 描述，原有 stco/co64 和 Range 坐标保持有效。
                patchType(data, entry.start + 4, originalType)
                patchType(data, sinf.start + 4, "free")
                for (box in tables) {
                    if (box.type in setOf("senc", "saiz", "saio")) patchType(data, box.start + 4, "free")
                    if (box.type in setOf("sgpd", "sbgp")) {
                        bounded(box, box.payload, 8)
                        requireMp4(type(data, box.payload + 4) != "seig", "暂不支持样本密钥轮换")
                    }
                }
            }
            requireMp4(allSamples.isNotEmpty(), "没有找到支持的 CENC 加密轨道")
            val sorted = allSamples.sortedBy { it.offset }
            for (i in 1 until sorted.size) requireMp4(sorted[i - 1].end <= sorted[i].offset, "音视频样本范围发生重叠")
            return CencMp4(moovOffset, data, sorted)
        }
    }
}
