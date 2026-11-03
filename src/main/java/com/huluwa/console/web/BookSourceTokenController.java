package com.huluwa.console.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.ApiResult;
import com.huluwa.console.security.BookSourceTokenService;
import com.huluwa.console.service.source.BookSource;
import com.huluwa.console.service.source.BookSourceOrigin;
import com.huluwa.console.service.source.BookSourceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
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
    private final BookSourceOrigin origins;
    private final ObjectMapper json;

    public BookSourceTokenController(BookSourceTokenService tokens, BookSourceRegistry registry,
                                     BookSourceOrigin origins, ObjectMapper json) {
        this.tokens = tokens;
        this.registry = registry;
        this.origins = origins;
        this.json = json;
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
     * <p>返回值带长期书源令牌，只在登录态下返回；调用方不要公开分享。</p>
     *
     * @param baseUrl 可选，葫芦娃的公网地址；留空时按当前请求推断
     * @param download 为 true 时直接作为 .json 文件下载，便于手机浏览器保存后导入
     */
    @GetMapping("/{sourceId}/legado")
    public ResponseEntity<byte[]> legado(@PathVariable String sourceId,
                                         @RequestParam(required = false) String baseUrl,
                                         @RequestParam(defaultValue = "false") boolean download,
                                         HttpServletRequest request) throws JsonProcessingException {
        BookSource source = registry.require(sourceId);
        String origin = origins.resolve(baseUrl, request);
        String token = tokens.issue(source.id());
        if (download) {
            byte[] body = json.writeValueAsBytes(LegadoBookSource.json(source, origin, token));
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/json; charset=utf-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("huluwa-" + source.id() + "-legado.json", StandardCharsets.UTF_8)
                            .build().toString())
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(body);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("origin", origin);
        data.put("token", token);
        // 顶层数组，与下载文件结构保持一致，方便直接复制保存。
        data.put("json", LegadoBookSource.json(source, origin, token));
        // 不带 download 时仍返回统一信封；方法签名是 byte[]，这里按 JSON 序列化后再返回
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/json; charset=utf-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(json.writeValueAsBytes(ApiResult.ok(data)));
    }

}
