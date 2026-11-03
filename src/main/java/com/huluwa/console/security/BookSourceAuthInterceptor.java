package com.huluwa.console.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.ApiResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 书源接口鉴权：只接受书源令牌，不接受登录令牌。
 *
 * <p>单独的拦截器让书源令牌的能力被限制在 /api/sources/** 内，
 * 即使令牌泄露也无法访问管理接口。</p>
 */
@Component
public class BookSourceAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_SOURCE = "huluwa.book-source";

    private final BookSourceTokenService tokens;
    private final ObjectMapper objectMapper;

    public BookSourceAuthInterceptor(BookSourceTokenService tokens, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) return true;
        String sourceId = tokens.verify(resolveToken(request));
        if (sourceId != null) {
            request.setAttribute(ATTR_SOURCE, sourceId);
            return true;
        }
        writeUnauthorized(response);
        return false;
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && !header.isBlank()) {
            String value = header.trim();
            if (value.regionMatches(true, 0, "Bearer ", 0, 7)) return value.substring(7).trim();
            return value;
        }
        // 部分阅读器只能配置 URL，无法自定义请求头时退回查询参数。
        return request.getParameter("token");
    }

    private void writeUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(objectMapper.writeValueAsString(
                ApiResult.fail(401, "书源令牌无效或已过期，请在葫芦娃中重新生成")));
    }
}
