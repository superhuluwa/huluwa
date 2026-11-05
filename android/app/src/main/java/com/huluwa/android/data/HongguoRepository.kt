package com.huluwa.android.data

import kotlinx.serialization.json.*

const val HONGGUO_SESSION_KIND = "hongguo"

data class HongguoSeries(val id: String, val title: String, val cover: String, val intro: String, val episodeCount: Int) {
    companion object {
        fun from(json: JsonObject) = HongguoSeries(json.text("id"), json.text("title"), json.text("cover"),
            json.text("intro"), json.number("episodeCount")?.toInt() ?: 0)
    }
}
data class HongguoEpisode(val id: String, val number: Int, val title: String, val locked: Boolean)
data class HongguoDetail(val series: HongguoSeries, val episodes: List<HongguoEpisode>)
data class HongguoStream(
    val url: String, val backupUrl: String, val definition: String, val codec: String,
    val width: Int, val height: Int, val size: Long, val encrypted: Boolean,
    val encryptionMethod: String, val spadeA: String, val kid: String,
) {
    val supported: Boolean get() = codec.lowercase() !in setOf("bytevc2", "bvc2") &&
        (!encrypted || (encryptionMethod == "cenc-aes-ctr" && spadeA.isNotBlank()))
}
data class HongguoPlayback(val videoId: String, val streams: List<HongguoStream>, val expireTime: Long, val userAgent: String)
data class HongguoSettings(val signUrl: String, val configured: Boolean, val deviceModel: String, val message: String = "")
data class HongguoSearchPage(val items: List<HongguoSeries>, val nextOffset: Int, val hasMore: Boolean)

class HongguoRepository(private val api: ApiClient) {
    private fun settings(json: JsonObject) = HongguoSettings(json.text("signUrl"), json.flag("configured"), json.text("deviceModel"), json.text("message"))
    suspend fun settings() = settings(api.get("hongguo", "settings").asObject())
    suspend fun saveSettings(url: String) = settings(api.post("hongguo", "settings", body = jsonBody {
        put("signUrl", url)
    }).asObject())
    suspend fun syncSettings() = settings(api.post("hongguo", "settings", "sync").asObject())
    suspend fun search(query: String, offset: Int): HongguoSearchPage {
        val json = api.get("hongguo", "search", query = mapOf("query" to query, "offset" to "$offset")).asObject()
        return HongguoSearchPage(json.rows("items").map { HongguoSeries.from(it.asObject()) },
            json.number("nextOffset")?.toInt() ?: offset + 20, json.flag("hasMore"))
    }
    suspend fun detail(id: String): HongguoDetail {
        val json = api.get("hongguo", "series", id).asObject()
        return HongguoDetail(HongguoSeries.from(json), json.rows("episodes").map { row ->
            val item = row.asObject()
            HongguoEpisode(item.text("id"), item.number("number")?.toInt() ?: 0, item.text("title"), item.flag("locked"))
        })
    }
    suspend fun playback(id: String): HongguoPlayback {
        val json = api.get("hongguo", "episodes", id, "playback").asObject()
        return HongguoPlayback(json.text("videoId"), json.rows("streams").map { row ->
            val item = row.asObject()
            HongguoStream(item.text("url"), item.text("backupUrl"), item.text("definition"), item.text("codec"),
                item.number("width")?.toInt() ?: 0, item.number("height")?.toInt() ?: 0,
                item.number("size")?.toLong() ?: 0, item.flag("encrypted"), item.text("encryptionMethod"), item.text("spadeA"), item.text("kid"))
        }, json.number("expireTime")?.toLong() ?: 0, json.text("userAgent"))
    }
}
