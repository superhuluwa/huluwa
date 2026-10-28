package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.AndroidBuildCoordinator;
import com.huluwa.console.service.AndroidBuildService;
import com.huluwa.console.service.AndroidDownloadService;
import com.huluwa.console.service.TerminalBuddyBuildService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 免登录的 TerminalBuddy 更新入口；不签发后台令牌，不暴露节点管理或其他项目。 */
@RestController
@RequestMapping("/api/public/terminalbuddy/android")
@ConditionalOnProperty(prefix = "huluwa.terminalbuddy-public", name = "enabled", havingValue = "true")
public class TerminalBuddyPublicBuildController {
    private static final String CLIENT_HEADER = "X-TerminalBuddy-Client";
    private static final String DOWNLOAD_PATH = "/api/public/terminalbuddy/android/downloads/";
    private static final String PUBLIC_ACTOR = "terminalbuddy-public:";
    private static final List<String> TASK_FIELDS = List.of("id", "serverId", "type", "status", "stage", "commit",
            "commitSubject", "versionCode", "versionName", "artifactName", "apkSize", "sha256", "signatureSha256",
            "error", "createdAt", "startedAt", "completedAt");
    private final TerminalBuddyBuildService project;
    private final AndroidBuildService builds;
    private final AndroidBuildCoordinator coordinator;
    private final AndroidDownloadService downloads;
    private long lastBuildAt = Long.MIN_VALUE;

    public TerminalBuddyPublicBuildController(TerminalBuddyBuildService project, AndroidBuildCoordinator coordinator,
                                              AndroidDownloadService downloads) {
        this.project = project;
        this.builds = project.builds();
        this.coordinator = coordinator;
        this.downloads = downloads;
    }

    @GetMapping({"", "/"})
    public ApiResult<Map<String, Object>> service() {
        return ApiResult.ok(Map.of("service", "terminalbuddy-updates", "packageName", "com.terminalbuddy.android",
                "authentication", "none"));
    }

    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status(@RequestParam(defaultValue = "local") String serverId,
            @RequestHeader(value = CLIENT_HEADER, required = false) String client) {
        var status = builds.status(project.server(serverId));
        var response = fields(status, List.of("enabled", "workerOnline", "signingReady", "branch", "serverId"));
        if (status.get("source") instanceof Map<?, ?> source)
            response.put("source", fields(source, List.of("commit", "commitSubject", "branch", "updatedAt")));
        response.put("servers", serverViews());
        response.put("activeTask", taskView(status.get("activeTask"), client));
        response.put("latestApk", taskView(status.get("latestApk"), client));
        return ApiResult.ok(response);
    }

    @GetMapping("/servers")
    public ApiResult<List<Map<String, Object>>> servers() { return ApiResult.ok(serverViews()); }

    @GetMapping("/releases/latest")
    public ApiResult<Map<String, Object>> latestRelease() { return ApiResult.ok(builds.latestRelease()); }

    @GetMapping("/tasks")
    public ApiResult<List<Map<String, Object>>> tasks(@RequestParam(defaultValue = "30") int limit,
            @RequestHeader(value = CLIENT_HEADER, required = false) String client) {
        return ApiResult.ok(builds.list(limit).stream().map(task -> taskView(task, client)).toList());
    }

    @GetMapping("/tasks/{id}")
    public ApiResult<Map<String, Object>> task(@PathVariable long id,
            @RequestHeader(value = CLIENT_HEADER, required = false) String client) {
        return ApiResult.ok(taskView(builds.task(id), client));
    }

    @GetMapping("/tasks/{id}/logs")
    public ApiResult<Map<String, Object>> logs(@PathVariable long id, @RequestParam(defaultValue = "0") long cursor,
            @RequestHeader(value = CLIENT_HEADER, required = false) String client) {
        var response = new LinkedHashMap<>(builds.logs(id, cursor));
        response.put("task", taskView(response.get("task"), client));
        return ApiResult.ok(response);
    }

    @PostMapping("/build")
    public synchronized ApiResult<Map<String, Object>> build(@RequestParam(defaultValue = "local") String serverId,
            @RequestHeader(value = CLIENT_HEADER, required = false) String client) {
        String actor = requireActor(client);
        long now = System.nanoTime();
        if (lastBuildAt != Long.MIN_VALUE && now - lastBuildAt < Duration.ofSeconds(30).toNanos())
            throw new BusinessException(429, "刚刚已有用户发起编译，请稍后重试");
        var task = coordinator.submit(builds, "BUILD", actor, project.server(serverId));
        lastBuildAt = now;
        return ApiResult.ok(taskView(task, client));
    }

    @PostMapping("/tasks/{id}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable long id,
            @RequestHeader(value = CLIENT_HEADER, required = false) String client) {
        String actor = requireActor(client);
        if (!actor.equals(builds.task(id).get("requestedBy")))
            throw new BusinessException(403, "只能取消本设备发起的构建任务");
        return ApiResult.ok(taskView(builds.cancel(id), client));
    }

    public record DownloadRequest(boolean logFile) {}

    @PostMapping("/tasks/{id}/download-ticket")
    public ApiResult<Map<String, Object>> downloadTicket(@PathVariable long id,
            @RequestBody(required = false) DownloadRequest request) {
        return ApiResult.ok(downloads.create(builds, id, request != null && request.logFile(), DOWNLOAD_PATH));
    }

    @GetMapping("/downloads/{ticket}")
    public ResponseEntity<Resource> download(@PathVariable String ticket) throws IOException {
        var file = downloads.resolve(ticket, builds);
        MediaType type = file.logFile() ? MediaType.TEXT_PLAIN : MediaType.parseMediaType("application/vnd.android.package-archive");
        return ResponseEntity.ok().contentType(type).contentLength(Files.size(file.path()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(new FileSystemResource(file.path()));
    }

    private List<Map<String, Object>> serverViews() {
        return builds.servers().stream().map(server -> fields(server, List.of("id", "name", "online", "platform"))).toList();
    }

    private Map<String, Object> taskView(Object value, String client) {
        if (!(value instanceof Map<?, ?> task)) return null;
        var response = fields(task, TASK_FIELDS);
        String actor = actor(client);
        String requester = String.valueOf(task.get("requestedBy"));
        response.put("requestedBy", requester.startsWith(PUBLIC_ACTOR) ? "共享更新用户" : "管理员");
        response.put("canCancel", actor != null && actor.equals(requester)
                && Set.of("QUEUED", "RUNNING", "CANCELLING").contains(task.get("status")));
        return response;
    }

    private Map<String, Object> fields(Map<?, ?> source, List<String> names) {
        var result = new LinkedHashMap<String, Object>();
        for (String name : names) if (source.get(name) != null) result.put(name, source.get(name));
        return result;
    }

    private String requireActor(String client) {
        String actor = actor(client);
        if (actor == null) throw new BusinessException(400, "缺少有效的更新客户端标识，请更新安卓 App 后重试");
        return actor;
    }

    private String actor(String client) {
        if (client == null || !client.matches("[a-f0-9]{32}")) return null;
        try {
            // 任务只保存摘要，公开日志或历史中出现发起者信息也无法据此取消其他人的任务。
            return PUBLIC_ACTOR + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(client.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
