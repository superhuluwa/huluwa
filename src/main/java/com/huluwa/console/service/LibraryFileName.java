package com.huluwa.console.service;

import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 作品、季度与集号分别识别；归类键不含季度，也不改动磁盘文件名。 */
final class LibraryFileName {
    record Identity(String title, String showKey, int season, boolean seasonExplicit,
                    int episode, int episodeEnd, boolean seriesRecognized) { }
    private static final String NUMBER = "[0-9零〇一二两三四五六七八九十百千]+";
    private static final Pattern STANDARD = Pattern.compile("(?i)(?<![a-z0-9])S(\\d{1,3})[ ._-]*E(\\d{1,4})(?!\\d)");
    private static final Pattern CHINESE_EPISODE = Pattern.compile(
            "第\\s*(" + NUMBER + ")(?:\\s*[-~～至到]\\s*(" + NUMBER + "))?\\s*[集话期]");
    private static final Pattern EXPLICIT_EPISODE = Pattern.compile("(?i)(?<![a-z0-9])EP?[ ._-]*(\\d{1,4})(?![a-z0-9])");
    private static final Pattern DOTTED = Pattern.compile("(?i)(?<![a-z0-9])S(\\d{1,3})[ ._-]+(\\d{1,4})(?![a-z0-9])");
    private static final Pattern COMPACT = Pattern.compile("(?<=[\\p{IsHan}])(\\d{2,4})(?=$|[ ._\\-\\[【])");
    private static final Pattern BRACKETED = Pattern.compile("[\\[【]\\s*(\\d{1,4})(?:\\s*[-~～至到]\\s*(\\d{1,4}))?\\s*[\\]】]");
    private static final Pattern SEPARATED = Pattern.compile("(?:^|[ ._-])(\\d{1,4})(?=$|[ ._\\-\\[\\]【】()（）])");
    private static final Pattern RANGE_END = Pattern.compile("(?i)^[ ._]*[-~～至到][ ._]*(?:EP?|第)?\\s*(" + NUMBER + ")(?![a-z0-9])");
    private static final Pattern CHINESE_SEASON = Pattern.compile("第\\s*(" + NUMBER + ")\\s*季");
    private static final Pattern ENGLISH_SEASON = Pattern.compile("(?i)(?<![a-z0-9])(?:Season[ ._-]*|S)(\\d{1,3})(?!\\d)");
    private static final Pattern NOISE = Pattern.compile(
            "(?i)(?:^|[\\s\\p{P}]|(?<=[\\p{IsHan}]))(?:4320p|2160p|1440p|1080[pi]|720p|576p|480p|[48]k|web[ ._-]?(?:dl|rip)|blu[ ._-]?ray|bd(?:rip|remux)|remux|hdtv|[hx][ ._-]?26[45]|hevc|avc|av1|hdr(?:10\\+?)?|dovi|dv|dolby[ ._-]*vision|杜比视界|aac|ddp?(?:[ .]?\\d)?|atmos|[\\d]+bit|[\\d]+fps)(?=$|[^a-z0-9]).*");
    private static final Pattern GENERIC = Pattern.compile(
            "(?i)^(?:第?" + NUMBER + "[集话期]?|EP?\\d+|S\\d+(?:E\\d+)?|Season\\s*\\d+|video|sample|movie|index|master|playlist|part|file|下载|迅雷下载|新建文件夹|电视剧|剧集|国产剧|国剧|美剧|韩剧|日剧|港台剧|动漫|动画|电影|movies?|media|completed|downloads?|videos?|tv[ ._-]*shows?)$");
    private static final Pattern YEAR = Pattern.compile("(?<!\\d)(?:19|20)\\d{2}(?!\\d)");
    private static final Pattern LEADING_NOISE = Pattern.compile(
            "(?i)^[\\[【(（][^\\]】)）]*(?:www\\.|https?://|\\.(?:com|net|org|cn)|字幕|无水印|高清|发布|搬运|影视|资源|1080|2160|4k)[^\\]】)）]*[\\]】)）][ ._-]*");

    static Identity parse(Path root, Path file) {
        String fileName = file.getFileName().toString();
        String base = stripLeadingNoise(fileName.substring(0, fileName.lastIndexOf('.')));
        Integer season = seasonOf(base);
        int episode = 0, end = 0, titleEnd = base.length();
        Matcher match = STANDARD.matcher(base);
        if (match.find()) {
            season = positive(number(match.group(1)));
            episode = number(match.group(2)); titleEnd = match.start();
            end = rangeEnd(base, match.end(), episode);
        } else {
            match = CHINESE_EPISODE.matcher(base);
            if (match.find()) {
                episode = number(match.group(1)); titleEnd = match.start();
                end = match.group(2) == null ? rangeEnd(base, match.end(), episode) : number(match.group(2));
            } else {
                match = EXPLICIT_EPISODE.matcher(base);
                if (match.find()) {
                    episode = number(match.group(1)); titleEnd = match.start();
                    end = rangeEnd(base, match.end(), episode);
                } else {
                    match = DOTTED.matcher(base);
                    if (match.find()) {
                        season = positive(number(match.group(1)));
                        episode = number(match.group(2)); titleEnd = match.start();
                        end = rangeEnd(base, match.end(), episode);
                    } else {
                        match = BRACKETED.matcher(base);
                        if (match.find() && plausibleEpisode(number(match.group(1)))) {
                            episode = number(match.group(1)); titleEnd = match.start();
                            end = match.group(2) == null ? episode : number(match.group(2));
                        } else {
                            match = SEPARATED.matcher(base);
                            while (match.find()) {
                                int value = number(match.group(1));
                                if (!plausibleEpisode(value) || seasonNumber(base, match.start(1))) continue;
                                String prefix = base.substring(0, match.start(1));
                                // 清晰度后紧跟裸集号可以识别；音轨声道、码率等后面的数字不能当集号。
                                if (NOISE.matcher(prefix).find() && !prefix.matches(
                                        "(?i).*(?:2160p|1080p|720p|480p|4k|8k|web[ ._-]?dl|bluray|hdtv)[ ._-]*")) continue;
                                // 标题中的独立数字保留；裸集号允许借助父目录归类。
                                if (match.start() == 0 && match.end() < base.length()
                                        && !base.substring(match.end()).matches("[ ._]*(?:[-~～]\\d+)?[ ._]*")) continue;
                                episode = value; titleEnd = match.start();
                                end = rangeEnd(base, match.end(), episode);
                                break;
                            }
                        }
                    }
                }
            }
        }
        if (episode == 0) {
            Matcher compact = COMPACT.matcher(base);
            if (compact.find() && plausibleEpisode(number(compact.group(1)))) {
                episode = number(compact.group(1)); titleEnd = compact.start();
                end = rangeEnd(base, compact.end(), episode);
            }
        }
        String title = clean(base.substring(0, titleEnd));
        String parentTitle = "";
        for (Path parent = file.getParent(); parent != null && parent.startsWith(root) && !parent.equals(root); parent = parent.getParent()) {
            String raw = parent.getFileName().toString();
            if (season == null) season = seasonOf(raw);
            String candidate = clean(raw);
            if (parentTitle.isBlank() && usefulTitle(candidate)) parentTitle = candidate;
        }
        if (!usefulTitle(title) || !title.matches(".*\\p{IsHan}.*")
                && parentTitle.matches(".*\\p{IsHan}.*")) title = parentTitle;
        // 同名翻拍保留父目录给出的年份，避免两个版本因清理文件名而被合并。
        if (!parentTitle.isBlank() && !YEAR.matcher(title).find() && YEAR.matcher(parentTitle).find()
                && key(title).equals(key(YEAR.matcher(parentTitle).replaceAll("")))) title = parentTitle;
        boolean recognized = usefulTitle(title);
        if (!recognized) title = "待归类";
        if (episode <= 0 || episode > 9999) episode = 0;
        end = end >= episode && end <= 9999 ? end : episode;
        return new Identity(title, recognized ? "show:" + key(title) : "unclassified",
                season == null ? 1 : season, season != null, episode, end, recognized);
    }

    private static boolean seasonNumber(String value, int offset) {
        Matcher season = ENGLISH_SEASON.matcher(value);
        while (season.find()) if (offset >= season.start(1) && offset < season.end(1)) return true;
        return false;
    }

    private static int rangeEnd(String base, int offset, int episode) {
        Matcher range = RANGE_END.matcher(base.substring(offset));
        return range.find() ? number(range.group(1)) : episode;
    }

    private static boolean plausibleEpisode(int value) {
        return value > 0 && value <= 999 && !List.of(264, 265, 480, 576, 720).contains(value);
    }

    private static String stripLeadingNoise(String value) {
        String result = value;
        while (true) {
            String next = LEADING_NOISE.matcher(result).replaceFirst("");
            if (next.equals(result)) return result;
            result = next;
        }
    }

    private static String clean(String value) {
        String result = stripLeadingNoise(value).trim();
        // 目录也可能是「剧名.01-14.1080p」，不能将分集范围保留在外层剧名中。
        int boundary = result.length();
        for (Pattern marker : List.of(STANDARD, CHINESE_EPISODE, EXPLICIT_EPISODE, DOTTED, BRACKETED, COMPACT)) {
            Matcher episode = marker.matcher(result);
            while (episode.find()) {
                if ((marker == BRACKETED || marker == COMPACT) && !plausibleEpisode(number(episode.group(1)))) continue;
                boundary = Math.min(boundary, episode.start());
                break;
            }
        }
        Matcher separated = SEPARATED.matcher(result);
        while (separated.find()) {
            int candidate = number(separated.group(1));
            if (plausibleEpisode(candidate) && !seasonNumber(result, separated.start(1))) {
                boundary = Math.min(boundary, separated.start());
                break;
            }
        }
        result = NOISE.matcher(result.substring(0, boundary)).replaceFirst("");
        result = result.replaceAll("[ ._\\-\\[【(（]*(?:全\\s*\\d+\\s*集|全集|合集|完结|国语|中字|中文字幕)[\\]】)） ._-]*$", "");
        Matcher season = CHINESE_SEASON.matcher(result);
        if (season.find()) result = result.substring(0, season.start());
        season = ENGLISH_SEASON.matcher(result);
        if (season.find()) result = result.substring(0, season.start());
        result = result.replaceAll("[._\\[\\]【】]+", " ")
                .replaceAll("^[\\s-]+|[\\s(（-]+$", "")
                .replaceAll("\\s+", " ").trim();
        if ((result.startsWith("(") && result.endsWith(")"))
                || (result.startsWith("（") && result.endsWith("）"))) result = result.substring(1, result.length() - 1).trim();
        // 常见「中文剧名.English.Alias」用中文主标题归类，数字和年份仍保留。
        if (result.matches("^[\\p{IsHan}0-9·：: ]+ [A-Za-z].*")) result = result.replaceFirst(" [A-Za-z].*$", "").trim();
        return result;
    }

    private static boolean usefulTitle(String value) {
        return !value.isBlank() && !GENERIC.matcher(value).matches()
                && !value.matches("(?i)(?:[a-f0-9]{16,}|[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}|(?:video|part|file|download)[ _-]*[0-9]+)");
    }
    private static String key(String title) {
        return Normalizer.normalize(title, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{P}\\p{Z}]+", "");
    }
    private static Integer seasonOf(String value) {
        Matcher match = CHINESE_SEASON.matcher(value);
        if (match.find()) return positive(number(match.group(1)));
        match = ENGLISH_SEASON.matcher(value);
        return match.find() ? positive(number(match.group(1))) : null;
    }
    private static Integer positive(int value) { return value > 0 && value <= 999 ? value : null; }
    private static int number(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            if (value.matches("[0-9]+")) return Integer.parseInt(value);
            if (value.matches("[零〇一二两三四五六七八九]+")) {
                StringBuilder digits = new StringBuilder();
                for (char ch : value.toCharArray()) digits.append("零一二三四五六七八九".indexOf(ch == '两' ? '二' : ch == '〇' ? '零' : ch));
                return Integer.parseInt(digits.toString());
            }
            int result = 0, digit = 0;
            for (char ch : value.toCharArray()) {
                int unit = switch (ch) { case '十' -> 10; case '百' -> 100; case '千' -> 1000; default -> 0; };
                if (unit > 0) { result += (digit == 0 ? 1 : digit) * unit; digit = 0; }
                else digit = "零一二三四五六七八九".indexOf(ch == '两' ? '二' : ch == '〇' ? '零' : ch);
            }
            return result + Math.max(digit, 0);
        } catch (NumberFormatException ignored) { return 0; }
    }
}
