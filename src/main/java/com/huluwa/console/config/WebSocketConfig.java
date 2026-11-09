package com.huluwa.console.config;

import com.huluwa.console.ws.ChatWebSocketHandler;
import com.huluwa.console.ws.WsAuthHandshakeInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 聊天 WebSocket 通道：原生 TextWebSocketHandler（无 STOMP/SockJS，保持项目轻量风格）。
 * 鉴权在握手拦截器完成（token 走 query，浏览器 WS 无法自定义 header）；
 * /ws/** 不在 MVC 拦截器的 /api/** 范围内，AuthInterceptor 天然不参与握手。
 * AllowedOrigins 放开：dev 环境 Vite(5173) 代理与直连都要能握手，token 校验已覆盖安全面。
 */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor;

    public WebSocketConfig(ChatWebSocketHandler chatWebSocketHandler,
                           WsAuthHandshakeInterceptor wsAuthHandshakeInterceptor) {
        this.chatWebSocketHandler = chatWebSocketHandler;
        this.wsAuthHandshakeInterceptor = wsAuthHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws/chat")
                .addInterceptors(wsAuthHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
