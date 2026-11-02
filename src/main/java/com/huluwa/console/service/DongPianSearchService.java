package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Pattern;
import java.util.function.Consumer;

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
 * <p><b>访客会话</b>：官方线路兑换前先调用 {@code POST /v1/users/anonymous}，
 * 使用稳定的 {@code anonymous_id} 初始化访客，并由实例内的 CookieManager 复用
 * {@code ai_movie_session}。接口明确返回会话失效时重建一次，不把登录或等级限制当成限流。
 *
 * <p><b>播放相关接口</b>：
 * <ol>
 *   <li>{@code GET /v1/browse/catalog?q=片名&limit=N} —— 搜索，返回作品卡片；</li>
 *   <li>{@code GET /v1/catalog/{作品ID}} —— 作品详情，返回海报 / 年份 / 地区 / 类型与每集 {@code token}；</li>
 *   <li>{@code GET /v1/playback/resolve/{该集token}} —— 播放解析，返回该集全部线路（{@code line_options}）；</li>
 *   <li>{@code POST /v1/playback/resolve-line} —— 官方线路按集兑换地址。</li>
 * </ol>
 *
 * <p><b>线路形态</b>：{@code line_options} 中第三方资源站通常直接给出 m3u8；
 * 官方线路则给出 {@code resolve_required=true}、url 为 {@code resolve://…} 的票据，
 * 需要通过 {@code POST /v1/playback/resolve-line} 按集兑换。官方直链可能是 MP4 或没有扩展名，
 * 甚至使用图片后缀，因此兑换结果按站点的 {@code resolved} 标记与 HTTP(S) 地址判断，不能仅认 m3u8。
 * 播放、复制与离线下载均支持这些视频直链；下载任务执行时才兑换地址。
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
    private static final String API_ANONYMOUS = "/v1/users/anonymous";
    private static final URI SITE_URI = URI.create(BASE_URL);
    private static final String SESSION_COOKIE = "ai_movie_session";
    /** 官方线路兑换：POST {ticket, episode_token} → {line: {resolved, url}}，携带访客会话 */
    private static final String API_RESOLVE_LINE = "/v1/playback/resolve-line";

    /** 懂片帝自己的一套消息载荷类型，安卓端据此走独立渲染，不与其他影视来源混用 */
    public static final String KIND_TITLE = "dpd-title";
    public static final String KIND_DETAIL = "dpd-detail";
    public static final String KIND_LINE = "dpd-line";

    /** 单次搜索拉取的条数（站点上限 50） */
    private static final int SEARCH_PAGE_SIZE = 20;
    /** 与电影港 / 电影天堂一致的默认展示条数 */
    public static final int DEFAULT_MAX = 10;
    /** 从首集探测候选线路；后续集是否提供该线路，需要在用户选择该集时确认。 */
    private static final int PROBE_EPISODES = 1;
    /** 单条线路首屏先返回的集数，其余在后台继续解析（避免用户干等十几秒） */
    private static final int FIRST_SCREEN_EPISODES = 12;
    /** 第三方线路后台最多预解析的集数；列表与用户主动点选不受此上限限制 */
    private static final int MAX_LINE_EPISODES = 150;
    /** 并发解析数：实测 10 并发约 2.5 秒/集，既能压住总耗时也不至于触发限流 */
    private static final int CONCURRENCY = 6;
    /** 官方线路兑换串行执行并保持最小间隔；401 会话错误另行恢复，不按限流重试。 */
    private static final long REDEEM_INTERVAL_MS = 4000;
    /** 限流、网络或服务端临时故障的最大兑换尝试次数 */
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

    /** 会话只保存在当前服务实例内，不修改 JVM 全局 Cookie，也不转发给媒体下载域名。 */
    private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER);
    private final Object sessionLock = new Object();
    /** 与浏览器一致，同一实例重建会话时复用身份，不在每次重试时生成新访客。 */
    private final String anonymousId = "web_" + UUID.randomUUID();
    private final HttpClient http = HttpClient.newBuilder()
            .cookieHandler(cookies)
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
        /** 永久记录在内存中的副本（线路名 → URL），重复点同一集不再获取 */
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

    /** 目标集缺少用户选择的线路；与网络、会话或兑换失败分开处理。 */
    public static final class DongPianLineUnavailableException extends DongPianException {
        public DongPianLineUnavailableException(String message) {
            super(message, null);
        }
    }

    /** 保留上游错误类型，只有临时故障才参与兑换退避重试。 */
    private static final class DongPianApiException extends DongPianException {
        private final boolean sessionRequired;
        private final boolean retryable;

        private DongPianApiException(String message, boolean sessionRequired, boolean retryable) {
            super(message, null);
            this.sessionRequired = sessionRequired;
            this.retryable = retryable;
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

    /** 主动打开或刷新资源列表时重新取得更新集数，已保存的链接由数据库保留。 */
    public Catalog refreshDetail(String id) {
        String valid = requireId(id);
        cache.remove(valid);
        return catalog(valid);
    }

    /**
     * 作品的线路清单（只含名称与集数）。
     *
     * <p>只解析一集、且**不兑换官方线路**——线路名从原始 `line_options` 直接读，
     * 不需要真实地址，所以这里几乎不耗额度、也快。真实地址在用户主动解析或下载时才获取。
     *
     * @param catalog {@link #detail(String)} 的返回值
     * @return 按站点权重降序的线路；该集一条线路都没有时抛异常
     */
    public List<Line> lines(Catalog catalog) {
        if (catalog.episodes().isEmpty()) {
            return List.of();
        }
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
        int total = catalog.episodes().size();
        List<Line> result = new ArrayList<>();
        merged.values().stream()
                .sorted(Comparator.comparingInt(LineAcc::weight).reversed())                .forEach(acc -> result.add(new Line(acc.name(), total)));
        return result;
    }

    /**
     * 只取某集的线路名与权重，**不兑换地址**，票据留存供点击后使用。
     *
     * <p>`line_options` 里官方线路虽然只有 `resolve://` 票据，但显示名和权重是齐全的，
     * 生成线路清单足够了——避免为了「看到名字」去兑换、白耗限额。
     */
    private List<LineAcc> probeLineNames(Episode episode) {
        List<LineAcc> names = new ArrayList<>();
        Map<String, String> tickets = new LinkedHashMap<>();
        JsonNode root = getJson(API_RESOLVE + encode(episode.token()));
        for (JsonNode node : root.path("line_options")) {
            String name = firstNonBlank(text(node, "display_label"), text(node, "label"), text(node, "provider_name"));
            if (name.isEmpty()) {
                continue;
            }
            if (node.path("resolve_required").asBoolean(false) && text(node, "url").startsWith("resolve://")) {
                tickets.putIfAbsent(name, text(node, "url"));
            }
            // 直链要有 m3u8；官方线路只要有票据就算候选
            boolean usable = node.path("resolved").asBoolean(false)
                    ? isM3u8(text(node, "url"))
                    : node.path("resolve_required").asBoolean(false);
            if (usable) {
                names.add(new LineAcc(name, node.path("preference_weight").asInt(0)));
            }
        }
        synchronized (episode) {
            episode.tickets = tickets;
            episode.ticketsLoaded = true;
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
     *   <li>官方线路：点选线路时只列出已更新集数，不兑换地址，下载任务执行或用户主动解析时才兑换。</li>
     * </ul>
     *
     * @param catalog  作品详情
     * @param lineName 线路显示名（{@link Line#name()}）
     * @return 全部分集（按集号升序）；未解析的集地址留空，官方线路无需预先兑换
     */
    public List<PlayItem> line(Catalog catalog, String lineName) {
        String wanted = lineName == null ? "" : lineName.trim();
        if (wanted.isEmpty()) {
            throw new DongPianException("请指定线路", null);
        }
        int total = catalog.episodes().size();
        List<Episode> episodes = catalog.episodes().subList(0, total);

        // 永久保存的链接先批量回填；没有保存过的官方集数保持空地址。
        loadSavedLinks(episodes, wanted);
        loadCachedEpisodes(episodes);

        boolean official = isOfficialLine(episodes, wanted);
        List<PlayItem> items = new ArrayList<>();
        if (official) {
            // 展示线路只返回已更新剧集与永久链接，不预兑换第一集，也不消耗下载额度。
            for (Episode episode : episodes) {
                String url = episode.redeemed.get(wanted);
                items.add(new PlayItem(episode.title(), url == null ? "" : url));
            }
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
            for (Episode episode : episodes.subList(0, Math.min(MAX_LINE_EPISODES, episodes.size()))) {
                if (episode.lines == null && episode.markQueued()) {
                    CompletableFuture.runAsync(() -> resolveQuietly(episode), pool);
                }
            }
        }
        return items;
    }

    /**
     * 服务端持续补齐选中线路；首份快照立即落库，后续每批最多 6 集，覆盖全部已更新集数。
     * 官方线路仅读回永久链接，后台始终不兑换票据。
     */
    public void cacheLine(Catalog catalog, String lineName, Consumer<List<PlayItem>> snapshots) {
        String wanted = requireLine(lineName);
        List<Episode> episodes = catalog.episodes();
        loadCachedEpisodes(episodes);
        // 将升级前的短期直链缓存也归档到稳定键，服务重启或 token 轮换后仍可读回。
        for (Episode episode : episodes) {
            if (episode.lines != null) saveDirectLinks(episode, episode.lines);
        }
        snapshots.accept(cachedPlayItems(episodes, wanted));
        if (isOfficialLine(episodes, wanted)) return;

        List<Episode> missing = episodes.stream().filter(episode -> {
            Map<String, RawLine> lines = episode.lines;
            return !episode.redeemed.containsKey(wanted) && (lines == null || !lines.containsKey(wanted));
        }).toList();
        int failures = 0;
        for (int offset = 0; offset < missing.size(); offset += CONCURRENCY) {
            List<CompletableFuture<Boolean>> batch = new ArrayList<>();
            for (Episode episode : missing.subList(offset, Math.min(offset + CONCURRENCY, missing.size()))) {
                batch.add(CompletableFuture.supplyAsync(() -> {
                    try {
                        // 只解析第三方直链及线路票据，不调用会消耗额度的 resolveSingle。
                        saveDirectLinks(episode, resolve(episode));
                        return true;
                    } catch (RuntimeException error) {
                        log.debug("懂片帝后台缓存分集失败 {}：{}", episode.title(), error.getMessage());
                        return false;
                    }
                }, pool));
            }
            for (var task : batch) if (!task.join()) failures++;
            snapshots.accept(cachedPlayItems(episodes, wanted));
        }
        if (failures > 0) throw new DongPianException("部分剧集地址未能缓存，请刷新重试", null);
    }

    /** 纯缓存快照，不请求站点；永久链接优先，未解析的集继续保留为空地址。 */
    private List<PlayItem> cachedPlayItems(List<Episode> episodes, String lineName) {
        loadSavedLinks(episodes, lineName);
        return episodes.stream().map(episode -> {
            String url = episode.redeemed.get(lineName);
            if (url == null && episode.lines != null) {
                RawLine direct = episode.lines.get(lineName);
                if (direct != null) url = direct.url();
            }
            return new PlayItem(episode.title(), url == null ? "" : url);
        }).toList();
    }

    /** 第三方解析一次得到多条线路，全部永久保存；不会兑换或覆盖官方线路票据。 */
    private void saveDirectLinks(Episode episode, Map<String, RawLine> lines) {
        String now = LocalDateTime.now().format(TIME_FORMAT);
        for (RawLine line : lines.values()) {
            db.update("""
                    INSERT INTO dpd_episode_link (res_id, episode_title, line_name, url, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    ON CONFLICT(res_id, episode_title, line_name) DO UPDATE SET
                        url = excluded.url, updated_at = excluded.updated_at
                    WHERE url <> excluded.url
                    """, episode.resId(), episode.title(), line.name(), line.url(), now, now);
        }
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
        // 使用站点实际更新 / 可用集数，不使用包含未来占位集的 total_episode_count。
        int updated = root.path("current_episode_count").asInt(-1);
        int available = root.path("available_episode_count").asInt(-1);
        if (available >= 0) updated = updated >= 0 ? Math.min(updated, available) : available;
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
        if (updated >= 0 && episodes.size() > updated) {
            episodes = new ArrayList<>(episodes.subList(0, updated));
        }
        if (episodes.isEmpty() && updated != 0) {
            throw new DongPianException("该作品没有已更新的分集", null);
        }
        int year = root.path("year").asInt(0);
        double score = root.path("score").asDouble(0);
        Catalog catalog = new Catalog(id, text(root, "title"),
                year > 0 ? String.valueOf(year) : "",
                text(root, "area"), String.join(",", strings(root.path("genres"))),
                text(root, "poster_url"), firstNonBlank(text(root, "episode_progress_text"), text(root, "remarks")),
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
            episode.ticketsLoaded = true;
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

    /** 识别首集探测到的官方线路；后续集是否提供该线路仍须逐集确认。 */
    private boolean isOfficialLine(List<Episode> episodes, String lineName) {
        if (episodes.isEmpty()) {
            return false;
        }
        Episode first = episodes.getFirst();
        if (!first.ticketsLoaded) {
            loadTickets(first);
        }
        if (!first.ticketsLoaded) {
            throw new DongPianException("暂时无法读取线路信息，请稍后重试", null);
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
     * @return 该集地址；缺源时明确报告当前集与线路，并给出可选线路
     */
    public PlayItem resolveEpisode(Catalog catalog, String lineName, String title) {
        String wanted = requireLine(lineName);
        Episode episode = findEpisode(catalog, title);
        String url = resolveSingle(episode, wanted);
        if (url == null) {
            throw lineUnavailable(catalog, episode, wanted);
        }
        return new PlayItem(episode.title(), url);
    }

    /** 入队前只检查该集的线路和票据，不兑换地址，避免缺源的剧集先显示已添加再后台失败。 */
    public void requireEpisodeLine(Catalog catalog, String lineName, String title) {
        String wanted = requireLine(lineName);
        Episode episode = findEpisode(catalog, title);
        synchronized (episode) {
            loadSavedLinks(List.of(episode), wanted);
            if (episode.redeemed.containsKey(wanted)) {
                return;
            }
            Map<String, RawLine> lines = resolve(episode);
            if (lines.containsKey(wanted)) {
                return;
            }
            loadTickets(episode);
            if (!episode.ticketsLoaded) {
                throw new DongPianException("暂时无法读取该集线路信息，请稍后重试", null);
            }
            if (!episode.tickets.containsKey(wanted)) {
                throw lineUnavailable(catalog, episode, wanted);
            }
        }
    }

    private static String requireLine(String lineName) {
        String wanted = lineName == null ? "" : lineName.trim();
        if (wanted.isEmpty()) {
            throw new DongPianException("请指定线路", null);
        }
        return wanted;
    }

    private static Episode findEpisode(Catalog catalog, String title) {
        String wanted = title == null ? "" : title.trim();
        return catalog.episodes().stream().filter(episode -> episode.title().equals(wanted))
                .findFirst().orElseThrow(() -> new DongPianException("没有找到这一集", null));
    }

    private static DongPianLineUnavailableException lineUnavailable(Catalog catalog, Episode episode, String lineName) {
        // 4K 与普通源是不同的 provider，不能去掉 4K 前缀后自动替换用户选择的线路。
        var available = new LinkedHashSet<>(episode.tickets.keySet());
        if (episode.lines != null) available.addAll(episode.lines.keySet());
        String alternatives = String.join("、", available.stream().limit(8).toList());
        return new DongPianLineUnavailableException("《" + catalog.title() + "》" + episode.title()
                + "在「" + lineName + "」没有资源"
                + (alternatives.isEmpty() ? "，请切换其他线路" : "；可切换到：" + alternatives));
    }

    /**
     * 解析某条线路的一集：直链直接返回；官方线路在这一刻才兑换（按集按需，最省次数）。
     *
     * @param episode    目标集
     * @param lineName   线路显示名
     * @return 该集在该线路上的视频直链；不可用返回 null
     */
    private String resolveSingle(Episode episode, String lineName) {
        // 同一集的点击解析与后台下载共用永久记录，串行避免重复兑换。
        synchronized (episode) {
            String cached = episode.redeemed.get(lineName);
            if (cached != null) {
                return cached;
            }
            List<String> saved = db.queryForList("""
                    SELECT url FROM dpd_episode_link
                     WHERE res_id = ? AND episode_title = ? AND line_name = ?
                    """, String.class, episode.resId(), episode.title(), lineName);
            if (!saved.isEmpty() && isPlayableUrl(saved.getFirst())) {
                episode.redeemed.put(lineName, saved.getFirst());
                return saved.getFirst();
            }
            Map<String, RawLine> lines = resolve(episode);
            RawLine direct = lines.get(lineName);
            String url;
            if (direct != null) {
                url = direct.url();
            } else {
                loadTickets(episode);
                String ticket = episode.tickets.get(lineName);
                if (ticket == null) {
                    if (!episode.ticketsLoaded) {
                        throw new DongPianException("暂时无法读取该集线路信息，请稍后重试", null);
                    }
                    return null;
                }
                url = redeemTicket(ticket, episode.token());
            }
            if (url != null) {
                String now = LocalDateTime.now().format(TIME_FORMAT);
                db.update("""
                        INSERT INTO dpd_episode_link (res_id, episode_title, line_name, url, created_at, updated_at)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON CONFLICT(res_id, episode_title, line_name) DO UPDATE SET
                            url = excluded.url, updated_at = excluded.updated_at
                        """, episode.resId(), episode.title(), lineName, url, now, now);
                episode.redeemed.put(lineName, url);
            }
            return url;
        }
    }

    /** 永久记录没有有效期；按稳定的作品、集标题和线路匹配，不依赖临时 token。 */
    private void loadSavedLinks(List<Episode> episodes, String lineName) {
        if (episodes.isEmpty()) {
            return;
        }
        Map<String, Episode> byTitle = new LinkedHashMap<>();
        for (Episode episode : episodes) {
            byTitle.put(episode.title(), episode);
        }
        for (Map<String, Object> row : db.queryForList("""
                SELECT episode_title, url FROM dpd_episode_link WHERE res_id = ? AND line_name = ?
                """, episodes.getFirst().resId(), lineName)) {
            Episode episode = byTitle.get(String.valueOf(row.get("episode_title")));
            String url = String.valueOf(row.get("url"));
            if (episode != null && isPlayableUrl(url)) {
                episode.redeemed.put(lineName, url);
            }
        }
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
     * 用该集专属票据兑换官方线路的真实地址，携带访客会话并串行限速。
     * 仅限流、网络和服务端临时故障退避重试；失败保留具体原因给下载队列及接口调用方。
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
            for (int attempt = 0; attempt < REDEEM_ATTEMPTS; attempt++) {
                long wait = Math.max(lastRedeemAt + REDEEM_INTERVAL_MS - System.currentTimeMillis(),
                        REDEEM_RETRY_BACKOFF_MS * attempt);
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
                    String detail = firstNonBlank(text(line, "message"), text(root, "message"));
                    throw new DongPianException("懂片帝官方线路未返回可播放地址"
                            + (detail.isEmpty() ? "" : "：" + detail), null);
                } catch (DongPianException e) {
                    boolean retryable = e instanceof DongPianApiException api
                            ? api.retryable : e.getCause() instanceof IOException;
                    if (!retryable || Thread.currentThread().isInterrupted() || attempt + 1 >= REDEEM_ATTEMPTS) {
                        throw e;
                    }
                    log.debug("懂片帝官方线路兑换第 {} 次失败，将退避重试：{}", attempt + 1, e.getMessage());
                } finally {
                    lastRedeemAt = System.currentTimeMillis();
                }
            }
            throw new DongPianException("懂片帝官方线路兑换失败", null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DongPianException("懂片帝官方线路兑换已中断", e);
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
        if (API_RESOLVE_LINE.equals(pathAndQuery)) {
            ensureAnonymousSession(null);
        }
        String usedSession = sessionCookie();
        try {
            return sendJsonOnce(method, pathAndQuery, body);
        } catch (DongPianApiException e) {
            if (!e.sessionRequired) {
                throw e;
            }
            // 每次请求最多恢复一次会话；重新发送时生成新的时间戳、nonce 和签名。
            ensureAnonymousSession(usedSession);
            return sendJsonOnce(method, pathAndQuery, body);
        }
    }

    /** rejectedSession 为空表示按需初始化，否则只替换被站点拒绝的那个会话。 */
    private void ensureAnonymousSession(String rejectedSession) {
        synchronized (sessionLock) {
            String current = sessionCookie();
            if (current != null && (rejectedSession == null || !current.equals(rejectedSession))) {
                return;
            }
            // 并发请求如果已经重建会话，上面的比较会直接复用；只移除本站的旧会话 Cookie。
            for (var cookie : cookies.getCookieStore().get(SITE_URI)) {
                if (SESSION_COOKIE.equals(cookie.getName())) {
                    cookies.getCookieStore().remove(SITE_URI, cookie);
                }
            }
            sendJsonOnce("POST", API_ANONYMOUS, Map.of("anonymous_id", anonymousId));
            if (sessionCookie() == null) {
                throw new DongPianException("懂片帝访客会话初始化失败：站点未返回有效的会话 Cookie", null);
            }
        }
    }

    private String sessionCookie() {
        return cookies.getCookieStore().get(SITE_URI).stream()
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName()) && !cookie.hasExpired())
                .map(java.net.HttpCookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    /** 初始化会话也走此方法，避免初始化与自动恢复相互递归。 */
    private JsonNode sendJsonOnce(String method, String pathAndQuery, Map<String, Object> body) {
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
                DongPianApiException error = apiFailure(status, responseBody);
                log.warn("懂片帝接口 {} {}：{}", method, pathAndQuery, error.getMessage());
                throw error;
            }
            return mapper.readTree(responseBody);
        } catch (DongPianException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DongPianException("懂片帝请求已中断", e);
        } catch (Exception e) {
            throw new DongPianException("请求失败：" + e.getMessage(), e);
        }
    }

    /** 根据站点结构化错误恢复会话或决定退避，不能把所有 401/403 都当成签名或限流。 */
    private DongPianApiException apiFailure(int status, byte[] responseBody) {
        JsonNode error = mapper.createObjectNode();
        try {
            JsonNode parsed = mapper.readTree(responseBody);
            if (parsed != null && parsed.isObject()) error = parsed;
        } catch (IOException ignored) {
            // 非 JSON 错误页只报告 HTTP 状态，不把整页内容或可能的敏感字段带给客户端。
        }
        String code = text(error, "code");
        String state = text(error, "state");
        String detail = firstNonBlank(text(error, "message"), text(error, "error"));
        boolean sessionRequired = status == 401 && (
                ("playback_user_session_required".equals(code)
                        && (state.isEmpty() || "login_required".equals(state)))
                || ("request_error".equals(text(error, "error"))
                        && ("User session is required.".equals(detail)
                        || "User session belongs to a different site.".equals(detail))));
        boolean rateLimited = status == 429 || code.contains("rate_limit") || state.contains("rate_limit")
                || "too_many_requests".equals(code);
        String reason;
        if ("invalid_request_signature".equals(code)) {
            reason = "懂片帝接口签名校验失败";
        } else if (sessionRequired) {
            reason = "懂片帝访客会话缺失或已失效";
        } else if (rateLimited) {
            reason = "懂片帝请求过于频繁，请稍后重试";
        } else if (code.contains("level") || state.contains("level")) {
            reason = "懂片帝线路有等级限制";
        } else if ("login_required".equals(state)) {
            reason = "懂片帝该线路需要登录账号";
        } else {
            reason = "懂片帝站点请求失败";
        }
        String message = reason + "（HTTP " + status
                + (code.isEmpty() ? "" : "，code=" + code)
                + (state.isEmpty() ? "" : "，state=" + state) + "）";
        if (!detail.isBlank() && !detail.stripLeading().startsWith("<")) {
            message += "：" + HlsSupport.redact(detail.substring(0, Math.min(detail.length(), 300)));
        }
        return new DongPianApiException(message, sessionRequired,
                !sessionRequired && (rateLimited || status == 408 || status >= 500 && status <= 599));
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
        payload.put("episodeCount", catalog.episodes().size());
        payload.put("updatedEpisodeCount", catalog.episodes().size());
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
