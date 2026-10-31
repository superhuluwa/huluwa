package com.huluwa.console.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/** BT影视目录和磁力的数据库缓存；目录先返回旧值，刷新与全量磁力补齐由后台继续执行。 */
@Service
public class BtbtlaCacheService {
    private static final Logger log = LoggerFactory.getLogger(BtbtlaCacheService.class);
    private final BtbtlaSearchService source;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final ExecutorService refreshWorkers = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("btbtla-refresh-", 0).factory());
    private final ExecutorService magnetWorkers = Executors.newFixedThreadPool(4,
            Thread.ofPlatform().daemon().name("btbtla-magnet-", 0).factory());
    private final Semaphore magnetPermits = new Semaphore(4, true);
    private final Map<String, CompletableFuture<BtbtlaSearchService.Detail>> refreshes = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<String>> magnetRequests = new ConcurrentHashMap<>();
    private final Set<String> queuedMagnets = ConcurrentHashMap.newKeySet();

    public BtbtlaCacheService(BtbtlaSearchService source, JdbcTemplate jdbc, ObjectMapper json) {
        this.source = source;
        this.jdbc = jdbc;
        this.json = json;
    }

    /** 保留原目录字段，额外返回刷新状态，安卓仅在刷新期间读取缓存快照。 */
    public record DetailResponse(String id, String title, String pageUrl, String cover, int totalEpisodes,
                                 int resourceCount, List<BtbtlaSearchService.QualityGroup> groups,
                                 boolean refreshing, String refreshError) {
        private DetailResponse(BtbtlaSearchService.Detail detail, boolean refreshing, String refreshError) {
            this(detail.id(), detail.title(), detail.pageUrl(), detail.cover(), detail.totalEpisodes(),
                    detail.resourceCount(), detail.groups(), refreshing, refreshError);
        }
    }

    private record CachedDetail(BtbtlaSearchService.Detail detail, String refreshError) { }

    public DetailResponse detail(String requestedId, boolean cachedOnly) {
        String id = BtbtlaSearchService.numericId(requestedId);
        CachedDetail cached = readDetail(id);
        if (!cachedOnly) {
            CompletableFuture<BtbtlaSearchService.Detail> refresh = startRefresh(id);
            // 首次只等待目录，磁力在后台限并发补齐，不阻塞列表展示。
            if (cached == null) await(refresh, "BT影视资源暂时无法加载，请稍后重试");
        }
        // 先取任务状态再读数据库，避免旧快照配上「刷新完成」而漏掉最后一次更新。
        CompletableFuture<BtbtlaSearchService.Detail> refresh = refreshes.get(id);
        boolean refreshing = refresh != null && !refresh.isDone();
        cached = readDetail(id);
        if (cached == null) throw new BusinessException(404, "该作品尚无资源缓存，请重新打开资源列表");
        return new DetailResponse(cached.detail(), refreshing, refreshing ? null : cached.refreshError());
    }

    private CompletableFuture<BtbtlaSearchService.Detail> startRefresh(String id) {
        CompletableFuture<BtbtlaSearchService.Detail> task = new CompletableFuture<>();
        CompletableFuture<BtbtlaSearchService.Detail> active = refreshes.putIfAbsent(id, task);
        if (active != null) return active;
        try {
            refreshWorkers.execute(() -> {
                try {
                    BtbtlaSearchService.Detail fresh = source.detail(id);
                    saveDetail(fresh);
                    task.complete(fresh);
                    // 每次打开都补齐缺失磁力；失败项不存空值，下次打开可继续重试。
                    try {
                        prefetchMagnets(fresh);
                    } catch (RuntimeException error) {
                        log.warn("BT影视作品 {} 的磁力缓存任务未能完整入队", id, error);
                    }
                } catch (RuntimeException error) {
                    log.warn("BT影视作品 {} 刷新失败，保留已有资源缓存", id, error);
                    try {
                        jdbc.update("UPDATE btbtla_detail_cache SET refresh_error = ? WHERE detail_id = ?",
                                error instanceof BusinessException ? error.getMessage() : "资源刷新失败，请稍后重试", id);
                    } catch (RuntimeException cacheError) {
                        log.warn("BT影视作品 {} 的刷新错误未能保存", id, cacheError);
                    }
                    task.completeExceptionally(error);
                } finally {
                    refreshes.remove(id, task);
                }
            });
        } catch (RuntimeException error) {
            refreshes.remove(id, task);
            task.completeExceptionally(error);
        }
        return task;
    }

    private CachedDetail readDetail(String id) {
        var rows = jdbc.query("SELECT detail_json, refresh_error FROM btbtla_detail_cache WHERE detail_id = ?",
                (rs, row) -> Map.entry(rs.getString("detail_json"),
                        rs.getString("refresh_error") == null ? "" : rs.getString("refresh_error")), id);
        if (rows.isEmpty()) return null;
        try {
            return new CachedDetail(json.readValue(rows.getFirst().getKey(), BtbtlaSearchService.Detail.class),
                    rows.getFirst().getValue());
        } catch (JsonProcessingException error) {
            log.warn("BT影视作品 {} 的旧缓存无法读取，将重新获取目录", id, error);
            return null;
        }
    }

    private void saveDetail(BtbtlaSearchService.Detail detail) {
        String encoded;
        try {
            encoded = json.writeValueAsString(detail);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("BT影视目录缓存序列化失败", error);
        }
        jdbc.update("""
                INSERT INTO btbtla_detail_cache (detail_id, detail_json, updated_at)
                VALUES (?, ?, datetime('now', 'localtime'))
                ON CONFLICT(detail_id) DO UPDATE SET
                    detail_json = excluded.detail_json, refresh_error = NULL,
                    updated_at = CASE WHEN detail_json <> excluded.detail_json
                                      THEN excluded.updated_at ELSE updated_at END
                WHERE detail_json <> excluded.detail_json OR refresh_error IS NOT NULL
                """, detail.id(), encoded);
    }

    private void prefetchMagnets(BtbtlaSearchService.Detail detail) {
        List<String> ids = detail.groups().stream().flatMap(group -> group.items().stream())
                .map(BtbtlaSearchService.Resource::id).toList();
        Set<String> stored = new HashSet<>();
        // 分批查询，避免长剧资源数量超过 SQLite 的占位参数上限。
        for (int offset = 0; offset < ids.size(); offset += 256) {
            List<String> batch = ids.subList(offset, Math.min(offset + 256, ids.size()));
            String placeholders = String.join(",", Collections.nCopies(batch.size(), "?"));
            stored.addAll(jdbc.queryForList("SELECT resource_id FROM btbtla_magnet_cache WHERE resource_id IN ("
                    + placeholders + ")", String.class, batch.toArray()));
        }
        for (String id : ids) {
            if (stored.contains(id) || !queuedMagnets.add(id)) continue;
            try {
                magnetWorkers.execute(() -> {
                    try {
                        magnet(id);
                    } catch (RuntimeException error) {
                        log.warn("BT影视资源 {} 的磁力缓存失败，后续打开时重试：{}", id, error.getMessage());
                    } finally {
                        queuedMagnets.remove(id);
                    }
                });
            } catch (RuntimeException error) {
                queuedMagnets.remove(id);
                throw error;
            }
        }
    }

    /** 点击复制和后台补齐共用数据库及进行中请求，不重复抓同一下载页。 */
    public Map<String, String> magnet(String requestedId) {
        String id = BtbtlaSearchService.numericId(requestedId);
        String cached = readMagnet(id);
        if (cached != null) return Map.of("id", id, "magnet", cached);
        CompletableFuture<String> task = new CompletableFuture<>();
        CompletableFuture<String> active = magnetRequests.putIfAbsent(id, task);
        if (active == null) {
            try {
                task.complete(fetchAndSaveMagnet(id));
            } catch (RuntimeException error) {
                task.completeExceptionally(error);
            } finally {
                magnetRequests.remove(id, task);
            }
        } else {
            task = active;
        }
        return Map.of("id", id, "magnet", await(task, "磁力链接暂时无法获取，请稍后重试"));
    }

    private String readMagnet(String id) {
        var rows = jdbc.queryForList("SELECT magnet FROM btbtla_magnet_cache WHERE resource_id = ?", String.class, id);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String fetchAndSaveMagnet(String id) {
        try {
            magnetPermits.acquire();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "磁力链接获取已取消");
        }
        try {
            String magnet = readMagnet(id);
            if (magnet == null) {
                magnet = source.magnet(id).get("magnet");
                jdbc.update("""
                        INSERT INTO btbtla_magnet_cache (resource_id, magnet, created_at)
                        VALUES (?, ?, datetime('now', 'localtime'))
                        ON CONFLICT(resource_id) DO NOTHING
                        """, id, magnet);
            }
            return magnet;
        } finally {
            magnetPermits.release();
        }
    }

    private static <T> T await(CompletableFuture<T> task, String message) {
        try {
            return task.join();
        } catch (CompletionException error) {
            if (error.getCause() instanceof BusinessException business) throw business;
            throw new BusinessException(502, message);
        }
    }

    @PreDestroy
    public void close() {
        refreshWorkers.shutdownNow();
        magnetWorkers.shutdownNow();
    }
}
