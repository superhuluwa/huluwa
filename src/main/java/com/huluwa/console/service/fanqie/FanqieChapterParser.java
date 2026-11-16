package com.huluwa.console.service.fanqie;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 兼容缩进标题，以及把整篇短篇正文中的连续章节还原为目录。 */
public final class FanqieChapterParser {
    private static final Pattern LINE = Pattern.compile("[^\\r\\n]+(?:\\r\\n?|\\n|$)");
    private static final Pattern HEADING = Pattern.compile(
            "第\\h*([0-9０-９零〇一二两三四五六七八九十百千]{1,12})\\h*[章节回].{0,60}");

    private FanqieChapterParser() { }

    public record Part(String title, String content) { }
    private record Heading(String title, int start, int end) { }

    /** trim/strip 无法覆盖所有不换行空格和零宽字符；只清理边缘，不改变标题正文。 */
    public static String normalizeTitle(String title) {
        return stripEdges(FanqieClient.decodeHtmlEntities(title));
    }

    /**
     * 只有从第一章开始、至少两章且章号连续时才拆分，减少把正文句子误当标题的情况。
     * 无法确认章节结构时返回空列表，由调用方保留上游原目录。
     */
    public static List<Part> split(String content) {
        if (content == null || content.isBlank()) return List.of();
        List<Heading> headings = new ArrayList<>();
        Matcher lines = LINE.matcher(content);
        while (lines.find()) {
            String title = normalizeTitle(lines.group());
            Matcher heading = HEADING.matcher(title);
            if (!heading.matches()) continue;
            if (number(heading.group(1)) != headings.size() + 1) return List.of();
            headings.add(new Heading(title, lines.start(), lines.end()));
        }
        if (headings.size() < 2) return List.of();

        List<Part> parts = new ArrayList<>(headings.size());
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            int end = i + 1 < headings.size() ? headings.get(i + 1).start() : content.length();
            String body = stripEdges(content.substring(heading.end(), end));
            if (body.isEmpty()) return List.of();
            // 序言或题记并入第一章，避免拆分后丢失标题前的内容。
            if (i == 0) {
                String preface = stripEdges(content.substring(0, heading.start()));
                if (!preface.isEmpty()) body = preface + "\n\n" + body;
            }
            parts.add(new Part(heading.title(), body));
        }
        return List.copyOf(parts);
    }

    private static int number(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFKC)
                .replace('〇', '零').replace('两', '二');
        if (normalized.chars().allMatch(c -> c >= '0' && c <= '9')) {
            try { return Integer.parseInt(normalized); }
            catch (NumberFormatException ignored) { return -1; }
        }
        String digits = "零一二三四五六七八九";
        int total = 0;
        int digit = 0;
        for (int i = 0; i < normalized.length(); i++) {
            char character = normalized.charAt(i);
            int unit = switch (character) {
                case '十' -> 10;
                case '百' -> 100;
                case '千' -> 1000;
                default -> 0;
            };
            if (unit > 0) {
                total += (digit == 0 ? 1 : digit) * unit;
                digit = 0;
            } else {
                int next = digits.indexOf(character);
                if (next < 0) return -1;
                digit = digit * 10 + next;
                if (digit > 9999) return -1;
            }
        }
        return total + digit;
    }

    private static String stripEdges(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && edgeSpace(value.charAt(start))) start++;
        while (end > start && edgeSpace(value.charAt(end - 1))) end--;
        return value.substring(start, end);
    }

    private static boolean edgeSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character)
                || character == '\u200B' || character == '\uFEFF';
    }
}
