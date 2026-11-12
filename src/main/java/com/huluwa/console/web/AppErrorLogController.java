package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.security.AuthInterceptor;
import com.huluwa.console.service.AppErrorLogService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 手机端错误日志上报与查询：App 侧 AppLog 的 W/E 条目批量上报，
 * 远程排查手机上的失败原因时直接查库或调 list 接口。
 */
@RestController
@RequestMapping("/api/app-logs")
public class AppErrorLogController {

    private final AppErrorLogService logs;

    public AppErrorLogController(AppErrorLogService logs) {
        this.logs = logs;
    }

    /** App 批量上报：body = { version, device, entries: [{at, level, tag, message, detail}] } */
    @PostMapping
    public ApiResult<Map<String, Object>> upload(HttpServletRequest request,
                                                 @RequestBody Map<String, Object> body) {
        String username = String.valueOf(request.getAttribute(AuthInterceptor.ATTR_USERNAME));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = body.get("entries") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list : List.of();
        int inserted = logs.upload(username,
                String.valueOf(body.getOrDefault("version", "")),
                String.valueOf(body.getOrDefault("device", "")),
                entries);
        return ApiResult.ok(Map.of("inserted", inserted));
    }

    /** 倒序查询最近的错误日志，可按级别与 tag 过滤 */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(
            @RequestParam(defaultValue = "200") int limit,
            @RequestParam(defaultValue = "") String level,
            @RequestParam(defaultValue = "") String tag) {
        return ApiResult.ok(logs.list(limit, level, tag));
    }
}
