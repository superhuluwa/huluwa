package com.huluwa.android.data

import java.text.Normalizer
import java.util.Locale

/** 客户端统一旧、新接口的文件命名，作品身份不依赖服务端曾经生成的分组键。 */
private object LibraryNames {
    private const val number = "[0-9零〇一二两三四五六七八九十百千]+"
    private val standard = Regex("(?i)S(\\d{1,3})[ ._-]*E[ ._-]*(\\d{1,4})(?!\\d)")
    private val chineseEpisode = Regex("第\\s*($number)(?:\\s*[-~～至到]\\s*($number))?\\s*[集话期]")
    private val explicit = Regex("(?i)(?<![a-z0-9])(?:EP(?:ISODE)?|E)[ ._-]*(\\d{1,4})(?![a-z0-9])")
    private val cross = Regex("(?i)(?<![a-z0-9])(\\d{1,2})x(\\d{1,4})(?![a-z0-9])")
    private val suffix = Regex("(?<![A-Za-z0-9])(\\d{1,4})(?:\\s*[-~～－—–至到]\\s*(\\d{1,4}))?\\s*[集话期回]")
    private val collectionCount = Regex("(?:全|共|总计|总|更新至|更新到)\\s*(?:第\\s*)?$number\\s*[集话期回](?:全|完)?|$number\\s*[集话期回]\\s*(?:全|完)")
    private val episodePrefixTail = Regex("[-~～－—–至到第]\\s*$")
    private val invisible = Regex("[\\u200B-\\u200D\\uFEFF]")
    private val compact = Regex("(?<=[\\u3400-\\u9fff])(\\d{2,4})(?=$|[ ._\\-\\[【])")
    private val bracketed = Regex("[\\[【]\\s*(\\d{1,4})(?:\\s*[-~～至到]\\s*(\\d{1,4}))?\\s*[\\]】]")
    private val separated = Regex("(?:^|[ ._-])(\\d{1,4})(?=$|[ ._\\-\\[\\]【】()（）])")
    private val rangeEnd = Regex("(?i)^[ ._]*[-~～至到][ ._]*(?:EP(?:ISODE)?|E|第)?[ ._-]*(?:\\d{1,2}x)?($number)(?![a-z0-9])")
    private val chineseSeason = Regex("第\\s*($number)\\s*季")
    private val englishSeason = Regex("(?i)(?<![a-z0-9])(?:Season[ ._-]*|S)(\\d{1,3})(?!\\d)")
    private val quality = Regex("(?i)(?:^|[\\s\\p{P}]|(?<=[\\u3400-\\u9fff]))(?:4320p|2160p|1440p|1080[pi]|720p|576p|480p|[48]k|web[ ._-]?(?:dl|rip)|blu[ ._-]?ray|bd(?:rip|remux)|remux|hdtv|[hx][ ._-]?26[45]|hevc|avc|av1|hdr(?:10\\+?)?|dovi|dv|dolby[ ._-]*vision|杜比视界|aac|ddp?(?:[ .]?\\d)?|atmos|\\d+bit|\\d+fps)(?=$|[^a-z0-9])")
    private val versionTag = Regex("(?i)(?:高码版|高码|高帧率版|高帧率|高帧版|高帧|60帧率版本|60帧率版|60帧率|60帧版本|60帧版|60帧|帧率版本|帧率版|普通版|标准版|修复版|重制版|加长版|未删减版|无删减版|完整版|导演剪辑版|导演版|杜比视界版|HDR版|DV版|4K版|2160P版|1080P版)")
    private val leadingNoise = Regex("(?i)^[\\[【(（][^\\]】)）]*(?:www\\.|https?://|\\.(?:com|net|org|cn)|字幕|无水印|高清|发布|搬运|影视|资源|1080|2160|4k)[^\\]】)）]*[\\]】)）][ ._-]*")
    private val decoration = Regex("(?i)[\\[【(（][^\\]】)）]*(?:www\\.|https?://|\\.(?:com|net|org|cn)|字幕|无水印|高清|发布|搬运|影视|资源|国语|中字|国剧|国产剧|完整版|全集|完结)[^\\]】)）]*[\\]】)）]")
    private val hints = listOf("8k|4320p" to "8K", "4k|2160p" to "4K", "1440p" to "1440P",
        "1080p" to "1080P", "720p" to "720P", "480p" to "480P").map { (pattern, label) ->
        Regex("(?i)(?:^|[^a-z0-9])(?:$pattern)(?=$|[^a-z0-9])") to label
    }
    private val dolbyVision = Regex("(?i)杜比视界|dolby[ ._-]*vision|(?:^|[^a-z0-9])(?:dv|dovi)(?=$|[^a-z0-9])")
    private val dynamicRange = Regex("(?i)(?:^|[^a-z0-9])(HDR10\\+?|HDR|HLG)(?=$|[^a-z0-9])")
    private val leadingGroup = Regex("^[\\[【]([A-Za-z0-9._ -]+)[\\]】][ ._-]*(?=[\\u3400-\\u9fff])")
    private val collection = Regex("[ ._\\-\\[【(（]*(?:全\\s*\\d+\\s*集|全集|合集|完结|更新至.*|国语|中字|中文字幕)[\\]】)） ._-]*$")
    private val year = Regex("(?<![\\p{L}\\d])(?:19|20)\\d{2}(?!\\d)")
    private val generic = Regex("(?i)^(?:第?$number[集话期]?|EP?\\d+|S\\d+(?:E\\d+)?|Season\\s*\\d+|video|sample|movie|index|master|playlist|part|file|下载|迅雷下载|新建文件夹|电视剧|剧集|国产剧|国剧|美剧|韩剧|日剧|港台剧|动漫|动画|电影|movies?|media|completed|downloads?|videos?|tv[ ._-]*shows?)$")
    private val opaque = Regex("(?i)(?:[a-f0-9]{16,}|[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}|(?:video|part|file|download)[ _-]*[0-9]+)")

    data class Episode(val start: Int, val end: Int, val titleEnd: Int)
    data class Title(val text: String, val year: String)
    data class Resolved(val entry: LibraryEntry, val title: Title, val recognized: Boolean)

    private fun chineseNumber(raw: String): Int {
        raw.toIntOrNull()?.let { return it }
        val digits = "零一二三四五六七八九"
        if (raw.none { it in "十百千" }) return raw.map {
            digits.indexOf(if (it == '两') '二' else if (it == '〇') '零' else it)
        }.joinToString("").toIntOrNull() ?: 0
        var result = 0
        var digit = 0
        raw.forEach { ch ->
            val unit = when (ch) { '十' -> 10; '百' -> 100; '千' -> 1000; else -> 0 }
            if (unit > 0) { result += digit.coerceAtLeast(1) * unit; digit = 0 }
            else digit = digits.indexOf(if (ch == '两') '二' else if (ch == '〇') '零' else ch).coerceAtLeast(0)
        }
        return result + digit
    }

    fun episode(value: String): Episode? {
        fun hint(match: MatchResult, first: String, last: String = ""): Episode? {
            val start = chineseNumber(first)
            if (start !in 1..9999 || collectionCount.findAll(value).any { match.range.first in it.range && !episodePrefixTail.containsMatchIn(value.substring(0, it.range.first)) }) return null
            val end = if (last.isNotBlank()) chineseNumber(last) else rangeEnd.find(value.substring(match.range.last + 1))
                ?.groupValues?.get(1)?.let(::chineseNumber) ?: start
            return Episode(start, end.takeIf { it in start..9999 } ?: start, match.range.first)
        }
        standard.findAll(value).firstNotNullOfOrNull { hint(it, it.groupValues[2]) }?.let { return it }
        chineseEpisode.findAll(value).firstNotNullOfOrNull { hint(it, it.groupValues[1], it.groupValues[2]) }?.let { return it }
        explicit.findAll(value).firstNotNullOfOrNull { hint(it, it.groupValues[1]) }?.let { return it }
        cross.findAll(value).firstNotNullOfOrNull { hint(it, it.groupValues[2]) }?.let { return it }
        suffix.findAll(value).firstNotNullOfOrNull { hint(it, it.groupValues[1], it.groupValues[2]) }?.let { return it }
        bracketed.findAll(value).firstOrNull { (it.groupValues[1].toIntOrNull() ?: 0).let { n -> n in 1..9999 && n !in 1900..2099 } }?.let {
            return hint(it, it.groupValues[1], it.groupValues[2])
        }
        compact.findAll(value).firstOrNull {
            val value = it.groupValues[1].toIntOrNull() ?: 0
            value in 1..9999 && value !in 1900..2099
        }?.let { return hint(it, it.groupValues[1]) }
        for (match in separated.findAll(value)) {
            val candidate = match.groupValues[1].toIntOrNull() ?: continue
            if (candidate !in 1..9999 || candidate in 1900..2099) continue
            val offset = match.groups[1]!!.range.first
            if (englishSeason.findAll(value).any { offset in it.groups[1]!!.range }) continue
            val prefix = value.substring(0, offset)
            if (quality.containsMatchIn(prefix) && !Regex("(?i).*(?:2160p|1080p|720p|480p|4k|8k|web[ ._-]?dl|bluray|hdtv)[ ._-]*").matches(prefix)) continue
            if (match.range.first == 0 && match.range.last + 1 < value.length &&
                !Regex("[ ._]*(?:[-~～]\\d+)?[ ._]*").matches(value.substring(match.range.last + 1))) continue
            return hint(match, match.groupValues[1])
        }
        return null
    }

    fun season(value: String): Int? = chineseSeason.find(value)?.groupValues?.get(1)?.let(::chineseNumber)
        ?: englishSeason.find(value)?.groupValues?.get(1)?.toIntOrNull()
        ?: cross.find(value)?.groupValues?.get(1)?.toIntOrNull()

    private fun withoutCollectionCounts(value: String): String = collectionCount.replace(value) { match ->
        if (episodePrefixTail.containsMatchIn(value.substring(0, match.range.first))) match.value else " "
    }

    fun title(raw: String): Title {
        var value = withoutCollectionCounts(invisible.replace(Normalizer.normalize(raw, Normalizer.Form.NFKC), "")).trim()
        while (true) {
            val next = leadingNoise.replaceFirst(value, "")
            if (next == value) break
            value = next
        }
        value = decoration.replace(leadingGroup.replaceFirst(value, ""), " ").trim()
        val boundary = listOfNotNull(episode(value)?.titleEnd, quality.find(value)?.range?.first,
            chineseSeason.find(value)?.range?.first, englishSeason.find(value)?.range?.first).minOrNull()
        if (boundary != null) value = value.substring(0, boundary)
        val releaseYear = year.find(value)?.value.orEmpty()
        value = year.replace(value, " ")
        value = versionTag.replace(value, " ")
        while (true) {
            val next = collection.replace(value, "").trim()
            if (next == value) break
            value = next
        }
        value = value.replace(Regex("[._\\[\\]【】()（）]+"), " ")
            .replace(Regex("\\s+"), " ").trim(' ', '-', '_', '.')
        // 同时带中文名和英文发布名时，中文主标题作为外层名称。
        value = value.replace(Regex("^([\\u3400-\\u9fff0-9·：: ]+) [A-Za-z].*$"), "$1").trim()
        return Title(value, releaseYear)
    }

    fun useful(value: String) = value.isNotBlank() && value != "待归类" && !generic.matches(value) && !opaque.matches(value)
    fun key(value: String) = Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
        .replace(Regex("[\\s\\p{P}\\p{Z}]+"), "")

    private fun videoInfo(entry: LibraryEntry): LibraryVideoInfo {
        val info = entry.videoInfo
        if (info.source == "probe") return info
        val resolution = info.resolution.ifBlank { hints.firstOrNull { it.first.containsMatchIn(entry.fileName) }?.second.orEmpty() }
        val range = info.dynamicRange.ifBlank {
            if (dolbyVision.containsMatchIn(entry.fileName)) "杜比视界"
            else dynamicRange.find(entry.fileName)?.groupValues?.get(1)?.uppercase(Locale.ROOT).orEmpty()
        }
        return if (resolution.isBlank() && range.isBlank()) info
            else info.copy(resolution = resolution, dynamicRange = range, source = "filename")
    }

    fun resolve(entry: LibraryEntry): Resolved {
        val rawBase = invisible.replace(Normalizer.normalize(entry.fileName.substringBeforeLast('.'), Normalizer.Form.NFKC), "")
        val base = withoutCollectionCounts(rawBase)
        val fileTitle = title(base)
        val reported = title(entry.seriesTitle)
        val directories = entry.relativePath.replace('\\', '/').split('/').dropLast(1).asReversed()
        val parents = directories.map(::title).filter { useful(it.text) }
        val parent = parents.firstOrNull()
        val parentAnchor = parents.firstOrNull { fileTitle.text.endsWith(" ${it.text}") }
        val chineseParent = parents.firstOrNull { it.text.any { ch -> ch in '\u3400'..'\u9fff' } }
        var chosen = when {
            !useful(fileTitle.text) -> parent ?: reported
            parentAnchor != null -> parentAnchor
            useful(reported.text) && reported.text.any { it in '\u3400'..'\u9fff' } &&
                fileTitle.text.none { it in '\u3400'..'\u9fff' } -> reported
            chineseParent != null && fileTitle.text.none { ch -> ch in '\u3400'..'\u9fff' } -> chineseParent
            else -> fileTitle
        }
        if (!useful(chosen.text) && useful(reported.text)) chosen = reported
        val matchedParent = parents.firstOrNull { key(it.text) == key(chosen.text) }
        if (chosen.year.isBlank()) chosen = chosen.copy(year = matchedParent?.year.orEmpty())
        val episode = episode(base)
        val countOnly = episode == null && collectionCount.containsMatchIn(rawBase)
        val explicitSeason = (listOf(base) + directories).firstNotNullOfOrNull { season(it)?.takeIf { n -> n > 0 } }
        val normalized = entry.copy(
            season = explicitSeason ?: entry.season,
            seasonExplicit = explicitSeason != null || entry.seasonExplicit,
            episode = episode?.start ?: if (countOnly) 0 else entry.episode,
            episodeEnd = episode?.end ?: if (countOnly) 0 else entry.episodeEnd,
            episodeRecognized = episode != null || !countOnly && entry.episodeRecognized,
            videoInfo = videoInfo(entry),
        )
        return Resolved(normalized, chosen, useful(chosen.text))
    }
}

/** 同名且只出现一个明确年份时合并发布版本；多个翻拍年份仍分别保留。 */
fun normalizeLibraryEntries(entries: List<LibraryEntry>): List<LibraryEntry> {
    val resolved = entries.map(LibraryNames::resolve)
    val years = resolved.filter { it.recognized }.groupBy { LibraryNames.key(it.title.text) }
        .mapValues { (_, rows) -> rows.map { it.title.year }.filter { it.isNotBlank() }.distinct() }
    return resolved.map { row ->
        if (!row.recognized) row.entry.copy(seriesTitle = "待归类", showKey = "unclassified", seriesRecognized = false)
        else {
            val baseKey = LibraryNames.key(row.title.text)
            val distinguishYear = years[baseKey].orEmpty().size > 1 && row.title.year.isNotBlank()
            row.entry.copy(
                seriesTitle = row.title.text + if (distinguishYear) "（${row.title.year}）" else "",
                showKey = "show:$baseKey" + if (distinguishYear) ":${row.title.year}" else "",
                seriesRecognized = true,
            )
        }
    }
}
