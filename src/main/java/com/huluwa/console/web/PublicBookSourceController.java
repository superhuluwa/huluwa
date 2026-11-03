package com.huluwa.console.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.security.BookSourceTokenService;
import com.huluwa.console.service.source.BookSource;
import com.huluwa.console.service.source.BookSourceOrigin;
import com.huluwa.console.service.source.BookSourceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 书源下载页与公开生成接口（无需登录）。
 *
 * <p>不做登录校验，但按来源 IP 限流，避免被大量调用签发令牌；
 * 生成的令牌和登录令牌互相独立，可随时通过管理接口整体吊销。</p>
 */
@RestController
public class PublicBookSourceController {

    /** 页面地址。 */
    public static final String PAGE_PATH = "/book-source";
    /** 同一 IP 每小时最多生成次数。 */
    private static final int LIMIT_PER_HOUR = 10;
    private static final long HOUR_MILLIS = 3600_000L;

    private final BookSourceRegistry registry;
    private final BookSourceTokenService tokens;
    private final BookSourceOrigin origins;
    private final ObjectMapper json;
    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    public PublicBookSourceController(BookSourceRegistry registry, BookSourceTokenService tokens,
                                      BookSourceOrigin origins, ObjectMapper json) {
        this.origins = origins;
        this.registry = registry;
        this.tokens = tokens;
        this.json = json;
    }

    /** 书源下载页面（纯静态 HTML）。 */
    @GetMapping(value = PAGE_PATH, produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<byte[]> page() throws IOException {
        try (var in = new ClassPathResource("book-source.html").getInputStream()) {
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/html; charset=utf-8"))
                    .header(HttpHeaders.CACHE_CONTROL, "no-cache")
                    .body(StreamUtils.copyToByteArray(in));
        }
    }

    /** 可选的书源列表，供页面下拉框使用。 */
    @GetMapping("/api/public/book-sources")
    public ApiResult<List<Map<String, Object>>> sources() {
        return ApiResult.ok(registry.all().stream()
                .map(source -> Map.<String, Object>of("id", source.id(), "name", source.name()))
                .toList());
    }

    /**
     * 生成书源 JSON 并直接下载。
     *
     * @param days    有效期天数，1..365
     * @param baseUrl 葫芦娃对外地址；不传则按当前请求推断
     */
    @GetMapping("/api/public/book-sources/{sourceId}/legado")
    public ResponseEntity<byte[]> download(@PathVariable String sourceId,
                                           @RequestParam(defaultValue = "30") int days,
                                           @RequestParam(required = false) String baseUrl,
                                           HttpServletRequest request) throws JsonProcessingException {
        BookSource source = registry.require(sourceId);
        checkRate(request);
        String origin = origins.resolve(baseUrl, request);
        int valid = Math.max(1, Math.min(days, BookSourceTokenService.EXPIRE_DAYS));
        String token = tokens.issue(source.id(), valid);
        byte[] body = json.writeValueAsBytes(LegadoBookSource.json(source, origin, token));
        Instant expires = Instant.now().plusSeconds(valid * 86400L);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/json; charset=utf-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("huluwa-" + source.id() + "-legado.json", StandardCharsets.UTF_8)
                        .build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("X-Token-Expires", expires.toString())
                .body(body);
    }

    /** 简单滑动窗口限流：同一 IP 每小时最多 {@link #LIMIT_PER_HOUR} 次。 */
    private void checkRate(HttpServletRequest request) {
        String ip = clientIp(request);
        long now = System.currentTimeMillis();
        Deque<Long> window = hits.computeIfAbsent(ip, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > HOUR_MILLIS) window.pollFirst();
            if (window.size() >= LIMIT_PER_HOUR) {
                throw new BusinessException(429, "生成次数过多，请稍后再试");
            }
            window.addLast(now);
        }
        // 定期清理过期 IP，避免内存持续增长。
        if (hits.size() > 2048) {
            hits.entrySet().removeIf(entry -> {
                synchronized (entry.getValue()) {
                    return entry.getValue().isEmpty()
                            || now - entry.getValue().peekLast() > HOUR_MILLIS;
                }
            });
        }
    }

    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma >= 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        String real = request.getHeader("X-Real-IP");
        return real == null || real.isBlank() ? request.getRemoteAddr() : real.trim();
    }
}
