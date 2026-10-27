package com.huluwa.console.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.ChatService;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

/**
 * 聊天 WebSocket 帧分发。协议（JSON 文本帧，type 区分）：
 *  上行 chat.send {requestId, sessionId, content} / chat.read {sessionId} / ping {t}
 *  下行 hello / chat.ack / chat.processing / chat.messages / chat.done / chat.sessions / pong / error
 *
 * <p>handler 里只做毫秒级 DB 受理（submit），耗时的搜索 / 提交 / 解析在 chat-worker 线程，
 * 处理结果由 {@link ChatPushService} 广播——本 handler 不阻塞容器 WS 线程。</p>
 */
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    /** 连接内鉴权失效（握手拦截器之后的二次防御）时通知前端停止重连的关闭码 */
    private static final CloseStatus CLOSE_UNAUTHORIZED = new CloseStatus(4001, "unauthorized");

    private final ChatService chatService;
    private final ChatPushService chatPushService;
    private final ChatSessionRegistry registry;
    private final ObjectMapper objectMapper;

    public ChatWebSocketHandler(ChatService chatService, ChatPushService chatPushService,
                                ChatSessionRegistry registry, ObjectMapper objectMapper) {
        this.chatService = chatService;
        this.chatPushService = chatPushService;
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object user = session.getAttributes().get(WsAuthHandshakeInterceptor.ATTR_USER);
        if (user == null) {
            // 握手拦截器已挡住未授权连接，这里只做防御性兜底
            chatPushService.error(session, 401, "连接未授权，请重新登录", null);
            session.close(CLOSE_UNAUTHORIZED);
            return;
        }
        registry.register(session);
        chatPushService.hello(session, String.valueOf(user));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node;
        try {
            node = objectMapper.readTree(message.getPayload());
        } catch (Exception e) {
            chatPushService.error(session, 400, "消息格式错误，需要 JSON 文本帧", null);
            return;
        }
        String type = node.path("type").asText("");
        switch (type) {
            case "chat.send" -> handleSend(session, node);
            case "chat.read" -> handleRead(session, node);
            case "ping" -> {
                registry.touch(session);
                chatPushService.pong(session, node.path("t").asLong());
            }
            default -> chatPushService.error(session, 400, "未知消息类型: " + type, null);
        }
    }

    private void handleSend(WebSocketSession session, JsonNode node) {
        String requestId = node.path("requestId").asText(null);
        String sessionId = node.path("sessionId").asText("");
        String content = node.path("content").asText(null);
        if (isBlank(requestId) || isBlank(sessionId) || isBlank(content)) {
            chatPushService.error(session, 400, "chat.send 需要 requestId / sessionId / content", requestId);
            return;
        }
        try {
            ChatService.SubmitResult result = chatService.submit(sessionId, requestId, content);
            // ack 定向回发送方：前端用它把 pending 气泡收编为落库行；
            // 幂等命中（processing/done/failed）只回 ack，在途请求已有 worker 在跑、历史请求无后续
            chatPushService.ack(session, result.requestId(), result.status(), result.userMessage());
            if ("received".equals(result.status())) {
                chatPushService.processing(session, result.requestId(), result.userMessage().sessionId());
            }
        } catch (BusinessException e) {
            chatPushService.error(session, e.getCode(), e.getMessage(), requestId);
        }
    }

    private void handleRead(WebSocketSession session, JsonNode node) {
        String sessionId = node.path("sessionId").asText("");
        if (isBlank(sessionId)) {
            chatPushService.error(session, 400, "chat.read 需要 sessionId", null);
            return;
        }
        try {
            chatService.markRead(sessionId);
            // 已读本身由 markRead 推 sessions 快照（有未读变化时）
        } catch (BusinessException e) {
            chatPushService.error(session, e.getCode(), e.getMessage(), null);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        registry.unregister(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable error) {
        registry.unregister(session);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
