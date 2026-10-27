package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
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
 * <p><b>线路形态</b>：{@code line_options} 中第三方资源站通常直接给出 m3u8；
 * 官方线路则给出 {@code resolve_required=true}、url 为 {@code resolve://…} 的票据，
 * 需要通过 {@code POST /v1/playback/resolve-line} 按集兑换。官方直链可能是 MP4 或没有扩展名，
 * 甚至使用图片后缀，因此兑换结果按站点的 {@code resolved} 标记与 HTTP(S) 地址判断，不能仅认 m3u8。
 * 播放与复制支持这些视频直链，离线下载仍只支持 m3u8。
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
    /** 官方线路兑换：POST {ticket, episode_token} → {line: {resolved, url}}，是否需登录以站点响应为准 */
    private static final String API_RESOLVE_LINE = "/v1/playback/resolve-line";

    /** 懂片帝自己的一套消息载荷类型，安卓端据此走独立渲染，不与其他影视来源混用 */
    public static final String KIND_TITLE = "dpd-title";
    public static final String KIND_DETAIL = "dpd-detail";
    public static final String KIND_LINE = "dpd-line";

    /** 单次搜索拉取的条数（站点上限 50） */
    private static final int SEARCH_PAGE_SIZE = 20;
    /** 与电影港 / 电影天堂一致的默认展示条数 */
    public static final int DEFAULT_MAX = 10;
    /** 确认线路清单时预解析的集数：实测第 1 集就包含全部线路（25/25），无需多解析 */
    private static final int PROBE_EPISODES = 1;
    /** 单条线路首屏先返回的集数，其余在后台继续解析（避免用户干等十几秒） */
    private static final int FIRST_SCREEN_EPISODES = 12;
    /** 单条线路最多解析的集数，避免超长短剧把请求拖到分钟级 */
    private static final int MAX_LINE_EPISODES = 150;
    /** 并发解析数：实测 10 并发约 2.5 秒/集，既能压住总耗时也不至于触发限流 */
    private static final int CONCURRENCY = 6;
    /**
     * 官方线路兑换的最小间隔。
     *
     * <p>实测：连续快速兑换会返回 401，但间隔 4 秒左右就能稳定成功。
     * 报错码是 {@code playback_user_session_required}（提示"需要登录"），
     * 实际是未登录用户的**免费额度被限速**，不是真的要登录。
     */
    private static final long REDEEM_INTERVAL_MS = 4000;
    /** 兑换失败后的重试次数（限速是暂时的，退避重试基本都能成功） */
    private static final int REDEEM_ATTEMPTS = 3;
    /** 重试退避基数：第 n 次重试前等 n × 该值 */
    private static final long REDEEM_RETRY_BACKOFF_MS = 5000;
    /** 作品缓存条数上限（每部作品的详情 + 各集解析结果） */
    private static final int CACHE_MAX = 64;
    /** 作品缓存有效期：站点每集会更新，token 也会过期 */
    private static final long CACHE_TTL_MINUTES = 30;
    /** 第三方直链解析结果落库的有效期；官方兑换地址只保留在作品内存缓存中 */
    private static final long EPISODE_CACHE_HOURS = 6;
    /** 数据库时间格式（与其它表保持一致） */
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final SecureRandom random = new SecureRandom();
    private final ObjectMapper mapper = new ObjectMapper();
    /** 每集解析结果落库缓存：站点解析慢且官方线路兑换有频率限制，重复打开不该再打站点 */
    private final JdbcTemplate db;
    /** 官方线路兑换的串行锁：站点限流很严，必须串行并保持间隔 */
    private final ReentrantLock redeemLock = new ReentrantLock();
    /** 上一次兑换完成的时间戳（毫秒），用于保证最小间隔 */
    private volatile long lastRedeemAt = 0;
    public DongPianSearchService(JdbcTemplate db) {
        this.db = db;
    }

    /** 并发解析用的固定线程池：请求是阻塞式 send，用普通线程池即可 */
    private final ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY, runnable -> {
        Thread thread = new Thread(runnable, "dongpian-resolve");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * 预热专用的小线程池：与实时请求隔离，避免用户点开时被后台预热抢占线程。
     * 只预热展示前几条，2 个线程足够。
     */
    private final ExecutorService warmPool = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "dongpian-warmup");
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
        private final String resId;
        private final String title;
        private final String token;
        /** 首解析后缓存，null 表示还没解析过 */
        private volatile Map<String, RawLine> lines;
        /** 该集官方线路的票据（线路名 → resolve://…），真正点播时才兑换，避免浪费 */
        private volatile Map<String, String> tickets = Map.of();
        /** 票据是否已取过（可能一条都没有，避免重复请求站点） */
        private volatile boolean ticketsLoaded;
        /** 已兑换出的官方视频直链（线路名 → URL），重复点同一集不再兑换 */
        private final Map<String, String> redeemed = new ConcurrentHashMap<>();
        /** 已提交到后台队列：避免反复点同一线路时把同一集重复排队（解析本身靠 synchronized 去重） */
        private volatile boolean queued;

        private Episode(String resId, String title, String token) {
            this.resId = resId;
            this.title = title;
            this.token = token;
        }

        /** 所属作品 ID（写缓存时按作品归档） */
        public String resId() { return resId; }

        public String title() { return title; }

        public String token() { return token; }

        /** 尝试占位，成功说明本线程负责排队这一集 */
        private boolean markQueued() {
            synchronized (this) {
                if (lines != null || queued) {
                    return false;
                }
                queued = true;
                return true;
            }
        }
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
        // 拿到结果就预热展示前几条：用户看图选片的几秒里，详情与首条线路已经拉好
        warmUp(items, DEFAULT_MAX);
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
     * <p>只解析一集、且**不兑换官方线路**——线路名从原始 `line_options` 直接读，
     * 不需要真实地址，所以这里几乎不耗额度、也快。真实地址在用户点选某条线路时才拉。
     *
     * @param catalog {@link #detail(String)} 的返回值
     * @return 按站点权重降序的线路；该集一条线路都没有时抛异常
     */
    public List<Line> lines(Catalog catalog) {
        Map<String, LineAcc> merged = new LinkedHashMap<>();
        int probe = Math.min(PROBE_EPISODES, catalog.episodes().size());
        for (int i = 0; i < probe; i++) {
            for (LineAcc acc : probeLineNames(catalog.episodes().get(i))) {
                merged.putIfAbsent(acc.name(), acc);
            }
        }
        if (merged.isEmpty()) {
            throw new DongPianException("该作品暂无可播放的线路", null);
        }
        int total = Math.min(catalog.episodes().size(), MAX_LINE_EPISODES);
        List<Line> result = new ArrayList<>();
        merged.values().stream()
                .sorted(Comparator.comparingInt(LineAcc::weight).reversed())                .forEach(acc -> result.add(new Line(acc.name(), total)));
        return result;
    }

    /**
     * 只取某集的线路名与权重，**不兑换、不缓存**。
     *
     * <p>`line_options` 里官方线路虽然只有 `resolve://` 票据，但显示名和权重是齐全的，
     * 生成线路清单足够了——避免为了「看到名字」去兑换、白耗限额。
     */
    private List<LineAcc> probeLineNames(Episode episode) {
        List<LineAcc> names = new ArrayList<>();
        JsonNode root = getJson(API_RESOLVE + encode(episode.token()));
        for (JsonNode node : root.path("line_options")) {
            String name = firstNonBlank(text(node, "display_label"), text(node, "label"), text(node, "provider_name"));
            if (name.isEmpty()) {
                continue;
            }
            // 直链要有 m3u8；官方线路只要有票据就算候选
            boolean usable = node.path("resolved").asBoolean(false)
                    ? isM3u8(text(node, "url"))
                    : node.path("resolve_required").asBoolean(false);
            if (usable) {
                names.add(new LineAcc(name, node.path("preference_weight").asInt(0)));
            }
        }
        return names;
    }

    /**
     * 解析指定线路的剧集。
     *
     * <p>两段式，核心目标是**少兑换**：
     * <ul>
     *   <li>直链线路：每集一次站点请求（约 1.6 秒），首屏同步解析前
     *       {@value #FIRST_SCREEN_EPISODES} 集，其余后台补；</li>
     *   <li>官方线路：点选线路时只兑换第一集，其余集在用户点开时按需兑换。</li>
     * </ul>
     *
     * @param catalog  作品详情
     * @param lineName 线路显示名（{@link Line#name()}）
     * @return 当前已解析出来的播放地址（按集号升序）；一集都没有时抛异常
     */
    public List<PlayItem> line(Catalog catalog, String lineName) {
        String wanted = lineName == null ? "" : lineName.trim();
        if (wanted.isEmpty()) {
            throw new DongPianException("请指定线路", null);
        }
        int total = Math.min(catalog.episodes().size(), MAX_LINE_EPISODES);
        List<Episode> episodes = catalog.episodes().subList(0, total);

        // 先把已有缓存一次性读进内存，避免逐个查库；之后只解析真正缺失的集
        loadCachedEpisodes(episodes);

        boolean official = isOfficialLine(episodes, wanted);
        List<PlayItem> items = new ArrayList<>();
        if (official) {
            // 官方线路：站点配额只有「约 20 秒 3 次」，绝不批量兑换。
            // 只兑换第一集，其余集在用户点开时走 /api/dpd/episode 单集兑换。
            for (Episode episode : episodes) {
                String url = episode.redeemed.get(wanted);
                items.add(new PlayItem(episode.title(), url == null ? "" : url));
            }
            String first = resolveSingle(episodes.getFirst(), wanted);
            if (first == null) {
                throw new DongPianException("线路「" + wanted + "」暂未返回可播放的直链，请稍后重试或换一条线路", null);
            }
            items.set(0, new PlayItem(episodes.getFirst().title(), first));
            return items;
        }

        // 直链线路：每集一次站点请求（约 1.6 秒），首屏同步解析前 N 集，其余后台补
        List<Episode> firstScreen = episodes.subList(0, Math.min(FIRST_SCREEN_EPISODES, episodes.size()));
        List<CompletableFuture<PlayItem>> futures = new ArrayList<>();
        for (Episode episode : firstScreen) {
            if (episode.lines != null && episode.lines.containsKey(wanted)) {
                continue;
            }
            futures.add(CompletableFuture.supplyAsync(() -> {
                String url = resolveSingle(episode, wanted);
                return url == null ? null : new PlayItem(episode.title(), url);
            }, pool));
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();

        Map<String, String> found = new LinkedHashMap<>();
        for (CompletableFuture<PlayItem> future : futures) {
            try {
                PlayItem item = future.join();
                if (item != null) {
                    found.putIfAbsent(item.title(), item.url());
                }
            } catch (Exception e) {
                // 单集失败跳过，不影响其它集
                log.debug("懂片帝线路解析失败：{}", e.getMessage());
            }
        }

        // 返回**全部**集：已解析的带地址，没解析的地址留空（前端显示为待解析）。
        // 只返回首屏会让长剧的第 13 集以后在界面上完全消失。
        int unresolved = 0;
        for (Episode episode : episodes) {
            Map<String, RawLine> resolved = episode.lines;
            RawLine match = resolved == null ? null : resolved.get(wanted);
            // 直链/内存里的优先，其次用本次刚解析出来的
            String url = match != null ? match.url() : found.get(episode.title());
            if (url == null) {
                unresolved++;
            }
            items.add(new PlayItem(episode.title(), url == null ? "" : url));
        }
        if (items.stream().noneMatch(item -> !item.url().isBlank())) {
            throw new DongPianException("线路「" + wanted + "」没有可播放的地址", null);
        }

        // 仍有未解析的集：后台继续补（直链），不阻塞本次响应
        if (unresolved > 0) {
            for (Episode episode : episodes) {
                if (episode.lines == null && episode.markQueued()) {
                    CompletableFuture.runAsync(() -> resolveQuietly(episode), pool);
                }
            }
        }
        return items;
    }

    /** 单集解析失败不影响整条线路，缺的集直接跳过；失败时释放排队标记，下次请求可重试 */
    private void resolveQuietly(Episode episode) {
        try {
            resolve(episode);
        } catch (Exception e) {
            synchronized (episode) {
                episode.queued = false;
            }
            log.debug("懂片帝分集解析失败：{}", e.getMessage());
        }
    }

    /**
     * 搜索后预热若干作品的详情与线路清单（后台执行，不阻塞搜索回复）。
     *
     * <p>搜索到用户点开「资源列表」之间通常有几秒到几十秒，提前把详情和首条线路
     * 预热进缓存，点开时基本是秒开。
     *
     * @param items  搜索结果
     * @param amount 预热前几个（建议不超过展示条数）
     */
    public void warmUp(List<DpdItem> items, int amount) {
        int count = Math.min(amount, items.size());
        for (int i = 0; i < count; i++) {
            String id = items.get(i).id();
            warmPool.execute(() -> {
                try {
                    Catalog catalog = catalog(id);
                    // 顺带解析首集，线路清单（lines）依赖它
                    if (!catalog.episodes().isEmpty()) {
                        resolveQuietly(catalog.episodes().getFirst());
                    }
                } catch (Exception e) {
                    // 预热失败不影响正常流程，用户点开时会重新拉
                    log.debug("懂片帝预热失败 {}：{}", id, e.getMessage());
                }
            });
        }
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
            episodes.add(new Episode(id, title, token));
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

    /**
     * 解析一集：缓存第三方 m3u8 直链与官方线路票据，官方视频直链在点播时兑换。
     *
     * <p>站点线路有两类：
     * <ul>
     *   <li>第三方资源站：`resolved=true`，`url` 本身就是直链，直接采用；</li>
     *   <li>官方线路（爱奇艺 / 优酷 / 4K 等）：只给 `resolve://` 票据，需要再调
     *       {@code POST /v1/playback/resolve-line} 兑换成带签名的真实视频地址。
     *       站点可能按内容与账号状态限制兑换，且**每集有专属票据**，必须用该集的票据兑换该集，
     *       用别的集的票据会拿到别的集。</li>
     * </ul>
     */
    private Map<String, RawLine> resolve(Episode episode) {
        // 同一集可能被并发的详情 / 线路请求撞上，统一在该集对象上同步，避免重复请求站点
        synchronized (episode) {
            Map<String, RawLine> cached = episode.lines;
            if (cached != null) {
                return cached;
            }
            // 先查数据库：直链部分命中就不必再打站点。
            // 注意此时 tickets 仍为空——官方线路的票据只存在于站点响应里，
            // 兑换前必须补一次站点请求，否则官方线路永远拿不到。
            Map<String, RawLine> stored = loadCachedLines(episode.token());
            if (stored != null) {
                episode.lines = stored;
                // 缓存命中时也补取票据，否则用户点官方线路会兑换失败
                loadTickets(episode);
                return stored;
            }
            JsonNode root = getJson(API_RESOLVE + encode(episode.token()));
            Map<String, RawLine> lines = new LinkedHashMap<>();
            // 官方线路只记票据，不在这里兑换：
            // 兑换单次 1.5–2 秒且必须串行（并发 5 个就 401），而绝大多数用户只看默认的直链资源。
            // 真正点播 / 下载官方线路时，由 line() 按「用户点的那条线路 + 那一集」兑换一次。
            Map<String, String> tickets = new LinkedHashMap<>();
            for (JsonNode node : root.path("line_options")) {
                String name = firstNonBlank(text(node, "display_label"), text(node, "label"), text(node, "provider_name"));
                if (name.isEmpty()) {
                    continue;
                }
                int weight = node.path("preference_weight").asInt(0);
                if (node.path("resolved").asBoolean(false)) {
                    String url = text(node, "url");
                    if (url.startsWith("http") && isM3u8(url)) {
                        // 线路用显示名作键：同一集内显示名唯一，跨集也能稳定对上
                        lines.putIfAbsent(name, new RawLine(name, url, weight));
                    }
                    continue;
                }
                if (node.path("resolve_required").asBoolean(false)) {
                    String ticket = text(node, "url");
                    if (ticket.startsWith("resolve://")) {
                        tickets.putIfAbsent(name, ticket);
                    }
                }
            }
            episode.tickets = tickets;
            episode.lines = lines;
            // 只缓存直链部分：官方线路地址带签名、随时可能过期，缓存没有意义
            if (!lines.isEmpty()) {
                saveCachedLines(episode, lines);
            }
            return lines;
        }
    }

    /**
     * 只为该集补取官方线路的票据（不兑换、不写缓存）。
     *
     * <p>直链命中数据库缓存时会跳过完整解析，但官方线路的票据只存在于站点响应里，
     * 不补这一步用户点官方线路就会「没有可播放的地址」。
     */
    private void loadTickets(Episode episode) {
        // 已经取过（哪怕一条都没有）就不再请求站点
        if (episode.ticketsLoaded) {
            return;
        }
        synchronized (episode) {
            if (episode.ticketsLoaded) {
                return;
            }
            try {
                JsonNode root = getJson(API_RESOLVE + encode(episode.token()));
                Map<String, String> tickets = new LinkedHashMap<>();
                for (JsonNode node : root.path("line_options")) {
                    if (!node.path("resolve_required").asBoolean(false)) {
                        continue;
                    }
                    String name = firstNonBlank(text(node, "display_label"), text(node, "label"), text(node, "provider_name"));
                    String ticket = text(node, "url");
                    if (!name.isEmpty() && ticket.startsWith("resolve://")) {
                        tickets.putIfAbsent(name, ticket);
                    }
                }
                episode.tickets = tickets;
            } catch (Exception e) {
                log.debug("懂片帝补取官方线路票据失败：{}", e.getMessage());
                return;
            }
            episode.ticketsLoaded = true;
        }
    }

    /** 这条线路是不是官方线路（需要兑换票据）；只看第一集即可，线路结构各集一致 */
    private boolean isOfficialLine(List<Episode> episodes, String lineName) {
        if (episodes.isEmpty()) {
            return false;
        }
        Episode first = episodes.getFirst();
        if (first.tickets.isEmpty()) {
            loadTickets(first);
        }
        return first.tickets.containsKey(lineName);
    }

    /**
     * 只解析某一集在指定线路上的地址（用户点开未解析的集时才调用）。
     *
     * <p>长剧首屏只解析前十几集，后面的集在界面上是「待解析」，
     * 用户点它时再兑换/解析这一集，避免一次性把所有集都兑换完。
     *
     * @param catalog  作品详情
     * @param lineName 线路显示名
     * @param title    集标题（用于定位是哪一集）
     * @return 该集地址；定位不到或有误返回 null
     */
    public PlayItem resolveEpisode(Catalog catalog, String lineName, String title) {
        String wanted = lineName == null ? "" : lineName.trim();
        String wantedTitle = title == null ? "" : title.trim();
        if (wanted.isEmpty() || wantedTitle.isEmpty()) {
            throw new DongPianException("请指定线路与集数", null);
        }
        int total = Math.min(catalog.episodes().size(), MAX_LINE_EPISODES);
        for (Episode episode : catalog.episodes().subList(0, total)) {
            if (!episode.title().equals(wantedTitle)) {
                continue;
            }
            String url = resolveSingle(episode, wanted);
            if (url == null) {
                throw new DongPianException("这一集在该线路上没有可播放的地址", null);
            }
            return new PlayItem(episode.title(), url);
        }
        throw new DongPianException("没有找到这一集", null);
    }

    /**
     * 解析某条线路的一集：直链直接返回；官方线路在这一刻才兑换（按集按需，最省次数）。
     *
     * @param episode    目标集
     * @param lineName   线路显示名
     * @return 该集在该线路上的视频直链；不可用返回 null
     */
    private String resolveSingle(Episode episode, String lineName) {
        Map<String, RawLine> lines = resolve(episode);
        RawLine direct = lines.get(lineName);
        if (direct != null) {
            return direct.url();
        }
        // 直链里没有，说明是官方线路：此时才兑换这一条这一集
        String cached = episode.redeemed.get(lineName);
        if (cached != null) {
            return cached;
        }
        // 兜底：票据可能在缓存命中路径里没取到
        loadTickets(episode);
        String ticket = episode.tickets.get(lineName);
        if (ticket == null) {
            return null;
        }
        String url = redeemTicket(ticket, episode.token());
        // 兑换结果按集按线路缓存，重复点同一集不再消耗兑换次数
        if (url != null) {
            episode.redeemed.put(lineName, url);
        }
        return url;
    }

    /** 批量把数据库里有效的解析结果读进内存（一次查询，避免逐集查库） */
    private void loadCachedEpisodes(List<Episode> episodes) {
        List<Episode> missing = episodes.stream().filter(e -> e.lines == null).toList();
        if (missing.isEmpty()) {
            return;
        }
        try {
            Map<String, Episode> byToken = new LinkedHashMap<>();
            for (Episode episode : missing) {
                byToken.put(episode.token(), episode);
            }
            // 用 resId 收敛查询范围（同一部作品），再按 token 精确匹配
            String resId = missing.getFirst().resId();
            List<Map<String, Object>> rows = db.queryForList(
                    "SELECT episode_token, lines FROM dpd_episode_cache WHERE res_id = ? AND updated_at >= ?",
                    resId, cacheCutoff());
            for (Map<String, Object> row : rows) {
                Episode episode = byToken.get(String.valueOf(row.get("episode_token")));
                if (episode == null) {
                    continue;
                }
                Map<String, RawLine> lines = parseLines(String.valueOf(row.get("lines")));
                if (lines != null) {
                    episode.lines = lines;
                }
            }
        } catch (Exception e) {
            // 缓存读取失败就当作没缓存，继续走站点解析
            log.debug("懂片帝批量读取缓存失败：{}", e.getMessage());
        }
    }

    /** JSON 数组 → 线路映射；坏数据返回 null */
    private Map<String, RawLine> parseLines(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Map<String, RawLine> lines = new LinkedHashMap<>();
            for (JsonNode node : mapper.readTree(json)) {
                String name = text(node, "name");
                String url = text(node, "url");
                if (!name.isEmpty() && url.startsWith("http")) {
                    lines.put(name, new RawLine(name, url, node.path("weight").asInt(0)));
                }
            }
            return lines.isEmpty() ? null : lines;
        } catch (Exception e) {
            return null;
        }
    }

    /** 从数据库读该集缓存；过期或不存在返回 null */
    private Map<String, RawLine> loadCachedLines(String token) {
        try {
            List<String> rows = db.queryForList(
                    "SELECT lines FROM dpd_episode_cache WHERE episode_token = ? AND updated_at >= ?",
                    String.class, token, cacheCutoff());
            return rows.isEmpty() ? null : parseLines(rows.getFirst());
        } catch (Exception e) {
            // 缓存坏了不影响主流程，继续走站点解析
            log.debug("懂片帝读取缓存失败：{}", e.getMessage());
            return null;
        }
    }

    /** 把该集解析结果写库（同一 token 覆盖更新） */
    private void saveCachedLines(Episode episode, Map<String, RawLine> lines) {
        try {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (RawLine line : lines.values()) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("name", line.name());
                row.put("url", line.url());
                row.put("weight", line.weight());
                rows.add(row);
            }
            String now = LocalDateTime.now().format(TIME_FORMAT);
            db.update("""
                    INSERT INTO dpd_episode_cache (episode_token, res_id, episode_title, lines, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(episode_token) DO UPDATE SET
                        res_id = excluded.res_id,
                        episode_title = excluded.episode_title,
                        lines = excluded.lines,
                        updated_at = excluded.updated_at
                    """, episode.token(), episode.resId(), episode.title(),
                    mapper.writeValueAsString(rows), now, now);
        } catch (Exception e) {
            log.debug("懂片帝写入缓存失败：{}", e.getMessage());
        }
    }

    /** 缓存有效期的截止时间字符串（只认 {@value #EPISODE_CACHE_HOURS} 小时内的） */
    private static String cacheCutoff() {
        return LocalDateTime.now().minusHours(EPISODE_CACHE_HOURS).format(TIME_FORMAT);
    }

    /**
     * 用该集专属票据兑换官方线路的真实地址。
     *
     * <p>兑换接口有频率限制：实测并发 5 个以上直接返回 401。这里用信号量把兑换
     * 串行化并保持最小间隔，宁可慢一点也不丢线路。
     *
     * @param ticket       线路 url 里的 {@code resolve://} 票据
     * @param episodeToken 该集的 token（站点按它确定兑换哪一集）
     * @return 兑换出的视频直链；失败返回 null（该线路直接跳过）
     */
    private String redeemTicket(String ticket, String episodeToken) {
        if (!ticket.startsWith("resolve://")) {
            return null;
        }
        String value = ticket.substring("resolve://".length()).trim();
        if (value.isEmpty()) {
            return null;
        }
        redeemLock.lock();
        try {
            // 实测连续兑换会被限流（第 4 次起返回 401），失败后加大间隔重试
            for (int attempt = 0; attempt < REDEEM_ATTEMPTS; attempt++) {
                // 与上一次兑换保持最小间隔，避免触发站点限流
                long wait = lastRedeemAt + REDEEM_INTERVAL_MS - System.currentTimeMillis();
                if (wait > 0) {
                    Thread.sleep(wait);
                }
                try {
                    JsonNode root = postJson(API_RESOLVE_LINE,
                            Map.of("ticket", value, "episode_token", episodeToken));
                    JsonNode line = root.path("line");
                    String url = text(line, "url");
                    // 官方 MP4 可能没有扩展名，甚至伪装为图片地址，按站点的已解析标记识别。
                    if (line.path("resolved").asBoolean(false) && isPlayableUrl(url)) {
                        return url;
                    }
                    // 拿到了响应但没有可用地址，重试也没用
                    return null;
                } catch (Exception e) {
                    // 401/限流：退避后再试，越往后等得越久
                    log.debug("懂片帝官方线路兑换第 {} 次失败：{}", attempt + 1, e.getMessage());
                    if (attempt + 1 < REDEEM_ATTEMPTS) {
                        Thread.sleep(REDEEM_RETRY_BACKOFF_MS * (attempt + 1L));
                    }
                } finally {
                    lastRedeemAt = System.currentTimeMillis();
                }
            }
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            redeemLock.unlock();
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
        return sendJson("GET", pathAndQuery, null);
    }

    /** 带签名的 POST（官方线路兑换用） */
    private JsonNode postJson(String path, Map<String, Object> body) {
        return sendJson("POST", path, body);
    }

    /**
     * 发一次带签名的请求。签名按方法 + 路径 + 时间戳 + nonce 计算，方法必须与实际请求一致。
     *
     * @param method        GET / POST
     * @param pathAndQuery  必须以 {@code /} 开头，且与实际请求的路径 + 查询串逐字一致
     * @param body          POST 请求体（GET 传 null），序列化成 JSON
     */
    private JsonNode sendJson(String method, String pathAndQuery, Map<String, Object> body) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = HexFormat.of().formatHex(nonceBytes);
        String signature = sign(method, pathAndQuery, timestamp, nonce);
        try {
            byte[] payload = body == null ? null
                    : mapper.writeValueAsBytes(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(BASE_URL + pathAndQuery))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("x-ai-movie-timestamp", timestamp)
                    .header("x-ai-movie-nonce", nonce)
                    .header("x-ai-movie-signature", signature);
            if (payload != null) {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofByteArray(payload));
            } else {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<byte[]> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            int status = response.statusCode();
            byte[] responseBody = response.body() == null ? new byte[0] : response.body();
            // 兑换接口成功时返回 201
            if (status != 200 && status != 201) {
                String detail = new String(responseBody, StandardCharsets.UTF_8);
                log.warn("懂片帝接口 {} {} 返回 HTTP {}：{}", method, pathAndQuery, status,
                        detail.length() > 200 ? detail.substring(0, 200) : detail);
                if (status == 401 || status == 403) {
                    throw new DongPianException("站点签名校验失败或兑换限流（HTTP " + status + "）", null);
                }
                throw new DongPianException("站点返回 HTTP " + status, null);
            }
            return mapper.readTree(responseBody);
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

    /**
     * 官方线路的已解析地址可能是 m3u8、MP4、无扩展名或伪装为图片的视频直链。
     * 调用方先检查站点的 resolved 标记；这里只接受带主机名的绝对 HTTP(S) 地址。
     */
    static boolean isPlayableUrl(String url) {
        if (url == null) {
            return false;
        }
        try {
            URI uri = URI.create(url);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
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
