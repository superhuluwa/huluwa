package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.HlsProperties;
import com.huluwa.console.web.HlsRequests;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** 单容器、单工作线程的持久化 HLS 队列，进程输出与媒体分片不进入正式影视目录。 */
@Service
@Order(2)
public class HlsDownloadService implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(HlsDownloadService.class);
    private final JdbcTemplate db;
    private final ConfigService config;
    private final HlsProperties paths;
    private final DyttSearchService source;
    private final HlsNoticeService notices;
    private final ObjectMapper json;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "hls-download"); thread.setDaemon(true); return thread;
    });
    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile boolean ready;
    private volatile boolean stopping;
    private volatile Process activeProcess;
    private record ActiveProgress(long taskId, HlsDownloadProgress progress) { }
    private volatile ActiveProgress activeProgress;

    public HlsDownloadService(JdbcTemplate db, ConfigService config, HlsProperties paths,
                              DyttSearchService source, HlsNoticeService notices, ObjectMapper json) {
        this.db = db; this.config = config; this.paths = paths;
        this.source = source; this.notices = notices; this.json = json;
    }

    public record Settings(int threadCount, int maxAttempts, int timeoutMinutes, String format,
                           String maxSpeed, String mediaRoot, boolean toolsReady, boolean mediaWritable) { }

    public synchronized Settings settings() {
        return new Settings(config.getInt("hls.threads", 8), config.getInt("hls.attempts", 3),
                config.getInt("hls.timeout", 180), config.get("hls.format", "mp4"),
                config.get("hls.speed", ""), root().toString(), toolsReady(), Files.isDirectory(root()) && Files.isWritable(root()));
    }

    public synchronized Settings saveSettings(HlsRequests.Settings body) {
        int threads = HlsSupport.range(body.threadCount(), 8, 1, 32, "分片线程数");
        int attempts = HlsSupport.range(body.maxAttempts(), 3, 1, 10, "尝试次数");
        int timeout = HlsSupport.range(body.timeoutMinutes(), 180, 5, 1440, "任务超时分钟数");
        String format = HlsSupport.text(body.format()).toLowerCase(Locale.ROOT);
        if (!List.of("mp4", "mkv").contains(format)) throw new BusinessException(400, "封装格式请选择 MP4 或 MKV");
        String speed = HlsSupport.text(body.maxSpeed()).trim().toUpperCase(Locale.ROOT);
        if (!speed.isEmpty() && !speed.matches("[1-9][0-9]*(?:\\.[0-9]+)?[MK]")) {
            throw new BusinessException(400, "限速请填写 15M 或 500K，留空不限速");
        }
        config.put("hls.threads", Integer.toString(threads));
        config.put("hls.attempts", Integer.toString(attempts));
        config.put("hls.timeout", Integer.toString(timeout));
        config.put("hls.format", format); config.put("hls.speed", speed);
        return settings();
    }

    public void requireEnvironment() {
        if (!toolsReady()) throw new BusinessException(400, "当前运行环境缺少 HLS 下载工具，请先更新包含下载工具的 Docker 镜像");
        if (!Files.isDirectory(root()) || !Files.isWritable(root())) {
            throw new BusinessException(400, "媒体目录不存在或无法写入，请检查媒体挂载及容器 UID 10001 的权限");
        }
    }

    private boolean toolsReady() {
        return Files.isExecutable(Path.of(paths.getBinary())) && Files.isExecutable(Path.of(paths.getFfmpeg()))
                && Files.isExecutable(Path.of(paths.getFfprobe()));
    }

    public List<Map<String, Object>> list() {
        List<Map<String, Object>> tasks = db.queryForList("""
                SELECT t.id, t.subscription_id AS subscriptionId, t.series_title AS seriesTitle,
                       t.season, t.episode, t.episode_title AS episodeTitle, t.source_name AS sourceName,
                       t.status, t.attempts, t.next_attempt_at AS nextAttemptAt, t.save_path AS savePath,
                       t.message, t.created_at AS createdAt, t.completed_at AS completedAt,
                       CASE WHEN s.enabled = 0 THEN 1 ELSE 0 END AS paused
                  FROM hls_download_task t LEFT JOIN hls_subscription s ON s.id = t.subscription_id
                 ORDER BY t.id DESC LIMIT 500
                """);
        ActiveProgress active = activeProgress;
        for (Map<String, Object> task : tasks) {
            String status = HlsSupport.text(task.get("status"));
            boolean running = status.equals("downloading") || status.equals("verifying");
            HlsDownloadProgress.Snapshot progress = running && active != null
                    && active.taskId() == HlsSupport.number(task.get("id")) ? active.progress().snapshot() : null;
            Double percent = null;
            if (status.equals("completed")) percent = 100d;
            else if (status.equals("verifying")) percent = 99d;
            else if (progress != null) percent = progress.percent();
            task.put("progressPercent", percent);
            task.put("downloadSpeed", status.equals("downloading") && progress != null ? progress.speed() : "");
            task.put("phase", status.equals("verifying") ? "verifying"
                    : status.equals("downloading") ? progress == null ? "parsing" : progress.phase() : status);
        }
        return tasks;
    }

    /** 仅返回归属于该任务的脱敏日志尾部，不接受客户端传入文件路径。 */
    public Map<String, String> log(long id) {
        Map<String, Object> task = task(id);
        if (task.get("log_path") == null) return Map.of("content", "暂无下载日志，请查看任务状态与提示。");
        try {
            Path file = Path.of(task.get("log_path").toString());
            if (!Files.exists(file)) return Map.of("content", "日志文件已不存在。");
            if (!managedPath(file, ".huluwa-work")) {
                throw new IOException("日志路径无效");
            }
            String content = Files.readString(file, StandardCharsets.UTF_8);
            return Map.of("content", content.substring(Math.max(0, content.length() - 20000)));
        } catch (IOException e) { throw new BusinessException(400, "无法读取任务日志：" + HlsSupport.error(e)); }
    }

    public Map<String, Object> enqueue(HlsRequests.Download body) {
        requireEnvironment();
        String title = HlsSupport.safeName(HlsSupport.required(body.title(), "标准剧名"));
        String year = year(body.year());
        int season = HlsSupport.range(body.season(), 1, 1, 999, "季号");
        if (body.episode() == null) throw new BusinessException(400, "请确认下载剧集的集号");
        int episode = HlsSupport.range(body.episode(), 1, 1, 9999, "集号");
        String resId = body.resId() == null || body.resId().isBlank() ? "" : HlsSupport.resourceId(body.resId());
        String api = resId.isBlank() ? "" : HlsSupport.providerUrl(body.apiUrl());
        String key = HlsSupport.seriesKey(api, resId, title, year);
        String url = m3u8(body.url());
        List<Map<String, Object>> subscriptions = db.queryForList(
                "SELECT id, headers FROM hls_subscription WHERE series_key = ? AND season = ?", key, season);
        Long subscription = subscriptions.isEmpty() ? null : HlsSupport.number(subscriptions.getFirst().get("id"));
        String previous = subscriptions.isEmpty() ? null : HlsSupport.text(subscriptions.getFirst().get("headers"));
        Map<String, String> saved = HlsSupport.headerMap(previous);
        String referer = body.referer() == null || body.referer().isBlank() ? saved.get("Referer") : body.referer();
        String userAgent = body.userAgent() == null || body.userAgent().isBlank() ? saved.get("User-Agent") : body.userAgent();
        String headers = HlsSupport.headers(referer, userAgent, body.cookie(), previous);
        String sourceName = HlsSupport.text(body.sourceName());
        if (!resId.isBlank()) {
            DyttSearchService.PlaySource chosen = selectSource(source.detail(api, resId), sourceName);
            sourceName = chosen.name();
            // 卡片地址可能已失效；入队时也从完整详情重新解析。
            url = episodeUrl(chosen, season, episode);
        }
        long id = discover(subscription, key, api, resId, title, year, season, episode,
                HlsSupport.text(body.episodeTitle()), sourceName, url, headers, false);
        retrySkipped(id);
        kick();
        return publicTask(id);
    }

    /** 唯一键与 URL 无关；换线路、临时签名变化不会产生重复剧集任务。 */
    public long discover(Long subscription, String key, String api, String resId,
                                      String title, String year, int season, int episode, String episodeTitle,
                                      String sourceName, String url, String headers, boolean skip) {
        String now = HlsSupport.now();
        db.update("""
                INSERT OR IGNORE INTO hls_download_task(subscription_id, series_key, api_url, res_id,
                    series_title, year, season, episode, episode_title, source_name, url, headers,
                    status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, subscription, key, api, resId, title, year, season, episode, episodeTitle,
                sourceName, m3u8(url), headers, skip ? "skipped" : "pending", now, now);
        db.update("""
                UPDATE hls_download_task SET subscription_id = COALESCE(?, subscription_id),
                    api_url = ?, res_id = ?, url = ?, source_name = ?, headers = ?,
                    series_title = ?, year = ?, episode_title = ?, updated_at = ?
                 WHERE series_key = ? AND season = ? AND episode = ?
                   AND status NOT IN ('downloading', 'verifying', 'completed')
                """, subscription, api, resId, url, sourceName, headers, title, year, episodeTitle, now, key, season, episode);
        if (subscription != null) db.update("""
                UPDATE hls_download_task SET subscription_id = ?
                 WHERE series_key = ? AND season = ? AND episode = ? AND subscription_id IS NULL
                """, subscription, key, season, episode);
        return db.queryForObject("SELECT id FROM hls_download_task WHERE series_key = ? AND season = ? AND episode = ?",
                Long.class, key, season, episode);
    }

    private synchronized void retrySkipped(long id) {
        db.update("UPDATE hls_download_task SET status = 'pending', message = NULL, updated_at = ? WHERE id = ? AND status = 'skipped'",
                HlsSupport.now(), id);
    }

    public synchronized Map<String, Object> retry(long id) {
        requireEnvironment();
        Map<String, Object> task = task(id);
        if (!List.of("failed", "skipped").contains(HlsSupport.text(task.get("status")))) {
            throw new BusinessException(400, "只有失败或跳过的任务可以重新下载");
        }
        db.update("""
                UPDATE hls_download_task SET status = 'pending', attempts = 0, next_attempt_at = NULL,
                       message = NULL, updated_at = ? WHERE id = ? AND status IN ('failed', 'skipped')
                """, HlsSupport.now(), id);
        kick(); return publicTask(id);
    }

    private Map<String, Object> publicTask(long id) {
        return list().stream().filter(item -> HlsSupport.number(item.get("id")) == id).findFirst()
                .orElseGet(() -> Map.of("id", id, "message", "该剧集已有下载记录"));
    }

    private Map<String, Object> task(long id) {
        List<Map<String, Object>> rows = db.queryForList("SELECT * FROM hls_download_task WHERE id = ?", id);
        if (rows.isEmpty()) throw new BusinessException(404, "下载任务不存在");
        return rows.getFirst();
    }

    @Override
    public void run(ApplicationArguments args) {
        // 已归档但尚未记成功的任务也回到队列，执行时先检查目标文件再决定是否下载。
        db.update("""
                UPDATE hls_download_task SET status = 'pending', next_attempt_at = NULL,
                       message = '应用重启后继续处理', updated_at = ?
                 WHERE status IN ('downloading', 'verifying')
                """, HlsSupport.now());
        ready = true; kick();
    }

    @Scheduled(initialDelay = 15000, fixedDelay = 15000)
    public void kick() {
        if (!ready || stopping || !busy.compareAndSet(false, true)) return;
        worker.submit(() -> {
            try {
                while (!stopping) {
                    List<Map<String, Object>> due = db.queryForList("""
                            SELECT t.id FROM hls_download_task t
                              LEFT JOIN hls_subscription s ON s.id = t.subscription_id
                             WHERE t.status = 'pending' AND (t.next_attempt_at IS NULL OR t.next_attempt_at <= ?)
                               AND (t.subscription_id IS NULL OR s.enabled = 1)
                             ORDER BY t.id LIMIT 1
                            """, HlsSupport.now());
                    if (due.isEmpty()) break;
                    long id = HlsSupport.number(due.getFirst().get("id"));
                    if (db.update("""
                            UPDATE hls_download_task SET status = 'downloading', attempts = attempts + 1, updated_at = ?
                             WHERE id = ? AND status = 'pending'
                               AND (subscription_id IS NULL OR EXISTS (
                                   SELECT 1 FROM hls_subscription s WHERE s.id = subscription_id AND s.enabled = 1))
                            """, HlsSupport.now(), id) == 1) download(id);
                }
            } catch (Exception e) { log.warn("HLS 队列处理失败：{}", HlsSupport.error(e)); }
            finally { busy.set(false); }
        });
    }

    private void download(long id) {
        Map<String, Object> task = task(id);
        String title = HlsSupport.text(task.get("series_title"));
        int season = (int) HlsSupport.number(task.get("season"));
        int episode = (int) HlsSupport.number(task.get("episode"));
        String name = HlsSupport.filename(title, season, episode);
        ActiveProgress progress = new ActiveProgress(id, new HlsDownloadProgress());
        activeProgress = progress;
        try {
            requireEnvironment();
            Settings settings = settings();
            Path archive = directory(root().resolve(HlsSupport.safeName(title)));
            // 两种封装都查一遍，避免修改默认格式后重复下载已完成的文件。
            Path previous = task.get("save_path") == null ? null : Path.of(task.get("save_path").toString());
            if (previous != null && Files.exists(previous) && managedPath(previous, null)) {
                verify(previous); complete(id, previous, name); return;
            }
            for (String extension : List.of("mp4", "mkv")) {
                Path existing = archive.resolve(name + "." + extension);
                if (Files.exists(existing, LinkOption.NOFOLLOW_LINKS)) {
                    verify(existing); complete(id, existing, name); return;
                }
            }
            Path workspace = directory(root().resolve(".huluwa-work").resolve(Long.toString(id)));
            // 每次尝试使用独立目录，不把失败尝试中的合并残片误认为本次结果。
            Path attempt = directory(workspace.resolve("attempt-" + task.get("attempts") + "-" + UUID.randomUUID()));
            Path output = directory(attempt.resolve("output"));
            Path segments = directory(attempt.resolve("segments"));
            Path taskLog = attempt.resolve("download.log");
            db.update("UPDATE hls_download_task SET log_path = ?, updated_at = ? WHERE id = ?", taskLog.toString(), HlsSupport.now(), id);
            String url = freshUrl(task);
            List<String> command = new ArrayList<>(List.of(paths.getBinary(), url,
                    "--save-name", name, "--save-dir", output.toString(), "--tmp-dir", segments.toString(),
                    "--auto-select", "--thread-count", Integer.toString(settings.threadCount()),
                    "--download-retry-count", "3", "--http-request-timeout", "60", "--check-segments-count", "true",
                    "--mux-after-done", "format=" + settings.format() + ":muxer=ffmpeg",
                    "--ffmpeg-binary-path", paths.getFfmpeg(), "--no-log", "--write-meta-json", "false",
                    "--no-ansi-color", "--disable-update-check", "--ui-language", "zh-CN"));
            Map<String, String> headers = HlsSupport.headerMap(HlsSupport.text(task.get("headers")));
            headers.forEach((key, value) -> { command.add("--header"); command.add(key + ": " + value); });
            if (!settings.maxSpeed().isBlank()) { command.add("--max-speed"); command.add(settings.maxSpeed()); }
            execute(command, attempt, taskLog, settings.timeoutMinutes(), headers, progress.progress());
            if (stopping) throw new InterruptedException();
            db.update("UPDATE hls_download_task SET status = 'verifying', updated_at = ? WHERE id = ?", HlsSupport.now(), id);
            List<Path> results;
            try (var files = Files.list(output)) {
                results = files.filter(file -> file.getFileName().toString().endsWith("." + settings.format()))
                        .filter(file -> Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)).toList();
            }
            if (results.size() != 1) throw new IOException("下载未产生唯一的最终媒体文件，请查看任务日志");
            Path media = results.getFirst();
            verify(media);
            Path target = archive.resolve(name + "." + settings.format());
            // 同一个媒体挂载内发布；硬链接原子创建且拒绝覆盖同名文件。
            Files.createLink(target, media);
            Files.delete(media);
            complete(id, target, name);
        } catch (Exception e) {
            if (stopping || e instanceof InterruptedException) {
                db.update("UPDATE hls_download_task SET status = 'pending', message = '等待应用重启后继续', updated_at = ? WHERE id = ?",
                        HlsSupport.now(), id);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                return;
            }
            int attempts = (int) HlsSupport.number(task.get("attempts"));
            boolean failed = attempts >= settings().maxAttempts();
            String message = HlsSupport.error(e);
            db.update("""
                    UPDATE hls_download_task SET status = ?, message = ?, next_attempt_at = ?, updated_at = ? WHERE id = ?
                    """, failed ? "failed" : "pending", message,
                    failed ? null : HlsSupport.afterMinutes(Math.min(60, 5L << Math.min(attempts - 1, 4))), HlsSupport.now(), id);
            if (failed) notices.publish("剧集下载失败", name + "：" + message, true);
        } finally {
            activeProgress = null;
        }
    }

    private String freshUrl(Map<String, Object> task) {
        String resId = HlsSupport.text(task.get("res_id"));
        if (resId.isBlank()) return m3u8(HlsSupport.text(task.get("url")));
        DyttSearchService.DyttItem item = source.detail(HlsSupport.text(task.get("api_url")), resId);
        DyttSearchService.PlaySource selected = selectSource(item, HlsSupport.text(task.get("source_name")));
        String url = episodeUrl(selected, (int) HlsSupport.number(task.get("season")), (int) HlsSupport.number(task.get("episode")));
        db.update("UPDATE hls_download_task SET url = ? WHERE id = ?", url, task.get("id"));
        return url;
    }

    public static DyttSearchService.PlaySource selectSource(DyttSearchService.DyttItem item, String name) {
        return item.playSources().stream().filter(candidate -> name == null || name.isBlank() || candidate.name().equals(name))
                .filter(candidate -> candidate.items().stream().anyMatch(play -> isM3u8(play.url())))
                .sorted(Comparator.comparing(candidate -> !candidate.name().toLowerCase(Locale.ROOT).contains("m3u8")))
                .findFirst().orElseThrow(() -> new BusinessException(400, "选定线路没有 m3u8 直链，请选择 m3u8 线路"));
    }

    public static String episodeUrl(DyttSearchService.PlaySource selected, int season, int episode) {
        List<DyttSearchService.PlayItem> matches = selected.items().stream()
                .filter(play -> Integer.valueOf(episode).equals(HlsSupport.episodeNumber(play.title())))
                .filter(play -> HlsSupport.matchesSeason(play.title(), season)).toList();
        if (matches.size() != 1) throw new BusinessException(400, "指定集号未找到或存在重复，请核实作品和线路的剧集编号");
        return m3u8(matches.getFirst().url());
    }

    public static boolean isM3u8(String value) {
        try { return java.net.URI.create(value).getPath().toLowerCase(Locale.ROOT).endsWith(".m3u8"); }
        catch (Exception e) { return false; }
    }
    public static String m3u8(String value) {
        String url = HlsSupport.httpUrl(value, "播放直链");
        if (!isM3u8(url)) throw new BusinessException(400, "仅支持 m3u8 直链，播放网页需要先解析");
        return url;
    }
    public static String year(String value) {
        String year = HlsSupport.text(value).trim();
        if (!year.isEmpty() && !year.matches("[12][0-9]{3}")) throw new BusinessException(400, "年份请填写四位数字或留空");
        return year;
    }

    private void execute(List<String> command, Path directory, Path taskLog, int timeout, Map<String, String> secrets,
                         HlsDownloadProgress progress) throws Exception {
        Process process = new ProcessBuilder(command).directory(directory.toFile()).redirectErrorStream(true).start();
        activeProcess = process;
        process.getOutputStream().close();
        Thread reader = Thread.ofVirtual().start(() -> {
            try (var input = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                java.io.BufferedWriter output = null;
                try { output = Files.newBufferedWriter(taskLog, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE); }
                catch (IOException e) { log.warn("HLS 日志文件创建失败：{}", HlsSupport.error(e)); }
                String line;
                long logged = 0;
                try {
                    while ((line = input.readLine()) != null) {
                        progress.accept(line);
                        // 日志无法写入或达到上限后仍消费管道，防止下载进程阻塞。
                        if (output == null || logged > 4_000_000) continue;
                        for (String secret : secrets.values()) if (secret.length() >= 4) line = line.replace(secret, "[已隐藏]");
                        line = HlsSupport.redact(line);
                        try { output.write(line); output.newLine(); output.flush(); logged += line.length(); }
                        catch (IOException e) {
                            log.warn("HLS 日志文件写入失败：{}", HlsSupport.error(e));
                            try { output.close(); } catch (IOException ignored) { }
                            output = null;
                        }
                    }
                } finally { if (output != null) output.close(); }
            } catch (IOException e) { log.warn("HLS 任务日志读取失败：{}", HlsSupport.error(e)); }
        });
        try {
            if (!process.waitFor(timeout, TimeUnit.MINUTES)) throw new IOException("下载任务超时");
            if (process.exitValue() != 0) throw new IOException("下载工具退出码 " + process.exitValue() + "，请查看任务日志");
        } finally {
            terminate(process); reader.join(5000); activeProcess = null;
        }
    }

    private void verify(Path file) throws Exception {
        if (!managedPath(file, null)
                || !Files.isRegularFile(file) || Files.size(file) < 1024) throw new IOException("媒体文件不存在、过小或路径无效");
        Process probe = new ProcessBuilder(paths.getFfprobe(), "-v", "error", "-show_entries",
                "format=duration:stream=codec_type", "-of", "json", file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        activeProcess = probe;
        // 只读取选定字段，避免完整媒体信息填满进程管道。
        var result = new java.util.concurrent.atomic.AtomicReference<String>("");
        Thread reader = Thread.ofVirtual().start(() -> {
            try { result.set(new String(probe.getInputStream().readNBytes(1_000_000), StandardCharsets.UTF_8)); }
            catch (IOException ignored) { }
        });
        try {
            if (!probe.waitFor(60, TimeUnit.SECONDS) || probe.exitValue() != 0) throw new IOException("ffprobe 无法读取媒体文件");
            reader.join(5000);
            JsonNode info = json.readTree(result.get());
            boolean video = false;
            if (info != null) for (JsonNode stream : info.path("streams")) if ("video".equals(stream.path("codec_type").asText())) video = true;
            if (!video || info.path("format").path("duration").asDouble(0) <= 0) throw new IOException("媒体文件缺少视频流或有效时长");
        } finally { terminate(probe); activeProcess = null; }
    }

    private void complete(long id, Path target, String name) {
        db.update("""
                UPDATE hls_download_task SET status = 'completed', save_path = ?, message = NULL,
                       next_attempt_at = NULL, completed_at = ?, updated_at = ? WHERE id = ?
                """, target.toString(), HlsSupport.now(), HlsSupport.now(), id);
        // 一集成功后清理所有尝试的残片，保留各次脱敏日志。
        cleanup(root().resolve(".huluwa-work").resolve(Long.toString(id)));
        notices.publish("剧集下载完成", name + " 已归档：" + target, false);
    }

    private Path root() { return Path.of(paths.getMediaRoot()).toAbsolutePath().normalize(); }
    /** 新目录允许归档，旧挂载仅允许读取已有文件；日志限于各自的工作目录。 */
    private boolean managedPath(Path file, String subdirectory) throws IOException {
        if (Files.isSymbolicLink(file)) return false;
        Path absolute = file.toAbsolutePath().normalize();
        Path real = file.toRealPath();
        for (Path base : List.of(root(), Path.of(paths.getLegacyMediaRoot()).toAbsolutePath().normalize())) {
            Path allowed = subdirectory == null ? base : base.resolve(subdirectory);
            if (Files.isDirectory(allowed) && absolute.startsWith(allowed) && real.startsWith(allowed.toRealPath())) return true;
        }
        return false;
    }
    private Path directory(Path value) throws IOException {
        Path normalized = value.toAbsolutePath().normalize();
        Path root = root();
        if (!normalized.startsWith(root)) throw new IOException("目录超出媒体根目录");
        Path current = root;
        for (Path part : root.relativize(normalized)) {
            current = current.resolve(part);
            if (Files.isSymbolicLink(current)) throw new IOException("媒体目录不能经过符号链接");
            if (!Files.exists(current)) Files.createDirectory(current);
            if (!Files.isDirectory(current)) throw new IOException("媒体路径不是目录");
        }
        if (!normalized.toRealPath().startsWith(root.toRealPath())) throw new IOException("媒体目录无效");
        return normalized;
    }
    private void cleanup(Path attempt) {
        if (!Files.exists(attempt, LinkOption.NOFOLLOW_LINKS)) return;
        try (var files = Files.walk(attempt)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(file)) throw new IOException("临时目录出现符号链接");
                if (file.getFileName().toString().equals("download.log") || Files.isDirectory(file)) continue;
                Files.deleteIfExists(file);
            }
        } catch (IOException e) { log.warn("HLS 临时文件清理失败：{}", HlsSupport.error(e)); }
    }
    private static void terminate(Process process) {
        if (process == null) return;
        process.descendants().sorted(Comparator.comparingLong(ProcessHandle::pid).reversed()).forEach(ProcessHandle::destroyForcibly);
        if (process.isAlive()) process.destroyForcibly();
    }
    @PreDestroy
    public void shutdown() { stopping = true; terminate(activeProcess); worker.shutdownNow(); }
}
