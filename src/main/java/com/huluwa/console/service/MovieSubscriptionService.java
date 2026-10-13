package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.ws.ChatPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 电影港订阅（对齐 quantum 的 dygangs_search.cs 订阅 + dygangs.cs 监听两个脚本，收敛到一个服务）：
 *
 * <ul>
 *   <li><b>订阅</b>：搜索结果卡片上的「订阅」按钮 → 记录片名 + 电影港具体链接，
 *       立刻抓一次详情页把每集磁力全部落库作为<b>基线</b>（基线不触发下载，只是对比基准）；</li>
 *   <li><b>巡检</b>：{@link #scheduledScan()} 每 10 分钟唤醒（配置项 movie.subscribe.interval-minutes，默认 60），
 *       对到期的订阅逐个抓详情页，与已存磁力做差集：
 *       只把<b>新增</b>且<b>清晰度最高</b>的每集条目提交所选下载工具，并按剧名建子目录；</li>
 *   <li><b>通知</b>：有新剧集 → 写系统通知（消息中心「系统通知」会话）；
 *       提交成功 / 下载成功 → 写「磁力下载」会话；</li>
 *   <li><b>文件名补全</b>：下载完成的种子若文件名不含剧名，用 qB 的 renameFile 补上前缀，
 *       保证归档到「剧名」文件夹里的文件名也带剧名。</li>
 * </ul>
 *
 * <p>所有时间都按 Asia/Shanghai 的本地时间字符串存 SQLite（与既有表一致）。</p>
 */
@Service
public class MovieSubscriptionService {

    private static final Logger log = LoggerFactory.getLogger(MovieSubscriptionService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 默认巡检间隔（分钟）：没有单独配置时，每个订阅每小时一轮 */
    public static final int DEFAULT_INTERVAL_MINUTES = 60;
    /** 电影港统一保存根目录，两种工具共用 */
    public static final String DEFAULT_SAVE_ROOT = "/vol1/1000/downloads";
    /** 旧版配置键，仅保留兼容；电影港使用统一根目录 */
    public static final String CFG_DOWNLOAD_ROOT = "movie.download.root";
    public static final String CFG_DOWNLOADER = "movie.download.downloader";
    /** 周期巡检的唤醒间隔（分钟）：到期判定按订阅自身的 next_check_at，唤醒频率与巡检间隔解耦 */
    private static final int WAKE_MINUTES = 10;
    /** 单轮最多处理的订阅数（防止一次抓太多把站点抓崩） */
    private static final int MAX_PER_ROUND = 5;
    /** 单轮最多提交的下载条数 */
    private static final int MAX_DOWNLOADS_PER_ROUND = 20;

    private final JdbcTemplate jdbcTemplate;
    private final MovieSearchService movieSearchService;
    private final QBittorrentService qBittorrentService;
    private final XunleiService xunleiService;
    private final MessageService messageService;
    private final ChatPushService chatPushService;

    public MovieSubscriptionService(JdbcTemplate jdbcTemplate,
                                    MovieSearchService movieSearchService,
                                    QBittorrentService qBittorrentService,
                                    XunleiService xunleiService,
                                    MessageService messageService,
                                    ChatPushService chatPushService) {
        this.jdbcTemplate = jdbcTemplate;
        this.movieSearchService = movieSearchService;
        this.qBittorrentService = qBittorrentService;
        this.xunleiService = xunleiService;
        this.messageService = messageService;
        this.chatPushService = chatPushService;
    }

    /** 订阅条目视图（订阅列表 / 搜索结果卡片上的按钮状态） */
    public record Subscription(long id, String resId, String title, String year, String origin, String genre,
                               String cover, String detailUrl, boolean enabled, int magnetCount,
                               String baselineAt, String lastCheckAt, String nextCheckAt, String lastError, String downloader,
                               String createdAt) {
    }

    /**
     * 订阅结果。
     *
     * @param created       本次是否新建了订阅行
     * @param reenabled     该详情页此前订阅过、本次是取消后重新启用（磁力基线沿用）
     * @param baselineCount 本次新入库的基线磁力条数
     */
    public record SubscribeResult(Subscription subscription, boolean created, boolean reenabled,
                                  int baselineCount) {
    }

    // ------------------------------------------------------------------ 订阅 / 取消

    /**
     * 订阅一部剧：入库片名 + 电影港具体链接，并立刻抓详情页把每集磁力全部存为基线。
     *
     * <p>已订阅（同一详情页）时只补/更新元信息并返回 existing，不覆盖已有基线与新增记录。</p>
     *
     * @param title     片名（来自搜索结果或用户手填）
     * @param detailUrl 电影港具体链接（详情页）
     * @param resId     资源ID（可为 null，从链接里推断）
     */
    public SubscribeResult subscribe(String title, String detailUrl, String resId,
                                     String year, String origin, String genre, String cover) {
        return subscribe(title, detailUrl, resId, year, origin, genre, cover, null);
    }

    public SubscribeResult subscribe(String title, String detailUrl, String resId,
                                     String year, String origin, String genre, String cover, String downloader) {
        if (downloader != null) {
            validateDownloader(downloader);
        }
        String url = movieSearchService.absoluteUrl(detailUrl == null ? "" : detailUrl.trim());
        if (url.isBlank() || "null".equals(url)) {
            throw new BusinessException(400, "缺少电影港详情页链接，无法订阅");
        }
        String name = title == null ? "" : title.trim();
        String id = resId != null && !resId.isBlank() ? resId.trim() : MovieSearchService.idFrom(url);

        Map<String, Object> existing = findRowByUrl(url);
        String tool = downloader != null ? downloader
                : existing == null ? downloadSettings().downloader() : subscriptionDownloader(existing);
        if (existing != null && number(existing.get("enabled")) == 1) {
            Subscription current = toSubscription(existing);
            // 已订阅：只补元信息（片名别再被搜索结果里的别名覆盖），不重抓基线
            jdbcTemplate.update("""
                    UPDATE movie_subscription
                       SET title = CASE WHEN title IS NULL OR title = '' THEN ? ELSE title END,
                           year = COALESCE(NULLIF(?, ''), year),
                           origin = COALESCE(NULLIF(?, ''), origin),
                           genre = COALESCE(NULLIF(?, ''), genre),
                           cover = COALESCE(NULLIF(?, ''), cover),
                           downloader = ?, updated_at = ?
                     WHERE id = ?
                    """, name, text(year), text(origin), text(genre), text(cover),
                    tool, now(), current.id());
            return new SubscribeResult(toSubscription(findRowById(current.id())), false, false, current.magnetCount());
        }

        // 抓详情页：标题兜底 + 磁力基线；网络异常不阻断订阅（先入库，让巡检继续补）
        String siteTitle = "";
        List<MovieSearchService.MagnetItem> magnets = List.of();
        String fetchError = null;
        try {
            MovieSearchService.DetailPage detail = movieSearchService.fetchDetail(url);
            siteTitle = detail.title();
            magnets = detail.magnets();
        } catch (Exception e) {
            fetchError = safeMessage(e);
            log.warn("订阅时抓详情页失败（先入库，巡检再补）：{} - {}", url, fetchError);
        }
        if ((name == null || name.isBlank()) && !siteTitle.isBlank()) {
            name = siteTitle;
        }
        if (name.isBlank()) {
            name = id == null ? url : ("电影港 " + id);
        }

        String stamp = now();
        long subscriptionId;
        boolean created;
        boolean reenabled = existing != null;
        if (existing != null) {
            // 曾取消订阅：直接复用原行重新启用（磁力基线沿用，避免把老剧集当更新又下一遍）
            subscriptionId = number(existing.get("id"));
            created = false;
            jdbcTemplate.update("""
                    UPDATE movie_subscription
                       SET title = ?, res_id = ?, year = ?, origin = ?, genre = ?, cover = ?,
                           enabled = 1, downloader = ?, last_error = ?, updated_at = ?,
                           next_check_at = COALESCE(NULLIF(?, ''), ?)
                     WHERE id = ?
                    """, name, id, text(year), text(origin), text(genre), text(cover), tool, fetchError, stamp,
                    nextCheck(stamp), stamp, subscriptionId);
        } else {
            jdbcTemplate.update("""
                    INSERT INTO movie_subscription
                        (res_id, title, year, origin, genre, cover, detail_url, downloader, enabled,
                         baseline_at, last_check_at, next_check_at, last_error, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?, ?)
                    """, id, name, text(year), text(origin), text(genre), text(cover), url, tool,
                    magnets.isEmpty() ? null : stamp, magnets.isEmpty() ? null : stamp,
                    nextCheck(stamp), fetchError, stamp, stamp);
            subscriptionId = subscriptionIdOf(url);
            created = true;
        }

        int added = fetchError == null ? saveMagnets(subscriptionId, magnets, true) : 0;
        if (fetchError == null) {
            jdbcTemplate.update("UPDATE movie_subscription SET baseline_at = ?, last_check_at = ? WHERE id = ?",
                    stamp, stamp, subscriptionId);
        }
        return new SubscribeResult(toSubscription(findRowById(subscriptionId)), created, reenabled, added);
    }

    /** 兼容旧签名（只给片名与链接） */
    public SubscribeResult subscribe(String title, String detailUrl) {
        return subscribe(title, detailUrl, MovieSearchService.idFrom(detailUrl), null, null, null, null);
    }

    /** 取消订阅：保留磁力基线与下载记录，只停巡检，之后可以重新订阅 */
    public boolean unsubscribe(long id) {
        int updated = jdbcTemplate.update(
                "UPDATE movie_subscription SET enabled = 0, updated_at = ? WHERE id = ?", now(), id);
        if (updated == 0) {
            throw new BusinessException(404, "订阅不存在");
        }
        return true;
    }

    /** 删除订阅及其磁力 / 下载记录 */
    public void delete(long id) {
        requireExists(id);
        jdbcTemplate.update("DELETE FROM movie_subscription_magnet WHERE subscription_id = ?", id);
        jdbcTemplate.update("DELETE FROM movie_download_task WHERE subscription_id = ?", id);
        jdbcTemplate.update("DELETE FROM movie_subscription WHERE id = ?", id);
    }

    /** 订阅清单（默认只回启用中的；includeDisabled 为真时含已取消） */
    public List<Subscription> list(boolean includeDisabled) {
        String sql = "SELECT * FROM movie_subscription"
                + (includeDisabled ? "" : " WHERE enabled = 1")
                + " ORDER BY id DESC";
        List<Subscription> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql)) {
            list.add(toSubscription(row));
        }
        return list;
    }

    /** 按详情页 URL 查订阅（前端点「订阅」前判断是否已订阅） */
    public Subscription find(String detailUrl) {
        if (detailUrl == null || detailUrl.isBlank()) {
            return null;
        }
        Map<String, Object> row = findRowByUrl(movieSearchService.absoluteUrl(detailUrl.trim()));
        return row == null ? null : toSubscription(row);
    }

    /** 已订阅的详情页集合（搜索结果整批回填按钮状态用） */
    public Set<String> subscribedUrls() {
        Set<String> urls = new LinkedHashSet<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT detail_url FROM movie_subscription WHERE enabled = 1")) {
            urls.add(String.valueOf(row.get("detail_url")));
        }
        return urls;
    }

    // ------------------------------------------------------------------ 定时巡检

    /**
     * 巡检调度：每 10 分钟唤醒一次，挑出 next_check_at 到期的订阅。
     * 默认每个订阅每小时一轮（next_check_at = 上轮 + 1 小时，见 {@link #intervalMinutes()}）。
     */
    @Scheduled(fixedDelay = WAKE_MINUTES * 60 * 1000L, initialDelay = 60_000L)
    public void scheduledScan() {
        final List<Long> due;
        try {
            due = dueSubscriptions();
        } catch (Exception e) {
            log.warn("订阅巡检读取到期清单失败：{}", safeMessage(e));
            return;
        }
        if (due.isEmpty()) {
            return;
        }
        int handled = 0;
        for (Long id : due) {
            if (handled >= MAX_PER_ROUND) {
                break;
            }
            handled++;
            try {
                scan(id);
            } catch (Exception e) {
                log.warn("订阅巡检异常（订阅 {}）：{}", id, safeMessage(e));
            }
        }
    }

    /**
     * 下载跟踪调度：与巡检同频唤醒，把已提交的任务同步到 qB 实时状态，
     * 完成 / 失败时写「磁力下载」会话；下载完成后顺带补齐文件名里的剧名。
     */
    @Scheduled(fixedDelay = WAKE_MINUTES * 60 * 1000L, initialDelay = 90_000L)
    public void scheduledDownloadTracking() {
        try {
            refreshDownloads();
        } catch (Exception e) {
            log.warn("订阅下载跟踪失败：{}", safeMessage(e));
        }
    }

    /** 当前到期（next_check_at <= now）且启用中的订阅 id，按到期时间先后 */
    private List<Long> dueSubscriptions() {
        List<Long> ids = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT id FROM movie_subscription
                 WHERE enabled = 1
                   AND (next_check_at IS NULL OR next_check_at <= ?)
                 ORDER BY next_check_at IS NULL DESC, next_check_at, id
                 LIMIT ?
                """, now(), MAX_PER_ROUND)) {
            ids.add(number(row.get("id")));
        }
        return ids;
    }

    /**
     * 巡检单个订阅：抓详情页 → 与已存磁力比对 → 新增里按「每集取最高清晰度」提交下载 →
     * 写系统通知与「磁力下载」会话。
     *
     * @return 本轮新增并已入库的磁力条数
     */
    public int scan(long subscriptionId) {
        Map<String, Object> row = findRowById(subscriptionId);
        if (row == null) {
            return 0;
        }
        Subscription subscription = toSubscription(row);
        String url = subscription.detailUrl();
        String stamp = now();

        List<MovieSearchService.MagnetItem> found;
        try {
            found = movieSearchService.fetchMagnets(url);
        } catch (Exception e) {
            String error = safeMessage(e);
            jdbcTemplate.update("""
                    UPDATE movie_subscription
                       SET last_check_at = ?, next_check_at = ?, last_error = ?, updated_at = ?
                     WHERE id = ?
                    """, stamp, nextCheck(stamp), error, stamp, subscriptionId);
            log.warn("订阅巡检抓取失败（{}）：{}", url, error);
            return 0;
        }

        Set<String> known = knownKeys(subscriptionId);
        List<MovieSearchService.MagnetItem> fresh = new ArrayList<>();
        for (MovieSearchService.MagnetItem item : found) {
            String key = item.hash() == null ? item.magnet() : item.hash();
            if (!known.contains(key)) {
                fresh.add(item);
            }
        }

        jdbcTemplate.update("""
                UPDATE movie_subscription
                   SET last_check_at = ?, next_check_at = ?, last_error = NULL, updated_at = ?
                 WHERE id = ?
                """, stamp, nextCheck(stamp), stamp, subscriptionId);

        if (fresh.isEmpty()) {
            // 没有新剧集时也顺带重试此前失败（例如刚配好 qB）的提交
            retryFailed(subscription);
            return 0;
        }

        // 基线还没建起来（订阅时站点不可达）：这一轮直接作为基线，不触发下载，避免整季重下
        boolean baselineMissing = subscription.baselineAt() == null || subscription.baselineAt().isBlank();
        saveMagnets(subscriptionId, fresh, baselineMissing);
        if (baselineMissing) {
            jdbcTemplate.update("""
                    UPDATE movie_subscription SET baseline_at = ?, updated_at = ? WHERE id = ?
                    """, stamp, stamp, subscriptionId);
            log.info("订阅 {}（{}）补建基线 {} 条磁力，本轮不下载", subscriptionId, subscription.title(), fresh.size());
            return fresh.size();
        }

        // 已经处理过的集不再重复下（同一集之后补档的 1080P 不该在 2160P 之后又下一遍）
        Set<String> handledEpisodes = handledEpisodes(subscriptionId);
        List<MovieSearchService.MagnetItem> picks = new ArrayList<>();
        for (MovieSearchService.MagnetItem item : pickByEpisode(fresh)) {
            String episode = item.episode();
            if (episode != null && !episode.isBlank() && handledEpisodes.contains(episode)) {
                log.debug("订阅 {} 第 {} 集已有下载记录，跳过 {}", subscriptionId, episode, item.quality());
                continue;
            }
            picks.add(item);
        }
        int submitted = 0;
        List<String> downloadedTitles = new ArrayList<>();
        for (MovieSearchService.MagnetItem item : picks) {
            if (submitted >= MAX_DOWNLOADS_PER_ROUND) {
                break;
            }
            if (submit(subscription, item)) {
                submitted++;
                downloadedTitles.add(describe(item, subscription));
            }
        }
        // 之前失败（例如当时还没配置 qB）的条目在本轮顺手重试，避免要等下一次更新才有机会补下
        submitted += retryFailed(subscription);

        // 更新通知：写系统通知会话（消息中心的「系统通知」）
        StringBuilder content = new StringBuilder();
        content.append("【").append(subscription.title()).append("】采集到 ")
                .append(fresh.size()).append(" 条新剧集，已提交最高清晰度 ")
                .append(submitted).append(" 条：\n");
        for (String line : downloadedTitles) {
            content.append("• ").append(line).append('\n');
        }
        content.append("\n详情：").append(url);
        messageService.publish("电影港更新：" + subscription.title(), content.toString().trim(), "success", "movie");
        log.info("订阅 {}（{}）新增 {} 条、提交下载 {} 条", subscriptionId, subscription.title(), fresh.size(), submitted);
        return fresh.size();
    }

    /**
     * 该订阅下已经处理过（已提交 / 下载中 / 已完成）的集号集合。
     * 站点常在正片之后补挂低清晰度版本，据此避免同一集重复下载。
     */
    private Set<String> handledEpisodes(long subscriptionId) {
        Set<String> episodes = new LinkedHashSet<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT DISTINCT episode FROM movie_download_task
                 WHERE subscription_id = ?
                   AND episode IS NOT NULL AND episode <> ''
                   AND status IN ('added', 'downloading', 'completed')
                """, subscriptionId)) {
            episodes.add(String.valueOf(row.get("episode")));
        }
        return episodes;
    }

    /**
     * 同一集出现多个新条目时只取清晰度最高的（对齐参考脚本「仅 2160P/4K 自动下载」的意图，
     * 但订阅场景放宽为逐集取最高：有 2160P 就取 2160P，否则取该集最高的 1080P/720P…）。
     */
    static List<MovieSearchService.MagnetItem> pickByEpisode(List<MovieSearchService.MagnetItem> items) {
        Map<String, MovieSearchService.MagnetItem> best = new LinkedHashMap<>();
        int index = 0;
        for (MovieSearchService.MagnetItem item : items) {
            // 识别不出集号的条目各自独立（避免把整季打包条目与单集互相吞掉）
            String key = item.episode() == null || item.episode().isBlank() ? "#" + (index++) : item.episode();
            MovieSearchService.MagnetItem current = best.get(key);
            if (current == null || MovieSearchService.qualityRank(item.quality())
                    > MovieSearchService.qualityRank(current.quality())) {
                best.put(key, item);
            }
        }
        return new ArrayList<>(best.values());
    }

    // ------------------------------------------------------------------ 下载提交 / 通知

    /**
     * 重试此前提交失败的任务（如提交时 qB 未配置 / 临时网络错误）。
     * 只重试该订阅下最近失败的条目，成功即转为 added 并照常跟踪下载。
     *
     * @return 本轮重试成功的条数
     */
    private int retryFailed(Subscription subscription) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT * FROM movie_download_task
                 WHERE subscription_id = ? AND status = 'failed'
                 ORDER BY id DESC LIMIT ?
                """, subscription.id(), MAX_DOWNLOADS_PER_ROUND);
        String tool = subscription.downloader();
        if (rows.isEmpty() || !downloaderConfigured(tool)) {
            return 0;
        }
        int done = 0;
        for (Map<String, Object> row : rows) {
            try {
                Map<String, Object> result = submitOne(subscription.id(), subscription.title(),
                        string(row.get("episode")), string(row.get("quality")), string(row.get("magnet")),
                        string(row.get("torrent_hash")), savePathOf(subscription.title()));
                if (Boolean.TRUE.equals(result.get("ok"))) {
                    done++;
                }
            } catch (Exception e) {
                log.debug("重试提交失败（任务 {}）：{}", row.get("id"), safeMessage(e));
            }
        }
        return done;
    }

    /** 单条磁力提交：保存目录 = 根目录 / 剧名；同时落 movie_download_task 并通知「磁力下载」会话 */
    private boolean submit(Subscription subscription, MovieSearchService.MagnetItem item) {
        String magnet = item.magnet();
        if (alreadySubmitted(magnet)) {
            return false;
        }
        Map<String, Object> result = submitOne(subscription.id(), subscription.title(), item.episode(),
                item.quality(), magnet, item.hash(), savePathOf(subscription.title()));
        return Boolean.TRUE.equals(result.get("ok"));
    }

    /**
     * 提交一条磁力到订阅选择的下载工具（订阅巡检与手动下载共用）。
     *
     * <p>保存目录固定为「根目录 / 剧名子目录」，任务表里记录剧名与集号：
     * 之后下载跟踪补文件名、以及巡检去重（同一集不重复下）都依赖这两个字段。</p>
     *
     * @param subscriptionId 订阅 id；手动下载时可为 null（不参与订阅巡检去重）
     * @return { ok, duplicate, inserted, taskId, savePath, status, message }
     */
    private Map<String, Object> submitOne(Long subscriptionId, String seriesTitle, String episode,
                                          String quality, String magnet, String hash, String savePath) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("savePath", savePath);
        out.put("seriesTitle", seriesTitle);
        String tool = subscriptionId == null ? downloadSettings().downloader()
                : subscriptionDownloader(findRowById(subscriptionId));
        out.put("downloader", tool);
        String stamp = now();
        int inserted = jdbcTemplate.update("""
                INSERT INTO movie_download_task
                    (subscription_id, series_title, episode, quality, magnet, torrent_hash,
                     save_path, downloader, status, message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'pending', NULL, ?, ?)
                ON CONFLICT(magnet) DO NOTHING
                """, subscriptionId, seriesTitle, episode, quality, magnet, hash, savePath, tool, stamp, stamp);
        long taskId = taskIdOf(magnet);
        out.put("taskId", taskId);
        out.put("inserted", inserted > 0);

        String status;
        String headline;
        String externalTaskId = null;
        if (!downloaderConfigured(tool)) {
            status = "failed";
            headline = "⚠️ 未配置" + toolLabel(tool) + "，未提交下载";
        } else {
            Submission result = null;
            String failure = null;
            try {
                result = addDownload(tool, magnet, savePath);
            } catch (Exception e) {
                failure = safeMessage(e);
            }
            if (failure != null) {
                status = "failed";
                headline = "⚠️ 提交失败：" + failure;
            } else if (result != null && result.ok()) {
                status = "added";
                externalTaskId = result.externalTaskId();
                headline = (result.duplicate() ? "已在下载列表" : "✅ 已提交下载")
                        + (quality == null || quality.isBlank() ? "" : "：清晰度 " + quality);
            } else {
                status = "failed";
                headline = "⚠️ 提交失败：" + (result == null ? toolLabel(tool) + " 未返回结果" : result.message());
            }
        }
        // 重新提交（含自动重试此前失败的行）时解除隐藏，让被「清空下载记录」隐藏的条目重新可见。
        jdbcTemplate.update("""
                UPDATE movie_download_task SET status = ?, message = ?, save_path = ?, downloader = ?,
                       external_task_id = ?, hidden_at = NULL, updated_at = ?
                 WHERE id = ?
                """, status, headline, savePath, tool, externalTaskId, now(), taskId);

        String detail = describeParts(episode, quality, seriesTitle);
        magnetNotice(tool, headline, detail, savePath, "failed".equals(status) ? "config" : "ready");
        out.put("status", status);
        out.put("ok", !"failed".equals(status));
        out.put("headline", headline);
        return out;
    }

    /** 通知里的条目描述：集号 + 清晰度 + 标题 */
    static String describeParts(String episode, String quality, String title) {
        StringBuilder sb = new StringBuilder();
        if (episode != null && !episode.isBlank()) {
            sb.append("第 ").append(episode).append(" 集 ");
        }
        String grade = quality == null || quality.isBlank() ? "未知清晰度" : quality;
        sb.append('[').append(grade).append("] ");
        sb.append(title == null || title.isBlank() ? "未命名剧集" : title);
        return sb.toString();
    }

    /** 提交前重复判定：已完成 / 排队 / 下载中的不重复提交；提交失败（如未配置 qB）允许重试 */
    private boolean alreadySubmitted(String magnet) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM movie_download_task WHERE magnet = ? AND status <> 'failed'",
                Long.class, magnet);
        return count != null && count > 0;
    }

    /** 下载跟踪：把 added 的任务同步到 qB 的实时状态，完成时发「磁力下载」会话通知并补文件名 */
    public void refreshDownloads() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("""
                SELECT * FROM movie_download_task
                 WHERE status IN ('added', 'downloading')
                 ORDER BY id
                 LIMIT 50
                """);
        if (rows.isEmpty()) {
            return;
        }
        for (Map<String, Object> row : rows) {
            try {
                String tool = string(row.get("downloader"));
                if (!downloaderConfigured(tool)) {
                    continue;
                }
                if ("xunlei".equals(tool)) {
                    trackXunleiDownload(row);
                } else {
                    trackDownload(row);
                }
            } catch (Exception e) {
                log.debug("下载跟踪失败（任务 {}）：{}", row.get("id"), safeMessage(e));
            }
        }
    }

    private void trackXunleiDownload(Map<String, Object> row) {
        String externalId = string(row.get("external_task_id"));
        if (externalId == null || externalId.isBlank()) {
            return;
        }
        XunleiService.TaskStatus task = xunleiService.task(externalId);
        if (task == null) {
            return;
        }
        String status = switch (task.phase()) {
            case "PHASE_TYPE_COMPLETE" -> "completed";
            case "PHASE_TYPE_ERROR" -> "failed";
            case "PHASE_TYPE_RUNNING" -> "downloading";
            default -> "added";
        };
        if (status.equals(string(row.get("status")))) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE movie_download_task SET status = ?, message = ?, completed_at = ?, updated_at = ? WHERE id = ?
                """, status, task.message(), "completed".equals(status) ? now() : null, now(), number(row.get("id")));
        if ("completed".equals(status) || "failed".equals(status)) {
            String headline = ("completed".equals(status) ? "✅ 下载完成：" : "⚠️ 下载失败：")
                    + string(row.get("series_title"));
            magnetNotice("xunlei", headline, describeRow(row) + "\n" + task.message(),
                    string(row.get("save_path")), "failed".equals(status) ? "config" : "ready");
        }
    }

    private void trackDownload(Map<String, Object> row) {
        String hash = string(row.get("torrent_hash"));
        if (hash == null || hash.isBlank()) {
            return;
        }
        QBittorrentService.Torrent torrent = qBittorrentService.torrent(hash);
        if (torrent == null) {
            return;
        }
        long taskId = number(row.get("id"));
        String seriesTitle = string(row.get("series_title"));
        String state = torrent.state() == null ? "" : torrent.state();

        if (isCompletedState(state)) {
            String renameNote = renameFilesIfNeeded(hash, seriesTitle);
            jdbcTemplate.update("""
                    UPDATE movie_download_task
                       SET status = 'completed', message = ?, completed_at = ?, updated_at = ?
                     WHERE id = ?
                    """, renameNote, now(), now(), taskId);
            magnetNotice("✅ 下载完成：" + seriesTitle, describeRow(row) + renameNote,
                    string(row.get("save_path")), "ready");
            return;
        }
        if (isFailedState(state)) {
            jdbcTemplate.update("""
                    UPDATE movie_download_task SET status = 'failed', message = ?, updated_at = ? WHERE id = ?
                    """, QBittorrentService.stateLabel(state), now(), taskId);
            magnetNotice("⚠️ 下载失败：" + seriesTitle, describeRow(row) + "状态：" + QBittorrentService.stateLabel(state),
                    string(row.get("save_path")), "config");
            return;
        }
        if ("added".equals(string(row.get("status")))) {
            jdbcTemplate.update(
                    "UPDATE movie_download_task SET status = 'downloading', updated_at = ? WHERE id = ?",
                    now(), taskId);
        }
    }

    /** 下载完成后的文件名补全：文件名里没有剧名时加上剧名前缀（保留集号与清晰度） */
    private String renameFilesIfNeeded(String hash, String seriesTitle) {
        if (seriesTitle == null || seriesTitle.isBlank()) {
            return "";
        }
        List<QBittorrentService.TorrentFile> files;
        try {
            files = qBittorrentService.files(hash);
        } catch (Exception e) {
            return "\n（文件名检查失败：" + safeMessage(e) + "）";
        }
        int renamed = 0;
        List<String> problems = new ArrayList<>();
        for (QBittorrentService.TorrentFile file : files) {
            String path = file.name();
            if (path == null || path.isBlank() || path.endsWith("/")) {
                continue;
            }
            int slash = path.lastIndexOf('/');
            String dir = slash < 0 ? "" : path.substring(0, slash + 1);
            String name = slash < 0 ? path : path.substring(slash + 1);
            if (name.contains(seriesTitle)) {
                continue;
            }
            String target = withSeriesPrefix(name, seriesTitle);
            if (target.equals(name)) {
                continue;
            }
            String error = qBittorrentService.renameFile(hash, path, dir + target);
            if (error == null) {
                renamed++;
            } else {
                problems.add(target + "（" + error + "）");
            }
        }
        if (renamed == 0 && problems.isEmpty()) {
            return "\n文件名已包含剧名，无需补全。";
        }
        StringBuilder sb = new StringBuilder();
        if (renamed > 0) {
            sb.append("\n已为 ").append(renamed).append(" 个文件补上剧名「").append(seriesTitle).append("」。");
        }
        for (String problem : problems) {
            sb.append("\n补名失败：").append(problem);
        }
        return sb.toString();
    }

    /** 把「第5季212.2160p.mp4」补成「斗破苍穹 第5季212.2160p.mp4」（扩展名前加前缀，尽量保留站点原始结构） */
    static String withSeriesPrefix(String fileName, String seriesTitle) {
        if (fileName == null || fileName.isBlank() || seriesTitle == null || seriesTitle.isBlank()) {
            return fileName;
        }
        String trimmed = seriesTitle.trim();
        if (fileName.startsWith(trimmed)) {
            return fileName;
        }
        return trimmed + " " + fileName;
    }

    /**
     * 保存目录：根目录 + 剧名子目录，保证同一部剧归到一个文件夹、不会串到别的剧。
     *
     * <p>两种工具固定使用 {@link #DEFAULT_SAVE_ROOT}，不会被各工具自己的默认目录覆盖。</p>
     */
    String savePathOf(String seriesTitle) {
        return savePathUnder(DEFAULT_SAVE_ROOT, seriesTitle);
    }

    public record DownloadSettings(String downloader, String saveRoot, int intervalMinutes) { }

    public DownloadSettings downloadSettings() {
        String tool = "xunlei".equals(configValue(CFG_DOWNLOADER)) ? "xunlei" : "qb";
        return new DownloadSettings(tool, DEFAULT_SAVE_ROOT, intervalMinutes());
    }

    public DownloadSettings saveDownloadSettings(String downloader) {
        validateDownloader(downloader);
        new ConfigService(jdbcTemplate).put(CFG_DOWNLOADER, downloader);
        return downloadSettings();
    }

    private static void validateDownloader(String downloader) {
        if (!"qb".equals(downloader) && !"xunlei".equals(downloader)) {
            throw new BusinessException(400, "下载工具只能选择迅雷或 qBittorrent");
        }
    }

    private String subscriptionDownloader(Map<String, Object> row) {
        String tool = row == null ? null : text(string(row.get("downloader")));
        return "qb".equals(tool) || "xunlei".equals(tool) ? tool : downloadSettings().downloader();
    }

    public Subscription updateSubscriptionSettings(long id, String downloader) {
        validateDownloader(downloader);
        requireExists(id);
        jdbcTemplate.update("UPDATE movie_subscription SET downloader = ?, updated_at = ? WHERE id = ?",
                downloader, now(), id);
        return toSubscription(findRowById(id));
    }

    private boolean downloaderConfigured(String tool) {
        return "xunlei".equals(tool) ? xunleiService.settings().configured()
                : qBittorrentService.settings().configured();
    }

    private record Submission(boolean ok, boolean duplicate, String message, String externalTaskId) { }

    private Submission addDownload(String tool, String magnet, String savePath) {
        if ("xunlei".equals(tool)) {
            XunleiService.TaskResult result = xunleiService.add(List.of(magnet), savePath).getFirst();
            return new Submission(result.ok(), false, result.message(), result.taskId());
        }
        QBittorrentService.AddResult result = qBittorrentService.add(List.of(magnet), savePath);
        return new Submission(result.ok(), result.duplicate(), result.message(), null);
    }

    /** 纯函数版路径拼接（root 为空时用 {@link #DEFAULT_SAVE_ROOT}），便于单测与复用 */
    static String savePathUnder(String root, String seriesTitle) {
        String base = root == null || root.isBlank() ? DEFAULT_SAVE_ROOT : root.trim();
        while (base.endsWith("/") || base.endsWith("\\")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            base = DEFAULT_SAVE_ROOT;
        }
        return base + "/" + sanitizeFolder(seriesTitle);
    }

    /** 读取一条配置原始值（不存在返回 null） */
    private String configValue(String key) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT cfg_value FROM sys_config WHERE cfg_key = ?", key);
        return rows.isEmpty() ? null : string(rows.get(0).get("cfg_value"));
    }

    static String sanitizeFolder(String title) {
        String name = title == null ? "" : title.trim();
        if (name.isEmpty()) {
            return "未命名";
        }
        StringBuilder sb = new StringBuilder(name.length());
        for (int i = 0; i < name.length(); i++) {
            char ch = name.charAt(i);
            if ("/\\:*?\"<>|".indexOf(ch) >= 0 || Character.isISOControl(ch)) {
                sb.append('_');
            } else {
                sb.append(ch);
            }
        }
        String value = sb.toString().trim();
        return value.isEmpty() ? "未命名" : (".".equals(value) || "..".equals(value) ? "_" : value);
    }

    /** 系统通知之外，把「提交成功 / 下载成功」也写进「磁力下载」会话（与用户要求的会话位置一致）；
     *  写入成功后经 WebSocket 广播，让打开着的聊天页实时看到巡检产出 */
    private void magnetNotice(String headline, String detail, String savePath, String status) {
        magnetNotice("qb", headline, detail, savePath, status);
    }

    private static String toolLabel(String tool) {
        return "xunlei".equals(tool) ? "迅雷" : "qBittorrent";
    }

    private void magnetNotice(String tool, String headline, String detail, String savePath, String status) {
        String sessionId = "xunlei".equals(tool) ? "xunlei" : "magnet";
        String content = headline + "（" + toolLabel(tool) + "）"
                + (detail == null || detail.isBlank() ? "" : "\n" + detail)
                + (savePath == null || savePath.isBlank() ? "" : "\n保存位置：" + savePath);
        String payload = "{\"kind\":\"" + sessionId + "\",\"status\":\"" + status + "\",\"settings\":"
                + toJson("xunlei".equals(tool) ? xunleiService.describe() : qBittorrentService.describe()) + "}";
        String stamp = now();
        try {
            jdbcTemplate.update("""
                    INSERT INTO chat_message (session_id, role, type, content, content_text, payload, read_flag, created_at)
                    VALUES (?, 'bot', 'text', ?, NULL, ?, 0, ?)
                    """, sessionId, content, payload, stamp);
            jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", stamp, sessionId);
            Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
            if (id != null) {
                ChatService.ChatMessage row = new ChatService.ChatMessage(id, sessionId, "bot", "text",
                        content, null, payload, false, stamp);
                chatPushService.messages(sessionId, List.of(row));
            }
        } catch (Exception e) {
            log.warn("写入 qBittorrent 下载会话失败：{}", safeMessage(e));
        }
    }

    // ------------------------------------------------------------------ 磁力入库

    /** 把详情页磁力写入订阅基线表；返回新入库条数 */
    private int saveMagnets(long subscriptionId, List<MovieSearchService.MagnetItem> magnets, boolean baseline) {
        int added = 0;
        String stamp = now();
        for (MovieSearchService.MagnetItem item : magnets) {
            String key = item.hash() == null ? item.magnet() : item.hash();
            int inserted = jdbcTemplate.update("""
                    INSERT INTO movie_subscription_magnet
                        (subscription_id, title, magnet, magnet_key, quality, episode, baseline, first_seen_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT(subscription_id, magnet_key) DO NOTHING
                    """, subscriptionId, item.title(), item.magnet(), key, item.quality(),
                    item.episode(), baseline ? 1 : 0, stamp);
            added += inserted;
        }
        return added;
    }

    /** 已采集的磁力去重键集合（info hash 优先，无 hash 时用磁力原文） */
    private Set<String> knownKeys(long subscriptionId) {
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT magnet_key FROM movie_subscription_magnet WHERE subscription_id = ?", subscriptionId)) {
            keys.add(String.valueOf(row.get("magnet_key")));
        }
        return keys;
    }

    /** 订阅磁力明细（前端订阅页展开查看；带磁力原文与下载状态，可直接点按钮下载） */
    public List<Map<String, Object>> magnets(long subscriptionId) {
        Set<String> downloaded = downloadedKeys();
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT id, title, magnet, magnet_key, quality, episode, baseline, first_seen_at
                  FROM movie_subscription_magnet
                 WHERE subscription_id = ?
                 ORDER BY first_seen_at DESC, id DESC
                 LIMIT 500
                """, subscriptionId)) {
            String magnet = string(row.get("magnet"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("title", row.get("title"));
            item.put("magnet", magnet);
            item.put("hash", row.get("magnet_key"));
            item.put("quality", row.get("quality"));
            item.put("episode", row.get("episode"));
            item.put("baseline", number(row.get("baseline")) == 1);
            item.put("downloaded", isDownloaded(downloaded, magnet, string(row.get("magnet_key"))));
            item.put("firstSeenAt", row.get("first_seen_at"));
            list.add(item);
        }
        return list;
    }

    /** 下载去重判定：info hash 优先，无 hash 时退化为磁力原文比较 */
    private static boolean isDownloaded(Set<String> keys, String magnet, String hash) {
        if (hash != null && keys.contains(hash.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (magnet == null || magnet.isBlank()) {
            return false;
        }
        String computed = MovieSearchService.hashOf(magnet);
        return keys.contains(computed == null ? magnet : computed.toLowerCase(Locale.ROOT));
    }

    /** 按订阅磁力记录 id 手动下载（订阅页磁力列表的下载按钮） */
    public Map<String, Object> downloadStoredMagnet(long magnetId) {
        Map<String, Object> row = jdbcTemplate.queryForList("""
                SELECT m.*, s.title AS series_title, s.detail_url AS detail_url
                  FROM movie_subscription_magnet m
                  JOIN movie_subscription s ON s.id = m.subscription_id
                 WHERE m.id = ?
                """, magnetId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(404, "磁力记录不存在"));
        return downloadMagnet(string(row.get("detail_url")), string(row.get("series_title")),
                string(row.get("magnet")), string(row.get("quality")), string(row.get("episode")));
    }

    // ------------------------------------------------------------------ 详情页磁力（结果卡片「资源列表」）

    /**
     * 抓取某部剧详情页的磁力清单（不落基线库），供搜索结果卡片的「资源列表」使用。
     *
     * <p>返回结果里带 {@code downloaded} 标记：该 info hash 是否已经在下载任务表里
     * （非 failed 状态），前端据此把按钮渲染成「已下载」避免重复提交。</p>
     *
     * <p>列表按集数倒序返回（最新一集在最前），同集保持详情页原始顺序；
     * 无法识别集号的条目排在最后，仅影响「资源列表」弹窗，不改变订阅巡检使用的详情页顺序。</p>
     *
     * @param detailUrl  电影港详情页地址
     * @param seriesTitle 片名（搜索结果里的名字，作为剧名与目录名基准）
     */
    public Map<String, Object> detailMagnets(String detailUrl, String seriesTitle) {
        String url = movieSearchService.absoluteUrl(detailUrl == null ? "" : detailUrl.trim());
        if (url.isBlank() || "null".equals(url)) {
            throw new BusinessException(400, "缺少电影港详情页链接，无法获取资源列表");
        }
        MovieSearchService.DetailPage detail;
        try {
            detail = movieSearchService.fetchDetail(url);
        } catch (Exception e) {
            throw new BusinessException(502, "抓取详情页失败：" + safeMessage(e));
        }
        String title = text(seriesTitle);
        if (title == null) {
            title = text(detail.title());
        }
        if (title == null) {
            title = movieSearchService.idFrom(url) == null ? "未命名剧集" : ("电影港 " + movieSearchService.idFrom(url));
        }
        Subscription subscription = findRowByUrl(url) == null ? null : toSubscription(findRowByUrl(url));
        if (subscription != null) {
            title = subscription.title();
        }

        Set<String> downloaded = downloadedKeys();
        // 「资源列表」按集数倒序（最新一集在最前）：详情页原顺序保留给订阅巡检，不在这里改动
        List<MovieSearchService.MagnetItem> magnets = new ArrayList<>(detail.magnets());
        magnets.sort(MovieSubscriptionService::compareEpisodeDesc);
        List<Map<String, Object>> items = new ArrayList<>();
        for (MovieSearchService.MagnetItem item : magnets) {
            String key = item.hash() == null ? item.magnet() : item.hash();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("title", item.title());
            row.put("magnet", item.magnet());
            row.put("hash", item.hash());
            row.put("quality", item.quality());
            row.put("episode", item.episode());
            row.put("downloaded", downloaded.contains(key));
            items.add(row);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", url);
        data.put("title", title);
        data.put("siteTitle", detail.title());
        data.put("subscribed", subscription != null && subscription.enabled());
        data.put("subscriptionId", subscription == null ? null : subscription.id());
        data.put("savePath", savePathOf(title));
        data.put("downloader", subscriptionDownloader(findRowByUrl(url)));
        data.put("count", items.size());
        data.put("magnets", items);
        return data;
    }

    /**
     * 「资源列表」弹窗的排序：按集数倒序，最新一集在最前；识别不出集号的条目排最后。
     *
     * <p>同一集的多条磁力保持详情页内的相对顺序（稳定排序）。集号按数字比较，
     * 因此「第 10 集」排在「第 9 集」前面，而不是按字符串比较。</p>
     */
    static int compareEpisodeDesc(MovieSearchService.MagnetItem left, MovieSearchService.MagnetItem right) {
        Integer leftEpisode = episodeNumber(left == null ? null : left.episode());
        Integer rightEpisode = episodeNumber(right == null ? null : right.episode());
        if (leftEpisode == null && rightEpisode == null) {
            return 0;
        }
        if (leftEpisode == null) {
            return 1;
        }
        if (rightEpisode == null) {
            return -1;
        }
        return Integer.compare(rightEpisode, leftEpisode);
    }

    /** 集号转数字用于倒序比较；非数字集号（如特别篇编号）按无法识别处理，排在最后 */
    private static Integer episodeNumber(String episode) {
        if (episode == null || episode.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(episode.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 找到某条磁力在详情页里的原始标题（用于推断集号 / 清晰度）。
     *
     * <p>前端「资源列表」已经带有标题，正常路径不会走到这里；仅当调用方只给了磁力原文时，
     * 才回查一次详情页（失败返回 null，由调用方用 OTHER 兜底）。</p>
     */
    private String magnetTitleOf(String url, String hash, String magnet) {
        if (url == null || url.isBlank() || "null".equals(url)) {
            return null;
        }
        try {
            for (MovieSearchService.MagnetItem item : movieSearchService.fetchMagnets(url)) {
                if (hash != null && hash.equalsIgnoreCase(item.hash())) {
                    return item.title();
                }
                if (magnet != null && magnet.equals(item.magnet())) {
                    return item.title();
                }
            }
        } catch (Exception e) {
            log.debug("回查详情页推断集号失败：{}", safeMessage(e));
        }
        return null;
    }

    /** 已进入下载流程的磁力键集合（info hash 优先，无 hash 用磁力原文），用于去重展示 */
    private Set<String> downloadedKeys() {
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT magnet, torrent_hash FROM movie_download_task WHERE status <> 'failed'")) {
            String hash = text(string(row.get("torrent_hash")));
            if (hash != null) {
                keys.add(hash.toLowerCase(Locale.ROOT));
            }
            String magnet = text(string(row.get("magnet")));
            if (magnet != null) {
                String computed = MovieSearchService.hashOf(magnet);
                keys.add(computed == null ? magnet : computed.toLowerCase(Locale.ROOT));
            }
        }
        return keys;
    }

    /**
     * 手动下载单条磁力（结果卡片「资源列表」列表里的下载按钮）。
     *
     * <p>与订阅巡检共用同一套提交逻辑，因此同样保证：</p>
     * <ul>
     *   <li>保存目录 = 下载根目录 / 剧名子目录（同名剧归到同一文件夹）；</li>
     *   <li>任务表记录剧名与集号，下载完成的文件名补全与结果通知照常生效；</li>
     *   <li>提交结果写入「磁力下载」会话。</li>
     * </ul>
     *
     * @param detailUrl   详情页地址（用于关联订阅与兜底剧名）
     * @param seriesTitle 片名（决定子目录名与文件前缀）
     * @param magnet      磁力链接原文
     * @param quality     清晰度（可为空，从标题推断兜底）
     * @param episode     集号（可为空，从标题推断兜底）
     * @return 提交结果（含保存目录、状态与提示文案）
     */
    public Map<String, Object> downloadMagnet(String detailUrl, String seriesTitle, String magnet,
                                              String quality, String episode) {
        String link = text(magnet);
        if (link == null || !link.toLowerCase(Locale.ROOT).startsWith("magnet:?xt=urn:btih:")) {
            throw new BusinessException(400, "磁力链接格式不正确");
        }
        String url = movieSearchService.absoluteUrl(detailUrl == null ? "" : detailUrl.trim());
        Map<String, Object> subscriptionRow = url.isBlank() ? null : findRowByUrl(url);
        Long subscriptionId = subscriptionRow == null ? null : number(subscriptionRow.get("id"));

        String title = subscriptionRow == null ? text(seriesTitle) : text(string(subscriptionRow.get("title")));
        if (title == null) {
            title = text(seriesTitle);
        }
        if (title == null) {
            String id = movieSearchService.idFrom(url);
            title = id == null ? "未命名剧集" : ("电影港 " + id);
        }

        String hash = MovieSearchService.hashOf(link);
        String grade = text(quality);
        String ep = text(episode);
        // 前端可能只给磁力原文：用站点标题兜底推断集号 / 清晰度，保证任务行与通知信息完整
        if (grade == null || grade.isBlank() || ep == null || ep.isBlank()) {
            String probe = magnetTitleOf(url, hash, link);
            if (grade == null || grade.isBlank()) {
                grade = probe == null ? null : MovieSearchService.qualityOf(probe);
            }
            if (ep == null || ep.isBlank()) {
                ep = probe == null ? null : MovieSearchService.episodeOf(probe);
            }
        }
        if (grade == null || grade.isBlank()) {
            grade = "OTHER";
        }
        String savePath = savePathOf(title);
        if (alreadySubmitted(link)) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("ok", true);
            data.put("duplicate", true);
            data.put("status", "added");
            data.put("savePath", savePath);
            data.put("seriesTitle", title);
            data.put("episode", ep);
            data.put("quality", grade);
            data.put("message", "该磁力已在下载列表中，未重复提交。");
            return data;
        }
        Map<String, Object> result = submitOne(subscriptionId, title, ep, grade, link, hash, savePath);
        Map<String, Object> data = new LinkedHashMap<>(result);
        data.put("episode", ep);
        data.put("quality", grade);
        data.put("message", result.get("ok") != Boolean.TRUE
                ? String.valueOf(result.get("headline"))
                : "已提交下载：" + describeParts(ep, grade, title) + "，保存到 " + savePath);
        return data;
    }

    /** 下载任务明细（订阅页查看进度；默认最近 100 条，已清空的历史行不再返回） */
    public List<Map<String, Object>> downloads(long subscriptionId) {
        StringBuilder sql = new StringBuilder("SELECT * FROM movie_download_task WHERE hidden_at IS NULL");
        List<Object> params = new ArrayList<>();
        if (subscriptionId > 0) {
            sql.append(" AND subscription_id = ?");
            params.add(subscriptionId);
        }
        sql.append(" ORDER BY id DESC LIMIT 100");
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), params.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("subscriptionId", row.get("subscription_id"));
            item.put("seriesTitle", row.get("series_title"));
            item.put("episode", row.get("episode"));
            item.put("quality", row.get("quality"));
            item.put("status", row.get("status"));
            item.put("downloader", row.get("downloader"));
            item.put("message", row.get("message"));
            item.put("savePath", row.get("save_path"));
            item.put("createdAt", row.get("created_at"));
            item.put("completedAt", row.get("completed_at"));
            list.add(item);
        }
        return list;
    }

    /**
     * 清空下载记录：只隐藏终态行（已完成 / 失败 / 已移除），
     * 已提交与下载中的任务保留在列表里；隐藏而非删除，
     * 重复提交判定与订阅巡检去重仍依赖这些历史行。
     */
    public Map<String, Object> clearDownloads() {
        int hidden = jdbcTemplate.update("""
                UPDATE movie_download_task SET hidden_at = ?
                 WHERE hidden_at IS NULL AND status NOT IN ('pending', 'added', 'downloading')
                """, now());
        Integer kept = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM movie_download_task WHERE hidden_at IS NULL", Integer.class);
        int keptInt = kept == null ? 0 : kept;
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hidden", hidden);
        result.put("kept", keptInt);
        result.put("message", hidden > 0
                ? "已清空 " + hidden + " 条下载记录" + (keptInt > 0 ? "，保留 " + keptInt + " 条进行中的任务" : "")
                : keptInt > 0 ? "进行中的任务不会被清空，当前没有可清理的下载记录" : "暂无下载记录");
        return result;
    }

    // ------------------------------------------------------------------ 工具

    /** 巡检间隔（分钟）：sys_config 的 movie.subscribe.interval-minutes，缺省 60 分钟 */
    private int intervalMinutes() {
        int value = configInt("movie.subscribe.interval-minutes", DEFAULT_INTERVAL_MINUTES);
        return value <= 0 ? DEFAULT_INTERVAL_MINUTES : value;
    }

    private int configInt(String key, int defaultValue) {
        String value = text(configValue(key));
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String nextCheck(String from) {
        return LocalDateTime.parse(from, TIME_FORMAT).plusMinutes(intervalMinutes()).format(TIME_FORMAT);
    }

    private Map<String, Object> findRowByUrl(String url) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM movie_subscription WHERE detail_url = ?", url);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Map<String, Object> findRowById(long id) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM movie_subscription WHERE id = ?", id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 详情页 URL 反查订阅 id（用于“先入库再回查”的场景） */
    private long subscriptionIdOf(String detailUrl) {
        Map<String, Object> row = findRowByUrl(detailUrl);
        return row == null ? 0L : number(row.get("id"));
    }

    private void requireExists(long id) {
        if (findRowById(id) == null) {
            throw new BusinessException(404, "订阅不存在");
        }
    }

    private long lastInsertId() {
        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        return id == null ? 0L : id;
    }

    /** 取某条磁力对应的下载任务 id（磁力列有唯一索引） */
    private long taskIdOf(String magnet) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id FROM movie_download_task WHERE magnet = ?", magnet);
        return rows.isEmpty() ? 0L : number(rows.get(0).get("id"));
    }


    private Subscription toSubscription(Map<String, Object> row) {
        long id = number(row.get("id"));
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM movie_subscription_magnet WHERE subscription_id = ?", Long.class, id);
        return new Subscription(id, string(row.get("res_id")), string(row.get("title")),
                string(row.get("year")), string(row.get("origin")), string(row.get("genre")),
                string(row.get("cover")), string(row.get("detail_url")), number(row.get("enabled")) == 1,
                count == null ? 0 : count.intValue(), string(row.get("baseline_at")),
                string(row.get("last_check_at")), string(row.get("next_check_at")), string(row.get("last_error")),
                subscriptionDownloader(row), string(row.get("created_at")));
    }

    /** 描述一条磁力（用于通知 / 下载列表）：集号 + 清晰度 + 标题 */
    static String describe(MovieSearchService.MagnetItem item, Subscription subscription) {
        StringBuilder sb = new StringBuilder();
        if (item.episode() != null && !item.episode().isBlank()) {
            sb.append("第 ").append(item.episode()).append(" 集 ");
        }
        sb.append('[').append(item.quality()).append("] ");
        String title = item.title();
        sb.append(title == null || title.isBlank()
                ? (subscription == null ? "未命名剧集" : subscription.title())
                : title);
        return sb.toString();
    }

    private static String describeRow(Map<String, Object> row) {
        StringBuilder sb = new StringBuilder();
        String episode = string(row.get("episode"));
        if (episode != null && !episode.isBlank()) {
            sb.append("第 ").append(episode).append(" 集 ");
        }
        sb.append('[').append(string(row.get("quality"))).append("] ");
        sb.append(string(row.get("series_title")));
        return sb.toString();
    }

    /** 完成状态：qB 的做种 / 上传阶段都算下载完成 */
    static boolean isCompletedState(String state) {
        if (state == null) {
            return false;
        }
        return switch (state) {
            case "uploading", "stalledUP", "pausedUP", "stoppedUP", "queuedUP",
                 "checkingUP", "forcedUP", "moving" -> true;
            default -> false;
        };
    }

    static boolean isFailedState(String state) {
        return "error".equals(state) || "missingFiles".equals(state);
    }

    private static String text(String value) {
        return value == null ? null : value.trim();
    }

    private String now() {
        return LocalDateTime.now().format(TIME_FORMAT);
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private String toJson(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(entry.getKey()).append("\":");
            Object value = entry.getValue();
            if (value == null) {
                sb.append("null");
            } else if (value instanceof Number || value instanceof Boolean) {
                sb.append(value);
            } else {
                sb.append('"').append(String.valueOf(value).replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            }
        }
        return sb.append('}').toString();
    }
}
