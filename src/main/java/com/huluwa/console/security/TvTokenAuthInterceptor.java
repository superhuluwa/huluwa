package com.huluwa.console.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 电视端接口鉴权：/api/tv/** 只接受内置的电视访问密钥。
 *
 * <p>红果 TV 是自用私有客户端，密钥编译进 APK 并由本服务配置项提供，
 * 不走登录令牌或书源令牌，泄露后只需改这里的配置即可整体作废。</p>
 */
@Component
public class TvTokenAuthInterceptor implements HandlerInterceptor {

    /** 请求头：Authorization: Bearer &lt;key&gt;，或直接放 X-TV-Key。 */
    private static final String HEADER = "X-TV-Key";

    private final String key;
    private final ObjectMapper objectMapper;

    public TvTokenAuthInterceptor(@Value("${huluwa.tv.access-key:}") String key, ObjectMapper objectMapper) {
        this.key = key == null ? "" : key.trim();
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        // 未配置密钥时直接拒绝，避免误把接口暴露出去。
        if (!key.isBlank() && matches(resolveToken(request))) return true;
        writeUnauthorized(response);
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String direct = request.getHeader(HEADER);
        if (direct != null && !direct.isBlank()) return direct.trim();
        String header = request.getHeader("Authorization");
        if (header != null && !header.isBlank()) {
            String value = header.trim();
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) return value.substring(7).trim();
            return value;
        }
        return null;
    }

    /** 定长比较，避免按字符提前返回泄露长度信息。 */
    private boolean matches(String token) {
        if (token == null) return false;
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8), key.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.fail(401, "电视访问密钥无效，请检查葫芦娃服务配置")));
    }
}
