package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

/**
 * 懂片帝（dongpian19.com）搜索：逆向其 Web 前端调用的私有 JSON 接口。
 *
 * <p><b>接口签名</b>：站点的 {@code /v1/**} 接口都要求 HMAC-SHA256 请求签名，
 * 密钥硬编码在前端包 {@code movie-card-runtime-*.js} 里（见 {@link #SECRET}）。
 * 签名串为四行拼接（与前端 {@code Ta()} 完全一致）：
 * <pre>
 * {METHOD}
 * {pathname}{?query}
 * {毫秒时间戳}
 * {32 位十六进制随机 nonce}
 * </pre>
 * 结果以小写十六进制放入 {@code x-ai-movie-signature}，并同时带上
 * {@code x-ai-movie-timestamp} 与 {@code x-ai-movie-nonce}。
 *
 * <p><b>用到的三个接口</b>：
 * <ol>
 *   <li>{@code GET /v1/browse/catalog?q=片名&limit=N} —— 搜索，返回作品卡片；</li>
 *   <li>{@code GET /v1/catalog/{作品ID}} —— 作品详情，返回海报 / 年份 / 地区 / 类型与每集 {@code token}；</li>
 *   <li>{@code GET /v1/playback/resolve/{该集token}} —— 播放解析，返回该集全部线路（{@code line_options}）。</li>
 * </ol>
 *
 * <p><b>只保留可直接播放的 m3u8</b>：{@code line_options} 里有两类线路——
 * 一类带真实 {@code url}（{@code resolved=true}，第三方资源站直链，占绝大多数，可直接播放 / 下载）；
 * 另一类 {@code resolve_required=true}、url 为 {@code resolve://…} 票据（需要登录额度兑换的官方线路，
 * 未登录时没有公开的兑换接口）。本站只取前者，与电影天堂「选定线路必须含 m3u8 直链」的约束一致。
 *
 * <p><b>线路按需解析</b>：每条线路的每一集都要单独请求一次 {@code /v1/playback/resolve}
 * （单次约 2-3 秒），一部 40 集的剧全量解析要 10 秒以上，长剧更久。因此
 * {@link #detail(String)} 只预解析前几集拿到线路清单，具体线路的各集地址由
 * {@link #line(String, String)} 在用户点选该线路时并发拉取；每集结果按 token 缓存，
 * 同一部剧切换线路不会重复解析。
 */
@Service
public class DongPianSearchService {

    private static final Logger log = LoggerFactory.getLogger(DongPianSearchService.class);

    /** 站点根地址（备用域名 dongpian.ai，接口与签名规则相同） */
    public static final String BASE_URL = "https://dongpian19.com";
    /** 前端包内硬编码的签名密钥；站点轮换后需要重新从前端包提取 */
    private static final String SECRET =
            "8b9a908a05eac640e1ee06f52acaa741bfe4ba9e004eeffdbeb635e532e06666";

    private static final String API_SEARCH = "/v1/browse/catalog";
    private static final String API_CATALOG = "/v1/catalog/";
    private static final String API_RESOLVE = "/v1/playback/resolve/";

    /** 懂片帝自己的一套消息载荷类型，安卓端据此走独立渲染，不与其他影视来源混用 */
    public static final String KIND_TITLE = "dpd-title";
    public static final String KIND_DETAIL = "dpd-detail";
    public static final String KIND_LINE = "dpd-line";

    /** 单次搜索拉取的条数（站点上限 50） */
    private static final int SEARCH_PAGE_SIZE = 20;
    /** 与电影港 / 电影天堂一致的默认展示条数 */
    public static final int DEFAULT_MAX = 10;
    /** 确认线路清单时预解析的集数（个别线路缺首集，多解析几集合并更稳） */
    private static final int PROBE_EPISODES = 3;
    /** 单条线路最多解析的集数，避免超长短剧把请求拖到分钟级 */
    private static final int MAX_LINE_EPISODES = 150;
    /** 并发解析数：实测 10 并发约 2.5 秒/集，既能压住总耗时也不至于触发限流 */
    private static final int CONCURRENCY = 8;
    /** 作品缓存条数上限（每部作品的详情 + 各集解析结果） */
    private static final int CACHE_MAX = 64;
    /** 作品缓存有效期：站点每集会更新，token 也会过期 */
    private static final long CACHE_TTL_MINUTES = 30;

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper = new ObjectMapper();
    /** 并发解析用的固定线程池：请求是阻塞式 send，用普通线程池即可 */
    private final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "dongpian-resolve");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * 作品缓存：作品 ID → 详情（含分集 token 与各集已解析的线路）。
     *
     * <p>缓存 {@value #CACHE_TTL_MINUTES} 分钟：站点每集更新的集数会变，token 也会过期，
     * 过期后重新拉详情，避免「追剧更新了但列表还是旧的」。
     */
    private final Map<String, CachedCatalog> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedCatalog> eldest) {
                    return size() > CACHE_MAX;
                }
            });

    /** 缓存项：详情 + 写入时间（毫秒） */
    private record CachedCatalog(Catalog catalog, long writtenAt) { }

    /** 一集：标题 + 解析 token；lines 在首次解析后填充（按线路 key 索引） */
    public static final class Episode {
        private final String title;
        private final String token;
        /** 首解析后缓存，null 表示还没解析过 */
        private volatile Map<String, RawLine> lines;

        private Episode(String title, String token) {
            this.title = title;
            this.token = token;
        }

        public String title() { return title; }

        public String token() { return token; }
    }

    /**
     * 作品详情：卡片信息 + 分集清单。
     *
     * <p>对外公开是因为控制器要在两次请求间传递它（详情 → 线路），
     * {@code episodes} 不外泄给前端，只在服务内部使用。
     */
    public record Catalog(String id, String title, String year, String origin, String genre,
                          String cover, String remarks, String score, List<Episode> episodes) { }

    /** 一条线路在当前集上的可用地址（name 同时作为跨集匹配的键） */
    private record RawLine(String name, String url, int weight) { }

    /** 合并线路清单时用的累计项：只保留显示名与站点权重 */
    private record LineAcc(String name, int weight) { }

    /** 对外：一条线路（只含名称与集数，各集地址按需解析） */
    public record Line(String name, int total) {
    }

    /** 对外：一条线路里的一集 */
    public record PlayItem(String title, String url) {
    }

    /** 搜索结果条目：与前两个来源的卡片同形 */
    public record DpdItem(String id, String title, String year, String origin, String genre,
                          String cover, String url, String score, String remarks) {
    }

    /** 搜索 / 解析失败（网络 / 站点结构 / 密钥失效），调用方转成友好聊天回复 */
    public static class DongPianException extends RuntimeException {
        public DongPianException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 搜索关键字并返回作品卡片（不含线路，线路在打开资源列表时再解析）。
     *
     * @param keyword 搜索关键字（片名，非空）
     * @return 单页结果，无结果返回空列表
     */
    public List<DpdItem> search(String keyword) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        JsonNode root = getJson(API_SEARCH + "?q=" + encode(trimmed) + "&limit=" + SEARCH_PAGE_SIZE);
        List<DpdItem> items = new ArrayList<>();
        for (JsonNode card : root.path("cards")) {
            // 成人分区的内容不进影视搜索（字段缺失时不过滤）
            if (card.path("is_adult").asBoolean(false)) {
                continue;
            }
            DpdItem item = toItem(card);
            if (item != null) {
                items.add(item);
            }
        }
        return items;
    }

    /**
     * 读取作品详情（含分集 token）；线路清单由 {@link #lines(Catalog)} 另算。
     *
     * @param id 作品 ID（搜索卡片里的 {@code av_…} 串）
     */
    public Catalog detail(String id) {
        return catalog(requireId(id));
    }

    /**
     * 作品的线路清单（只含名称与集数）。
     *
     * @param catalog {@link #detail(String)} 的返回值
     * @return 按站点权重降序的线路；没有可直链线路时抛异常
     */
    public List<Line> lines(Catalog catalog) {
        Map<String, LineAcc> merged = new LinkedHashMap<>();
        int probe = Math.min(PROBE_EPISODES, catalog.episodes().size());
        for (int i = 0; i < probe; i++) {
            for (RawLine raw : resolve(catalog.episodes().get(i))) {
                merged.computeIfAbsent(raw.name(), name -> new LineAcc(name, raw.weight()));
            }
        }
        if (merged.isEmpty()) {
            throw new DongPianException("该作品暂无可直接播放的 m3u8 线路", null);
        }
        int total = Math.min(catalog.episodes().size(), MAX_LINE_EPISODES);
        List<Line> result = new ArrayList<>();
        merged.values().stream()
                .sorted(Comparator.comparingInt(LineAcc::weight).reversed())                .forEach(acc -> result.add(new Line(acc.name(), total)));
        return result;
    }

    /**
     * 解析指定线路的全部集（并发拉取，结果逐集缓存）。
     *
     * @param catalog  作品详情
     * @param lineName 线路显示名（{@link Line#name()}）
     * @return 按集号升序的播放地址；该线路没有可用集时返回空列表
     */
    public List<PlayItem> line(Catalog catalog, String lineName) {
        String wanted = lineName == null ? "" : lineName.trim();
        if (wanted.isEmpty()) {
            throw new DongPianException("请指定线路", null);
        }
        int total = Math.min(catalog.episodes().size(), MAX_LINE_EPISODES);
        List<Episode> episodes = catalog.episodes().subList(0, total);
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (Episode episode : episodes) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    resolve(episode);
                } catch (DongPianException e) {
                    // 单集失败不影响整条线路，缺的集直接跳过
                    log.debug("懂片帝分集解析失败：{}", e.getMessage());
                }
            }, pool));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<PlayItem> items = new ArrayList<>();
        for (Episode episode : episodes) {
            Map<String, RawLine> resolved = episode.lines;
            if (resolved == null) {
                continue;
            }
            RawLine match = resolved.get(wanted);
            if (match != null) {
                items.add(new PlayItem(episode.title(), match.url()));
            }
        }
        if (items.isEmpty()) {
            throw new DongPianException("线路「" + wanted + "」没有可播放的地址", null);
        }
        return items;
    }

    /** 作品详情（带缓存）；缓存缺失或过期时现拉一次 */
    private Catalog catalog(String id) {
        CachedCatalog cached = cache.get(id);
        if (cached != null && System.currentTimeMillis() - cached.writtenAt() < CACHE_TTL_MINUTES * 60_000L) {
            return cached.catalog();
        }
        JsonNode root = getJson(API_CATALOG + encode(id));
        List<Episode> episodes = new ArrayList<>();
        for (JsonNode node : root.path("episodes")) {
            String token = text(node, "token");
            if (token.isEmpty()) {
                continue;
            }
            int number = node.path("number").asInt(episodes.size() + 1);
            String title = text(node, "title");
            if (title.isEmpty()) {
                title = number <= 1 && episodes.isEmpty() ? "正片" : "第" + number + "集";
            }
            episodes.add(new Episode(title, token));
        }
        if (episodes.isEmpty()) {
            throw new DongPianException("该作品没有可播放的分集", null);
        }
        int year = root.path("year").asInt(0);
        double score = root.path("score").asDouble(0);
        Catalog catalog = new Catalog(id, text(root, "title"),
                year > 0 ? String.valueOf(year) : "",
                text(root, "area"), String.join(",", strings(root.path("genres"))),
                text(root, "poster_url"), text(root, "remarks"),
                score > 0 ? trimNumber(score) : null,
                List.copyOf(episodes));
        cache.put(id, new CachedCatalog(catalog, System.currentTimeMillis()));
        return catalog;
    }

    /** 解析一集：只保留 resolved 且是 http m3u8 的线路，结果写回该集缓存 */
    private Map<String, RawLine> resolve(Episode episode) {
        // 同一集可能被并发的详情 / 线路请求撞上，统一在该集对象上同步，避免重复请求站点
        synchronized (episode) {
            Map<String, RawLine> cached = episode.lines;
            if (cached != null) {
                return cached;
            }
            JsonNode root = getJson(API_RESOLVE + encode(episode.token()));
            Map<String, RawLine> lines = new LinkedHashMap<>();
            for (JsonNode node : root.path("line_options")) {
                if (!node.path("resolved").asBoolean(false)) {
                    continue;
                }
                String url = text(node, "url");
                if (!url.startsWith("http") || !isM3u8(url)) {
                    continue;
                }
                String name = firstNonBlank(text(node, "display_label"), text(node, "label"), text(node, "provider_name"));
                if (name.isEmpty()) {
                    continue;
                }
                // 线路用显示名作键：同一集内显示名唯一，跨集也能稳定对上（provider_id 会随集变化）
                lines.putIfAbsent(name, new RawLine(name, url, node.path("preference_weight").asInt(0)));
            }
            episode.lines = lines;
            return lines;
        }
    }

    /** 搜索卡片 → 条目 */
    private DpdItem toItem(JsonNode card) {
        String id = firstNonBlank(text(card, "selected_variant_id"), text(card, "default_variant_id"), text(card, "id"));
        String title = text(card, "title");
        if (id.isEmpty() || !SAFE_ID.matcher(id).matches() || title.isEmpty()) {
            return null;
        }
        int year = card.path("year").asInt(0);
        double score = card.path("score").asDouble(0);
        return new DpdItem(id, title, year > 0 ? String.valueOf(year) : "",
                text(card, "area"), String.join(",", strings(card.path("genres"))),
                text(card, "poster_url"), playerUrl(id),
                score > 0 ? trimNumber(score) : null, text(card, "remarks"));
    }

    /**
     * 发一次带签名的 GET。
     *
     * @param pathAndQuery 必须以 {@code /} 开头，且与实际请求的路径 + 查询串逐字一致（签名按它计算）
     */
    private JsonNode getJson(String pathAndQuery) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        String signature = sign("GET", pathAndQuery, timestamp, nonce);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(BASE_URL + pathAndQuery))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("x-ai-movie-timestamp", timestamp)
                    .header("x-ai-movie-nonce", nonce)
                    .header("x-ai-movie-signature", signature)
                    .GET()
                    .build();
            HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            byte[] body = response.body() == null ? new byte[0] : response.body();
            if (status != 200) {
                String detail = new String(body, StandardCharsets.UTF_8);
                log.warn("懂片帝接口 {} 返回 HTTP {}：{}", pathAndQuery, status,
                        detail.length() > 200 ? detail.substring(0, 200) : detail);
                if (status == 401 || status == 403) {
                    throw new DongPianException("站点签名校验失败（密钥可能已轮换），请更新 DongPianSearchService", null);
                }
                throw new DongPianException("站点返回 HTTP " + status, null);
            }
            return mapper.readTree(body);
        } catch (DongPianException e) {
            throw e;
        } catch (Exception e) {
            throw new DongPianException("请求失败：" + e.getMessage(), e);
        }
    }

    /** HMAC-SHA256(密钥, "METHOD\npathAndQuery\ntimestamp\nnonce")，小写十六进制 */
    static String sign(String method, String pathAndQuery, String timestamp, String nonce) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String message = method + "\n" + pathAndQuery + "\n" + timestamp + "\n" + nonce;
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new DongPianException("生成请求签名失败：" + e.getMessage(), e);
        }
    }

    /** 站点详情页地址（前端路由固定为 /player/{作品ID}） */
    public static String playerUrl(String id) {
        return id == null || id.isBlank() ? null : BASE_URL + "/player/" + id;
    }

    /** 作品 ID 只能是前端下发的 av_ 串，避免把任意字符串拼进请求路径 */
    static String requireId(String value) {
        String id = value == null ? "" : value.trim();
        if (!SAFE_ID.matcher(id).matches()) {
            throw new DongPianException("作品 ID 无效", null);
        }
        return id;
    }

    /** 站点 ID 字符集（av_ + URL 安全的 base64 变体） */
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9_-]{8,512}");

    /** 与离线下载一致的 m3u8 判定：按 URI 路径后缀，忽略查询串 */
    static boolean isM3u8(String url) {
        try {
            String path = URI.create(url).getPath();
            return path != null && path.toLowerCase().contains(".m3u8");
        } catch (Exception e) {
            return false;
        }
    }

    /** 关键字按 UTF-8 百分号编码（空格编码成 %20，与前端 encodeURIComponent 对齐） */
    static String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return "";
        }
        return value.asText("").trim();
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                String value = item.asText("").trim();
                if (!value.isEmpty()) {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }

    /** 评分去掉多余的 .0（8.0 → 8） */
    private static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    /**
     * 供搜索回复组装：条目 → 懂片帝自己的卡片载荷。
     *
     * <p>字段刻意与电影港 / 电影天堂的卡片不同：{@code kind=dpd-title} 标明这是懂片帝作品卡，
     * 不带 {@code playSources} / {@code magnets}，安卓端用独立的渲染分支，不跟前两个来源混在一起。
     */
    public Map<String, Object> toPayload(DpdItem item) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND_TITLE);
        payload.put("dpdId", item.id());
        payload.put("title", item.title());
        payload.put("year", item.year());
        payload.put("area", item.origin());
        payload.put("genres", item.genre());
        payload.put("cover", item.cover());
        payload.put("pageUrl", item.url());
        if (item.score() != null && !item.score().isBlank()) {
            payload.put("score", item.score());
        }
        if (item.remarks() != null && !item.remarks().isBlank()) {
            payload.put("remarks", item.remarks());
        }
        return payload;
    }

    /** 详情接口返回：作品信息 + 线路清单（items 留空，点选线路后再拉各集） */
    public Map<String, Object> toDetail(Catalog catalog, List<Line> lines) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND_DETAIL);
        payload.put("dpdId", catalog.id());
        payload.put("title", catalog.title());
        payload.put("year", catalog.year());
        payload.put("area", catalog.origin());
        payload.put("genres", catalog.genre());
        payload.put("cover", catalog.cover());
        payload.put("pageUrl", playerUrl(catalog.id()));
        if (catalog.score() != null) {
            payload.put("score", catalog.score());
        }
        if (catalog.remarks() != null && !catalog.remarks().isBlank()) {
            payload.put("remarks", catalog.remarks());
        }
        payload.put("episodeCount", Math.min(catalog.episodes().size(), MAX_LINE_EPISODES));
        // 线路清单用 dpdLines 而非 playSources：与前两个来源的载荷字段完全隔开
        List<Map<String, Object>> sources = new ArrayList<>();
        for (Line line : lines) {
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("name", line.name());
            view.put("total", line.total());
            view.put("episodes", List.of());
            view.put("lazy", true);
            sources.add(view);
        }
        payload.put("dpdLines", sources);
        return payload;
    }

    /** 线路各集返回：与详情同形，只是填上 episodes */
    public Map<String, Object> toLinePayload(Catalog catalog, String lineName, List<PlayItem> items) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("kind", KIND_LINE);
        payload.put("dpdId", catalog.id());
        payload.put("title", catalog.title());
        payload.put("line", lineName);
        List<Map<String, Object>> plays = new ArrayList<>();
        for (PlayItem item : items) {
            Map<String, Object> play = new LinkedHashMap<>();
            play.put("title", item.title());
            play.put("url", item.url());
            plays.add(play);
        }
        payload.put("episodes", plays);
        payload.put("total", plays.size());
        return payload;
    }
}
