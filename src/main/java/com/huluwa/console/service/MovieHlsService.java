package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

/** 电影港新版的在线视频：UTF-8 搜索，详情页列分集，按需解析播放页里的 m3u8。 */
@Service
public class MovieHlsService {
    public static final String BASE_URL = "https://www.66vod.com";
    public static final String SOURCE = "movie-hls";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36";
    private static final Pattern ANCHOR = Pattern.compile("<a\\b([^>]*)>([\\s\\S]*?)</a>", Pattern.CASE_INSENSITIVE);
    private static final Pattern IMAGE = Pattern.compile("<img\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern DETAIL_PATH = Pattern.compile("/(?:dy/[a-z]+|dsj/[a-z]+|dongman|duanju|zyjm)/[0-9]{1,18}\\.html");
    private static final Pattern PLAY_PATH = Pattern.compile("(?:^|&)pathid([24])=([0-9]+)(?:&|$)");
    private static final Pattern SCRIPT = Pattern.compile("<script\\b[^>]*>([\\s\\S]*?)</script>", Pattern.CASE_INSENSITIVE);
    private static final Pattern M3U8 = Pattern.compile("https?://[^\\s\"'<>]*?\\.m3u8(?:\\?[^\\s\"'<>]*?)?(?=[\"'<>\\s]|->|$)", Pattern.CASE_INSENSITIVE);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public List<MovieSearchService.MovieItem> search(String keyword) {
        String text = keyword == null ? "" : keyword.trim();
        if (text.isBlank()) return List.of();
        if (text.startsWith("https://") || text.startsWith("http://")) {
            String url = detailUrl(text);
            return List.of(movie(url, get(url)));
        }
        // 新版表单与老版不同：使用 UTF-8，并且不能传老版的 tbname=article。
        String body = "tempid=1&show=title%2Czhuyan&keyboard=" + URLEncoder.encode(text, StandardCharsets.UTF_8);
        HttpRequest request = builder(BASE_URL + "/e/search/index.php")
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        String html = request(request);
        Map<String, MovieSearchService.MovieItem> results = new LinkedHashMap<>();
        var anchors = ANCHOR.matcher(html);
        while (anchors.find()) {
            String attrs = anchors.group(1);
            if (!attribute(attrs, "class").contains("link-hover")) continue;
            String href = attribute(attrs, "href");
            if (!isDetailUrl(href)) continue;
            String url = detailUrl(href);
            String title = attribute(attrs, "title");
            if (title.isBlank()) title = clean(anchors.group(2));
            if (title.isBlank()) continue;
            results.putIfAbsent(url, new MovieSearchService.MovieItem(MovieSearchService.idFrom(url), title,
                    "", "", "", cover(anchors.group(2)), url));
        }
        if (results.isEmpty() && !html.contains("没有搜索到") && !html.contains("没有找到")) {
            throw new BusinessException(502, "电影港未返回搜索结果，请稍后重试或粘贴新版详情页链接");
        }
        return new ArrayList<>(results.values());
    }

    /** 老站与手机站不共用作品编号，按片名匹配唯一手机详情页。 */
    public String websiteUrl(String title, String value) {
        if (isDetailUrl(value)) return detailUrl(value);
        String name = websiteSearchTitle(HlsSupport.required(title, "影片名称"));
        String key = websiteTitleKey(name);
        List<MovieSearchService.MovieItem> matches = search(name).stream()
                .filter(item -> websiteTitleKey(item.title()).equals(key)).toList();
        if (matches.size() == 1) return matches.getFirst().url();
        throw new BusinessException(400, matches.isEmpty()
                ? "没有找到该影片对应的电影港手机页面，可在电影港会话中搜索"
                : "电影港手机站有多个同名影片，请在电影港会话中选择对应结果");
    }

    static String websiteSearchTitle(String title) {
        return title.replaceAll("[\\[（(【]\\s*(?:全集|完结|更新(?:至|到)\\s*\\d+\\s*集)\\s*[\\]）)】]", "").trim();
    }

    static String websiteTitleKey(String title) {
        return websiteSearchTitle(title).replaceAll("[\\p{P}\\p{Z}\\s]", "").toLowerCase(Locale.ROOT);
    }

    public Map<String, Object> detail(String value) {
        String url = detailUrl(value);
        String html = get(url);
        Map<String, Object> result = payload(movie(url, html));
        // 站点列表一、三是 iframe 网页；列表二、四是 CKPlayer 的 m3u8 线路。
        // 此处保留分集播放页，用户操作某一集时再解析，长篇动漫也只需一次详情页请求。
        Map<String, Map<String, DyttSearchService.PlayItem>> groups = new LinkedHashMap<>();
        var anchors = ANCHOR.matcher(html);
        while (anchors.find()) {
            String href = attribute(anchors.group(1), "href");
            if (!href.contains("/e/DownSys/play/")) continue;
            var line = PLAY_PATH.matcher(href);
            if (!line.find()) continue;
            String player = playUrl(href);
            String title = attribute(anchors.group(1), "title");
            if (title.isBlank()) title = clean(anchors.group(2));
            String name = "线路" + line.group(1) + " · m3u8";
            groups.computeIfAbsent(name, ignored -> new LinkedHashMap<>()).putIfAbsent(player,
                    new DyttSearchService.PlayItem(title.isBlank() ? "正片" : title, player));
        }
        List<DyttSearchService.PlaySource> sources = new ArrayList<>();
        groups.forEach((name, items) -> sources.add(new DyttSearchService.PlaySource(name, items.size(), new ArrayList<>(items.values()))));
        result.put("playSources", sources);
        return result;
    }

    /** 只抓取本站播放页，直接读取 CKPlayer 配置；不把 iframe 页面当作视频直链返回。 */
    public String resolve(String value) {
        String player = playUrl(value);
        String html = get(player).replace("\\/", "/").replace("\\u0026", "&");
        var scripts = SCRIPT.matcher(html);
        while (scripts.find()) {
            var links = M3U8.matcher(scripts.group(1));
            if (links.find()) return HlsDownloadService.m3u8(MovieSearchService.unescape(links.group()));
        }
        throw new BusinessException(400, "该分集暂未解析到 m3u8 直链，请切换线路或稍后重试");
    }

    public Map<String, Object> payload(MovieSearchService.MovieItem item) {
        Map<String, Object> payload = MovieSearchService.toPayload(item);
        payload.put("source", SOURCE);
        return payload;
    }

    public String playUrl(String value) {
        URI uri = siteUri(value);
        if (!"/e/DownSys/play/".equals(uri.getPath()) || uri.getRawQuery() == null) {
            throw new BusinessException(400, "请使用电影港新版的分集播放链接");
        }
        Map<String, String> query = new LinkedHashMap<>();
        for (String part : uri.getRawQuery().split("&")) {
            if (!part.matches("(?:classid|id|pathid[24]|bf)=[0-9]{1,18}")) {
                throw new BusinessException(400, "电影港分集播放链接参数不正确");
            }
            String[] pair = part.split("=", 2);
            if (query.putIfAbsent(pair[0], pair[1]) != null) throw new BusinessException(400, "电影港分集参数重复");
        }
        String path = query.containsKey("pathid2") ? "pathid2" : "pathid4";
        if (query.size() != 4 || !query.containsKey("classid") || !query.containsKey("id")
                || !query.containsKey(path) || !("pathid2".equals(path) ? "1" : "3").equals(query.get("bf"))) {
            throw new BusinessException(400, "请选择电影港的 m3u8 线路（列表二或四）");
        }
        return BASE_URL + "/e/DownSys/play/?classid=" + query.get("classid") + "&id=" + query.get("id")
                + "&" + path + "=" + query.get(path) + "&bf=" + query.get("bf");
    }

    static String detailUrl(String value) {
        URI uri = siteUri(value);
        if (!DETAIL_PATH.matcher(uri.getPath()).matches() || uri.getQuery() != null) {
            throw new BusinessException(400, "请使用电影港新版（66vod.com）的影片详情页链接");
        }
        return BASE_URL + uri.getPath();
    }

    static boolean isDetailUrl(String value) {
        try { detailUrl(value); return true; }
        catch (BusinessException e) { return false; }
    }

    private static URI siteUri(String value) {
        try {
            URI uri = URI.create(BASE_URL + "/").resolve(MovieSearchService.unescape(value == null ? "" : value.trim()));
            if (!("www.66vod.com".equalsIgnoreCase(uri.getHost()) || "66vod.com".equalsIgnoreCase(uri.getHost()))
                    || !("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    || uri.getUserInfo() != null || uri.getPort() != -1 || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri;
        } catch (Exception e) { throw new BusinessException(400, "仅支持电影港新版（66vod.com）的页面链接"); }
    }

    private static MovieSearchService.MovieItem movie(String url, String html) {
        int start = html.indexOf("class=\"ct-l\"");
        String content = start < 0 ? html : html.substring(start);
        String flat = MovieSearchService.flatten(clean(content));
        String origin = MovieSearchService.fieldAfter(flat, "产地");
        if (origin.isBlank()) origin = MovieSearchService.fieldAfter(flat, "国家");
        return new MovieSearchService.MovieItem(MovieSearchService.idFrom(url), MovieSearchService.parseTitle(html),
                MovieSearchService.fieldAfter(flat, "年代"), origin,
                MovieSearchService.fieldAfter(flat, "类别"), cover(content), url);
    }

    private static String cover(String html) {
        var image = IMAGE.matcher(html);
        if (!image.find()) return "";
        String value = attribute(image.group(1), "data-original");
        if (value.isBlank()) value = attribute(image.group(1), "src");
        try { return HlsSupport.httpUrl(URI.create(BASE_URL).resolve(value).toString(), "封面"); }
        catch (RuntimeException e) { return ""; }
    }

    private static String attribute(String html, String name) {
        var match = Pattern.compile("(?<![\\w-])" + name + "\\s*=\\s*([\"'])([\\s\\S]*?)\\1", Pattern.CASE_INSENSITIVE).matcher(html);
        return match.find() ? MovieSearchService.unescape(match.group(2)).trim() : "";
    }

    private static String clean(String html) {
        return MovieSearchService.unescape(html.replaceAll("<[^>]+>", " ")).replaceAll("\\s+", " ").trim();
    }

    private HttpRequest.Builder builder(String url) {
        return HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT).header("Referer", BASE_URL + "/").header("Accept-Encoding", "gzip");
    }

    private String get(String url) { return request(builder(url).GET().build()); }

    private String request(HttpRequest request) {
        try {
            for (int redirects = 0; redirects < 5; redirects++) {
                HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() / 100 == 3) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new BusinessException(502, "电影港返回了无效跳转"));
                    URI target = siteUri(request.uri().resolve(location).toString());
                    request = builder(BASE_URL + target.getRawPath() + (target.getRawQuery() == null ? "" : "?" + target.getRawQuery())).GET().build();
                    continue;
                }
                if (response.statusCode() / 100 != 2) throw new BusinessException(502, "电影港返回 HTTP " + response.statusCode());
                byte[] body = response.body();
                // 部分页面在未请求 gzip 时也返回压缩内容，按魔数识别，避免把压缩字节误作中文。
                if (body.length >= 2 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b) {
                    try (var gzip = new GZIPInputStream(new ByteArrayInputStream(body))) { body = gzip.readAllBytes(); }
                }
                return new String(body, StandardCharsets.UTF_8);
            }
            throw new BusinessException(502, "电影港页面跳转过多，请稍后重试");
        } catch (BusinessException e) { throw e; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "电影港请求已取消");
        } catch (Exception e) { throw new BusinessException(502, "电影港请求失败，请稍后重试"); }
    }
}
