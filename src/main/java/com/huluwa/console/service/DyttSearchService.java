package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
import java.util.Map;

/**
 * 电影天堂（dyttzyw.com）搜索：苹果 CMS v10（MacCMS）采集接口。
 *
 * <p>与电影港（{@link MovieSearchService}）的差异：
 * <ul>
 *   <li>接口返回 UTF-8 JSON（{@code /api.php/provide/vod/?ac=detail&amp;wd=…}），不用解析 HTML；</li>
 *   <li>只接受 GET：实测 POST 会忽略关键字并返回全站列表；</li>
 *   <li>关键字只匹配片名与副标题，不匹配演员；</li>
 *   <li>在线 m3u8 通过独立 HLS 队列订阅下载，磁力订阅仍使用电影港链路。</li>
 * </ul>
 *
 * <p>DNS 投毒：dyttzyw.com 在部分网络被解析到 221.228.32.13（江苏反诈页），
 * 由 {@link com.huluwa.console.config.DyttDnsResolver} 在解析层钉住真实 IP。
 */
@Service
public class DyttSearchService {

    private static final Logger log = LoggerFactory.getLogger(DyttSearchService.class);

    /** 站点根地址 */
    public static final String BASE_URL = "https://dyttzyw.com";
    /** MacCMS 采集接口（ac=detail 才有播放地址） */
    private static final String API_URL = BASE_URL + "/api.php/provide/vod/";
    /** 单次搜索拉取的条数 */
    private static final int SEARCH_PAGE_SIZE = 20;
    /** 单个播放源随卡片下发的最大条目数（超长剧集只截前若干条，避免 payload 过大） */
    private static final int MAX_PLAY_ITEMS = 60;
    /** 与电影港一致的默认展示条数 */
    public static final int DEFAULT_MAX = 10;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** 搜索结果条目：与电影港卡片同形的字段 + 豆瓣评分 / 更新备注 / 播放源 */
    public record DyttItem(String id, String title, String year, String origin, String genre,
                           String cover, String url, String score, String remarks,
                           List<PlaySource> playSources) {
    }

    /** 一条播放源（站点常见的 dytt / dyttm3u8 两套线路） */
    public record PlaySource(String name, int total, List<PlayItem> items) {
    }

    /** 播放源里的一集（正片只有一条） */
    public record PlayItem(String title, String url) {
    }

    /** 搜索失败（网络 / 站点结构），调用方转成友好聊天回复 */
    public static class DyttSearchException extends RuntimeException {
        public DyttSearchException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 搜索关键字并返回解析后的条目。
     *
     * @param keyword 搜索关键字（片名，非空）
     * @return 站点返回的结果（单页，最多 {@value #SEARCH_PAGE_SIZE} 条），无结果返回空列表
     * @throws DyttSearchException 网络异常 / 响应异常时抛出，由调用方转成聊天回复
     */
    public List<DyttItem> search(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        // 站点只认 GET：POST 会忽略 wd 并返回全站列表（2026-10 实测）
        String url = API_URL + "?ac=detail&wd=" + encode(trimmed) + "&pg=1&pagesize=" + SEARCH_PAGE_SIZE;
        String json = get(url);
        return parse(json);
    }

    /** 按作品 ID 拉取完整剧集，订阅和下载重试不受卡片展示条数限制。 */
    public DyttItem detail(String apiUrl, String id) {
        String provider = HlsSupport.providerUrl(apiUrl);
        String resource = HlsSupport.resourceId(id);
        return parse(get(provider + "?ac=detail&ids=" + encode(resource))).stream()
                .filter(item -> resource.equals(item.id())).findFirst()
                .orElseThrow(() -> new DyttSearchException("采集接口没有返回该作品，请检查作品 ID", null));
    }

    /** 站点详情页地址（卡片配文里的「详情」链接；MacCMS 固定路由） */
    public static String detailUrl(String id) {
        return id == null || id.isBlank() ? null : BASE_URL + "/index.php/vod/detail/id/" + id + ".html";
    }

    private String get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() != 200) {
                throw new DyttSearchException("站点返回 HTTP " + response.statusCode(), null);
            }
            byte[] body = response.body();
            return new String(body == null ? new byte[0] : body, StandardCharsets.UTF_8);
        } catch (DyttSearchException e) {
            throw e;
        } catch (Exception e) {
            throw new DyttSearchException("搜索请求失败：" + e.getMessage(), e);
        }
    }

    /** 解析采集接口 JSON（可见性为包内，供单测直接调用） */
    List<DyttItem> parse(String json) {
        List<DyttItem> items = new ArrayList<>();
        if (json == null || json.isBlank()) {
            return items;
        }
        JsonNode root;
        try {
            root = new ObjectMapper().readTree(json);
        } catch (Exception e) {
            throw new DyttSearchException("站点返回内容无法解析：" + e.getMessage(), e);
        }
        JsonNode list = root.path("list");
        if (!list.isArray()) {
            return items;
        }
        for (JsonNode node : list) {
            String id = text(node, "vod_id");
            String playFrom = text(node, "vod_play_from");
            String playUrl = text(node, "vod_play_url");
            items.add(new DyttItem(
                    id,
                    text(node, "vod_name"),
                    text(node, "vod_year"),
                    text(node, "vod_area"),
                    text(node, "vod_class"),
                    text(node, "vod_pic"),
                    detailUrl(id),
                    normalizeScore(text(node, "vod_score")),
                    text(node, "vod_remarks"),
                    parsePlaySources(playFrom, playUrl)));
        }
        return items;
    }

    /**
     * 解析播放源：{@code vod_play_from} 与 {@code vod_play_url} 都用 {@code $$$} 分隔源，
     * 源内用 {@code #} 分隔集，集内用第一个 {@code $} 分隔「集名」与「地址」。
     *
     * <p>正片只有一条（如 {@code HD国语$https://…m3u8}）；
     * 剧集形如 {@code 第01集$https://…#第02集$https://…}。地址里可能带 {@code $}，
     * 因此只按第一个分隔符切分。</p>
     */
    static List<PlaySource> parsePlaySources(String playFrom, String playUrl) {
        List<PlaySource> sources = new ArrayList<>();
        if (playUrl == null || playUrl.isBlank() || "no".equalsIgnoreCase(playUrl.trim())) {
            return sources;
        }
        String[] fromParts = playFrom == null || playFrom.isBlank()
                ? new String[0] : playFrom.split("\\$\\$\\$", -1);
        String[] urlParts = playUrl.split("\\$\\$\\$", -1);
        for (int i = 0; i < urlParts.length; i++) {
            String name = i < fromParts.length && !fromParts[i].isBlank() ? fromParts[i].trim() : "线路" + (i + 1);
            List<PlayItem> items = parsePlayItems(urlParts[i]);
            if (!items.isEmpty()) {
                sources.add(new PlaySource(name, items.size(), items));
            }
        }
        return sources;
    }

    /** 单个源内的剧集列表（{@code #} 分隔，每集 {@code 名称$地址}） */
    static List<PlayItem> parsePlayItems(String part) {
        List<PlayItem> items = new ArrayList<>();
        if (part == null || part.isBlank()) {
            return items;
        }
        for (String episode : part.split("#")) {
            String entry = episode == null ? "" : episode.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int split = entry.indexOf('$');
            if (split < 0) {
                continue;
            }
            String title = entry.substring(0, split).trim();
            String url = entry.substring(split + 1).trim();
            if (url.isEmpty()) {
                continue;
            }
            PlayItem item = new PlayItem(title.isEmpty() ? "正片" : title, unescape(url));
            if (item.url().startsWith("http")) {
                items.add(item);
            }
        }
        return items;
    }

    /** 站点评分 0.0 视为「暂无评分」（MacCMS 默认值），返回 null 便于前端隐藏 */
    static String normalizeScore(String score) {
        if (score == null || score.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(score.trim()) <= 0 ? null : score.trim();
        } catch (NumberFormatException e) {
            return score.trim();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    /** 关键字按 UTF-8 百分号编码（避开 URLEncoder 把空格编成 + 的差异） */
    static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 最小 HTML 实体还原（播放地址里偶见 &amp;） */
    static String unescape(String text) {
        return text == null ? "" : text.replace("&amp;", "&").replace("&#38;", "&");
    }

    /** 供搜索回复组装：条目 → 前端影视卡片载荷（source=dytt 让前端与电影港卡片区分） */
    public Map<String, Object> toPayload(DyttItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "dytt");
        payload.put("id", item.id());
        payload.put("title", item.title());
        payload.put("year", item.year());
        payload.put("origin", item.origin());
        payload.put("genre", item.genre());
        payload.put("cover", item.cover());
        payload.put("url", item.url());
        if (item.score() != null && !item.score().isBlank()) {
            payload.put("score", item.score());
        }
        if (item.remarks() != null && !item.remarks().isBlank()) {
            payload.put("remarks", item.remarks());
        }
        List<Map<String, Object>> sources = new ArrayList<>();
        for (PlaySource source : item.playSources()) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", source.name());
            view.put("total", source.total());
            List<Map<String, Object>> plays = new ArrayList<>();
            int shown = Math.min(source.items().size(), MAX_PLAY_ITEMS);
            for (int i = 0; i < shown; i++) {
                PlayItem play = source.items().get(i);
                Map<String, Object> playView = new LinkedHashMap<>();
                playView.put("title", play.title());
                playView.put("url", play.url());
                plays.add(playView);
            }
            view.put("items", plays);
            if (source.items().size() > shown) {
                view.put("truncated", true);
            }
            sources.add(view);
        }
        payload.put("playSources", sources);
        return payload;
    }
}
