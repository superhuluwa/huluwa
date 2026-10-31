package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** BT影视公开页面适配：搜索 → 作品资源目录 → 下载页磁力，持久化缓存交由 BtbtlaCacheService。 */
@Service
public class BtbtlaSearchService {
    public static final String BASE_URL = "https://www.btbtla.com";
    public static final int DEFAULT_MAX = 10;
    private static final int MAX_PAGE_BYTES = 4 * 1024 * 1024;
    private static final Pattern ANCHOR = pattern("<a\\b([^>]*)>(.*?)</a>");
    private static final Pattern ITEM = pattern("<div\\b[^>]*class=[\"']module-item[\"'][^>]*>");
    private static final Pattern DETAIL_PATH = Pattern.compile("/detail/(\\d{1,15})\\.html");
    private static final Pattern DOWNLOAD_PATH = Pattern.compile("/tdown/(\\d{1,15})\\.html");
    private static final Pattern RESOLUTION = Pattern.compile("(?i)(?<!\\d)(4320|2160|1440|1080|720|576|480|360)[pi]\\b");
    private static final Pattern CN_EPISODE = Pattern.compile("第\\s*(\\d{1,4})(?:\\s*集)?(?:\\s*[-~～－—–至到]\\s*(?:第\\s*)?(\\d{1,4}))?\\s*集");
    private static final Pattern EN_EPISODE = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,4})(?:\\s*[-~～－—–至到]\\s*(?:S\\d{1,2})?E?(\\d{1,4}))?(?!\\d)");
    private static final Pattern EP_EPISODE = Pattern.compile("(?i)\\bEP?(\\d{1,4})(?:\\s*[-~～－—–至到]\\s*E?P?(\\d{1,4}))?\\b");
    private static final Pattern MAGNET = Pattern.compile("(?i)magnet:\\?xt=urn:btih:([a-f0-9]{40}|[a-z2-7]{32})(?![a-z0-9])(?:&[^\\s\"'<>]*)?");
    private static final Pattern COLLECTION = Pattern.compile("(?i)全集|合集|打包|整季|全\\s*[0-9一二三四五六七八九十百]+\\s*集|\\b(?:complete[ ._-]*(?:series|season)|full[ ._-]*season|season[ ._-]*pack)\\b");
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    public record SearchItem(String id, String title, String cover, String year, String genre, String origin,
                             String pageUrl) { }
    public record Resource(String id, String title, String size, String pageUrl, int resolution, String quality,
                           int season, int episodeStart, int episodeEnd, String episodeLabel, List<String> tags, boolean collection) { }
    public record QualityGroup(String quality, int resolution, List<Resource> items) { }
    public record Detail(String id, String title, String pageUrl, String cover, int totalEpisodes,
                         int resourceCount, List<QualityGroup> groups) { }

    public List<SearchItem> search(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if ((query.startsWith("《") && query.endsWith("》")) || (query.startsWith("【") && query.endsWith("】"))) {
            query = query.substring(1, query.length() - 1).trim();
        }
        if (query.isEmpty() || query.length() > 120) throw new BusinessException(400, "请输入 1–120 个字符的片名");
        String page = fetch("/search/" + URLEncoder.encode(query, StandardCharsets.UTF_8).replace("+", "%20"));
        List<Integer> starts = new ArrayList<>();
        Matcher blocks = ITEM.matcher(page);
        while (blocks.find()) starts.add(blocks.start());
        Map<String, SearchItem> found = new LinkedHashMap<>();
        for (int i = 0; i < starts.size(); i++) {
            String block = page.substring(starts.get(i), i + 1 < starts.size() ? starts.get(i + 1) : page.length());
            Matcher links = ANCHOR.matcher(block);
            while (links.find()) {
                if (!hasClass(links.group(1), "module-item-title")) continue;
                Matcher id = DETAIL_PATH.matcher(attribute(links.group(1), "href"));
                if (!id.matches()) continue;
                String title = text(links.group(2));
                if (title.isBlank()) continue;
                String caption = first(pattern("<div[^>]*class=[\"']module-item-caption[\"'][^>]*>(.*?)</div>"), block, 1);
                List<String> meta = new ArrayList<>();
                Matcher spans = pattern("<span\\b[^>]*>(.*?)</span>").matcher(caption);
                while (spans.find()) meta.add(text(spans.group(1)));
                found.putIfAbsent(id.group(1), new SearchItem(id.group(1), title, image(block),
                        meta.isEmpty() ? "" : meta.getFirst(), meta.size() > 1 ? meta.get(1) : "",
                        meta.size() > 2 ? meta.get(2) : "", BASE_URL + id.group()));
            }
        }
        if (found.isEmpty() && !page.contains("搜索结果") && !page.contains("找到 0")) {
            throw new BusinessException(502, "BT影视未返回可识别的搜索页，请稍后重试");
        }
        return List.copyOf(found.values());
    }

    public Map<String, Object> payload(SearchItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", "btbtla-title");
        payload.put("source", "btbtla");
        payload.put("btbtlaId", item.id());
        payload.put("title", item.title());
        payload.put("cover", item.cover());
        payload.put("year", item.year());
        payload.put("genre", item.genre());
        payload.put("origin", item.origin());
        payload.put("pageUrl", item.pageUrl());
        return payload;
    }

    public Detail detail(String requestedId) {
        String id = numericId(requestedId);
        String page = fetch("/detail/" + id + ".html");
        String title = text(first(pattern("<h1\\b[^>]*class=[\"']page-title[\"'][^>]*>(.*?)</h1>"), page, 1));
        if (title.isBlank()) throw new BusinessException(502, "BT影视未返回可识别的作品详情");
        int total = integer(first(pattern("集数[：:]\\s*</span>\\s*<div[^>]*>\\s*(\\d+)"), page, 1), 0);
        Map<String, Resource> unique = new LinkedHashMap<>();
        Matcher links = ANCHOR.matcher(page);
        while (links.find()) {
            if (!hasClass(links.group(1), "module-row-text")) continue;
            Matcher download = DOWNLOAD_PATH.matcher(attribute(links.group(1), "href"));
            if (!download.matches()) continue;
            String heading = first(pattern("<h4\\b[^>]*>(.*?)</h4>"), links.group(2), 1);
            String size = text(first(pattern("<span[^>]*>\\s*\\[([^\\]]+)]\\s*</span>"), heading, 1));
            String name = text(heading.replaceAll("(?is)<span\\b[^>]*>.*?</span>", ""));
            if (name.isBlank()) continue;
            int resolution = resolution(name);
            int[] episode = episode(name);
            boolean collection = COLLECTION.matcher(name).find()
                    || (total > 1 && episode[1] == 1 && episode[2] >= total)
                    || (episode[1] != episode[2] || episode[1] == 0)
                        && Pattern.compile("(?i)\\bCOMPLETE\\b").matcher(name).find();
            String label = episode[1] == 0 ? "未标集数"
                    : "第 " + episode[1] + (episode[2] > episode[1] ? "–" + episode[2] : "") + " 集";
            if (collection) label = "全集 / 合集" + (episode[1] > 0 ? " · " + label : "");
            unique.putIfAbsent(download.group(1), new Resource(download.group(1), name, size,
                    BASE_URL + download.group(), resolution, quality(resolution), episode[0], episode[1], episode[2], label, tags(name), collection));
        }
        if (unique.isEmpty() && !page.contains("资源下载")) throw new BusinessException(502, "BT影视资源列表暂时无法识别，请稍后重试");
        List<Resource> sorted = unique.values().stream().sorted(Comparator.comparingInt(Resource::resolution).reversed()
                .thenComparingInt(BtbtlaSearchService::resourceOrder)
                .thenComparing(Comparator.comparingInt(Resource::season).reversed())
                .thenComparing(Comparator.comparingInt(Resource::episodeEnd).reversed())
                .thenComparing(Comparator.comparingInt(Resource::episodeStart).reversed())
                .thenComparing(Comparator.comparingInt(BtbtlaSearchService::versionScore).reversed())
                .thenComparing(Resource::id)).toList();
        Map<Integer, List<Resource>> byQuality = new LinkedHashMap<>();
        sorted.forEach(item -> byQuality.computeIfAbsent(item.resolution(), key -> new ArrayList<>()).add(item));
        List<QualityGroup> groups = byQuality.entrySet().stream()
                .map(entry -> new QualityGroup(quality(entry.getKey()), entry.getKey(), List.copyOf(entry.getValue()))).toList();
        String poster = first(pattern("<div\\b[^>]*class=[\"'][^\"']*video-cover[^\"']*[\"'][^>]*>(.*?)</div>"), page, 1);
        return new Detail(id, title, BASE_URL + "/detail/" + id + ".html", image(poster), total, sorted.size(), groups);
    }

    public Map<String, String> magnet(String requestedId) {
        String id = numericId(requestedId);
        Matcher match = MAGNET.matcher(MovieSearchService.unescape(fetch("/tdown/" + id + ".html")));
        if (!match.find()) throw new BusinessException(502, "该资源暂未提供可识别的磁力链接，请稍后重试");
        return Map.of("id", id, "magnet", match.group());
    }

    private String fetch(String path) {
        URI uri = URI.create(BASE_URL + path);
        try {
            for (int redirect = 0; redirect < 4; redirect++) {
                if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null
                        || (uri.getPort() != -1 && uri.getPort() != 443)
                        || !("www.btbtla.com".equalsIgnoreCase(uri.getHost()) || "btbtla.com".equalsIgnoreCase(uri.getHost()))) {
                    throw new BusinessException(502, "BT影视返回了不支持的跳转地址");
                }
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(25))
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "text/html").header("Accept-Encoding", "identity")
                        .header("Referer", BASE_URL + "/").GET().build();
                HttpResponse<java.io.InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                try (var body = response.body()) {
                    if (response.statusCode() / 100 == 3) {
                        String location = response.headers().firstValue("Location")
                                .orElseThrow(() -> new BusinessException(502, "BT影视返回了无效跳转"));
                        uri = uri.resolve(location);
                        continue;
                    }
                    if (response.statusCode() / 100 != 2) throw new BusinessException(502, "BT影视访问失败（HTTP " + response.statusCode() + "）");
                    byte[] bytes = body.readNBytes(MAX_PAGE_BYTES + 1);
                    if (bytes.length > MAX_PAGE_BYTES) throw new BusinessException(502, "BT影视返回的页面过大");
                    return new String(bytes, StandardCharsets.UTF_8);
                }
            }
            throw new BusinessException(502, "BT影视页面跳转过多");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "BT影视请求已取消");
        } catch (IOException | IllegalArgumentException ex) {
            throw new BusinessException(502, "BT影视暂时无法连接，请稍后重试");
        }
    }

    private static int[] episode(String title) {
        Matcher en = EN_EPISODE.matcher(title);
        if (en.find()) return new int[]{integer(en.group(1), 1), integer(en.group(2), 0), Math.max(integer(en.group(2), 0), integer(en.group(3), 0))};
        Matcher cn = CN_EPISODE.matcher(title);
        if (!cn.find()) {
            cn = EP_EPISODE.matcher(title);
            if (!cn.find()) return new int[]{1, 0, 0};
        }
        int season = integer(first(pattern("(?:第\\s*(\\d+)\\s*季|\\bS(\\d{1,2})\\b)"), title, 1), 1);
        // 中文资源经常用 S01 而不带 E；季号独立识别，集号只认明确的「第 N 集 / EP / SxxExx」。
        Matcher seasonEn = Pattern.compile("(?i)\\bS(\\d{1,2})\\b").matcher(title);
        if (seasonEn.find()) season = integer(seasonEn.group(1), season);
        int start = integer(cn.group(1), 0);
        return new int[]{season, start, Math.max(start, integer(cn.group(2), start))};
    }

    private static int resolution(String title) {
        Matcher match = RESOLUTION.matcher(title);
        if (match.find()) return integer(match.group(1), 0);
        if (Pattern.compile("(?i)\\b8k\\b").matcher(title).find()) return 4320;
        if (Pattern.compile("(?i)\\b4k\\b").matcher(title).find()) return 2160;
        return 0;
    }
    private static String quality(int value) {
        return switch (value) {
            case 4320 -> "8K · 4320p";
            case 2160 -> "4K · 2160p";
            case 0 -> "未标清晰度";
            default -> value + "p";
        };
    }
    private static List<String> tags(String title) {
        List<String> tags = new ArrayList<>();
        if (Pattern.compile("(?i)杜比视界|\\bDV\\b|DOVI").matcher(title).find()) tags.add("杜比视界");
        if (Pattern.compile("(?i)HDR").matcher(title).find()) tags.add("HDR");
        if (Pattern.compile("(?i)高码|\\bHQ\\b").matcher(title).find()) tags.add("高码");
        if (Pattern.compile("(?i)60帧|60fps").matcher(title).find()) tags.add("60帧");
        if (Pattern.compile("(?i)\\bNF\\b").matcher(title).find()) tags.add("Netflix");
        return List.copyOf(tags);
    }
    /** 单集优先于分包，全集 / 合集与未知集数排后；同类资源按季集倒序。 */
    private static int resourceOrder(Resource resource) {
        if (resource.collection()) return 2;
        if (resource.episodeStart() <= 0) return 3;
        return resource.episodeStart() == resource.episodeEnd() ? 0 : 1;
    }
    private static int versionScore(Resource resource) {
        return (resource.tags().contains("杜比视界") ? 8 : resource.tags().contains("HDR") ? 4 : 0)
                + (resource.tags().contains("高码") ? 2 : 0) + (resource.tags().contains("60帧") ? 1 : 0);
    }
    static String numericId(String value) {
        if (value == null || !value.matches("\\d{1,15}")) throw new BusinessException(400, "请选择有效的 BT影视资源");
        return value;
    }
    private static Pattern pattern(String regex) { return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL); }
    private static String first(Pattern pattern, String value, int group) {
        Matcher match = pattern.matcher(value);
        return match.find() && match.group(group) != null ? match.group(group) : "";
    }
    private static String attribute(String attrs, String name) {
        return MovieSearchService.unescape(first(pattern("\\b" + name + "\\s*=\\s*[\"']([^\"']*)[\"']"), attrs, 1));
    }
    private static boolean hasClass(String attrs, String name) {
        return List.of(attribute(attrs, "class").split("\\s+")).contains(name);
    }
    private static String text(String html) {
        return MovieSearchService.unescape(html.replaceAll("(?s)<[^>]*>", " ")).replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }
    private static int integer(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (RuntimeException ex) { return fallback; }
    }
    private static String image(String block) {
        Matcher image = pattern("<img\\b([^>]*)>").matcher(block);
        if (!image.find()) return "";
        String src = attribute(image.group(1), "data-src");
        if (src.isBlank()) src = attribute(image.group(1), "src");
        if (src.startsWith("//")) return "https:" + src;
        if (src.startsWith("/")) return BASE_URL + src;
        return src.startsWith("https://") || src.startsWith("http://") ? src : "";
    }
}
