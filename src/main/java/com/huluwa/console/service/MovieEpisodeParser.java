package com.huluwa.console.service;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 电影港的资源集号：显式集号优先，再在移除剧名、季号和发布参数后识别裸数字。 */
final class MovieEpisodeParser {
    private static final String NUMBER = "(?:[0-9]{1,4}|[零〇一二两三四五六七八九十百千]{1,8})";
    private static final String RANGE = "\\s*[-~～－—–至到]\\s*";
    private static final Pattern STANDARD = Pattern.compile(
            "(?i)S\\d{1,3}[ ._-]*E[ ._-]*(\\d{1,4})(?:" + RANGE
                    + "(?:S\\d{1,3}[ ._-]*)?E?[ ._-]*(\\d{1,4}))?(?![a-z0-9])");
    private static final Pattern CHINESE = Pattern.compile(
            "第\\s*(" + NUMBER + ")(?:\\s*[集话期回])?(?:" + RANGE + "(?:第\\s*)?(" + NUMBER + "))?\\s*[集话期回]");
    private static final Pattern ENGLISH = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:EP(?:ISODE)?|E)[ ._-]*(\\d{1,4})(?:" + RANGE
                    + "(?:EP(?:ISODE)?|E)?[ ._-]*(\\d{1,4}))?(?![a-z0-9])");
    private static final Pattern CROSS = Pattern.compile(
            "(?i)(?<![a-z0-9])\\d{1,2}x(\\d{1,4})(?:" + RANGE + "(?:\\d{1,2}x)?(\\d{1,4}))?(?![a-z0-9])");
    private static final Pattern SUFFIX = Pattern.compile(
            "(?<![a-zA-Z0-9])([0-9]{1,4})(?:" + RANGE + "([0-9]{1,4}))?\\s*[集话期回]", Pattern.CASE_INSENSITIVE);
    private static final List<Pattern> EXPLICIT = List.of(STANDARD, CHINESE, ENGLISH, CROSS, SUFFIX);
    private static final Pattern SEASON = Pattern.compile(
            "(?i)第\\s*" + NUMBER + "\\s*季|(?<![a-z0-9])(?:Season[ ._-]*|S)\\d{1,3}(?![a-z0-9])");
    private static final Pattern COLLECTION_COUNT = Pattern.compile(
            "(?:全|共|总计|总|更新至|更新到)\\s*(?:第\\s*)?" + NUMBER + "\\s*[集话期回](?:全|完)?|"
                    + NUMBER + "\\s*[集话期回]\\s*(?:全|完)");
    private static final Pattern EPISODE_PREFIX_TAIL = Pattern.compile("[-~～－—–至到第]\\s*$");
    private static final Pattern COLLECTION_LABEL = Pattern.compile("全集|合集|打包|COMPLETE", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXTENSION = Pattern.compile(
            "(?i)\\.(?:mp4|m4v|mkv|avi|rmvb|rm|ts|m2ts|mts|mov|flv|wmv|webm|mpg|mpeg|iso|3gp|vob|asf|f4v|ogv)\\s*$");
    private static final Pattern RESOLUTION = Pattern.compile(
            "(?i)(?:\\d{3,5}\\s*[x×*]\\s*\\d{3,5}|(?:4320|2160|1440|1080|720|576|480|360)[pi]|[248]k)(?![a-z0-9])");
    private static final Pattern CODEC = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:[hx][ ._-]?26[456]|HEVC|AVC|AV1|E[ ._-]?AC[ ._-]?3|AC[ ._-]?3)(?![a-z0-9])");
    private static final Pattern AUDIO = Pattern.compile(
            "(?i)(?<![a-z0-9])(?:DDP?|AAC|TRUEHD|DTS(?:[ ._-]?HD)?|LPCM|FLAC)[ ._-]*(?:\\d[ .]\\d|\\d(?:CH)?)(?![a-z0-9])");
    private static final Pattern MEASURE = Pattern.compile(
            "(?i)(?:\\d+(?:[.,]\\d+)?\\s*(?:fps|帧|bits?|[KMGT]i?B(?:/s)?|[KM]bps|k?Hz)|(?:fps|帧率)[ ._-]*\\d+(?:[.,]\\d+)?)(?![a-z0-9])");
    private static final Pattern DIGITS = Pattern.compile(
            "(?<![0-9A-Za-z])(?<!\\d[.,])(\\d{1,4})(?:" + RANGE
                    + "(\\d{1,4}))?(?=$|[\\s\\p{P}\\p{Z}]|全集|合集|[集话期回])(?![.,]\\d)");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");

    static String parse(String title, String seriesTitle) {
        String value = normalize(title);
        if (value.isBlank()) return null;
        // 「全24集 / 更新至24集」只描述合集数量，不能据此把文件当成第24集。
        value = stripSeriesPrefix(withoutCollectionCounts(value), seriesTitle);
        for (Pattern pattern : EXPLICIT) {
            Matcher explicit = pattern.matcher(value);
            while (explicit.find()) {
                String episode = episode(explicit.group(1), explicit.group(2));
                if (episode != null) return episode;
            }
        }
        Matcher season = SEASON.matcher(value);
        int afterSeason = 0;
        while (season.find()) afterSeason = season.end();
        value = value.substring(afterSeason);
        value = EXTENSION.matcher(value).replaceAll(" ");
        value = RESOLUTION.matcher(value).replaceAll(" ");
        value = CODEC.matcher(value).replaceAll(" ");
        value = AUDIO.matcher(value).replaceAll(" ");
        value = MEASURE.matcher(value).replaceAll(" ");
        Matcher digits = DIGITS.matcher(value);
        while (digits.find()) {
            String first = digits.group(1), last = digits.group(2);
            int start = number(first), end = last == null ? start : number(last);
            if (start >= 1900 && start <= 2099 || end >= 1900 && end <= 2099) continue;
            // 没有范围的「全集 / 打包」里的数字通常是数量或版本号，继续保持未识别。
            if (last == null && COLLECTION_LABEL.matcher(value).find()) continue;
            String episode = episode(first, last);
            if (episode != null) return episode;
        }
        return null;
    }

    static String withoutCollectionCounts(String value) {
        Matcher count = COLLECTION_COUNT.matcher(value);
        StringBuilder result = new StringBuilder();
        while (count.find()) {
            // 「第24集全 / 01-24集全」含明确集号，保留集号及范围端点。
            boolean rangeEnd = EPISODE_PREFIX_TAIL.matcher(value.substring(0, count.start())).find();
            count.appendReplacement(result, rangeEnd ? Matcher.quoteReplacement(count.group()) : " ");
        }
        count.appendTail(result);
        return result.toString();
    }

    private static String stripSeriesPrefix(String value, String seriesTitle) {
        String series = normalize(seriesTitle);
        if (series.isBlank()) return value;
        series = COLLECTION_LABEL.matcher(withoutCollectionCounts(series)).replaceAll(" ");
        series = SEASON.matcher(series).replaceAll(" ");
        Matcher year = YEAR.matcher(series);
        if (year.find() && year.start() > 0) series = year.replaceAll(" ");
        int[] name = series.codePoints().filter(Character::isLetterOrDigit).toArray();
        if (name.length < 2) return value;
        int matched = 0, offset = 0;
        while (offset < value.length() && matched < name.length) {
            int ch = value.codePointAt(offset);
            offset += Character.charCount(ch);
            if (!Character.isLetterOrDigit(ch)) continue;
            if (Character.toLowerCase(ch) != Character.toLowerCase(name[matched])) return value;
            matched++;
        }
        if (matched != name.length || offset < value.length() && Character.isDigit(name[name.length - 1])
                && Character.isDigit(value.codePointAt(offset))) return value;
        return value.substring(offset);
    }

    private static String normalize(String value) {
        return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replaceAll("[\\u200B-\\u200D\\uFEFF]", "").replace('\u00a0', ' ').trim();
    }

    private static String episode(String first, String last) {
        int start = number(first), end = last == null || last.isBlank() ? start : number(last);
        if (start < 1 || start > 9999 || end < start || end > 9999) return null;
        return start == end ? Integer.toString(start) : start + "-" + end;
    }

    private static int number(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            if (value.matches("[0-9]+")) return Integer.parseInt(value);
            String digits = "零一二三四五六七八九";
            if (!value.matches(".*[十百千].*")) {
                StringBuilder result = new StringBuilder();
                for (char ch : value.toCharArray()) {
                    int digit = digits.indexOf(ch == '两' ? '二' : ch == '〇' ? '零' : ch);
                    if (digit < 0) return 0;
                    result.append(digit);
                }
                return Integer.parseInt(result.toString());
            }
            int result = 0, digit = 0;
            for (char ch : value.toCharArray()) {
                int unit = switch (ch) { case '十' -> 10; case '百' -> 100; case '千' -> 1000; default -> 0; };
                if (unit > 0) { result += Math.max(digit, 1) * unit; digit = 0; }
                else {
                    digit = digits.indexOf(ch == '两' ? '二' : ch == '〇' ? '零' : ch);
                    if (digit < 0) return 0;
                }
            }
            return result + digit;
        } catch (NumberFormatException ignored) { return 0; }
    }
}
