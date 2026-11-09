package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.AndroidBuildService;
import com.huluwa.console.service.AndroidDownloadService;
import com.huluwa.console.service.HongguoTvBuildService;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 红果 TV 客户端免登录的更新入口。
 *
 * <p>电视端只做两件事：查最新正式版、下载安装包。这里只暴露这两条链路，
 * 不开放编译、任务列表、日志或其他项目的管理接口，也不签发后台登录令牌。</p>
 */
@RestController
@RequestMapping("/api/public/hongguotv/android")
@ConditionalOnProperty(prefix = "huluwa.hongguotv-public", name = "enabled", havingValue = "true")
public class HongguoTvPublicBuildController {
    private static final String DOWNLOAD_PATH = "/api/public/hongguotv/android/downloads/";
    /** 客户端需要的发布字段；签名摘要、大小和校验和在安装前核对。 */
    private static final List<String> RELEASE_FIELDS = List.of("taskId", "versionName", "versionCode",
            "packageName", "apkSize", "sha256", "signatureSha256", "minSdk", "publishedAt");
    private final AndroidBuildService builds;
    private final AndroidDownloadService downloads;

    public HongguoTvPublicBuildController(HongguoTvBuildService project, AndroidDownloadService downloads) {
        this.builds = project.builds();
        this.downloads = downloads;
    }

    @GetMapping({"", "/"})
    public ApiResult<Map<String, Object>> service() {
        return ApiResult.ok(Map.of("service", "hongguotv-updates", "packageName", "com.huluwa.nativeapp",
                "authentication", "none"));
    }

    /** 最新可安装的正式版；没有成功产物时返回 available=false，而不是报错或回退旧包。 */
    @GetMapping("/releases/latest")
    public ApiResult<Map<String, Object>> latestRelease() {
        Map<String, Object> latest = builds.latestRelease();
        if (!(latest.get("release") instanceof Map<?, ?> release)) return ApiResult.ok(Map.of("available", false));
        Map<String, Object> view = new LinkedHashMap<>();
        for (String name : RELEASE_FIELDS) if (release.get(name) != null) view.put(name, release.get(name));
        // 更新说明取构建提交标题，电视上比只显示版本号更有信息量。
        try {
            Object subject = builds.task(number(release.get("taskId"))).get("commitSubject");
            if (subject != null) view.put("commitSubject", subject);
        } catch (BusinessException ignored) {
            // 任务记录已被清理时，版本信息本身仍然够用。
        }
        return ApiResult.ok(Map.of("available", true, "release", view));
    }

    public record DownloadRequest(boolean logFile) {}

    /** 短时票据；匿名入口只能兑换红果 TV 自己的产物，借票据下载不到其他 App 的文件。 */
    @PostMapping("/tasks/{id}/download-ticket")
    public ApiResult<Map<String, Object>> downloadTicket(@PathVariable long id,
                                                        @RequestBody(required = false) DownloadRequest request) {
        return ApiResult.ok(downloads.create(builds, id, request != null && request.logFile(), DOWNLOAD_PATH));
    }

    @GetMapping("/downloads/{ticket}")
    public ResponseEntity<Resource> download(@PathVariable String ticket) throws IOException {
        AndroidDownloadService.Download file = downloads.resolve(ticket, builds);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                .contentLength(Files.size(file.path()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.filename(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(new FileSystemResource(file.path()));
    }

    private long number(Object value) {
        return value instanceof Number number ? number.longValue() : 0;
    }
}
