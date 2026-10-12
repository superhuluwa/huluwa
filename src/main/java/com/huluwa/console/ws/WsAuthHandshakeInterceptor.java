package com.huluwa.console.ws;

import com.huluwa.console.security.AuthInterceptor;
import com.huluwa.console.security.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * WebSocket 握手鉴权：浏览器 WebSocket API 无法自定义 Authorization 头，
 * token 走 query 参数（/ws/chat?token=&lt;jwt&gt;），校验复用 {@link TokenService}。
 * 通过后把用户名放进握手 attributes，handler 里随 session 取用。
 */
@Component
public class WsAuthHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_USER = AuthInterceptor.ATTR_USERNAME;

    private final TokenService tokenService;

    public WsAuthHandshakeInterceptor(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        String token = resolveToken(request.getURI());
        if (token != null && tokenService.isValid(token)) {
            attributes.put(ATTR_USER, tokenService.parseSubject(token));
            return true;
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // 无需处理
    }

    /** 从 /ws/chat?token=xxx 提取 token（支持 &amp; 多参数场景），与 AuthInterceptor 的裸值风格一致 */
    private static String resolveToken(URI uri) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return null;
        }
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            String key = eq >= 0 ? pair.substring(0, eq) : pair;
            if ("token".equals(key)) {
                return eq >= 0 ? pair.substring(eq + 1) : null;
            }
        }
        return null;
    }
}
