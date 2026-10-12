package com.huluwa.console.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 聊天 WebSocket 连接注册表：单用户多标签页各持一条连接，推送时全量广播。
 * 连接包一层 {@link ConcurrentWebSocketSessionDecorator}：worker 线程广播与容器 IO 线程
 * 并发写同一连接的防护（发送超时 5s、缓冲上限 512KB，磁力串/长直链足够）。
 * 半开连接由「应用层心跳 + 空闲清扫」双保险回收：前端每 25s 发 ping，
 * 调度器每 60s 清扫静默超 120s 的连接。
 */
@Service
public class ChatSessionRegistry {

    private static final Logger log = LoggerFactory.getLogger(ChatSessionRegistry.class);

    /** 静默超时：超过该时长没有收到 ping 的连接视为半开，主动关闭 */
    private static final long IDLE_TIMEOUT_MILLIS = 120_000L;
    private static final long SWEEP_INTERVAL_MILLIS = 60_000L;

    private final ObjectMapper objectMapper;
    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();
    private final Map<String, Long> lastPingAt = new ConcurrentHashMap<>();

    private final ScheduledExecutorService sweeper = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("ws-idle-sweep-", 0).factory());

    public ChatSessionRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        sweeper.scheduleWithFixedDelay(this::sweepIdle, SWEEP_INTERVAL_MILLIS, SWEEP_INTERVAL_MILLIS,
                TimeUnit.MILLISECONDS);
    }

    public void register(WebSocketSession session) {
        sessions.add(new ConcurrentWebSocketSessionDecorator(session, 5000, 512 * 1024));
        lastPingAt.put(session.getId(), System.currentTimeMillis());
    }

    public void unregister(WebSocketSession session) {
        sessions.removeIf(item -> item.getId().equals(session.getId()));
        lastPingAt.remove(session.getId());
    }

    /** 收到应用层心跳时刷新静默计时 */
    public void touch(WebSocketSession session) {
        lastPingAt.put(session.getId(), System.currentTimeMillis());
    }

    /** 当前在线连接数（日志/观测用） */
    public int size() {
        return sessions.size();
    }

    /** 广播一帧到所有连接；单条连接死亡只静默剔除，不影响其它标签页 */
    public void broadcast(Map<String, Object> frame) {
        for (WebSocketSession session : sessions) {
            send(session, frame);
        }
    }

    /** 定向发送一帧（hello/pong/error/ack 只回发起方连接，避免错投其它标签页）；失败静默剔除 */
    public void send(WebSocketSession session, Map<String, Object> frame) {
        if (session == null || frame == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(frame);
            session.sendMessage(new TextMessage(json));
        } catch (Exception e) {
            log.debug("定向推送失败，剔除连接 {}：{}", session.getId(), e.getMessage());
            unregister(session);
            closeQuietly(session);
        }
    }

    private void sweepIdle() {
        long now = System.currentTimeMillis();
        lastPingAt.forEach((id, pingAt) -> {
            if (now - pingAt > IDLE_TIMEOUT_MILLIS) {
                sessions.stream()
                        .filter(item -> item.getId().equals(id))
                        .findFirst()
                        .ifPresent(session -> {
                            log.info("关闭静默超时的 WebSocket 连接 {}", id);
                            unregister(session);
                            closeQuietly(session);
                        });
            }
        });
    }

    private static void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.GOING_AWAY);
        } catch (Exception ignored) {
            // 连接本就已死
        }
    }

    @PreDestroy
    public void shutdown() {
        sweeper.shutdownNow();
    }
}
