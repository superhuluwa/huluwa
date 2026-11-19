package com.huluwa.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** 黄果推荐流/搜索结果卡片。 */
data class HuangguoCard(
    val id: String,
    val title: String,
    val cover: String,
    val description: String,
    val score: Double,
    val episodeCount: Int,
    val tags: List<String>,
) {
    val meta: String get() = listOf(
        episodeCount.takeIf { it > 0 }?.let { "全${it}集" }.orEmpty(),
        score.takeIf { it > 0 }?.let { "评分 ${"%.1f".format(it)}" }.orEmpty(),
        tags.take(2).joinToString(" · "),
    ).filter(String::isNotBlank).joinToString(" · ")

    companion object {
        fun from(raw: JsonObject) = HuangguoCard(
            id = raw.text("id"), title = raw.text("title"), cover = raw.text("cover"),
            description = raw.text("description"), score = raw.number("score") ?: 0.0,
            episodeCount = raw.number("episodeCount")?.toInt() ?: 0,
            tags = raw.rows("tags").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }
}

data class HuangguoEpisode(val ep: Int, val title: String, val duration: Int) {
    companion object {
        fun rows(raw: JsonObject): List<HuangguoEpisode> = raw.rows("episodes").mapNotNull { element ->
            val item = element.asObject()
            val ep = item.number("ep")?.toInt() ?: return@mapNotNull null
            HuangguoEpisode(ep, item.text("title").ifBlank { "第${ep}集" }, item.number("duration")?.toInt() ?: 0)
        }
    }
}

data class HuangguoDetail(val card: HuangguoCard, val episodes: List<HuangguoEpisode>) {
    companion object {
        fun from(raw: JsonObject) = HuangguoDetail(HuangguoCard.from(raw), HuangguoEpisode.rows(raw))
    }
}

/** 单集播放描述；地址短时有效，切集时必须重新请求。 */
data class HuangguoPlayback(
    val id: String, val ep: Int, val url: String, val hevc: String, val preview: String,
) {
    companion object {
        fun from(raw: JsonObject) = HuangguoPlayback(
            raw.text("id"), raw.number("ep")?.toInt() ?: 1, raw.text("url"),
            raw.text("hevc"), raw.text("preview"),
        )
    }
}

data class HuangguoState(
    val visible: Boolean = false,
    /** 是否从功能页进入；用于只高亮当前入口，不影响其他页面。 */
    val fromFunctions: Boolean = false,
    val recommend: LoadState<List<HuangguoCard>> = LoadState(emptyList()),
    val searchKeyword: String = "",
    val search: LoadState<List<HuangguoCard>> = LoadState(emptyList()),
    val detail: HuangguoDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    val selectedEp: Int = 1,
    val playback: HuangguoPlayback? = null,
    val playerVisible: Boolean = false,
    val playerLoading: Boolean = false,
    val playerError: String? = null,
    val resumePositionMs: Long = 0L,
)
