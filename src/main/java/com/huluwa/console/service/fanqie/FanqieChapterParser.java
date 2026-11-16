package com.huluwa.console.service.fanqie;

import java.util.regex.Pattern;

/** 仅规范章节标题，不从标题序号推断目录，也不拆分或裁剪章节正文。 */
public final class FanqieChapterParser {
    private static final String TITLE_SPACE = "[\\h\\u200B\\uFEFF]*";
    private static final Pattern CHAPTER_PREFIX = Pattern.compile(
            "^第" + TITLE_SPACE + "([0-9０-９零〇一二两三四五六七八九十百千万]+)"
                    + TITLE_SPACE + "([章节回])");

    private FanqieChapterParser() { }

    /** 兼容首尾缩进和「第 5章」「第487 章」，保留副标题中的空格与原始章号。 */
    public static String normalizeTitle(String title) {
        String value = FanqieClient.decodeHtmlEntities(title);
        int start = 0;
        int end = value.length();
        while (start < end && edgeSpace(value.charAt(start))) start++;
        while (end > start && edgeSpace(value.charAt(end - 1))) end--;
        return CHAPTER_PREFIX.matcher(value.substring(start, end)).replaceFirst("第$1$2");
    }

    private static boolean edgeSpace(char character) {
        return Character.isWhitespace(character) || Character.isSpaceChar(character)
                || character == '\u200B' || character == '\uFEFF';
    }
}
