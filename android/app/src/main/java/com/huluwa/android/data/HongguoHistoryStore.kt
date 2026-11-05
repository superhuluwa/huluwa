package com.huluwa.android.data

import android.content.Context
import kotlinx.serialization.json.*
import java.security.MessageDigest

data class HongguoWatchProgress(
    val episodeId: String, val positionMs: Long, val durationMs: Long, val episodeNumber: Int,
) {
    val completed get() = durationMs > 0 && positionMs >= durationMs - 1500
}

/** 每个服务器、账号独立保存观看位置，不持久化 CDN URL、spade 或内容密钥。 */
class HongguoHistoryStore(context: Context, server: String, username: String) {
    private val scope = MessageDigest.getInstance("SHA-256").digest("$server\u0000$username".toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val prefs = context.getSharedPreferences("hongguo-watch-$scope", Context.MODE_PRIVATE)
    fun read(seriesId: String): HongguoWatchProgress? = runCatching {
        val raw = prefs.getString(seriesId, null) ?: return null
        val json = ApiJson.parseToJsonElement(raw).asObject()
        HongguoWatchProgress(json.text("episodeId"), json.number("positionMs")?.toLong() ?: 0,
            json.number("durationMs")?.toLong() ?: 0, json.number("episodeNumber")?.toInt() ?: 1)
    }.getOrNull()
    fun write(seriesId: String, progress: HongguoWatchProgress) {
        prefs.edit().putString(seriesId, jsonBody {
            put("episodeId", progress.episodeId); put("positionMs", progress.positionMs.coerceAtLeast(0))
            put("durationMs", progress.durationMs.coerceAtLeast(0)); put("episodeNumber", progress.episodeNumber)
        }.toString()).apply()
    }
}
