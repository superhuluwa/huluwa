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
data class HongguoRankBoard(val id: String, val name: String, val selected: Boolean)
data class HongguoRankContent(val id: String, val name: String, val selected: Boolean, val boards: List<HongguoRankBoard>)
data class HongguoRankItem(
    val id: String, val title: String, val cover: String, val score: String, val episodeCount: Int,
    val kind: String, val userId: String, val intro: String, val details: List<String>, val promoted: Boolean,
) {
    val actor get() = kind == "actor"
    companion object {
        fun from(json: JsonObject) = HongguoRankItem(json.text("id"), json.text("title"), json.text("cover"),
            json.text("score"), json.number("episodeCount")?.toInt() ?: 0, json.text("kind", fallback = "series"),
            json.text("userId"), json.text("intro"), json.rows("details").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            json.flag("promoted"))
    }
}
data class HongguoRanking(
    val content: String, val board: String, val contents: List<HongguoRankContent>, val items: List<HongguoRankItem>,
    val hasMore: Boolean, val cursor: String,
)
data class HongguoDiscoverPage(val items: List<HongguoSeries>)

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
    suspend fun discover(): HongguoDiscoverPage {
        val json = api.get("hongguo", "discover").asObject()
        return HongguoDiscoverPage(json.rows("items").map { HongguoSeries.from(it.asObject()) })
    }
    suspend fun ranking(content: String = "", board: String = "", cursor: String = ""): HongguoRanking {
        val query = buildMap {
            if (content.isNotBlank()) put("content", content)
            if (board.isNotBlank()) put("board", board)
            if (cursor.isNotBlank()) put("cursor", cursor)
        }
        val json = api.get("hongguo", "ranking", query = query).asObject()
        val contents = json.rows("contents").map { row ->
            val item = row.asObject()
            HongguoRankContent(item.text("id"), item.text("name"), item.flag("selected"),
                item.rows("boards").map { child ->
                    val b = child.asObject(); HongguoRankBoard(b.text("id"), b.text("name"), b.flag("selected"))
                })
        }
        return HongguoRanking(json.text("content"), json.text("board"), contents,
            json.rows("items").map { HongguoRankItem.from(it.asObject()) }, json.flag("hasMore"), json.text("cursor"))
    }
}
