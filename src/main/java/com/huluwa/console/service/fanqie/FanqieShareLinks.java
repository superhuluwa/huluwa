package com.huluwa.console.service.fanqie;

import java.net.URI;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 番茄小说分享文本解析。
 *
 * <p>番茄 App 的「分享 → 复制链接」形如
 * 「推荐一部好书《书名》https://changdunovel.com/t/UV9Yi-XDcr0/」，
 * 说明文字会紧贴链接两端。这里只做纯文本处理：取出链接、书名号里的书名，
 * 以及链接里已经带有的书号；短链（{@code /t/xxxx}）需要跟随 302 才能取到
 * {@code book_id}，由 {@link com.huluwa.console.service.FanqieSearchService} 负责。</p>
 */
public final class FanqieShareLinks {

    /** 番茄官方域名后缀：分享短链、网页版、zlink 落地页。 */
    private static final List<String> HOSTS = List.of("changdunovel.com", "fanqienovel.com", "fqnovel.com");

    /** 口令里的链接，按 RFC 3986 合法字符集匹配，中文自然截断（与 VideoParseService 同规则）。 */
    private static final Pattern HTTP_URL = Pattern.compile(
            "https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);

    /** 链接尾部常见的中英文句读，识别主机和书号前先裁掉。 */
    private static final String TRAILING_PUNCTUATION = ".,;:!?、，。；：！？）】》”’";

    /** 剥掉链接后需要从首部裁掉的前导句读。 */
    private static final String LEADING_PUNCTUATION = ".,;:!?、，。；：！？";

    /** 书名号包裹的书名。 */
    private static final Pattern BOOK_TITLE = Pattern.compile("《([^《》]{1,60})》");

    /** 分享落地页的查询参数：book_id / content_id。 */
    private static final Pattern BOOK_ID_PARAM =
            Pattern.compile("[?&#](?:book_id|content_id)=(\\d{6,24})(?:[&#]|$)");

    /** 网页版书籍地址：fanqienovel.com/page/{书号}、/reader/{书号}、/book/{书号}。 */
    private static final Pattern BOOK_ID_PATH =
            Pattern.compile("/(?:page|reader|book)/(\\d{6,24})(?:[/?#]|$)");

    private FanqieShareLinks() {
    }

    /** 文本里是否含有番茄分享链接（其他站点的链接不触发番茄的分享分支）。 */
    public static boolean hasLink(String text) {
        return firstUrl(text) != null;
    }

    /** 取文本里第一条番茄链接；没有则返回 null。 */
    public static String firstUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = HTTP_URL.matcher(text);
        while (matcher.find()) {
            // 只裁掉尾部中文标点：落地页的 report_params 带着 URL 编码的查询串，必须原样保留
            String url = trimTrailingPunctuation(matcher.group());
            if (url.length() > "https://".length() + 3 && isFanqieHost(url)) {
                return url;
            }
        }
        return null;
    }

    /** 主机是否属于番茄官方域名（按后缀匹配，避免命中 evil-fanqienovel.com 之类的伪装域名）。 */
    public static boolean isFanqieHost(String url) {
        String host = hostOf(url);
        if (host.isEmpty()) {
            return false;
        }
        for (String suffix : HOSTS) {
            if (host.equals(suffix) || host.endsWith("." + suffix)) {
                return true;
            }
        }
        return false;
    }

    /** 从链接里取书号：优先查询参数 book_id / content_id，其次网页版路径 /page/{书号}。 */
    public static String bookIdIn(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String value = trimTrailingPunctuation(url.trim());
        Matcher param = BOOK_ID_PARAM.matcher(value);
        if (param.find()) {
            return param.group(1);
        }
        Matcher path = BOOK_ID_PATH.matcher(value);
        if (path.find()) {
            return path.group(1);
        }
        return null;
    }

    /** 取第一个《书名》里的书名；没有书名号时返回 null。 */
    public static String sharedTitle(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = BOOK_TITLE.matcher(text);
        if (!matcher.find()) {
            return null;
        }
        String title = matcher.group(1).trim();
        return title.isEmpty() ? null : title;
    }

    /**
     * 去掉文本里的番茄链接，只留下可用于搜索的文字。
     *
     * <p>分享口令往往「说明文字 + 链接」连成一段，整段塞进搜索既超长又搜不到；
     * 这里剥掉链接后把空白压成单空格，并裁掉首尾标点。</p>
     */
    public static String stripLinks(String text) {
        if (text == null) {
            return "";
        }
        String stripped = HTTP_URL.matcher(text)
                .replaceAll(match -> isFanqieHost(match.group()) ? " " : match.group())
                .replaceAll("\\s+", " ")
                .trim();
        // 只裁句读、不动成对的书名号/括号，避免留下孤零零的「》」
        int start = 0;
        int end = stripped.length();
        while (start < end && LEADING_PUNCTUATION.indexOf(stripped.charAt(start)) >= 0) {
            start++;
        }
        while (end > start && TRAILING_PUNCTUATION.indexOf(stripped.charAt(end - 1)) >= 0) {
            end--;
        }
        return stripped.substring(start, end).trim();
    }

    /** 裁掉链接尾部的中英文句读。 */
    private static String trimTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
    }

    private static String hostOf(String url) {
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase();
        } catch (IllegalArgumentException error) {
            return "";
        }
    }
}
