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
    val collection: Boolean = false,
) {
    val section get() = "第 $season 季 · " + if (collection && !episodeLabel.contains("全集") && !episodeLabel.contains("合集"))
        "全集 / 合集 · $episodeLabel" else episodeLabel
    val order get() = when {
        collection -> 2
        episodeStart <= 0 -> 3
        episodeStart == episodeEnd -> 0
        else -> 1
    }
    val episodeKey get() = when {
        episodeStart > 0 && episodeEnd > episodeStart -> "$episodeStart-$episodeEnd"
        episodeStart > 0 -> episodeStart.toString()
        collection -> "全集"
        else -> "未标集数"
    }
    val versionScore get() = (if ("杜比视界" in tags) 8 else if ("HDR" in tags) 4 else 0) +
        (if ("高码" in tags) 2 else 0) + (if ("60帧" in tags) 1 else 0)
    companion object {
        private val collectionTitle = Regex("全集|合集|打包|整季|全\\s*[0-9一二三四五六七八九十百]+\\s*集|\\b(?:complete[ ._-]*(?:series|season)|full[ ._-]*season|season[ ._-]*pack)\\b", RegexOption.IGNORE_CASE)
        fun from(raw: JsonObject, totalEpisodes: Int = 0): BtbtlaResource {
            val title = raw.text("title")
            val start = raw.number("episodeStart")?.toInt() ?: 0
            val end = raw.number("episodeEnd")?.toInt() ?: 0
            val inferredCollection = collectionTitle.containsMatchIn(title) ||
                (totalEpisodes > 1 && start == 1 && end >= totalEpisodes) ||
                ((start == 0 || start != end) && Regex("\\bCOMPLETE\\b", RegexOption.IGNORE_CASE).containsMatchIn(title))
            return BtbtlaResource(raw.text("id"), title, raw.text("size"), raw.number("season")?.toInt() ?: 1,
                start, end, raw.text("episodeLabel"),
                raw.rows("tags").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                raw.flag("collection", fallback = inferredCollection))
        }
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
                group.rows("items").map { BtbtlaResource.from(it.asObject(), raw.number("totalEpisodes")?.toInt() ?: 0) }
                    .filter { it.id.isNotBlank() }
                    .sortedWith(compareBy<BtbtlaResource> { it.order }
                        .thenByDescending { it.season }.thenByDescending { it.episodeEnd }
                        .thenByDescending { it.episodeStart }.thenByDescending { it.versionScore }))
        }.sortedByDescending { it.resolution }
    }
}

/**
 * 番茄小说书目卡（kind=fanqie-book）。
 *
 * 只承载搜索接口下发的书目信息；番茄小说不参与订阅追更，
 * 摘要与最新章节标题用于卡片配文之外的补充展示。
 */
data class FanqieBook(
    val bookId: String, val title: String, val author: String, val cover: String,
    val category: String, val wordNumber: Long, val finished: Boolean,
    val score: Double, val abstractText: String, val lastChapterTitle: String,
) {
    val meta get() = listOf(category, if (finished) "完结" else "连载中",
        if (wordNumber > 0) "${wordNumber / 10000} 万字" else "",
        if (score > 0) "评分 ${"%.1f".format(score)}" else "")
        .filter(String::isNotBlank).joinToString(" · ")
    val summary get() = abstractText.replace(Regex("\\s+"), " ").trim()
    companion object {
        fun from(raw: JsonObject): FanqieBook? {
            if (raw.text("kind") != "fanqie-book") return null
            val id = raw.text("bookId")
            if (id.isBlank() || raw.text("title").isBlank()) return null
            return FanqieBook(id, raw.text("title"), raw.text("author"), raw.text("thumbUrl"),
                raw.text("category"), raw.number("wordNumber")?.toLong() ?: 0L,
                raw.flag("finished"), raw.number("score")?.toDouble() ?: 0.0,
                raw.text("abstract"), raw.text("lastChapterTitle"))
        }
    }
}

/**
 * 番茄小说阅读入口卡（kind=fanqie-reader）。
 *
 * 兼容手动阅读指令的入口卡，点按后从服务器恢复最新阅读位置。
 */
data class FanqieReaderCard(
    val bookId: String, val title: String, val author: String, val cover: String,
    val progressChapter: Int,
) {
    companion object {
        fun from(raw: JsonObject): FanqieReaderCard? {
            if (raw.text("kind") != "fanqie-reader") return null
            val id = raw.text("bookId")
            if (id.isBlank()) return null
            return FanqieReaderCard(id, raw.text("title"), raw.text("author"), raw.text("cover"),
                raw.number("progressChapter")?.toInt() ?: 0)
        }
    }
}

/** 阅读页的一章正文（/api/fanqie/books/{bookId}/read 的响应）。 */
data class FanqieChapter(
    val bookId: String, val title: String, val author: String,
    val order: Int, val chapterTitle: String, val content: String,
    val chapterCount: Int, val hasPrev: Boolean, val hasNext: Boolean,
    val prevChapter: String, val nextChapter: String,
    val paragraphIndex: Int = 0, val scrollOffset: Int = 0,
) {
    /** 正文按行拆成段落，供阅读页分段渲染（不分段会让长章一次性组合过大）。 */
    val paragraphs = content.split('\n').map(String::trim).filter(String::isNotBlank)
    companion object {
        fun from(raw: JsonObject): FanqieChapter = FanqieChapter(
            raw.text("bookId"), raw.text("title"), raw.text("author"),
            raw.number("order")?.toInt() ?: 1, raw.text("chapterTitle"), raw.text("content"),
            raw.number("chapterCount")?.toInt() ?: 0, raw.flag("hasPrev"), raw.flag("hasNext"),
            raw.text("prevChapter"), raw.text("nextChapter"),
            raw.number("paragraphIndex")?.toInt() ?: 0, raw.number("scrollOffset")?.toInt() ?: 0,
        )
    }
}

/** 目录（/api/fanqie/books/{bookId}/catalog），打开目录抽屉时单独拉取。 */
data class FanqieCatalog(val chapterCount: Int, val progressChapter: Int, val chapters: List<FanqieTocItem>) {
    companion object {
        fun from(raw: JsonObject): FanqieCatalog = FanqieCatalog(
            raw.number("chapterCount")?.toInt() ?: 0, raw.number("progressChapter")?.toInt() ?: 0,
            raw.rows("chapters").mapNotNull { FanqieTocItem.from(it.asObject()) },
        )
    }
}

/** 目录条目。 */
data class FanqieTocItem(val order: Int, val title: String) {
    companion object {
        fun from(raw: JsonObject): FanqieTocItem? {
            val order = raw.number("order")?.toInt() ?: return null
            val title = raw.text("title")
            if (title.isBlank()) return null
            return FanqieTocItem(order, title)
        }
    }
}

/** 书库条目（/api/fanqie/books）。 */
data class FanqieBookShelf(
    val bookId: String, val title: String, val author: String, val cover: String,
    val chapterCount: Int, val totalChars: Long, val progressChapter: Int,
    val downloadedChapterCount: Int = 0,
) {
    val progressLabel get() = when {
        progressChapter < 1 -> "尚未开始阅读"
        chapterCount > 0 -> "读到第 $progressChapter/$chapterCount 章"
        else -> "读到第 $progressChapter 章"
    }
    companion object {
        fun from(raw: JsonObject): FanqieBookShelf? {
            val id = raw.text("bookId")
            if (id.isBlank() || raw.text("title").isBlank()) return null
            return FanqieBookShelf(id, raw.text("title"), raw.text("author"), raw.text("cover"),
                raw.number("chapterCount")?.toInt() ?: 0, raw.number("totalChars")?.toLong() ?: 0L,
                raw.number("progressChapter")?.toInt() ?: 0, raw.number("downloadedChapterCount")?.toInt() ?: 0)
        }
    }
}

/** 书架后台更新的单本进度，下载数来自服务端正文缓存。 */
data class FanqieBookUpdate(val bookId: String, val title: String, val chapterCount: Int,
    val downloadedChapterCount: Int, val newChapters: Int, val downloadedChapters: Int,
    val failedChapters: Int, val error: String?) {
    companion object {
        fun from(raw: JsonObject) = FanqieBookUpdate(raw.text("bookId"), raw.text("title"),
            raw.number("chapterCount")?.toInt() ?: 0, raw.number("downloadedChapterCount")?.toInt() ?: 0,
            raw.number("newChapters")?.toInt() ?: 0, raw.number("downloadedChapters")?.toInt() ?: 0,
            raw.number("failedChapters")?.toInt() ?: 0, raw.text("error").ifBlank { null })
    }
}

data class FanqieShelfUpdateStatus(val startedAt: Long, val running: Boolean, val totalBooks: Int,
    val checkedBooks: Int, val currentBookId: String, val currentTitle: String,
    val books: List<FanqieBookUpdate>, val error: String?) {
    val summary: String get() = when {
        running -> if (totalBooks == 0) "正在读取书架…" else
            "已检查 $checkedBooks/$totalBooks 本，已下载 ${books.sumOf { it.downloadedChapters }} 章" +
                currentTitle.takeIf(String::isNotBlank)?.let { "\n正在更新：《$it》" }.orEmpty()
        error != null -> error
        startedAt == 0L -> ""
        totalBooks == 0 -> "书架里没有可更新的小说"
        else -> "检查完成，发现 ${books.sumOf { it.newChapters }} 章更新，补充下载 ${books.sumOf { it.downloadedChapters }} 章" +
            books.count { it.error != null || it.failedChapters > 0 }.takeIf { it > 0 }
                ?.let { "；$it 本未完成，可再次检查更新" }.orEmpty()
    }
    companion object {
        fun from(raw: JsonObject) = FanqieShelfUpdateStatus(raw.number("startedAt")?.toLong() ?: 0L,
            raw.flag("running"), raw.number("totalBooks")?.toInt() ?: 0, raw.number("checkedBooks")?.toInt() ?: 0,
            raw.text("currentBookId"), raw.text("currentTitle"), raw.rows("books").map { FanqieBookUpdate.from(it.asObject()) },
            raw.text("error").ifBlank { null })
    }
}

/** 批量删除逐本返回成功与失败，界面只移除真正删除成功的条目。 */
data class FanqieDeleteResult(val deletedIds: Set<String>, val failures: Map<String, String>) {
    companion object {
        fun from(raw: JsonObject) = FanqieDeleteResult(
            raw.rows("deletedIds").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet(),
            raw["failures"].asObject().mapValues { (_, value) -> (value as? JsonPrimitive)?.contentOrNull.orEmpty() },
        )
    }
}
