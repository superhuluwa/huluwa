package com.huluwa.console.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** 懂片帝、电影天堂和电影港的持久化资源快照；先读旧值，后台刷新，轮询只读数据库。 */
@Service
public class VideoResourceCacheService {
    private static final Logger log = LoggerFactory.getLogger(VideoResourceCacheService.class);
    private static final TypeReference<Map<String, Object>> PAYLOAD = new TypeReference<>() { };
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final DongPianSearchService dpd;
    private final DyttSearchService dytt;
    private final MovieCatalogService movies;
    private final ExecutorService detailWorkers = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("video-detail-refresh-", 0).factory());
    // 长剧线路补齐与目录刷新分开，避免后台解析阻塞其它作品的目录请求。
    private final ExecutorService lineWorkers = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("video-line-refresh-", 0).factory());
    /** future 在第一个快照落库后完成，映射直到全部后台工作结束才移除。 */
    private final Map<Key, CompletableFuture<Void>> refreshes = new ConcurrentHashMap<>();

    public VideoResourceCacheService(JdbcTemplate jdbc, ObjectMapper json,
                                     DongPianSearchService dpd, DyttSearchService dytt, MovieCatalogService movies) {
        this.jdbc = jdbc;
        this.json = json;
        this.dpd = dpd;
        this.dytt = dytt;
        this.movies = movies;
    }

    private record Key(String source, String provider, String resId, String line) { }
    private record Snapshot(Map<String, Object> payload, String error) { }

    /** 磁力与在线分别缓存；旧卡片缺少地址时，按片名和年份保存匹配结果。 */
    public Map<String, Object> movieResources(String type, String url, String title, String year, boolean cachedOnly) {
        if (!"magnet".equals(type) && !"online".equals(type)) {
            throw new BusinessException(400, "请选择磁力或在线资源");
        }
        String name = title == null ? "" : title.trim();
        String released = year == null ? "" : year.trim();
        String address = url == null ? "" : url.trim();
        if (address.isBlank()) {
            HlsSupport.required(name, "影片名称");
        } else {
            address = "online".equals(type) ? MovieHlsService.detailUrl(address) : MovieSearchService.absoluteUrl(address);
        }
        String detailUrl = address;
        // 两站的数字编号不通用；有地址时使用完整 URL，无地址时保留片名和年份的边界。
        String id = detailUrl.isBlank() ? "title:" + json.valueToTree(List.of(name, released)) : "url:" + detailUrl;
        var payload = load(new Key("movie", type, id, ""), cachedOnly, detailWorkers,
                save -> save.accept(movies.resources(type, detailUrl, name, released)));
        // 下载记录、订阅和保存目录读当前数据库及配置，不能跟随站点快照一起变旧。
        return "magnet".equals(type) ? movies.withCurrentMagnetState(payload, name) : payload;
    }

    public Map<String, Object> dyttDetail(String apiUrl, String requestedId, boolean cachedOnly) {
        String provider = HlsSupport.providerUrl(apiUrl);
        String id = HlsSupport.resourceId(requestedId);
        return load(new Key("dytt", provider, id, ""), cachedOnly, detailWorkers,
                save -> save.accept(json.convertValue(dytt.detail(provider, id), PAYLOAD)));
    }

    public Map<String, Object> dpdDetail(String requestedId, boolean cachedOnly) {
        String id = DongPianSearchService.requireId(requestedId);
        return load(new Key("dpd", "", id, ""), cachedOnly, detailWorkers, save -> {
            var catalog = dpd.refreshDetail(id);
            save.accept(dpd.toDetail(catalog, dpd.lines(catalog)));
        });
    }

    public Map<String, Object> dpdLine(String requestedId, String requestedLine, boolean cachedOnly) {
        String id = DongPianSearchService.requireId(requestedId);
        String line = HlsSupport.required(requestedLine, "线路");
        Map<String, Object> payload = load(new Key("dpd", "", id, line), cachedOnly, lineWorkers, save -> {
            // 同次打开的目录若仍在刷新，先等新目录，避免把旧集数重新写回线路缓存。
            var detailRefresh = refreshes.get(new Key("dpd", "", id, ""));
            if (detailRefresh != null) await(detailRefresh);
            var catalog = dpd.detail(id);
            dpd.cacheLine(catalog, line, items -> save.accept(dpd.toLinePayload(catalog, line, items)));
        });
        // 主动解析、离线下载会另行保存链接；轮询即使不刷新站点，也能同步这些新地址。
        var links = jdbc.queryForList("""
                SELECT episode_title, url FROM dpd_episode_link WHERE res_id = ? AND line_name = ?
                """, id, line);
        Map<String, String> saved = new LinkedHashMap<>();
        for (var row : links) saved.put(String.valueOf(row.get("episode_title")), String.valueOf(row.get("url")));
        var episodes = json.valueToTree(payload.get("episodes"));
        if (episodes.isArray()) {
            for (var episode : episodes) {
                String url = saved.get(episode.path("title").asText());
                if (url != null && episode.isObject()) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) episode).put("url", url);
                }
            }
            payload.put("episodes", json.convertValue(episodes, Object.class));
        }
        return payload;
    }

    private Map<String, Object> load(Key key, boolean cachedOnly, ExecutorService workers,
                                     Consumer<Consumer<Map<String, Object>>> refresh) {
        Snapshot cached = read(key);
        if (!cachedOnly) {
            var ready = startRefresh(key, workers, refresh);
            // 冷启动仅等待首份目录快照，后续地址补齐继续在服务端执行。
            if (cached == null) await(ready);
        }
        // 先读任务状态再读快照，避免旧数据配上「刷新完成」而漏掉最后一次更新。
        boolean refreshing = refreshes.containsKey(key);
        cached = read(key);
        if (cached == null) throw new BusinessException(404, "该作品尚无资源缓存，请重新打开资源列表");
        var result = new LinkedHashMap<>(cached.payload());
        result.put("refreshing", refreshing);
        result.put("refreshError", refreshing ? null : cached.error());
        return result;
    }

    private CompletableFuture<Void> startRefresh(Key key, ExecutorService workers,
                                                 Consumer<Consumer<Map<String, Object>>> refresh) {
        var ready = new CompletableFuture<Void>();
        var active = refreshes.putIfAbsent(key, ready);
        if (active != null) return active;
        try {
            workers.execute(() -> {
                try {
                    refresh.accept(payload -> {
                        save(key, payload);
                        ready.complete(null);
                    });
                    if (!ready.isDone()) throw new IllegalStateException("资源刷新未返回目录");
                } catch (RuntimeException error) {
                    log.warn("{} 作品 {} 的资源刷新失败，保留已有缓存", key.source(), key.resId(), error);
                    try {
                        jdbc.update("""
                                UPDATE video_resource_cache SET refresh_error = ?
                                WHERE source = ? AND provider = ? AND res_id = ? AND line_name = ?
                                """, error.getMessage() == null ? "资源刷新失败，请稍后重试" : error.getMessage(),
                                key.source(), key.provider(), key.resId(), key.line());
                    } catch (RuntimeException cacheError) {
                        log.warn("资源刷新错误未能保存", cacheError);
                    }
                    ready.completeExceptionally(error);
                } finally {
                    refreshes.remove(key, ready);
                }
            });
        } catch (RuntimeException error) {
            refreshes.remove(key, ready);
            ready.completeExceptionally(error);
        }
        return ready;
    }

    private Snapshot read(Key key) {
        var rows = jdbc.query("""
                SELECT payload_json, refresh_error FROM video_resource_cache
                WHERE source = ? AND provider = ? AND res_id = ? AND line_name = ?
                """, (rs, row) -> new String[] {rs.getString("payload_json"), rs.getString("refresh_error")},
                key.source(), key.provider(), key.resId(), key.line());
        if (rows.isEmpty()) return null;
        try {
            return new Snapshot(json.readValue(rows.getFirst()[0], PAYLOAD), rows.getFirst()[1]);
        } catch (JsonProcessingException error) {
            log.warn("{} 作品 {} 的资源缓存无法读取，将重新获取", key.source(), key.resId(), error);
            return null;
        }
    }

    private void save(Key key, Map<String, Object> payload) {
        String encoded;
        try {
            encoded = json.writeValueAsString(payload);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("资源目录缓存序列化失败", error);
        }
        jdbc.update("""
                INSERT INTO video_resource_cache (source, provider, res_id, line_name, payload_json, updated_at)
                VALUES (?, ?, ?, ?, ?, datetime('now', 'localtime'))
                ON CONFLICT(source, provider, res_id, line_name) DO UPDATE SET
                    payload_json = excluded.payload_json, refresh_error = NULL,
                    updated_at = CASE WHEN payload_json <> excluded.payload_json
                                      THEN excluded.updated_at ELSE updated_at END
                WHERE payload_json <> excluded.payload_json OR refresh_error IS NOT NULL
                """, key.source(), key.provider(), key.resId(), key.line(), encoded);
    }

    private static void await(CompletableFuture<Void> ready) {
        try {
            ready.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof BusinessException business) throw business;
            String message = error.getCause() == null ? null : error.getCause().getMessage();
            throw new BusinessException(502, message == null ? "资源暂时无法加载，请稍后重试" : message);
        }
    }

    @PreDestroy
    public void close() {
        detailWorkers.shutdownNow();
        lineWorkers.shutdownNow();
    }
}
