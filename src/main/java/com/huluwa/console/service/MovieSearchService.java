package com.huluwa.console.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 电影港（dygangs.net）站内搜索：对齐 quantum 参考工程的 dygangs_search.cs 搜索逻辑。
 *
 * 站点表单为 GB2312 编码，关键字必须按 GBK 逐字节百分号编码后再 POST；
 * 响应是 302 → /e/search/result/?searchid=NNN 的结果页，HttpClient 自动跟随（关闭时留手动兜底）。
 * 结果解析用正则实现（不引入 HTML 解析依赖），结构与站点模板一一对应：
 *   <a href="/dmq/20200727/45148.htm" class="classlinkclass">凡人修仙传</a>
 *   封面 = 同一条目内、该锚点之前最近的 <img src>；
 *   简介 = 该锚点之后最近的 <td valign="top">…（◎年代 / ◎产地 / ◎类别 用“◎标签”截取）。
 */
@Service
public class MovieSearchService {

    /** 站点根地址 */
    public static final String BASE_URL = "https://www.dygangs.net";
    private static final String SEARCH_URL = BASE_URL + "/e/search/index.php";
    /** 站点表单编码（GBK 兼容 GB2312；GB18030 的四字节区站点不认，故不用） */
    private static final Charset FORM_CHARSET = Charset.forName("GBK");
    /** 响应解码（GB18030 为 GB2312 超集，个别生僻字不丢） */
    private static final Charset PAGE_CHARSET = Charset.forName("GB18030");
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    /** 站点单页约 10-12 条结果，与参考脚本一致默认最多展示 10 条 */
    public static final int DEFAULT_MAX = 10;

    /** 搜索结果条目（封面 / 片名 / 年代 / 产地 / 类型 / 资源ID / 详情页） */
    public record MovieItem(String id, String title, String year, String origin, String genre,
                            String cover, String url) {
    }

    private static final Pattern ANCHOR = Pattern.compile(
            "<a\\b([^>]*\\bclass\\s*=\\s*[\"']?[^\"'>]*classlinkclass[^\"'>]*[\"']?[^>]*)>([\\s\\S]*?)</a>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HREF = Pattern.compile("\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMG = Pattern.compile("<img\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern INTRO_TD = Pattern.compile(
            "<td\\b[^>]*\\bvalign\\s*=\\s*[\"']?top[\"']?[^>]*>([\\s\\S]*?)</td>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DIGITS_SEGMENT = Pattern.compile("([0-9]+)(?:\\.htm[l]?)?$", Pattern.CASE_INSENSITIVE);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /**
     * 搜索关键字并返回解析后的条目。
     *
     * @param keyword 搜索关键字（片名等，非空）
     * @return 站点结果页解析出的全部条目（单页，通常 10-20 条），无结果返回空列表
     * @throws MovieSearchException 网络异常 / 站点结构异常时抛出，由调用方转成聊天回复
     */
    public List<MovieItem> search(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }

        String body = "tempid=1&tbname=article&keyboard=" + percentEncodeGb(trimmed)
                + "&show=title%2Csmalltext";
        HttpResponse<byte[]> response;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SEARCH_URL))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.US_ASCII))
                    .build();
            response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new MovieSearchException("搜索请求失败：" + e.getMessage(), e);
        }

        String html = decode(response.body());
        // 站点正常返回 302 结果页（HttpClient 已自动跟随）；若追踪被关闭做一次手动跟随兜底
        if (!html.contains("classlinkclass") && response.headers().firstValue("Location").isPresent()) {
            String location = response.headers().firstValue("Location").get();
            String absolute = location.startsWith("http") ? location : BASE_URL + (location.startsWith("/") ? "" : "/") + location;
            html = decode(fetch(absolute));
        }

        return parse(html);
    }

    private byte[] fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();
            return http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body();
        } catch (Exception e) {
            throw new MovieSearchException("搜索请求失败：" + e.getMessage(), e);
        }
    }

    private static String decode(byte[] raw) {
        return new String(raw == null ? new byte[0] : raw, PAGE_CHARSET);
    }

    /** 搜索失败（网络 / 站点结构），调用方转成友好聊天回复 */
    public static class MovieSearchException extends RuntimeException {
        public MovieSearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 解析搜索结果页。可见性为包内（供单测直接调用）。
     * 逐条锚点切块：块起点 = 上一条锚点结束位置（避免把上一部的封面算到本条），
     * 封面取 [块起点, 锚点起点) 内最后一个 img，简介取 [锚点结束, 下一条锚点起点) 内第一个 valign=top 的 td。
     */
    static List<MovieItem> parse(String html) {
        List<MovieItem> items = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return items;
        }
        List<int[]> spans = new ArrayList<>();
        List<String> hrefs = new ArrayList<>();
        List<String> titles = new ArrayList<>();
        Matcher anchor = ANCHOR.matcher(html);
        while (anchor.find()) {
            Matcher href = HREF.matcher(anchor.group(1));
            String url = href.find() ? href.group(1).trim() : "";
            if (idFrom(url) == null) {
                continue;
            }
            spans.add(new int[]{anchor.start(), anchor.end()});
            hrefs.add(url);
            titles.add(unescape(stripTags(anchor.group(2))).trim());
        }
        for (int i = 0; i < spans.size(); i++) {
            int anchorStart = spans.get(i)[0];
            int anchorEnd = spans.get(i)[1];
            int blockStart = i == 0 ? 0 : spans.get(i - 1)[1];
            int blockEnd = i + 1 < spans.size() ? spans.get(i + 1)[0] : html.length();

            String cover = nearestImage(html.substring(blockStart, anchorStart));
            String intro = firstIntro(html.substring(anchorEnd, blockEnd));
            String flat = flatten(intro);
            String url = hrefs.get(i);
            items.add(new MovieItem(
                    idFrom(url),
                    titles.get(i),
                    fieldAfter(flat, "年代"),
                    fieldAfter(flat, "产地"),
                    fieldAfter(flat, "类别"),
                    cover == null ? null : absoluteUrl(cover),
                    absoluteUrl(url)));
        }
        return items;
    }

    /** 块内最后一个 img 视为本条封面（模板里封面单元格紧跟条目锚点之前） */
    private static String nearestImage(String block) {
        Matcher matcher = IMG.matcher(block);
        String found = null;
        while (matcher.find()) {
            found = matcher.group(1).trim();
        }
        return found == null || found.isEmpty() ? null : found;
    }

    /** 块内第一个 valign=top 的 td 为简介单元格 */
    private static String firstIntro(String block) {
        Matcher matcher = INTRO_TD.matcher(block);
        if (!matcher.find()) {
            return "";
        }
        String inner = matcher.group(1);
        // <br /> 转换为换行，便于 flatten 后按 ◎ 标签截取
        inner = inner.replaceAll("(?i)<br\\s*/?>", "\n");
        return unescape(stripTags(inner));
    }

    /** 详情页 URL 文件名即资源ID（…/20200727/45148.htm → 45148），非纯数字返回 null */
    static String idFrom(String href) {
        if (href == null || href.isEmpty()) {
            return null;
        }
        int slash = href.lastIndexOf('/');
        String segment = slash < 0 ? href : href.substring(slash + 1);
        Matcher matcher = DIGITS_SEGMENT.matcher(segment);
        return matcher.find() ? matcher.group(1) : null;
    }

    static String absoluteUrl(String href) {
        if (href == null || href.isEmpty()) {
            return href;
        }
        if (href.startsWith("http://") || href.startsWith("https://")) {
            return href;
        }
        return BASE_URL + (href.startsWith("/") ? href : "/" + href);
    }

    /** 剔除全部空白（含全角空格 U+3000），便于按「◎标签」稳定截取字段（与参考脚本一致） */
    static String flatten(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch) && ch != '\u3000') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** 取「◎标签」后直到下一个「◎」的值；标签先经 flatten 去空白（◎年　代 → ◎年代） */
    static String fieldAfter(String flat, String label) {
        if (flat == null || flat.isEmpty()) {
            return "";
        }
        int start = flat.indexOf("◎" + label);
        if (start < 0) {
            return "";
        }
        start += label.length() + 1;
        int end = flat.indexOf('◎', start);
        return (end < 0 ? flat.substring(start) : flat.substring(start, end)).trim();
    }

    private static String stripTags(String html) {
        return html == null ? "" : TAG.matcher(html).replaceAll("");
    }

    /** 最小 HTML 实体还原（&amp; 等命名实体 + 十进制/十六进制数字实体） */
    static String unescape(String text) {
        if (text == null || text.indexOf('&') < 0) {
            return text == null ? "" : text;
        }
        Matcher matcher = ENTITY.matcher(text);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String body = matcher.group(1);
            String replaced;
            if (body.startsWith("#")) {
                try {
                    int code = body.startsWith("#x") || body.startsWith("#X")
                            ? Integer.parseInt(body.substring(2), 16)
                            : Integer.parseInt(body.substring(1));
                    replaced = new String(Character.toChars(code));
                } catch (RuntimeException e) {
                    replaced = matcher.group(0);
                }
            } else {
                replaced = switch (body.toLowerCase()) {
                    case "amp" -> "&";
                    case "lt" -> "<";
                    case "gt" -> ">";
                    case "quot" -> "\"";
                    case "apos" -> "'";
                    case "nbsp" -> " ";
                    default -> matcher.group(0);
                };
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replaced));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 关键字按 GBK 逐字节百分号编码（站点表单为 GB2312，UTF-8 直接提交搜不到） */
    static String percentEncodeGb(String text) {
        StringBuilder sb = new StringBuilder();
        for (byte b : text.getBytes(FORM_CHARSET)) {
            sb.append('%').append(String.format("%02X", b & 0xFF));
        }
        return sb.toString();
    }

    /** 供搜索回复组装：条目 → 前端 MovieCard 载荷 */
    public static Map<String, Object> toPayload(MovieItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", item.id());
        payload.put("title", item.title());
        payload.put("year", item.year());
        payload.put("origin", item.origin());
        payload.put("genre", item.genre());
        payload.put("cover", item.cover());
        payload.put("url", item.url());
        return payload;
    }
}
