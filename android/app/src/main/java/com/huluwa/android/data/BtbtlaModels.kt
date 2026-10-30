package com.huluwa.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** BT影视使用独立消息类型，避免套用电影港的订阅与在线播放接口。 */
data class BtbtlaCard(
    val id: String, val title: String, val cover: String, val year: String,
    val genre: String, val origin: String, val pageUrl: String,
) {
    val meta get() = listOf(year, genre, origin).filter(String::isNotBlank).joinToString(" · ")
    companion object {
        fun from(raw: JsonObject): BtbtlaCard? {
            if (raw.text("kind") != "btbtla-title") return null
            val id = raw.text("btbtlaId")
            if (id.isBlank() || raw.text("title").isBlank()) return null
            return BtbtlaCard(id, raw.text("title"), raw.text("cover"), raw.text("year"),
                raw.text("genre"), raw.text("origin"), raw.text("pageUrl"))
        }
    }
}

data class BtbtlaResource(
    val id: String, val title: String, val size: String, val season: Int,
    val episodeStart: Int, val episodeEnd: Int, val episodeLabel: String, val tags: List<String>,
) {
    val section get() = "第 $season 季 · $episodeLabel"
    companion object {
        fun from(raw: JsonObject) = BtbtlaResource(raw.text("id"), raw.text("title"), raw.text("size"),
            raw.number("season")?.toInt() ?: 1, raw.number("episodeStart")?.toInt() ?: 0,
            raw.number("episodeEnd")?.toInt() ?: 0, raw.text("episodeLabel"),
            raw.rows("tags").mapNotNull { (it as? JsonPrimitive)?.contentOrNull })
    }
}

data class BtbtlaQuality(val quality: String, val resolution: Int, val items: List<BtbtlaResource>) {
    val coverage: String get() {
        val episodes = items.filter { it.episodeStart > 0 && it.episodeEnd >= it.episodeStart }
            .groupBy { it.season }.toSortedMap()
        if (episodes.isEmpty()) return "集数未标明，以资源标题为准"
        return episodes.entries.joinToString("；") { (season, resources) ->
            val ranges = resources.map { it.episodeStart..it.episodeEnd }.sortedBy { it.first }
            val merged = mutableListOf<IntRange>()
            ranges.forEach { range ->
                val last = merged.lastOrNull()
                if (last != null && range.first <= last.last + 1) merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
                else merged.add(range)
            }
            "第 $season 季 · " + merged.joinToString("、") { if (it.first == it.last) "${it.first}" else "${it.first}–${it.last}" } + " 集"
        }
    }
    companion object {
        fun rows(raw: JsonObject): List<BtbtlaQuality> = raw.rows("groups").map { value ->
            val group = value.asObject()
            BtbtlaQuality(group.text("quality"), group.number("resolution")?.toInt() ?: 0,
                group.rows("items").map { BtbtlaResource.from(it.asObject()) }.filter { it.id.isNotBlank() })
        }.sortedByDescending { it.resolution }
    }
}
