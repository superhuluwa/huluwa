package com.huluwa.console.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.AndroidBuildProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AndroidBuildService {
    private static final Logger log = LoggerFactory.getLogger(AndroidBuildService.class);
    private static final TypeReference<Map<String, Object>> JSON_MAP = new TypeReference<>() {};
    private static final Set<String> ACTIVE = Set.of("QUEUED", "RUNNING", "CANCELLING");
    private static final Set<String> TERMINAL = Set.of("SUCCESS", "FAILED", "CANCELLED", "TIMED_OUT", "INTERRUPTED");
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final AndroidBuildProperties config;

    public AndroidBuildService(JdbcTemplate jdbc, TransactionTemplate transactions,
                               ObjectMapper json, AndroidBuildProperties config) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.json = json;
        this.config = config;
    }

    public synchronized Map<String, Object> status() {
        refresh();
        Map<String, Object> worker = readJson(config.directory().resolve("worker.json"));
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("enabled", config.enabled());
        response.put("workerOnline", workerOnline(worker));
        response.put("signingReady", Boolean.TRUE.equals(worker.get("signingReady")));
        response.put("repository", worker.getOrDefault("repository", ""));
        response.put("branch", worker.getOrDefault("branch", "master"));
        response.put("source", readJson(config.directory().resolve("source.json")));
        response.put("activeTask", first("SELECT * FROM android_build_task WHERE status IN ('QUEUED','RUNNING','CANCELLING') ORDER BY id LIMIT 1"));
        response.put("latestApk", first("SELECT * FROM android_build_task WHERE type='BUILD' AND status='SUCCESS' AND artifact_name IS NOT NULL ORDER BY id DESC LIMIT 1"));
        return response;
    }

    /** Mobile clients only need immutable metadata for the newest installable release. */
    public synchronized Map<String, Object> latestRelease() {
        refresh();
        List<Map<String, Object>> candidates = jdbc.query("SELECT * FROM android_build_task WHERE type='BUILD' AND status='SUCCESS' "
                + "AND artifact_name IS NOT NULL ORDER BY version_code DESC,id DESC", this::mapTask);
        for (Map<String, Object> task : candidates) {
            long id = number(task.get("id"), 0);
            String digest = String.valueOf(task.get("sha256"));
            String signer = String.valueOf(task.get("signatureSha256"));
            if (!digest.matches("[0-9a-f]{64}") || !signer.matches("[0-9a-f]{64}")) continue;
            try {
                Path file = download(id, false);
                if (Files.size(file) != number(task.get("apkSize"), -1)) continue;
                Map<String, Object> result = readJson(taskDirectory(id).resolve("result.json"));
                Map<String, Object> release = new LinkedHashMap<>();
                release.put("taskId", id);
                release.put("versionName", task.get("versionName"));
                release.put("versionCode", task.get("versionCode"));
                release.put("packageName", "com.huluwa.android");
                release.put("apkSize", task.get("apkSize"));
                release.put("sha256", digest);
                release.put("signatureSha256", signer);
                release.put("minSdk", number(result.get("minSdk"), 26));
                release.put("publishedAt", task.get("completedAt"));
                return Map.of("available", true, "release", release);
            } catch (BusinessException | IOException ignored) {
                // Missing files cannot be advertised as installable releases.
            }
        }
        return Map.of("available", false);
    }

    public synchronized List<Map<String, Object>> list(int limit) {
        refresh();
        return jdbc.query("SELECT * FROM android_build_task ORDER BY id DESC LIMIT ?", this::mapTask,
                Math.max(1, Math.min(limit, 100)));
    }

    public synchronized Map<String, Object> task(long id) {
        refresh();
        return requireTask(id);
    }

    public synchronized Map<String, Object> submit(String type, String username) {
        refresh();
        if (!config.enabled()) throw new BusinessException(503, "安卓构建服务尚未启用");
        Map<String, Object> worker = readJson(config.directory().resolve("worker.json"));
        if (!workerOnline(worker)) throw new BusinessException(503, "安卓构建容器离线，请稍后重试");
        if (first("SELECT * FROM android_build_task WHERE status IN ('QUEUED','RUNNING','CANCELLING') LIMIT 1") != null) {
            throw new BusinessException(409, "已有任务执行中，请等待完成或取消后再操作");
        }
        Map<String, Object> source = readJson(config.directory().resolve("source.json"));
        if ("BUILD".equals(type)) {
            if (!String.valueOf(source.get("commit")).matches("[0-9a-f]{40}")) {
                throw new BusinessException("请先更新安卓代码");
            }
            if (!Boolean.TRUE.equals(worker.get("signingReady"))) {
                throw new BusinessException("尚未配置 Release 签名，请先恢复现有签名文件");
            }
        }
        Instant created = Instant.now();
        long deadline = created.getEpochSecond() + Math.max(60, config.timeoutSeconds());
        Long id = transactions.execute(transaction -> {
            Integer versionCode = null;
            String versionName = null;
            if ("BUILD".equals(type)) {
                List<String> values = jdbc.query("SELECT cfg_value FROM sys_config WHERE cfg_key='android.build.version-code'",
                        (rs, row) -> rs.getString(1));
                long previous = values.isEmpty() ? 0 : Long.parseLong(values.getFirst());
                Long recorded = jdbc.queryForObject("SELECT MAX(version_code) FROM android_build_task WHERE type='BUILD'", Long.class);
                long floor = Math.max(previous, recorded == null ? 0 : recorded);
                long next = Math.max(floor, number(source.get("versionCode"), 1)) + 1;
                if (next > 2_100_000_000L) throw new BusinessException("安卓版本代码已达到上限");
                versionCode = (int) next;
                // One durable counter drives both the Android comparison and the visible version.
                // Do not copy a static versionName from the selected Git revision.
                versionName = "1.0." + versionCode;
                jdbc.update("INSERT INTO sys_config(cfg_key,cfg_value,updated_at) VALUES('android.build.version-code',?,?) "
                        + "ON CONFLICT(cfg_key) DO UPDATE SET cfg_value=excluded.cfg_value,updated_at=excluded.updated_at",
                        String.valueOf(versionCode), created.toString());
            }
            return jdbc.queryForObject("INSERT INTO android_build_task(type,status,source_commit,commit_subject,version_code,version_name,requested_by,created_at,deadline) "
                            + "VALUES(?,'QUEUED',?,?,?,?,?,?,?) RETURNING id", Long.class,
                    type, "BUILD".equals(type) ? source.get("commit") : null,
                    "BUILD".equals(type) ? source.get("commitSubject") : null, versionCode,
                    versionName,
                    username, created.toString(), deadline);
        });
        if (id == null) throw new BusinessException(500, "创建任务失败");
        try {
            Map<String, Object> request = new LinkedHashMap<>(requireTask(id));
            Files.createDirectories(taskDirectory(id));
            // Publish the request only after its durable database record exists.
            writeJson(taskDirectory(id).resolve("request.json"), request);
        } catch (IOException error) {
            jdbc.update("UPDATE android_build_task SET status='FAILED',stage='DONE',error_text=?,completed_at=? WHERE id=?",
                    "无法写入构建任务目录", Instant.now().toString(), id);
            throw new BusinessException(500, "无法写入构建任务目录，请检查共享卷权限");
        }
        return requireTask(id);
    }

    public synchronized Map<String, Object> cancel(long id) {
        refresh();
        Map<String, Object> task = requireTask(id);
        if (!ACTIVE.contains(String.valueOf(task.get("status")))) return task;
        try {
            Files.writeString(taskDirectory(id).resolve("cancel"), Instant.now().toString());
            jdbc.update("UPDATE android_build_task SET status='CANCELLING' WHERE id=?", id);
        } catch (IOException error) {
            throw new BusinessException(500, "无法发送取消请求，请检查共享卷权限");
        }
        return requireTask(id);
    }

    public synchronized Map<String, Object> logs(long id, long cursor) {
        refresh();
        Map<String, Object> task = requireTask(id);
        Path file = taskDirectory(id).resolve("build.log");
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("task", task);
        response.put("text", "");
        response.put("cursor", Math.max(0, cursor));
        response.put("hasMore", false);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) return response;
        try (RandomAccessFile input = new RandomAccessFile(file.toFile(), "r")) {
            long size = input.length();
            long position = cursor < 0 || cursor > size ? 0 : cursor;
            input.seek(position);
            byte[] bytes = new byte[(int) Math.min(65536, size - position)];
            input.readFully(bytes);
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            CharBuffer characters = CharBuffer.allocate(bytes.length + 1);
            // Leave incomplete UTF-8 characters for the next request instead of corrupting Chinese logs.
            var decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPLACE);
            decoder.decode(buffer, characters, false);
            characters.flip();
            response.put("text", characters.toString());
            response.put("cursor", position + buffer.position());
            response.put("hasMore", position + buffer.position() < size);
            response.put("reset", position != cursor);
            return response;
        } catch (IOException error) {
            throw new BusinessException(500, "读取构建日志失败");
        }
    }

    public synchronized Path download(long id, boolean fullLog) {
        refresh();
        Map<String, Object> task = requireTask(id);
        String filename;
        if (fullLog) {
            filename = "build.log";
        } else {
            if (!"SUCCESS".equals(task.get("status")) || !"BUILD".equals(task.get("type")) || task.get("artifactName") == null) {
                throw new BusinessException(404, "本次任务没有可下载的 APK");
            }
            filename = String.valueOf(task.get("artifactName"));
        }
        Path directory = taskDirectory(id);
        Path file = directory.resolve(filename).normalize();
        if (!file.getParent().equals(directory) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new BusinessException(404, fullLog ? "日志文件尚未生成" : "APK 文件不存在或已清理");
        }
        return file;
    }

    @Scheduled(fixedDelay = 1000)
    public synchronized void refresh() {
        List<Map<String, Object>> active = jdbc.query("SELECT * FROM android_build_task WHERE status IN ('QUEUED','RUNNING','CANCELLING')", this::mapTask);
        boolean finished = false;
        for (Map<String, Object> task : active) {
            long id = number(task.get("id"), 0);
            Path directory = taskDirectory(id);
            Map<String, Object> result = readJson(directory.resolve("result.json"));
            if (TERMINAL.contains(String.valueOf(result.get("status")))) {
                applyState(id, result, true);
                finished = true;
                continue;
            }
            Map<String, Object> state = readJson(directory.resolve("state.json"));
            if (state.isEmpty() && !Files.exists(directory.resolve("request.json")) && !Files.exists(directory.resolve("running.json"))
                    && Instant.now().getEpochSecond() - Instant.parse(String.valueOf(task.get("createdAt"))).getEpochSecond() > 30) {
                jdbc.update("UPDATE android_build_task SET status='INTERRUPTED',stage='DONE',error_text=?,completed_at=? WHERE id=?",
                        "网页服务在任务提交过程中重启，请重新提交", Instant.now().toString(), id);
                finished = true;
                continue;
            }
            if (!state.isEmpty()) {
                if ("CANCELLING".equals(task.get("status"))) state.put("status", "CANCELLING");
                applyState(id, state, false);
            }
            long deadline = number(task.get("deadline"), 0);
            if (Instant.now().getEpochSecond() > deadline + 20) {
                try {
                    Files.createDirectories(directory);
                    Files.writeString(directory.resolve("cancel"), "timeout");
                    // Keep the lock until a live worker has stopped its process tree.
                    if (!workerOnline(readJson(config.directory().resolve("worker.json")))) {
                        jdbc.update("UPDATE android_build_task SET status='TIMED_OUT',stage='DONE',error_text=?,completed_at=? WHERE id=?",
                                "构建服务离线且任务超时，请检查构建容器", Instant.now().toString(), id);
                        finished = true;
                    }
                } catch (IOException error) {
                    log.warn("无法停止超时安卓任务 {}", id, error);
                }
            }
        }
        if (finished) cleanup();
    }

    private void applyState(long id, Map<String, Object> state, boolean terminal) {
        jdbc.update("UPDATE android_build_task SET status=?,stage=?,started_at=COALESCE(?,started_at),"
                        + "source_commit=COALESCE(?,source_commit),commit_subject=COALESCE(?,commit_subject),"
                        + "version_name=COALESCE(?,version_name),completed_at=COALESCE(?,completed_at),"
                        + "artifact_name=COALESCE(?,artifact_name),apk_size=COALESCE(?,apk_size),"
                        + "sha256=COALESCE(?,sha256),signature_sha256=COALESCE(?,signature_sha256),error_text=? WHERE id=?",
                state.getOrDefault("status", "RUNNING"), state.getOrDefault("stage", "PREPARING"), state.get("startedAt"),
                state.get("commit"), state.get("commitSubject"), state.get("versionName"), terminal ? state.get("completedAt") : null,
                terminal ? state.get("artifactName") : null, terminal ? state.get("apkSize") : null,
                state.get("sha256"), state.get("signatureSha256"), state.get("error"), id);
    }

    private void cleanup() {
        Long latest = jdbc.queryForObject("SELECT MAX(id) FROM android_build_task WHERE type='BUILD' AND status='SUCCESS'", Long.class);
        for (String type : List.of("UPDATE", "BUILD")) {
            List<Map<String, Object>> old = jdbc.query("SELECT * FROM android_build_task WHERE type=? AND status NOT IN ('QUEUED','RUNNING','CANCELLING') ORDER BY id DESC LIMIT -1 OFFSET ?",
                    this::mapTask, type, Math.max(1, config.retention()));
            for (Map<String, Object> task : old) {
                long id = number(task.get("id"), 0);
                if (latest != null && latest == id) continue;
                Path directory = taskDirectory(id);
                try {
                    if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
                        try (var paths = Files.walk(directory)) {
                            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
                        }
                    }
                    jdbc.update("DELETE FROM android_build_task WHERE id=?", id);
                } catch (IOException error) {
                    log.warn("清理安卓构建历史失败 task={}", id, error);
                }
            }
        }
    }

    private Map<String, Object> requireTask(long id) {
        Map<String, Object> result = first("SELECT * FROM android_build_task WHERE id=?", id);
        if (result == null) throw new BusinessException(404, "构建任务不存在或已清理");
        return result;
    }

    private Map<String, Object> first(String sql, Object... arguments) {
        return jdbc.query(sql, this::mapTask, arguments).stream().findFirst().orElse(null);
    }

    private Map<String, Object> mapTask(ResultSet rs, int row) throws SQLException {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", rs.getLong("id"));
        for (String column : List.of("type", "status", "stage", "sha256", "deadline")) task.put(column, rs.getObject(column));
        String[][] fields = {{"source_commit", "commit"}, {"commit_subject", "commitSubject"},
                {"version_code", "versionCode"}, {"version_name", "versionName"}, {"artifact_name", "artifactName"},
                {"apk_size", "apkSize"}, {"signature_sha256", "signatureSha256"}, {"error_text", "error"},
                {"requested_by", "requestedBy"}, {"created_at", "createdAt"}, {"started_at", "startedAt"}, {"completed_at", "completedAt"}};
        for (String[] field : fields) task.put(field[1], rs.getObject(field[0]));
        return task;
    }

    private Path taskDirectory(long id) {
        return config.directory().toAbsolutePath().normalize().resolve("tasks").resolve(String.valueOf(id));
    }

    private boolean workerOnline(Map<String, Object> worker) {
        return Instant.now().getEpochSecond() - number(worker.get("heartbeat"), 0) < 20;
    }

    private Map<String, Object> readJson(Path path) {
        try {
            return json.readValue(path.toFile(), JSON_MAP);
        } catch (IOException error) {
            return new LinkedHashMap<>();
        }
    }

    private void writeJson(Path path, Map<String, Object> value) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        json.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static long number(Object value, long fallback) {
        if (value instanceof Number number) return number.longValue();
        return fallback;
    }
}
