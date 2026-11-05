package com.huluwa.android.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.security.MessageDigest

@Serializable
data class HongguoCachedRanking(
    val content: String, val board: String, val contents: List<HongguoRankContent>,
    val items: List<HongguoRankItem>, val hasMore: Boolean, val cursor: String,
)

/**
 * 推荐和排行榜按服务器、账号隔离保存在手机上。缓存只包含卡片展示数据，
 * 不保存播放地址、密钥或签名信息；服务端刷新成功后再覆盖对应缓存。
 */
class HongguoFeedCacheStore(context: Context, server: String, username: String) {
    private val scope = MessageDigest.getInstance("SHA-256").digest("$server\u0000$username".toByteArray())
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    private val preferences = context.applicationContext.getSharedPreferences("hongguo-feed-$scope", Context.MODE_PRIVATE)

    fun readDiscover(): List<HongguoSeries> = runCatching {
        ApiJson.decodeFromString<List<HongguoSeries>>(preferences.getString(DISCOVER_KEY, "[]").orEmpty())
    }.getOrDefault(emptyList())

    fun writeDiscover(items: List<HongguoSeries>) {
        preferences.edit().putString(DISCOVER_KEY, ApiJson.encodeToString(items)).apply()
    }

    fun readRanking(content: String, board: String): HongguoCachedRanking? = runCatching {
        preferences.getString(rankingKey(content, board), null)?.let {
            ApiJson.decodeFromString<HongguoCachedRanking>(it)
        }
    }.getOrNull()

    fun readDefaultRanking(): HongguoCachedRanking? = runCatching {
        preferences.getString(DEFAULT_RANKING_KEY, null)?.let {
            ApiJson.decodeFromString<HongguoCachedRanking>(it)
        }
    }.getOrNull()

    fun writeRanking(ranking: HongguoCachedRanking, requestContent: String, requestBoard: String, default: Boolean) {
        val encoded = ApiJson.encodeToString(ranking)
        preferences.edit().apply {
            putString(rankingKey(ranking.content, ranking.board), encoded)
            putString(rankingKey(requestContent, requestBoard), encoded)
            if (default) {
                putString(DEFAULT_RANKING_KEY, encoded)
            }
        }.apply()
    }

    private fun rankingKey(content: String, board: String) = "$RANKING_KEY${content.length}:$content$board"

    private companion object {
        const val DISCOVER_KEY = "discover"
        const val RANKING_KEY = "ranking:"
        const val DEFAULT_RANKING_KEY = "ranking-default"
    }
}
