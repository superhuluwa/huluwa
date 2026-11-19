package com.huluwa.android.data

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

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

/** 官网一级分类；route 为空表示“全部”。 */
data class HuangguoCategory(val name: String, val route: String, val available: Boolean = true) {
    companion object {
        fun from(raw: JsonObject) = HuangguoCategory(raw.text("name"), raw.text("route"), raw.flag("available", true))
    }
}

/** 当前一级频道下的二级筛选；value 为空或 tag: 前缀由客户端和后端约定。 */
data class HuangguoFilter(val name: String, val value: String) {
    companion object {
        fun from(raw: JsonObject) = HuangguoFilter(raw.text("name"), raw.text("value"))
    }
}

data class HuangguoTag(val name: String, val slug: String) {
    companion object {
        fun from(raw: JsonObject) = HuangguoTag(raw.text("name"), raw.text("slug"))
    }
}

data class HuangguoTagGroup(val code: String, val name: String, val tags: List<HuangguoTag>) {
    companion object {
        fun from(raw: JsonObject) = HuangguoTagGroup(
            raw.text("code"), raw.text("name"), raw.rows("tags").map { HuangguoTag.from(it.asObject()) },
        )
    }
}

/** 分类浏览列表与分页状态。 */
data class HuangguoBrowsePage(
    val items: List<HuangguoCard>, val page: Int, val pages: Int, val total: Int,
) {
    companion object {
        fun from(raw: JsonObject) = HuangguoBrowsePage(
            raw.rows("items").map { HuangguoCard.from(it.asObject()) },
            raw.number("page")?.toInt() ?: 1,
            (raw.number("pages")?.toInt() ?: 1).coerceAtLeast(1),
            raw.number("total")?.toInt() ?: 0,
        )
    }
}

data class HuangguoState(
    val visible: Boolean = false,
    /** 是否从功能页进入；用于只高亮当前入口，不影响其他页面。 */
    val fromFunctions: Boolean = false,
    val recommend: LoadState<List<HuangguoCard>> = LoadState(emptyList()),
    val categories: LoadState<List<HuangguoCategory>> = LoadState(emptyList()),
    val tagGroups: LoadState<List<HuangguoTagGroup>> = LoadState(emptyList()),
    val filters: LoadState<List<HuangguoFilter>> = LoadState(emptyList()),
    val selectedCategory: String = "",
    val selectedFilter: String = "",
    val selectedTagGroup: String = "",
    val selectedTag: String = "",
    val browse: LoadState<List<HuangguoCard>> = LoadState(emptyList()),
    val browsePage: Int = 1,
    val browsePages: Int = 1,
    val browseLoadingMore: Boolean = false,
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

/** 黄果博客卡片；与影视剧卡片分开，避免把文章当成可直接播放的剧集。 */
data class HuangguoPost(
    val id: String,
    val title: String,
    val cover: String,
    val description: String,
    val author: String,
    val publishedAt: String,
    val hot: Long,
    val tags: List<String>,
) {
    val date: String get() = publishedAt.take(10)
    val meta: String get() = listOf(author.takeIf { it.isNotBlank() }, date.takeIf { it.isNotBlank() }).filterNotNull().joinToString(" · ")

    companion object {
        fun from(raw: JsonObject) = HuangguoPost(
            id = raw.text("id"), title = raw.text("title"), cover = raw.text("cover"),
            description = raw.text("description"), author = raw.text("author"),
            publishedAt = raw.text("publishedAt"), hot = raw.number("hot")?.toLong() ?: 0L,
            tags = raw.rows("tags").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }
}

data class HuangguoPostBlock(val type: String, val text: String, val index: Int) {
    companion object {
        fun from(raw: JsonObject) = HuangguoPostBlock(raw.text("type"), raw.text("text"), raw.number("index")?.toInt() ?: 0)
    }
}

data class HuangguoPostDetail(
    val post: HuangguoPost,
    val authorAvatar: String,
    val reads: Int,
    val blocks: List<HuangguoPostBlock>,
    val tagLinks: List<String>,
) {
    companion object {
        fun from(raw: JsonObject) = HuangguoPostDetail(
            HuangguoPost.from(raw), raw.text("authorAvatar"), raw.number("reads")?.toInt() ?: 0,
            raw.rows("blocks").map { HuangguoPostBlock.from(it.asObject()) },
            raw.rows("tagLinks").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
        )
    }
}

data class HuangguoPostVideo(val index: Int, val url: String) {
    companion object {
        fun rows(raw: JsonObject) = raw.rows("videos").map { element ->
            val item = element.asObject()
            HuangguoPostVideo(item.number("index")?.toInt() ?: 0, item.text("url"))
        }
    }
}

data class HuangguoPostPage(val items: List<HuangguoPost>, val page: Int, val pages: Int, val total: Int) {
    companion object {
        fun from(raw: JsonObject) = HuangguoPostPage(
            raw.rows("items").map { HuangguoPost.from(it.asObject()) },
            raw.number("page")?.toInt() ?: 1,
            (raw.number("pages")?.toInt() ?: 1).coerceAtLeast(1),
            raw.number("total")?.toInt() ?: 0,
        )
    }
}

data class HuangguoBlogState(
    val channel: String = "",
    val posts: LoadState<List<HuangguoPost>> = LoadState(emptyList()),
    val page: Int = 1,
    val pages: Int = 1,
    val loadingMore: Boolean = false,
    val detail: HuangguoPostDetail? = null,
    val detailLoading: Boolean = false,
    val detailError: String? = null,
    /** 点击顶部博客入口时显示博客列表；为 false 时显示影视浏览。 */
    val fromEntry: Boolean = false,
    val visible: Boolean = false,
    val videos: Map<Int, String> = emptyMap(),
    val videoLoading: Boolean = false,
    val videoError: String? = null,
    val activeVideoIndex: Int? = null,
)
