package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.security.BookSourceTokenService;
import com.huluwa.console.service.source.BookSource;
import com.huluwa.console.service.source.BookSourceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书源令牌管理：用登录令牌调用，签发/吊销长期只读书源令牌。
 *
 * <p>与书源接口分开鉴权，避免书源令牌被用来签发新令牌。</p>
 */
@RestController
@RequestMapping("/api/source-tokens")
public class BookSourceTokenController {

    private final BookSourceTokenService tokens;
    private final BookSourceRegistry registry;

    public BookSourceTokenController(BookSourceTokenService tokens, BookSourceRegistry registry) {
        this.tokens = tokens;
        this.registry = registry;
    }

    /** 为指定书源签发令牌（重复调用会得到新令牌，旧令牌在过期前仍有效）。 */
    @PostMapping("/{sourceId}")
    public ApiResult<Map<String, Object>> issue(@PathVariable String sourceId) {
        BookSource source = registry.require(sourceId);
        String token = tokens.issue(source.id());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sourceId", source.id());
        data.put("token", token);
        data.put("expireDays", BookSourceTokenService.EXPIRE_DAYS);
        return ApiResult.ok(data);
    }

    /** 吊销全部书源令牌，已配置的阅读器需要重新填写。 */
    @PostMapping("/revoke")
    public ApiResult<Void> revoke() {
        tokens.revokeAll();
        return ApiResult.ok();
    }

    /** 当前书源列表（登录态下使用，便于管理页展示）。 */
    @GetMapping("/sources")
    public ApiResult<List<Map<String, Object>>> sources() {
        return ApiResult.ok(registry.all().stream()
                .map(source -> Map.<String, Object>of("id", source.id(), "name", source.name()))
                .toList());
    }

    /**
     * 生成可直接导入开源阅读（Legado）的书源 JSON。
     *
     * <p>返回值不落库、不下发到公开接口，令牌只在登录态下取一次；
     * 调用方需要自行保存这份 JSON，不要公开分享。</p>
     *
     * @param baseUrl 可选，葫芦娃的公网地址；留空时按当前请求推断
     */
    @GetMapping("/{sourceId}/legado")
    public ApiResult<Map<String, Object>> legado(@PathVariable String sourceId,
                                                 @RequestParam(required = false) String baseUrl,
                                                 HttpServletRequest request) {
        BookSource source = registry.require(sourceId);
        String origin = normalizeOrigin(baseUrl, request);
        String token = tokens.issue(source.id());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("origin", origin);
        data.put("token", token);
        data.put("json", LegadoBookSource.json(source, origin, token));
        return ApiResult.ok(data);
    }

    /** 优先使用传入地址；否则按反向代理头推断当前对外地址。 */
    private static String normalizeOrigin(String baseUrl, HttpServletRequest request) {
        String origin = baseUrl == null ? "" : baseUrl.trim();
        if (origin.isEmpty()) {
            String proto = firstHeader(request, "X-Forwarded-Proto");
            String host = firstHeader(request, "X-Forwarded-Host");
            String scheme = proto.isEmpty() ? request.getScheme() : proto;
            String authority = host.isEmpty() ? request.getHeader("Host") : host;
            if (authority == null || authority.isBlank()) authority = request.getServerName() + ":" + request.getServerPort();
            origin = scheme + "://" + authority;
        }
        while (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        return origin;
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) return "";
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }
}
