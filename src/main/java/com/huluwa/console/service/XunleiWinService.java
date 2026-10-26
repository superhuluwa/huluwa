package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * 迅雷 Windows 客户端下载对接（对应 Windows 机器上的 xunlei-win-api 服务）。
 *
 * <ul>
 *   <li>建任务：POST /api/tasks，服务端用 32 位 COM 助手静默创建任务，返回流水号 serial；</li>
 *   <li>查状态：GET /api/tasks/{serial}，服务端只读迅雷 TaskDb.dat，返回
 *       status / progress / receivedBytes / totalBytes / error；</li>
 *   <li>健康检查：GET /api/health。</li>
 * </ul>
 *
 * <p>与飞牛原生版（{@link XunleiService}）不同，这里的保存路径是 Windows 本机路径
 * （如 {@code D:\迅雷下载}），留空则由迅雷客户端使用自己的默认下载目录。</p>
 */
@Service
public class XunleiWinService {

    private static final Logger log = LoggerFactory.getLogger(XunleiWinService.class);

    /** 配置键：xunlei-win-api 服务地址（如 http://192.168.3.100:8899） */
    public static final String CFG_URL = "xlwin.url";
    /** 配置键：Windows 本机保存根目录（可选，留空用迅雷默认目录） */
    public static final String CFG_SAVE_PATH = "xlwin.savepath";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration TASK_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(20);

    private final ConfigService configService;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public XunleiWinService(ConfigService configService, JdbcTemplate jdbcTemplate) {
        this.configService = configService;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 连接配置：服务地址 + Windows 保存根目录 */
    public record Settings(String url, String savePath) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }

    /** 单条磁力的提交结果（失败时 message 为可直接展示给用户的中文说明） */
    public record TaskResult(boolean ok, String message, String name, long size, int fileCount, String taskId) {
    }

    /** 任务状态：phase 与飞牛版保持同一套取值口径，便于下载跟踪共用一套逻辑 */
    public record TaskStatus(String phase, String message, double progress) {
    }

    private record Response(int status, String body) {
    }

    private record RemoteTask(String status, String message, Double progress, Long receivedBytes,
                              Long totalBytes, String name, String savePath) {
    }

    // ------------------------------------------------------------------ 配置读写

    public Settings settings() {
        return new Settings(trim(configService.get(CFG_URL)), trim(configService.get(CFG_SAVE_PATH)));
    }

    /** 下发前端的配置视图（不含敏感值） */
    public Map<String, Object> describe() {
        Settings settings = settings();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", settings.url());
        data.put("savePath", settings.savePath());
        data.put("configured", settings.configured());
        return data;
    }

    /**
     * 保存配置。
     *
     * @param url      xunlei-win-api 服务地址（必填，缺协议时自动补 http://）
     * @param savePath Windows 本机保存根目录；留空表示使用迅雷默认下载目录
     */
    public Map<String, Object> save(String url, String savePath) {
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            throw new BusinessException(400,
                    "请填写 xunlei-win-api 服务地址，例如 http://192.168.3.100:8899");
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        try {
            URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "服务地址格式不正确：" + url);
        }
        String path = savePath == null ? "" : savePath.trim();
        configService.put(CFG_URL, base);
        configService.put(CFG_SAVE_PATH, path);
        return describe();
    }

    /** 只更新 Windows 保存目录（「路径」指令用） */
    public void saveSavePath(String savePath) {
        configService.put(CFG_SAVE_PATH, savePath == null ? "" : savePath.trim());
    }

    // ------------------------------------------------------------------ 连接测试

    /** 测试连接：调用 xunlei-win-api 的 /api/health */
    public Map<String, Object> test(String url) {
        Map<String, Object> result = new LinkedHashMap<>();
        Settings saved = settings();
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            base = saved.url() == null ? "" : saved.url();
        }
        if (base.isBlank()) {
            result.put("success", false);
            result.put("message", "请先填写 xunlei-win-api 服务地址");
            return result;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        try {
            Response response = send(base, "/api/health", null, QUERY_TIMEOUT);
            if (response.status() != 200) {
                result.put("success", false);
                result.put("message", "连接失败：HTTP " + response.status() + " " + abbreviate(response.body()));
                return result;
            }
            JsonNode root = objectMapper.readTree(response.body());
            boolean thunderRunning = root.path("thunderRunning").asBoolean(false);
            result.put("success", true);
            result.put("message", thunderRunning
                    ? "连接成功：xunlei-win-api 在线，迅雷客户端正在运行"
                    : "连接成功：xunlei-win-api 在线，但 Windows 上的迅雷客户端未运行（创建任务会失败）");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败：" + safeMessage(e));
        }
        return result;
    }

    // ------------------------------------------------------------------ 提交磁力

    /**
     * 提交磁力任务到 Windows 迅雷。
     *
     * @param magnets 磁力链接（每条独立提交、独立成败）
     * @param savePath 本次保存目录；为空时用配置里的根目录，仍为空则由迅雷决定
     * @return 与入参同序的逐条结果
     */
    public List<TaskResult> add(List<String> magnets, String savePath) {
        Settings settings = settings();
        if (!settings.configured()) {
            throw new BusinessException(400, "尚未配置迅雷下载【Win】服务地址");
        }
        if (magnets == null || magnets.isEmpty()) {
            throw new BusinessException(400, "请提供磁力链接");
        }
        String path = savePath == null || savePath.isBlank() ? settings.savePath() : savePath.trim();
        List<TaskResult> results = new ArrayList<>();
        for (String magnet : magnets) {
            results.add(submit(settings.url(), magnet, path));
        }
        return results;
    }

    /** 单条磁力：POST /api/tasks，成功返回的 serial 作为后续跟踪用的任务 ID */
    private TaskResult submit(String url, String magnet, String savePath) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", magnet);
        if (savePath != null && !savePath.isBlank()) {
            body.put("savePath", savePath);
        }
        Response response;
        try {
            response = send(url, "/api/tasks", objectMapper.writeValueAsString(body), TASK_TIMEOUT);
        } catch (Exception e) {
            return new TaskResult(false, "提交迅雷【Win】任务失败：" + safeMessage(e), null, 0, 0, null);
        }
        if (response.status() / 100 != 2) {
            return new TaskResult(false, "提交迅雷【Win】任务失败：HTTP " + response.status()
                    + " " + abbreviate(response.body()), null, 0, 0, null);
        }
        String serial = "";
        try {
            serial = objectMapper.readTree(response.body()).path("serial").asText("");
        } catch (Exception e) {
            // 缺 serial 时仍按提交成功处理，只是无法跟踪进度
        }
        log.info("迅雷【Win】任务已受理（磁力 {}…，流水号 {}）",
                magnet.substring(0, Math.min(24, magnet.length())), serial.isBlank() ? "未返回" : serial);
        rememberSubmission(url, serial, magnet, savePath);
        return new TaskResult(true, "已提交迅雷【Win】下载", null, 0, 0, serial.isBlank() ? null : serial);
    }

    // ------------------------------------------------------------------ 查询状态

    /**
     * 按流水号查询任务状态。
     *
     * @return 查不到（服务端无此记录 / 404）时返回 null
     */
    public TaskStatus task(String serial) {
        Settings settings = settings();
        if (!settings.configured() || serial == null || serial.isBlank()) {
            return null;
        }
        RemoteTask task = queryTask(settings.url(), serial);
        if (task == null) {
            return null;
        }
        String phase = switch (task.status()) {
            case "success" -> "PHASE_TYPE_COMPLETE";
            case "failed" -> "PHASE_TYPE_ERROR";
            case "downloading" -> "PHASE_TYPE_RUNNING";
            case "pending", "creating", "created" -> "PHASE_TYPE_PENDING";
            default -> "";
        };
        return new TaskStatus(phase, task.message(), task.progress() == null ? 0 : task.progress());
    }

    /** 独立标签页的记录快照，网络查询在后台进行，避免列表被慢服务阻塞。 */
    public List<Map<String, Object>> downloads() {
        return jdbcTemplate.queryForList("""
                SELECT id, serial, name, magnet, save_path AS savePath, status, progress,
                       received_bytes AS receivedBytes, total_bytes AS totalBytes,
                       message, query_error AS queryError, created_at AS createdAt,
                       updated_at AS updatedAt, checked_at AS checkedAt, completed_at AS completedAt
                  FROM xunlei_win_download_task
                 WHERE hidden_at IS NULL
                 ORDER BY CASE WHEN status = 'downloading' THEN 0 ELSE 1 END, id DESC LIMIT 500
                """);
    }

    /** 单条删除保留流水号，防止启动迁移补回记录；远端下载任务和文件不受影响。 */
    public Map<String, Object> deleteDownload(long id) {
        int updated = jdbcTemplate.update("""
                UPDATE xunlei_win_download_task SET hidden_at = COALESCE(hidden_at, ?) WHERE id = ?
                """, HlsSupport.now(), id);
        if (updated == 0) throw new BusinessException(404, "下载记录不存在");
        return Map.of("id", id, "message", "下载记录已删除");
    }

    /** 清空全部记录；保留流水号以防止启动迁移再次补入已清空的历史任务。 */
    public Map<String, Object> clearDownloads() {
        int hidden = jdbcTemplate.update("""
                UPDATE xunlei_win_download_task SET hidden_at = ? WHERE hidden_at IS NULL
                """, HlsSupport.now());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hidden", hidden);
        result.put("message", hidden > 0 ? "已清空 " + hidden + " 条下载记录" : "暂无下载记录");
        return result;
    }

    /** 所有入口提交成功后统一落库；同一服务的同一流水号只保留一条记录。 */
    private void rememberSubmission(String apiUrl, String serial, String magnet, String savePath) {
        String name = magnetName(magnet);
        var movies = jdbcTemplate.queryForList("""
                SELECT series_title, episode FROM movie_download_task
                 WHERE downloader = 'xunlei-win' AND magnet = ? LIMIT 1
                """, magnet);
        if (!movies.isEmpty()) {
            var movie = movies.getFirst();
            name = String.valueOf(movie.get("series_title"));
            if (movie.get("episode") != null && !movie.get("episode").toString().isBlank()) {
                name += " 第 " + movie.get("episode") + " 集";
            }
        }
        boolean trackable = serial != null && !serial.isBlank();
        String stamp = HlsSupport.now();
        jdbcTemplate.update("""
                INSERT INTO xunlei_win_download_task
                    (api_url, serial, name, magnet, save_path, status, message, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(api_url, serial) DO UPDATE SET
                    name = excluded.name, magnet = excluded.magnet,
                    save_path = COALESCE(NULLIF(excluded.save_path, ''), xunlei_win_download_task.save_path)
                """, apiUrl, trackable ? serial : null, name, magnet, savePath,
                trackable ? "pending" : "untracked",
                trackable ? "已提交，等待迅雷开始下载" : "已提交，但迅雷服务未返回流水号，无法查询进度",
                stamp, stamp);
    }

    /** 每轮最多并发查询 20 条，优先查询最久未更新的记录，完成和失败的历史状态保留。 */
    @Scheduled(initialDelay = 20_000L, fixedDelay = 1_000L)
    public void refreshDownloadRecords() {
        var rows = jdbcTemplate.queryForList("""
                SELECT id, api_url, serial FROM xunlei_win_download_task
                 WHERE hidden_at IS NULL AND serial IS NOT NULL AND status NOT IN ('success', 'failed', 'untracked')
                 ORDER BY checked_at, id LIMIT 20
                """);
        if (rows.isEmpty()) {
            return;
        }
        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<?>> futures = new ArrayList<>();
            for (var row : rows) {
                futures.add(workers.submit(() -> refreshDownloadRecord(row)));
            }
            for (var future : futures) {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException e) {
                    log.warn("迅雷【Win】下载记录更新失败", e.getCause());
                }
            }
        }
    }

    private void refreshDownloadRecord(Map<String, Object> row) {
        RemoteTask task;
        try {
            task = queryTask(String.valueOf(row.get("api_url")), String.valueOf(row.get("serial")));
            if (task == null) {
                throw new BusinessException(400, "迅雷服务暂未找到该任务，将继续查询");
            }
        } catch (Exception e) {
            jdbcTemplate.update("""
                    UPDATE xunlei_win_download_task SET query_error = ?, checked_at = ? WHERE id = ?
                    """, safeMessage(e), HlsSupport.now(), row.get("id"));
            return;
        }
        String stamp = HlsSupport.now();
        jdbcTemplate.update("""
                UPDATE xunlei_win_download_task
                   SET status = ?, message = ?, progress = COALESCE(?, progress),
                       received_bytes = COALESCE(?, received_bytes), total_bytes = COALESCE(?, total_bytes),
                       name = COALESCE(NULLIF(?, ''), name), save_path = COALESCE(NULLIF(?, ''), save_path),
                       query_error = NULL, checked_at = ?, updated_at = ?,
                       completed_at = CASE WHEN ? = 'success' THEN COALESCE(completed_at, ?) ELSE completed_at END
                 WHERE id = ?
                """, task.status(), task.message(), task.progress(), task.receivedBytes(), task.totalBytes(),
                task.name(), task.savePath(), stamp, stamp, task.status(), stamp, row.get("id"));
    }

    private RemoteTask queryTask(String apiUrl, String serial) {
        Response response;
        try {
            response = send(apiUrl, "/api/tasks/" + encode(serial), null, QUERY_TIMEOUT);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(400, "查询迅雷【Win】任务已中断");
        } catch (Exception e) {
            throw new BusinessException(400, "查询迅雷【Win】任务失败：" + safeMessage(e));
        }
        if (response.status() == 404) {
            return null;
        }
        if (response.status() != 200) {
            throw new BusinessException(400, "查询迅雷【Win】任务失败：HTTP " + response.status()
                    + " " + abbreviate(response.body()));
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(response.body());
            if (root == null || !root.isObject() || root.path("status").asText("").isBlank()) {
                throw new IllegalArgumentException();
            }
        } catch (Exception e) {
            throw new BusinessException(400, "迅雷【Win】任务状态响应无法解析");
        }
        String status = root.path("status").asText();
        Long received = root.path("receivedBytes").isNumber() ? Math.max(0, root.path("receivedBytes").asLong()) : null;
        Long total = root.path("totalBytes").isNumber() ? Math.max(0, root.path("totalBytes").asLong()) : null;
        Double progress = root.path("progress").isNumber() ? root.path("progress").asDouble() : null;
        if (progress == null && received != null && total != null && total > 0) {
            progress = received * 100.0 / total;
        }
        if (progress != null) {
            progress = Double.isFinite(progress) ? Math.max(0, Math.min(100, progress)) : null;
        }
        if ("success".equals(status)) {
            progress = 100.0;
        }
        String message = switch (status) {
            case "success" -> "下载完成";
            case "failed" -> blankToNull(root.path("error").asText("")) == null
                    ? "下载失败" : root.path("error").asText();
            case "downloading" -> progress == null ? "下载中" : "下载中 " + String.format(Locale.ROOT, "%.1f", progress) + "%";
            case "pending" -> "等待创建任务";
            case "creating" -> "正在创建迅雷任务";
            case "created" -> "等待迅雷开始下载";
            default -> status;
        };
        return new RemoteTask(status, message, progress, received, total,
                root.path("name").asText(null), root.path("savePath").asText(null));
    }

    private static String magnetName(String magnet) {
        for (String part : magnet.split("[?&]")) {
            if (part.startsWith("dn=")) {
                try {
                    String name = URLDecoder.decode(part.substring(3), StandardCharsets.UTF_8).trim();
                    if (!name.isEmpty()) {
                        return name;
                    }
                } catch (IllegalArgumentException ignored) {
                    // 错误的名称编码不影响任务本身的提交与查询。
                }
            }
        }
        for (String part : magnet.split("[?&]")) {
            if (part.startsWith("xt=urn:btih:")) {
                return "磁力任务 " + part.substring("xt=urn:btih:".length());
            }
        }
        return "迅雷下载任务";
    }

    // ------------------------------------------------------------------ 工具

    /**
     * 订阅下载用的 Windows 目录：根目录 + 剧名子目录。
     * 根目录未配置时返回空串，表示交给迅雷默认目录。
     */
    static String joinWindowsPath(String root, String folder) {
        String base = root == null ? "" : root.trim().replace('/', '\\');
        while (base.endsWith("\\")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.isEmpty()) {
            return "";
        }
        String name = folder == null ? "" : folder.trim();
        return name.isEmpty() ? base : base + "\\" + name;
    }

    /** 服务地址归一化：去空白、去尾部斜杠 */
    public static String normalizeBaseUrl(String url) {
        String value = trim(url);
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.trim();
    }

    private Response send(String base, String path, String json, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(timeout)
                .header("Accept", "application/json, text/plain, */*")
                .header("User-Agent", "huluwa-console");
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body() == null ? "" : response.body());
    }

    /** 查询参数 / 路径片段编码 */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
