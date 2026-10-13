package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.web.HlsRequests;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/** 订阅跟踪完整作品目录，首次补下载与只追后续更新采用同一剧集去重记录。 */
@Service
public class HlsSubscriptionService {
    private static final Logger log = LoggerFactory.getLogger(HlsSubscriptionService.class);
    private final JdbcTemplate db;
    private final DyttSearchService source;
    private final HlsDownloadService downloads;
    private final HlsNoticeService notices;
    private final TransactionTemplate transaction;
    private final ExecutorService checks = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "hls-subscriptions"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean stopping;

    public HlsSubscriptionService(JdbcTemplate db, DyttSearchService source, HlsDownloadService downloads,
                                  HlsNoticeService notices, PlatformTransactionManager manager) {
        this.db = db; this.source = source; this.downloads = downloads; this.notices = notices;
        this.transaction = new TransactionTemplate(manager);
    }

    public List<Map<String, Object>> list() {
        return db.queryForList("SELECT * FROM hls_subscription ORDER BY id DESC").stream().map(this::view).toList();
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[][] fields = {{"id", "id"}, {"apiUrl", "api_url"}, {"resId", "res_id"}, {"title", "title"},
                {"year", "year"}, {"season", "season"}, {"sourceName", "source_name"}, {"intervalMinutes", "interval_minutes"},
                {"fromEpisode", "from_episode"}, {"lastCheckAt", "last_check_at"}, {"nextCheckAt", "next_check_at"},
                {"lastError", "last_error"}, {"createdAt", "created_at"}};
        for (String[] field : fields) result.put(field[0], row.get(field[1]));
        result.put("enabled", HlsSupport.number(row.get("enabled")) == 1);
        Map<String, String> headers = HlsSupport.headerMap(HlsSupport.text(row.get("headers")));
        result.put("referer", headers.getOrDefault("Referer", ""));
        result.put("userAgent", headers.getOrDefault("User-Agent", ""));
        result.put("cookieConfigured", headers.containsKey("Cookie"));
        result.put("episodeCount", db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE subscription_id = ?",
                Integer.class, row.get("id")));
        return result;
    }

    public synchronized Map<String, Object> subscribe(HlsRequests.Subscription body) {
        downloads.requireEnvironment();
        String api = HlsSupport.providerUrl(body.apiUrl());
        String resId = HlsSupport.resourceId(body.resId());
        String title = HlsSupport.safeName(HlsSupport.required(body.title(), "标准剧名"));
        String year = HlsDownloadService.year(body.year());
        int season = HlsSupport.range(body.season(), 1, 1, 999, "季号");
        int interval = HlsSupport.range(body.intervalMinutes(), 60, 15, 10080, "检查间隔分钟数");
        int from = HlsSupport.range(body.fromEpisode(), 1, 1, 9999, "起始集号");
        String key = HlsSupport.seriesKey(api, resId, title, year);
        List<Map<String, Object>> old = db.queryForList("SELECT * FROM hls_subscription WHERE series_key = ? AND season = ?", key, season);
        String previous = old.isEmpty() || Boolean.TRUE.equals(body.clearCookie()) ? null : HlsSupport.text(old.getFirst().get("headers"));
        String headers = HlsSupport.headers(body.referer(), body.userAgent(), body.cookie(), previous);
        DyttSearchService.DyttItem detail = source.detail(api, resId);
        DyttSearchService.PlaySource selected = HlsDownloadService.selectSource(detail, body.sourceName());
        Map<Integer, DyttSearchService.PlayItem> episodes = episodes(selected, season);
        if (episodes.isEmpty()) throw new BusinessException(400, "该线路没有可识别季集编号的正片，请选择剧集资源");
        String now = HlsSupport.now();
        boolean existing = !old.isEmpty();
        boolean downloadExisting = !Boolean.FALSE.equals(body.downloadExisting());
        long id = transaction.execute(status -> {
            db.update("""
                    INSERT INTO hls_subscription(series_key, api_url, res_id, title, year, season, source_name,
                        interval_minutes, from_episode, headers, created_at, updated_at, next_check_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(series_key, season) DO UPDATE SET title = excluded.title, year = excluded.year,
                        source_name = excluded.source_name, interval_minutes = excluded.interval_minutes,
                        from_episode = excluded.from_episode, headers = excluded.headers, enabled = 1,
                        updated_at = excluded.updated_at
                    """, key, api, resId, title, year, season, selected.name(), interval, from, headers, now, now, now);
            long subscription = db.queryForObject("SELECT id FROM hls_subscription WHERE series_key = ? AND season = ?", Long.class, key, season);
            Map<String, Object> row = row(subscription);
            boolean initial = HlsSupport.number(row.get("initialized")) == 0;
            recordEpisodes(row, episodes, initial && !downloadExisting);
            db.update("""
                    UPDATE hls_subscription SET initialized = 1, last_check_at = ?, next_check_at = ?,
                           last_error = NULL, updated_at = ? WHERE id = ?
                    """, now, HlsSupport.afterMinutes(interval), now, subscription);
            return subscription;
        });
        downloads.kick();
        return Map.of("subscription", view(row(id)), "message", existing
                ? "订阅设置已保存，历史剧集不会重复下载" : downloadExisting
                ? "已订阅，当前符合起始集号的剧集已加入队列，后续更新自动下载" : "已订阅并记录当前剧集，只下载后续新增剧集");
    }

    private static Map<Integer, DyttSearchService.PlayItem> episodes(DyttSearchService.PlaySource source, int season) {
        Map<Integer, DyttSearchService.PlayItem> result = new LinkedHashMap<>();
        for (DyttSearchService.PlayItem play : source.items()) {
            Integer episode = HlsSupport.episodeNumber(play.title());
            if (episode == null || !HlsSupport.matchesSeason(play.title(), season)) continue;
            HlsDownloadService.m3u8(play.url());
            if (result.putIfAbsent(episode, play) != null) throw new BusinessException(400, "线路包含重复集号，请确认季号或选择其他线路");
        }
        return result;
    }

    private int recordEpisodes(Map<String, Object> row, Map<Integer, DyttSearchService.PlayItem> episodes, boolean skipExisting) {
        long id = HlsSupport.number(row.get("id"));
        int newTasks = 0;
        for (var entry : episodes.entrySet()) {
            boolean skip = skipExisting || entry.getKey() < HlsSupport.number(row.get("from_episode"));
            Integer before = db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE series_key = ? AND season = ? AND episode = ?",
                    Integer.class, row.get("series_key"), row.get("season"), entry.getKey());
            downloads.discover(id, HlsSupport.text(row.get("series_key")), HlsSupport.text(row.get("api_url")),
                    HlsSupport.text(row.get("res_id")), HlsSupport.text(row.get("title")), HlsSupport.text(row.get("year")),
                    (int) HlsSupport.number(row.get("season")), entry.getKey(), entry.getValue().title(),
                    HlsSupport.text(row.get("source_name")), entry.getValue().url(), HlsSupport.text(row.get("headers")), skip);
            if (before != null && before == 0 && !skip) newTasks++;
        }
        return newTasks;
    }

    public synchronized Map<String, Object> check(long id) {
        Map<String, Object> row = row(id);
        if (HlsSupport.number(row.get("enabled")) == 0) throw new BusinessException(400, "请先恢复订阅再检查更新");
        try {
            DyttSearchService.DyttItem detail = source.detail(HlsSupport.text(row.get("api_url")), HlsSupport.text(row.get("res_id")));
            Map<Integer, DyttSearchService.PlayItem> items = episodes(
                    HlsDownloadService.selectSource(detail, HlsSupport.text(row.get("source_name"))), (int) HlsSupport.number(row.get("season")));
            if (items.isEmpty()) throw new BusinessException(400, "作品暂未返回可识别的正片剧集");
            int added = transaction.execute(status -> {
                int count = recordEpisodes(row, items, false);
                db.update("""
                        UPDATE hls_subscription SET last_check_at = ?, next_check_at = ?, last_error = NULL,
                               initialized = 1, updated_at = ? WHERE id = ?
                        """, HlsSupport.now(), HlsSupport.afterMinutes(HlsSupport.number(row.get("interval_minutes"))), HlsSupport.now(), id);
                return count;
            });
            if (added > 0) notices.publish("订阅剧集更新", row.get("title") + " 发现 " + added + " 集更新，已加入下载队列", false);
            downloads.kick();
            return Map.of("added", added, "message", added == 0 ? "检查完成，暂无新剧集" : "已发现并排队下载 " + added + " 集");
        } catch (Exception e) {
            String error = HlsSupport.error(e);
            db.update("UPDATE hls_subscription SET last_check_at = ?, next_check_at = ?, last_error = ?, updated_at = ? WHERE id = ?",
                    HlsSupport.now(), HlsSupport.afterMinutes(HlsSupport.number(row.get("interval_minutes"))), error, HlsSupport.now(), id);
            if (HlsSupport.text(row.get("last_error")).isBlank()) notices.publish("订阅检查失败", row.get("title") + "：" + error, true);
            throw new BusinessException(400, error);
        }
    }

    public synchronized Map<String, Object> enabled(long id, boolean enabled) {
        row(id);
        db.update("UPDATE hls_subscription SET enabled = ?, next_check_at = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, HlsSupport.now(), HlsSupport.now(), id);
        downloads.kick(); return view(row(id));
    }

    private Map<String, Object> row(long id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM hls_subscription WHERE id = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "订阅不存在");
        return rows.getFirst();
    }

    @Scheduled(initialDelay = 30000, fixedDelay = 60000)
    public void schedule() {
        if (stopping || !busy.compareAndSet(false, true)) return;
        checks.submit(() -> {
            try {
                for (Map<String, Object> row : db.queryForList("""
                        SELECT id FROM hls_subscription WHERE enabled = 1 AND (next_check_at IS NULL OR next_check_at <= ?)
                         ORDER BY COALESCE(next_check_at, created_at), id LIMIT 5
                        """, HlsSupport.now())) {
                    if (stopping) break;
                    try { check(HlsSupport.number(row.get("id"))); }
                    catch (Exception e) { log.warn("HLS 订阅检查失败：{}", HlsSupport.error(e)); }
                }
            } finally { busy.set(false); }
        });
    }

    @PreDestroy
    public void shutdown() { stopping = true; checks.shutdownNow(); }
}
