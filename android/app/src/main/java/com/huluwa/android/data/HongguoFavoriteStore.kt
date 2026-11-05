package com.huluwa.android.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

@Serializable
data class HongguoFavorite(
    val id: String, val title: String, val cover: String = "", val intro: String = "",
    val episodeCount: Int = 0, val addedAt: Long = 0,
) {
    fun series() = HongguoSeries(id, title, cover, intro, episodeCount)
}

/** 红果收藏按服务器和账号隔离；只保存剧集展示信息，不保存播放地址或密钥。 */
class HongguoFavoriteStore(context: Context, server: String, username: String) {
    private val scope = MessageDigest.getInstance("SHA-256").digest("$server\u0000$username".toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val preferences = context.applicationContext.getSharedPreferences("hongguo-favorites-$scope", Context.MODE_PRIVATE)

    fun read(): List<HongguoFavorite> = runCatching {
        ApiJson.decodeFromString<List<HongguoFavorite>>(preferences.getString("favorites", "[]").orEmpty())
            .distinctBy { it.id }.sortedByDescending { it.addedAt }
    }.getOrDefault(emptyList())

    fun toggle(series: HongguoSeries): List<HongguoFavorite> {
        val saved = read()
        val next = if (saved.any { it.id == series.id }) saved.filterNot { it.id == series.id }
        else listOf(HongguoFavorite(series.id, series.title, series.cover, series.intro, series.episodeCount,
            System.currentTimeMillis())) + saved
        check(preferences.edit().putString("favorites",
            ApiJson.encodeToString(next.distinctBy { it.id }.sortedByDescending { it.addedAt })).commit()) {
            "收藏保存失败，请重试"
        }
        return next
    }
}
