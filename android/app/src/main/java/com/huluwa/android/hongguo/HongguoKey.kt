package com.huluwa.android.hongguo

import android.util.Base64
import java.io.IOException

/**
 * Kotlin adaptation of openhongguo/internal/drm/spade.go.
 * Copyright 2026 beilunyang. Licensed under Apache-2.0; see assets/licenses/openhongguo-LICENSE.txt.
 * 密钥仅在播放实例内使用，不写入进度、磁盘缓存或日志。
 */
internal object HongguoKey {
    fun derive(material: String): ByteArray {
        fun invalid(): Nothing = throw IOException("该集的加密信息暂不支持，请刷新播放地址或更换清晰度")
        val raw = try { Base64.decode(material.trim(), Base64.DEFAULT) } catch (_: IllegalArgumentException) { invalid() }
        if (raw.size < 4) invalid()
        fun byte(index: Int) = raw[index].toInt() and 255
        val marker = byte(0) xor byte(1) xor byte(2)
        val prefix = marker - 0x30
        val length = raw.size - marker + 0x2f
        if (prefix !in 1..raw.size || length < 2 || length + 1 > raw.size) invalid()
        val tail = raw.size - prefix
        if (tail < 2) invalid()
        val tag = ByteArray(prefix) { (byte(tail - 2) xor byte(tail - 1) xor byte(tail + it)).toByte() }
            .toString(Charsets.US_ASCII)
        val v2 = tag.startsWith("app_v2") || tag.startsWith("web_v2")
        val buffer = raw.copyOfRange(1, length + 1)
        if (v2) {
            var i = 0
            while (i + 1 < buffer.size) {
                val temp = buffer[i]; buffer[i] = buffer[i + 1]; buffer[i + 1] = temp
                if (i == buffer.lastIndex - 2) break
                i += 2
            }
        }
        var previous = 0x55
        var current = 0xfa
        for (index in buffer.indices) {
            val value = buffer[index].toInt() and 255
            val term = -(0x15 + Integer.bitCount(index))
            val mixed = (if (index % 2 == 0) current else previous) xor value
            buffer[index] = (mixed + term - if (v2) previous else 0).toByte()
            if (index % 2 == 0) current = value else previous = value
        }
        val versionLength = buffer[0].toInt().toChar().digitToIntOrNull(16) ?: invalid()
        if (versionLength < 1 || versionLength >= length) invalid()
        val keyLength = length - versionLength
        val hex = buffer.copyOfRange(1, keyLength).toString(Charsets.US_ASCII)
        if (hex.length != 32 || hex.any { it.digitToIntOrNull(16) == null }) invalid()
        return ByteArray(16) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }
}
