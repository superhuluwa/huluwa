package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.security.AuthInterceptor;
import com.huluwa.console.service.AndroidBuildCoordinator;
import com.huluwa.console.service.AndroidBuildService;
import com.huluwa.console.service.AndroidDownloadService;
import com.huluwa.console.service.HongguoTvBuildService;
import jakarta.servlet.http.HttpServletRequest;
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
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** 红果 TV 原生版打包入口，路径与葫芦娃、TerminalBuddy 的构建接口保持一致。 */
@RestController
@RequestMapping("/api/hongguotv/android")
public class HongguoTvBuildController {
    private final AndroidBuildService builds;
    private final HongguoTvBuildService project;
    private final AndroidDownloadService downloads;
    private final AndroidBuildCoordinator coordinator;

    public HongguoTvBuildController(HongguoTvBuildService project, AndroidDownloadService downloads, AndroidBuildCoordinator coordinator) {
        this.project = project;
        this.builds = project.builds();
        this.downloads = downloads;
        this.coordinator = coordinator;
    }

    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status(@RequestParam(defaultValue = "local") String serverId) { return ApiResult.ok(builds.status(project.server(serverId))); }

    @GetMapping("/servers")
    public ApiResult<List<Map<String, Object>>> servers() { return ApiResult.ok(builds.servers()); }

    public record ServerRequest(String name) {}

    @PostMapping("/servers")
    public ApiResult<Map<String, Object>> createServer(@RequestBody ServerRequest request) { return ApiResult.ok(builds.createServer(request.name())); }

    @DeleteMapping("/servers/{id}")
    public ApiResult<Void> deleteServer(@PathVariable String id) { builds.deleteServer(id); return ApiResult.ok(); }

    @PostMapping("/agents/{serverId}/poll")
    public ApiResult<Map<String, Object>> pollAgent(@PathVariable String serverId,
            @RequestHeader("X-Build-Token") String token, @RequestBody Map<String, Object> payload) throws IOException {
        return ApiResult.ok(builds.pollAgent(serverId, token, payload));
    }

    @PutMapping("/agents/{serverId}/tasks/{id}/files/{name}")
    public ApiResult<Void> uploadAgent(@PathVariable String serverId, @PathVariable long id, @PathVariable String name,
            @RequestHeader("X-Build-Token") String token, HttpServletRequest request) throws IOException {
        builds.uploadAgent(serverId, token, id, name, request.getInputStream());
        return ApiResult.ok();
    }

    @GetMapping("/releases/latest")
    public ApiResult<Map<String, Object>> latestRelease() { return ApiResult.ok(builds.latestRelease()); }

    @GetMapping("/tasks")
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(defaultValue = "30") int limit) {
        return ApiResult.ok(builds.list(limit));
    }

    @GetMapping("/tasks/{id}")
    public ApiResult<Map<String, Object>> task(@PathVariable long id) { return ApiResult.ok(builds.task(id)); }

    @PostMapping("/update")
    public ApiResult<Map<String, Object>> update(HttpServletRequest request, @RequestParam(defaultValue = "local") String serverId) {
        return ApiResult.ok(coordinator.submit(builds, "UPDATE", username(request), project.server(serverId)));
    }

    @PostMapping("/build")
    public ApiResult<Map<String, Object>> build(HttpServletRequest request, @RequestParam(defaultValue = "local") String serverId) {
        return ApiResult.ok(coordinator.submit(builds, "BUILD", username(request), project.server(serverId)));
    }

    @PostMapping("/tasks/{id}/cancel")
    public ApiResult<Map<String, Object>> cancel(@PathVariable long id) { return ApiResult.ok(builds.cancel(id)); }

    @GetMapping("/tasks/{id}/logs")
    public ApiResult<Map<String, Object>> logs(@PathVariable long id, @RequestParam(defaultValue = "0") long cursor) {
        return ApiResult.ok(builds.logs(id, cursor));
    }

    public record DownloadRequest(boolean logFile, String variant) {}

    @PostMapping("/tasks/{id}/download-ticket")
    public ApiResult<Map<String, Object>> downloadTicket(@PathVariable long id,
                                                        @RequestBody(required = false) DownloadRequest request) {
        boolean logFile = request != null && request.logFile();
        String variant = request == null ? "standard" : request.variant();
        return ApiResult.ok(downloads.create(builds, id, logFile, variant, "/api/public/android-downloads/"));
    }

    @GetMapping("/tasks/{id}/apk")
    public ResponseEntity<Resource> apk(@PathVariable long id,
                                        @RequestParam(defaultValue = "standard") String variant) throws IOException {
        Path path = builds.download(id, false, variant);
        return file(path, path.getFileName().toString(), MediaType.parseMediaType("application/vnd.android.package-archive"));
    }

    @GetMapping("/tasks/{id}/log-file")
    public ResponseEntity<Resource> logFile(@PathVariable long id) throws IOException {
        return file(builds.download(id, true), "android-task-" + id + ".log", MediaType.TEXT_PLAIN);
    }

    private ResponseEntity<Resource> file(Path path, String name, MediaType type) throws IOException {
        return ResponseEntity.ok().contentType(type).contentLength(Files.size(path))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(name, StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    private String username(HttpServletRequest request) {
        return String.valueOf(request.getAttribute(AuthInterceptor.ATTR_USERNAME));
    }
}
