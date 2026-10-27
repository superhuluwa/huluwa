package com.huluwa.android.data

import kotlinx.serialization.json.JsonObject

/**
 * 懂片帝作品卡片（独立于电影港 / 电影天堂的 FilmCard）。
 *
 * <p>后端在消息载荷里用 {@code kind=dpd-title} 标记，字段也刻意与其它来源不同
 * （{@code dpdId / pageUrl / dpdLines}），因此不会落进 [FilmCard] 的解析分支。
 */
data class DpdCard(
    /** 作品 ID，后续详情 / 线路请求都用它 */
    val id: String,
    val title: String,
    val cover: String,
    val year: String,
    val area: String,
    val genres: String,
    val remarks: String,
    val score: String,
    /** 站点作品页（/player/{作品ID}） */
    val pageUrl: String,
) {
    /** 有年代 / 地区 / 类型 / 评分时拼一行简介，与卡片上的次要信息对应 */
    val meta: String get() = listOf(year, area, genres, score.takeIf { it.isNotBlank() }?.let { "评分 $it" }.orEmpty())
        .filter(String::isNotBlank).joinToString(" · ")

    fun key(): String = "dpd:$id"

    companion object {
        const val KIND = "dpd-title"

        fun from(raw: JsonObject): DpdCard? {
            if (raw.text("kind") != KIND) return null
            val id = raw.text("dpdId")
            val title = raw.text("title")
            if (id.isBlank() || title.isBlank()) return null
            return DpdCard(
                id = id, title = title, cover = raw.text("cover"), year = raw.text("year"),
                area = raw.text("area"), genres = raw.text("genres"),
                remarks = raw.text("remarks"), score = raw.text("score"),
                pageUrl = raw.text("pageUrl"),
            )
        }
    }
}

/** 懂片帝的一条线路（详情接口下发，只有名称与集数，点位后再解析各集） */
data class DpdLine(val name: String, val total: Int) {
    companion object {
        fun rows(raw: JsonObject): List<DpdLine> = raw.rows("dpdLines").mapNotNull { element ->
            val item = element.asObject()
            val name = item.text("name")
            if (name.isBlank()) null else DpdLine(name, item.number("total")?.toInt() ?: 0)
        }
    }
}

/** 懂片帝的某一集（点选线路后由 /api/dpd/line 返回） */
data class DpdEpisode(val title: String, val url: String) {
    companion object {
        fun rows(raw: JsonObject): List<DpdEpisode> = raw.rows("episodes").mapNotNull { element ->
            val item = element.asObject()
            val title = item.text("title")
            // 官方线路的其余集地址为空，需要保留条目供用户点击后按集解析。
            if (title.isBlank()) null else DpdEpisode(title, item.text("url"))
        }
    }
}

/** 与后端剧集去重规则一致：切换线路后仍是同一季、同一集的下载任务。 */
data class DpdDownloadKey(val season: Int, val episode: Int)

enum class DpdDownloadState { Adding, Added }
