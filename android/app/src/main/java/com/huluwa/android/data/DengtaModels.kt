package com.huluwa.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

const val DENGTA_SESSION_ID = "local-dengta"
const val DENGTA_SESSION_KIND = "dengta"

/** 单位沿用接口，不把缺失值当作零。raw 用于画图与比较，label 用于展示。 */
data class DengtaValue(val value: String = "", val unit: String = "", val raw: Double? = null) {
    val label: String get() = if (value.isBlank()) "—" else value + unit
    companion object {
        fun from(node: JsonObject, raw: Double? = null): DengtaValue {
            val formatted = node.child("valueUnit").takeIf { it.isNotEmpty() } ?: node
            val value = formatted.text("value")
            val unit = formatted.text("unit")
            val factor = when (unit) { "万" -> 10_000.0; "亿" -> 100_000_000.0; "%" -> .01; else -> 1.0 }
            val number = raw ?: if (node["valueUnit"] is JsonObject) node.number("value")
                else value.replace(",", "").toDoubleOrNull()?.times(factor)
            return DengtaValue(value, unit, number?.takeIf(Double::isFinite))
        }
    }
}

@Serializable data class DengtaMedia(
    val id: String, val name: String, val poster: String = "", val platforms: String = "",
    val typeName: String = "", val episodeCount: Int? = null, val updatedEpisodes: Int? = null,
    val updateText: String = "",
)
data class DengtaRankRow(
    val media: DengtaMedia, val rank: Int, val rankChange: Int, val plays: DengtaValue,
    val share: String, val platformCode: String, val openDay: String, val playStateImage: String,
    val modifiedTime: String,
)
data class DengtaRanking(
    val title: String = "全网正片播放量", val column: String = "正片播放量", val date: String = "",
    val firstDate: String = "", val lastDate: String = "", val market: DengtaValue = DengtaValue(),
    val rows: List<DengtaRankRow> = emptyList(),
) {
    companion object {
        fun from(data: JsonObject): DengtaRanking {
            require(data["rankMap"] is JsonObject) { "灯塔返回的榜单数据格式不完整" }
            val rank = data.child("rankMap")
            val calendar = data.child("calendarMap")
            return DengtaRanking(
                title = rank.child("titleInfo").text("title", fallback = "全网正片播放量"),
                column = rank.child("titleInfo").text("columnName1", fallback = "正片播放量"),
                date = calendar.text("showDate"), firstDate = calendar.text("startDate"), lastDate = calendar.text("endDate"),
                market = DengtaValue.from(rank.child("market").child("vvCntWithUnit"), rank.child("market").number("vvCnt")),
                rows = rank.rows("rankList").mapIndexedNotNull { index, item ->
                    val row = item.asObject()
                    val id = row.text("mediaId")
                    if (id.isBlank()) return@mapIndexedNotNull null
                    DengtaRankRow(
                        DengtaMedia(id, row.text("mediaName", fallback = "未命名作品"), dengtaImageUrl(row.text("poster")),
                            row.text("platforms"), row.text("mediaTypeName")),
                        row.number("rankNo")?.toInt()?.takeIf { it > 0 } ?: (index + 1),
                        row.number("rankChange")?.toInt() ?: 0,
                        DengtaValue.from(row.child("vvCntWithUnit"), row.number("vvCnt")),
                        row.text("columnValue1").let { if (it.isBlank() || it.endsWith("%")) it else "$it%" },
                        row.text("platformCode"), row.text("openDayStr"), dengtaImageUrl(row.text("playState")), row.text("modifiedTime"),
                    )
                },
            )
        }
    }
}

data class DengtaPoint(val date: String, val value: DengtaValue)
data class DengtaSeries(val name: String, val points: List<DengtaPoint>)
data class DengtaHeat(
    val name: String, val current: String, val maximum: String, val maximumDate: String,
    val trends: List<DengtaSeries> = emptyList(),
)
data class DengtaPlayRule(val platform: String, val update: String, val rule: String)
data class DengtaDetail(
    val media: DengtaMedia, val genres: String, val openDay: String, val playTime: String,
    val duration: String, val background: String, val calendarUrl: String, val calendarTitle: String,
    val rankName: String, val rankNo: Int?, val hasPlayRule: Boolean,
    val today: DengtaValue, val total: DengtaValue, val share: DengtaValue, val shareMaximum: DengtaValue,
    val average: DengtaValue, val statisticsDate: String, val honors: List<String>,
    val playTrends: List<DengtaSeries>, val heats: List<DengtaHeat>,
    val englishName: String = "", val platformIcons: List<String> = emptyList(), val summaryName: String = "灯塔全网正片播放",
    val todayName: String = "今日实时播放量", val opinion: DengtaValue = DengtaValue(), val opinionMaximum: DengtaValue = DengtaValue(),
    val highlights: List<DengtaHighlight> = emptyList(), val highlightCount: Int = 0,
    val marketingEvents: List<DengtaMarketingEvent> = emptyList(), val information: DengtaInformation = DengtaInformation(),
    val informationTitle: String = "剧集信息", val highlightType: String = "SHOW_TV",
) {
    companion object {
        fun from(document: JsonObject, requestedId: String): DengtaDetail {
            val extra = document.child("extra")
            val layers = document.child("data").rows("layers").map { it.asObject() }
            require(extra.isNotEmpty() || layers.isNotEmpty()) { "灯塔返回的剧集详情不完整" }
            fun sections(title: String) = layers.filter { it.child("style").text("title") == title }
                .flatMap { it.rows("sections") }.map { it.asObject().child("item") }
                .filter { it.flag("dataPresent") }.map { it.child("data") }
            val info = sections("mediaInfo")
            val base = info.firstOrNull { "episodeCnt" in it || "openDay" in it } ?: JsonObject(emptyMap())
            val summary = info.firstOrNull { "todayTotalPlayVvCnt" in it || "todayTotalVVCnt" in it } ?: JsonObject(emptyMap())
            val plays = summary.child("todayTotalPlayVvCnt").takeIf { it.isNotEmpty() } ?: summary.child("todayTotalVVCnt")
            val share = summary.child("todayTotalMarketShare")
            val average = summary.child("episodeAveragePlay")
            val update = base.text("updateEpisode")
            val episodeCount = base.number("episodeCnt")?.toInt()?.takeIf { it > 0 }
            val updated = Regex("\\d+").find(update)?.value?.toIntOrNull()
                ?: episodeCount?.takeIf { update.contains("全") || update.contains("完结") }
            val name = base.text("mediaName").ifBlank { extra.text("mediaName", fallback = "未命名作品") }
            val media = DengtaMedia(extra.text("mediaId").ifBlank { requestedId }, name,
                dengtaImageUrl(base.text("poster").ifBlank { extra.text("poster") }), base.stringList("platforms").joinToString(" / "),
                base.text("mediaTypeName"), episodeCount, updated, update)
            val heatSummary = info.firstOrNull { node -> node.values.any { it.asObject().text("hotName").isNotBlank() } }
                ?: JsonObject(emptyMap())
            val heatDetails = sections("mediaHotDetail").firstOrNull() ?: JsonObject(emptyMap())
            val codes = (heatSummary.rows("platforms").map { it.asObject().text("value") } +
                heatDetails.rows("platforms").map { it.asObject().text("value") } +
                heatSummary.keys.filter { heatSummary.child(it).text("hotName").isNotBlank() }).filter(String::isNotBlank).distinct()
            val rank = extra.child("rankVO")
            val message = info.firstOrNull { "message" in it }?.child("message") ?: JsonObject(emptyMap())
            return DengtaDetail(
                media, base.stringList("type").joinToString(" / "), base.text("openDay"), base.text("playTime"),
                base.text("duration"), extra.text("bgColor"), dengtaImageUrl(extra.text("releaseCalendar")),
                if (extra.child("bigType").text("value") == "VARIETY") "追综日历" else "追剧日历",
                rank.text("rankName"), rank.number("rankNo")?.toInt()?.takeIf { it > 0 }, base.flag("hasPlayRule"),
                DengtaValue.from(plays.child("nowValueWithUnit")), DengtaValue.from(plays.child("totalValueWithUnit")),
                DengtaValue.from(share.child("nowValueWithUnit")), DengtaValue.from(share.child("maxValueWithUnit")),
                DengtaValue.from(average.child("nowValueWithUnit")), average.text("fixedDateStr"),
                info.flatMap { it.child("message").rows("list") }.map { it.asObject().text("contentHtml") }.filter(String::isNotBlank),
                sections("mediaMarketShareDetail").flatMap(::dengtaSeries),
                codes.map { code ->
                    val hot = heatSummary.child(code)
                    val detail = heatDetails.child(code)
                    DengtaHeat(hot.text("hotName").ifBlank { detail.text("name", fallback = code) },
                        if (heatSummary.flag("disableNowValue")) "" else hot.text("nowValue"),
                        hot.text("maxValue"), hot.text("maxValueDate"), dengtaSeries(detail))
                },
                englishName = base.text("mediaNameEn"), platformIcons = base.stringList("platformIcons").map(::dengtaImageUrl).filter(String::isNotBlank),
                summaryName = summary.text("name", fallback = "灯塔全网正片播放"), todayName = plays.text("name", fallback = "今日实时播放量"),
                opinion = DengtaValue.from(summary.child("todayHotIndexUnit")), opinionMaximum = DengtaValue.from(summary.child("maxHotIndexUnit")),
                highlights = message.rows("list").map { it.asObject() }.map { DengtaHighlight(it.text("contentHtml", "simpleContentHtml"), it.number("publishTimestamp")?.toLong() ?: 0L) }
                    .filter { it.html.isNotBlank() },
                highlightCount = message.number("count")?.toInt() ?: 0,
                highlightType = message.rows("list").firstOrNull()?.asObject()?.text("dataType").orEmpty().ifBlank { "SHOW_TV" },
                marketingEvents = sections("mediaMarketEvent").flatMap { it.rows("mediaEventList") }.map { DengtaMarketingEvent.from(it.asObject()) },
                information = DengtaInformation.from(sections("mediaArtAndCompany")),
                informationTitle = when (extra.child("bigType").text("value")) { "VARIETY" -> "综艺信息"; "ANIME", "ANIMATION" -> "动漫信息"; else -> "剧集信息" },
            )
        }
    }
}

internal fun JsonObject.stringList(key: String) = rows(key).mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.filter(String::isNotBlank)
private fun dengtaSeries(data: JsonObject): List<DengtaSeries> = data.child("chartDataVO").rows("lines").mapNotNull { item ->
    val line = item.asObject()
    val points = line.rows("line").mapNotNull { entry ->
        val point = entry.asObject()
        val value = DengtaValue.from(point.child("value"))
        val date = point.text("key")
        if (date.isBlank() || value.raw == null) null else DengtaPoint(date, value)
    }
    if (points.isEmpty()) null else DengtaSeries(line.text("name", fallback = "播放量"), points)
}

fun dengtaImageUrl(value: String): String {
    val url = value.trim().let { if (it.startsWith("//")) "https:$it" else it }
    return url.takeIf { it.startsWith("https://", ignoreCase = true) }.orEmpty()
}
fun dengtaDate(value: String): LocalDate? = runCatching {
    if (value.length == 8) LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE)
    else LocalDate.parse(value.take(10).replace('.', '-'))
}.getOrNull()
fun dengtaNumber(value: Double): String = when {
    value >= 100_000_000 -> String.format(Locale.ROOT, "%.1f亿", value / 100_000_000)
    value >= 10_000 -> String.format(Locale.ROOT, "%.1f万", value / 10_000)
    else -> String.format(Locale.ROOT, "%.0f", value)
}

@Serializable data class DengtaFollow(val media: DengtaMedia, val following: Boolean = true, val watched: Int = 0)
@Serializable data class DengtaWatchLog(val id: String, val mediaId: String, val episode: Int, val note: String, val recordedAt: Long)
@Serializable data class DengtaJournalData(val media: List<DengtaFollow> = emptyList(), val logs: List<DengtaWatchLog> = emptyList())
