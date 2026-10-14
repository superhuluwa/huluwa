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

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
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
    private final SubscriptionCheckLogService checkLogs;
    private final TransactionTemplate transaction;
    private final ExecutorService checks = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "hls-subscriptions"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    /** 本进程内已尝试补过封面的订阅 id：失败不反复重试，重启后再试一次 */
    private final java.util.Set<Long> coverBackfilled = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile boolean stopping;

    public HlsSubscriptionService(JdbcTemplate db, DyttSearchService source, HlsDownloadService downloads,
                                  HlsNoticeService notices, SubscriptionCheckLogService checkLogs,
                                  PlatformTransactionManager manager) {
        this.db = db; this.source = source; this.downloads = downloads; this.notices = notices;
        this.checkLogs = checkLogs;
        this.transaction = new TransactionTemplate(manager);
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM hls_subscription ORDER BY id DESC");
        backfillCovers(rows);
        return rows.stream().map(this::view).toList();
    }

    /**
     * 旧版订阅没有海报字段。列表加载时对这些订阅异步补一次封面，
     * 不阻塞本次返回（前端刷新后即可看到图片）；抓取失败只记录日志，等下次重启再试。
     */
    private void backfillCovers(List<Map<String, Object>> rows) {
        List<Map<String, Object>> missing = rows.stream()
                .filter(row -> HlsSupport.text(row.get("cover")).isBlank())
                .filter(row -> coverBackfilled.add(HlsSupport.number(row.get("id"))))
                .toList();
        if (missing.isEmpty() || stopping) return;
        checks.submit(() -> {
            for (Map<String, Object> row : missing) {
                if (stopping) return;
                try {
                    DyttSearchService.DyttItem item = source.detail(HlsSupport.text(row.get("api_url")), HlsSupport.text(row.get("res_id")));
                    if (!HlsSupport.text(item.cover()).isBlank()) {
                        db.update("UPDATE hls_subscription SET cover = ?, updated_at = ? WHERE id = ?",
                                item.cover(), HlsSupport.now(), row.get("id"));
                    }
                } catch (Exception e) {
                    log.debug("HLS 订阅封面补全失败（id={}）：{}", row.get("id"), HlsSupport.error(e));
                }
            }
        });
    }

    private Map<String, Object> view(Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        String[][] fields = {{"id", "id"}, {"apiUrl", "api_url"}, {"resId", "res_id"}, {"title", "title"},
                {"year", "year"}, {"cover", "cover"}, {"season", "season"}, {"sourceName", "source_name"},
                {"intervalMinutes", "interval_minutes"},
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
        return saveSubscription(body, source.detail(HlsSupport.providerUrl(body.apiUrl()), HlsSupport.resourceId(body.resId())));
    }

    /** 卡片只提交作品身份，参数从完整资源目录生成；再次点击保留原有订阅设置。 */
    public synchronized Map<String, Object> quickSubscribe(HlsRequests.QuickSubscription body) {
        downloads.requireEnvironment();
        String api = HlsSupport.providerUrl(body.apiUrl());
        String resId = HlsSupport.resourceId(body.resId());
        String key = HlsSupport.seriesKey(api, resId, "", "");
        List<Map<String, Object>> existing = db.queryForList(
                "SELECT * FROM hls_subscription WHERE series_key = ? ORDER BY enabled DESC, season, id LIMIT 1", key);
        if (!existing.isEmpty()) {
            Map<String, Object> saved = existing.getFirst();
            boolean paused = HlsSupport.number(saved.get("enabled")) == 0;
            Map<String, Object> subscription = paused ? enabled(HlsSupport.number(saved.get("id")), true) : view(saved);
            return Map.of("subscription", subscription, "message", paused ? "已恢复订阅，继续使用原有设置" : "已订阅，继续使用原有设置");
        }
        DyttSearchService.DyttItem detail = source.detail(api, resId);
        int season = HlsSupport.defaultSeason(detail.title());
        DyttSearchService.PlaySource selected = detail.playSources().stream()
                .sorted(Comparator.comparing(candidate -> !candidate.name().toLowerCase(Locale.ROOT).contains("m3u8")))
                .filter(candidate -> {
                    try { return !episodes(candidate, season).isEmpty(); }
                    catch (BusinessException e) { return false; }
                }).findFirst().orElseThrow(() -> new BusinessException(400, "该资源暂没有可自动下载的正片剧集线路"));
        String year = HlsSupport.text(detail.year()).matches("[12][0-9]{3}") ? detail.year() : "";
        return saveSubscription(new HlsRequests.Subscription(api, resId, HlsSupport.defaultTitle(detail.title()),
                year, season, selected.name(), 60, 1, true, detail.url(), null, null, false), detail);
    }

    private Map<String, Object> saveSubscription(HlsRequests.Subscription body, DyttSearchService.DyttItem detail) {
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
        DyttSearchService.PlaySource selected = HlsDownloadService.selectSource(detail, body.sourceName());
        Map<Integer, DyttSearchService.PlayItem> episodes = episodes(selected, season);
        if (episodes.isEmpty()) throw new BusinessException(400, "该线路没有可识别季集编号的正片，请选择剧集资源");
        String now = HlsSupport.now();
        boolean existing = !old.isEmpty();
        boolean downloadExisting = !Boolean.FALSE.equals(body.downloadExisting());
        long id = transaction.execute(status -> {
            db.update("""
                    INSERT INTO hls_subscription(series_key, api_url, res_id, title, year, cover, season, source_name,
                        interval_minutes, from_episode, headers, created_at, updated_at, next_check_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(series_key, season) DO UPDATE SET title = excluded.title, year = excluded.year,
                        cover = COALESCE(NULLIF(excluded.cover, ''), hls_subscription.cover),
                        source_name = excluded.source_name, interval_minutes = excluded.interval_minutes,
                        from_episode = excluded.from_episode, headers = excluded.headers, enabled = 1,
                        updated_at = excluded.updated_at
                    """, key, api, resId, title, year, HlsSupport.text(detail.cover()),
                    season, selected.name(), interval, from, headers, now, now, now);
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

    /** 兼容旧签名：立即检查（页面按钮触发） */
    public Map<String, Object> check(long id) {
        return check(id, SubscriptionCheckLogService.TRIGGER_MANUAL);
    }

    /**
     * 检查一个订阅的更新并排队下载。
     *
     * <p>无论有无新剧集、是否失败，都会往「消息中心 → 巡检记录」写一条结果快照；
     * 记录里的剧名与集数明细是当轮快照，订阅删除后仍然可读。</p>
     *
     * @param triggerType {@link SubscriptionCheckLogService#TRIGGER_MANUAL} 手动 / 定时
     */
    public synchronized Map<String, Object> check(long id, String triggerType) {
        Map<String, Object> row = row(id);
        String title = HlsSupport.text(row.get("title"));
        if (HlsSupport.number(row.get("enabled")) == 0) throw new BusinessException(400, "请先恢复订阅再检查更新");
        long startedAt = System.nanoTime();
        try {
            DyttSearchService.DyttItem detail = source.detail(HlsSupport.text(row.get("api_url")), HlsSupport.text(row.get("res_id")));
            Map<Integer, DyttSearchService.PlayItem> items = episodes(
                    HlsDownloadService.selectSource(detail, HlsSupport.text(row.get("source_name"))), (int) HlsSupport.number(row.get("season")));
            if (items.isEmpty()) throw new BusinessException(400, "作品暂未返回可识别的正片剧集");
            int added = transaction.execute(status -> {
                int count = recordEpisodes(row, items, false);
                db.update("""
                        UPDATE hls_subscription SET last_check_at = ?, next_check_at = ?, last_error = NULL,
                               initialized = 1, cover = COALESCE(NULLIF(?, ''), cover), updated_at = ? WHERE id = ?
                        """, HlsSupport.now(), HlsSupport.afterMinutes(HlsSupport.number(row.get("interval_minutes"))),
                        HlsSupport.text(detail.cover()), HlsSupport.now(), id);
                return count;
            });
            if (added > 0) notices.publish("订阅剧集更新", title + " 发现 " + added + " 集更新，已加入下载队列", false);
            downloads.kick();
            if (added == 0) {
                checkLogs.record(SubscriptionCheckLogService.SOURCE_DYTT, id, title, triggerType,
                        SubscriptionCheckLogService.STATUS_OK, 0, 0,
                        "检查完成：暂无新剧集（当前第 " + (int) HlsSupport.number(row.get("season")) + " 季共 "
                                + items.size() + " 集可识别）。", null, elapsedMs22(startedAt));
            } else {
                checkLogs.record(SubscriptionCheckLogService.SOURCE_DYTT, id, title, triggerType,
                        SubscriptionCheckLogService.STATUS_UPDATED, added, added,
                        "发现 " + added + " 集更新，已加入离线下载队列。",
                        episodeLines(items, added), elapsedMs22(startedAt));
            }
            return Map.of("added", added, "message", added == 0 ? "检查完成，暂无新剧集" : "已发现并排队下载 " + added + " 集");
        } catch (Exception e) {
            String error = HlsSupport.error(e);
            db.update("UPDATE hls_subscription SET last_check_at = ?, next_check_at = ?, last_error = ?, updated_at = ? WHERE id = ?",
                    HlsSupport.now(), HlsSupport.afterMinutes(HlsSupport.number(row.get("interval_minutes"))), error, HlsSupport.now(), id);
            if (HlsSupport.text(row.get("last_error")).isBlank()) notices.publish("订阅检查失败", title + "：" + error, true);
            checkLogs.record(SubscriptionCheckLogService.SOURCE_DYTT, id, title, triggerType,
                    SubscriptionCheckLogService.STATUS_FAILED, 0, 0,
                    "检查失败：" + error, null, elapsedMs22(startedAt));
            throw new BusinessException(400, error);
        }
    }

    /**
     * 巡检记录明细：只列本轮新入库的集（按集号升序，最多 12 条）。
     * 采集站的集标题通常已自带「第 N 集」，此时直接用原标题，避免出现「第 1 集 第1集」这类重复。
     */
    private static java.util.List<String> episodeLines(Map<Integer, DyttSearchService.PlayItem> items, int added) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (Map.Entry<Integer, DyttSearchService.PlayItem> entry : items.entrySet()) {
            if (lines.size() >= Math.max(added, 0)) break;
            String episodeTitle = HlsSupport.text(entry.getValue().title()).trim();
            String prefix = "第 " + entry.getKey() + " 集";
            lines.add(episodeTitle.contains(String.valueOf(entry.getKey())) || episodeTitle.isBlank()
                    ? (episodeTitle.isBlank() ? prefix : episodeTitle)
                    : prefix + " " + episodeTitle);
        }
        return lines;
    }

    private static long elapsedMs22(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    public synchronized Map<String, Object> enabled(long id, boolean enabled) {
        row(id);
        db.update("UPDATE hls_subscription SET enabled = ?, next_check_at = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, HlsSupport.now(), HlsSupport.now(), id);
        downloads.kick(); return view(row(id));
    }

    /**
     * 详情接口顺带回填订阅封面：旧订阅没有海报字段，或站点换了海报时无需等巡检。
     *
     * <p>按规范化后的采集接口 + 作品 ID 匹配该作品的全部订阅行（多季共用一张海报）。</p>
     */
    public void refreshCover(String apiUrl, String resId, String cover) {
        String value = HlsSupport.text(cover);
        if (value.isBlank()) return;
        try {
            db.update("""
                    UPDATE hls_subscription SET cover = ?, updated_at = ?
                     WHERE api_url = ? AND res_id = ? AND COALESCE(cover, '') <> ?
                    """, value, HlsSupport.now(), HlsSupport.providerUrl(apiUrl), HlsSupport.resourceId(resId), value);
        } catch (Exception e) {
            // 封面是展示信息，回填失败不影响详情返回
            log.debug("HLS 订阅封面回填失败：{}", e.getMessage());
        }
    }

    /**
     * 取消订阅即删除：连同该订阅的下载任务记录一起清除（本地已归档文件保留）。
     * 电影天堂没有「暂停」状态，取消订阅与暂停追更都按删除处理。
     */
    public synchronized Map<String, Object> delete(long id) {
        Map<String, Object> row = row(id);
        // 巡检记录是历史快照：订阅删除后继续保留，只解除关联
        checkLogs.detach(id);
        transaction.executeWithoutResult(status -> {
            db.update("DELETE FROM hls_download_task WHERE subscription_id = ?", id);
            db.update("DELETE FROM hls_subscription WHERE id = ?", id);
        });
        downloads.kick();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        result.put("title", HlsSupport.text(row.get("title")));
        result.put("message", "已取消订阅，订阅记录已删除");
        return result;
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
                    try { check(HlsSupport.number(row.get("id")), SubscriptionCheckLogService.TRIGGER_SCHEDULED); }
                    catch (Exception e) { log.warn("HLS 订阅检查失败：{}", HlsSupport.error(e)); }
                }
            } finally { busy.set(false); }
        });
    }

    @PreDestroy
    public void shutdown() { stopping = true; checks.shutdownNow(); }
}
