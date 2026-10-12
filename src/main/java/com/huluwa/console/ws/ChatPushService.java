package com.huluwa.console.ws;

import com.huluwa.console.service.ChatService;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 聊天推送帧构建与广播。只做「组帧 + 广播」，不持任何业务依赖
 * （注入 ChatService 会被 ChatService 反向依赖成环，因此仅引用其消息 record 类型）。
 * 帧语义见前端 libs/ws.js 与协议注释：
 *  chat.ack / chat.processing / chat.messages / chat.done / chat.sessions / pong / error
 */
@Service
public class ChatPushService {

    private final ChatSessionRegistry registry;

    public ChatPushService(ChatSessionRegistry registry) {
        this.registry = registry;
    }

    /** 连接建立确认（只发给新建立的连接，不打扰其它标签页） */
    public void hello(WebSocketSession target, String user) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "hello");
        frame.put("user", user);
        registry.send(target, frame);
    }

    /** 发送已受理（幂等重放时 status 为 chat_request 表里的既有状态）；定向回发送方连接 */
    public void ack(WebSocketSession target, String requestId, String status, ChatService.ChatMessage message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat.ack");
        frame.put("requestId", requestId);
        frame.put("status", status);
        frame.put("message", message);
        registry.send(target, frame);
    }

    /** 后台开始处理（前端显示 typing 指示）；定向回发送方连接 */
    public void processing(WebSocketSession target, String requestId, String sessionId) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat.processing");
        frame.put("requestId", requestId);
        frame.put("sessionId", sessionId);
        registry.send(target, frame);
    }

    /** 新消息推送（bot 回复 / 订阅巡检写入 / 其他标签页发送） */
    public void messages(String sessionId, List<? extends ChatService.ChatMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat.messages");
        frame.put("sessionId", sessionId);
        frame.put("messages", messages);
        registry.broadcast(frame);
    }

    /** 一次请求处理终止（无论产出多少条消息必发；前端据此消掉 typing） */
    public void done(String requestId, String sessionId, String status, String error) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat.done");
        frame.put("requestId", requestId);
        frame.put("sessionId", sessionId);
        frame.put("status", status);
        if (error != null) {
            frame.put("error", error);
        }
        registry.broadcast(frame);
    }

    /** 会话列表快照（未读数 / 最后一条 / 排序变化后推送） */
    public void sessions(List<Map<String, Object>> sessions) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "chat.sessions");
        frame.put("sessions", sessions);
        registry.broadcast(frame);
    }

    /** 应用层心跳应答；定向回发 ping 的连接 */
    public void pong(WebSocketSession target, long t) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "pong");
        frame.put("t", t);
        registry.send(target, frame);
    }

    /** 业务错误（能关联到 requestId 就带上，前端据此把 pending 气泡转 failed）；定向回发起方 */
    public void error(WebSocketSession target, int code, String message, String requestId) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "error");
        frame.put("code", code);
        frame.put("message", message);
        if (requestId != null) {
            frame.put("requestId", requestId);
        }
        registry.send(target, frame);
    }
}
