package com.huluwa.console.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
 *
 * <p>订阅监听（对齐参考工程 dygangs.cs）：详情页 /dmq/yyyyMMdd/{id}.htm 里的「下载地址」表格逐行放
 * 「磁力：<a href="magnet:?...&amp;dn=...">剧集标题</a>」，订阅时需要把该页全部磁力抓下来做基线，
 * 之后每小时对比一次抓到的差异。注意 href 里的 & 被站点转义成 &amp;，&amp; 原样提交 qB 会丢 tracker，
 * 解析时必须还原 HTML 实体（与参考脚本 2026-09-30 的修复一致）。</p>
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
    /** 详情页磁力锚点：href=magnet:?xt=urn:btih:…，锚文本即剧集标题 */
    private static final Pattern MAGNET_ANCHOR = Pattern.compile(
            "<a\\b[^>]*\\bhref\\s*=\\s*[\"'](magnet:\\?[^\"']*)[\"'][^>]*>([\\s\\S]*?)</a>",
            Pattern.CASE_INSENSITIVE);
    /** 剧集里的清晰度标识：2160P / 4K / 1080P / 720P / 480P（含 1080i 等写法） */
    private static final Pattern QUALITY = Pattern.compile("(?i)(2160P|4K|1080[PI]|720[PI]|480[PI]|360[PI])");
    private static final Pattern RESOURCE_LABEL = Pattern.compile(
            "^(?=.*(?:下载|磁力|地址|链接|资源))(?:(?:点击|立即|直接|本地|高速|普通|备用|电信|联通|移动|磁力|迅雷|高清|下载|地址|链接|资源|线路|复制)|[\\s\\p{P}\\d])+$");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern ENTITY = Pattern.compile("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private final MovieDetailCache detailCache;

    public MovieSearchService() {
        this(60, 128);
    }

    @Autowired
    public MovieSearchService(@Value("${huluwa.movie.detail-cache-seconds:60}") long cacheSeconds,
                              @Value("${huluwa.movie.detail-cache-max-entries:128}") int cacheEntries) {
        detailCache = new MovieDetailCache(Duration.ofSeconds(Math.max(0, cacheSeconds)), cacheEntries);
    }

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
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() / 100 != 2) {
                throw new MovieSearchException("详情页请求返回 HTTP " + response.statusCode(), null);
            }
            return response.body();
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

    // ------------------------------------------------------------------ 详情页（订阅监听）

    /**
     * 详情页里的一条磁力。
     *
     * @param title  站点锚文本（剧集名，通常带上清晰度与集号）
     * @param magnet 磁力链接（HTML 实体已还原）
     * @param hash   info hash（小写；无法解析时为 null）
     * @param quality 清晰度标签（2160P / 1080P / 720P / 480P / 360P / OTHER）
     * @param episode 剧集键（集号或起止范围，如 1 / 1-14；无法识别时为 null）
     */
    public record MagnetItem(String title, String magnet, String hash, String quality, String episode) {
    }

    /**
     * 详情页一次抓取的结果：站点标题（用作订阅片名兜底）与全部磁力。
     *
     * @param title   详情页 <title> 首段（如「斗破苍穹 第5季」；解析失败为空串）
     * @param magnets 按页面顺序去重后的磁力
     */
    public record DetailPage(String title, List<MagnetItem> magnets) {
        public DetailPage {
            magnets = List.copyOf(magnets);
        }
    }

    /**
     * 抓取电影港详情页（剧集页）：标题 + 全部磁力，订阅基线与每小时巡检都用它。
     *
     * @param detailUrl 详情页绝对地址（搜索结果里的 url 字段）
     * @throws MovieSearchException 网络异常时抛出，调用方转成聊天回复 / 巡检错误记录
     */
    public DetailPage fetchDetail(String detailUrl) {
        return fetchDetail(detailUrl, false);
    }

    /** 资源目录后台刷新与订阅巡检可绕过已完成的短期缓存，共享正在进行的抓取。 */
    DetailPage fetchDetail(String detailUrl, boolean refresh) {
        String url = absoluteUrl(detailUrl == null ? "" : detailUrl.trim());
        if (url.isBlank() || "null".equals(url)) {
            return new DetailPage("", List.of());
        }
        return detailCache.get(url, refresh, () -> {
            String html = decode(fetch(url));
            return new DetailPage(parseTitle(html), parseMagnets(html));
        });
    }

    /** 巡检读取最新磁力：绕过已完成缓存，但与正在抓取同 URL 的请求合并。 */
    public List<MagnetItem> fetchMagnets(String detailUrl) {
        return fetchDetail(detailUrl, true).magnets();
    }

    /**
     * 详情页标题：取 <title> 第一段（站点写法「斗破苍穹 第5季_免费下载_动漫_电影港」）。
     * 解析失败返回空串，调用方回退到搜索结果里的片名。
     */
    static String parseTitle(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        Matcher matcher = Pattern.compile("<title[^>]*>([\\s\\S]*?)</title>", Pattern.CASE_INSENSITIVE).matcher(html);
        if (!matcher.find()) {
            return "";
        }
        String title = unescape(stripTags(matcher.group(1))).replace('\u3000', ' ').trim();
        int cut = title.indexOf('_');
        if (cut > 0) {
            title = title.substring(0, cut);
        }
        return title.trim();
    }

    /**
     * 解析详情页磁力（可见性为包内，供单测直接调用）。
     *
     * <p>只认 href 以 magnet:?xt=urn:btih: 开头的锚点，与原参考脚本一致；
     * 同一 info hash 只保留首个（站点常把同一集重复挂多条），并按页面顺序返回。</p>
     */
    static List<MagnetItem> parseMagnets(String html) {
        List<MagnetItem> list = new ArrayList<>();
        if (html == null || html.isEmpty()) {
            return list;
        }
        Set<String> seen = new LinkedHashSet<>();
        String seriesTitle = parseTitle(html);
        Matcher matcher = MAGNET_ANCHOR.matcher(html);
        while (matcher.find()) {
            String magnet = unescape(matcher.group(1).trim());
            if (!magnet.toLowerCase(Locale.ROOT).startsWith("magnet:?xt=urn:btih:")) {
                continue;
            }
            String hash = hashOf(magnet);
            String key = hash == null ? magnet : hash;
            if (!seen.add(key)) {
                continue;
            }
            String title = unescape(stripTags(matcher.group(2))).trim();
            if (title.isBlank() || RESOURCE_LABEL.matcher(title).matches()) {
                String displayName = magnetDisplayName(magnet);
                if (displayName != null) title = displayName;
            }
            list.add(new MagnetItem(title, magnet, hash, qualityOf(title, magnet), episodeOf(title, seriesTitle, magnet)));
        }
        return list;
    }

    /** 从磁力链接抽 info hash（btih 后 40 位十六进制；base32 形式返回原样小写） */
    static String hashOf(String magnet) {
        if (magnet == null) {
            return null;
        }
        Matcher matcher = Pattern.compile("(?i)btih:([A-Za-z0-9]{32,40})").matcher(magnet);
        return matcher.find() ? matcher.group(1).toLowerCase(Locale.ROOT) : null;
    }

    /** 标题里的清晰度标识（找不到返回 OTHER）；2160P / 4K 视为最高清晰度 */
    public static String qualityOf(String title) {
        if (title == null || title.isBlank()) {
            return "OTHER";
        }
        Matcher matcher = QUALITY.matcher(title);
        if (!matcher.find()) {
            return "OTHER";
        }
        String value = matcher.group(1).toUpperCase(Locale.ROOT);
        if (value.startsWith("4K") || value.startsWith("2160")) {
            return "2160P";
        }
        if (value.startsWith("1080")) {
            return "1080P";
        }
        if (value.startsWith("720")) {
            return "720P";
        }
        if (value.startsWith("480")) {
            return "480P";
        }
        return "360P";
    }

    /**
     * 清晰度排序权重：数值越大越清晰，供「同一集优先取最高清晰度」比较。
     * 2160P=4 > 1080P=3 > 720P=2 > 480P=1 > 360P=0 > OTHER=-1
     */
    public static int qualityRank(String quality) {
        if (quality == null) {
            return -1;
        }
        return switch (quality.toUpperCase(Locale.ROOT)) {
            case "2160P" -> 4;
            case "1080P" -> 3;
            case "720P" -> 2;
            case "480P" -> 1;
            case "360P" -> 0;
            default -> -1;
        };
    }

    /** 抽取集号或范围；显式季集、中文数字优先，未知与未标范围的合集返回 null。 */
    public static String episodeOf(String title) {
        return episodeOf(title, null);
    }

    /** 页面剧名作为上下文，防止把「三体2」等片名中的数字当成集号。 */
    public static String episodeOf(String title, String seriesTitle) {
        return MovieEpisodeParser.parse(title == null ? null : unescape(stripTags(title)), seriesTitle);
    }

    /** 锚文本未标集数时，用磁力 dn 中的真实文件名补充识别。 */
    public static String episodeOf(String title, String seriesTitle, String magnet) {
        String episode = title != null && RESOURCE_LABEL.matcher(title.trim()).matches() ? null : episodeOf(title, seriesTitle);
        return episode != null ? episode : episodeOf(magnetDisplayName(magnet), seriesTitle);
    }

    public static String qualityOf(String title, String magnet) {
        String quality = qualityOf(title);
        return "OTHER".equals(quality) ? qualityOf(magnetDisplayName(magnet)) : quality;
    }

    /** 磁力名称按查询参数解码，兼容 UTF-8 与旧站点的 GB18030，不读取 tracker 等字段。 */
    private static String magnetDisplayName(String magnet) {
        if (magnet == null || magnet.isBlank()) return null;
        String link = unescape(magnet);
        int query = link.indexOf('?');
        if (query < 0) return null;
        for (String parameter : link.substring(query + 1).split("&")) {
            int equals = parameter.indexOf('=');
            if (equals < 0 || !"dn".equalsIgnoreCase(parameter.substring(0, equals))) continue;
            try {
                String encoded = parameter.substring(equals + 1);
                String name = URLDecoder.decode(encoded, StandardCharsets.UTF_8);
                if (name.indexOf('\ufffd') >= 0) name = URLDecoder.decode(encoded, PAGE_CHARSET);
                return name.isBlank() ? null : name.trim();
            } catch (IllegalArgumentException ignored) { }
        }
        return null;
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
        String value = (end < 0 ? flat.substring(start) : flat.substring(start, end)).trim();
        // 站点模板带冒号（◎年　　代:　2026）：去空白后即为「:2026」，这里去掉前导冒号
        int cut = 0;
        while (cut < value.length() && (value.charAt(cut) == ':' || value.charAt(cut) == '：')) {
            cut++;
        }
        return value.substring(cut).trim();
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
