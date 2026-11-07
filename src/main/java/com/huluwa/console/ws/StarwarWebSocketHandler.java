package com.huluwa.console.ws;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.service.starwar.StarwarRoomEngine;
import com.huluwa.console.service.starwar.StarwarWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 星球大战对战 WebSocket（{@code /ws/starwar}）。
 *
 * <p>与聊天通道分开：对战是高频帧（20Hz 世界快照），不应该挤占聊天连接的发送缓冲。
 * 握手鉴权复用 {@link WsAuthHandshakeInterceptor}。</p>
 *
 * <p>上行帧：
 * <ul>
 *   <li>{@code join {roomId}} 订阅房间对战帧</li>
 *   <li>{@code leave {roomId}}</li>
 *   <li>{@code input {roomId, dx, dy, item}} 方向键与使用道具</li>
 *   <li>{@code chat {roomId, content, kind}} 等待聊天室发言（经 REST 落库后广播）</li>
 *   <li>{@code ping}</li>
 * </ul>
 * 下行帧：{@code hello} / {@code starwar.world} / {@code starwar.chat} / {@code starwar.settled} /
 * {@code starwar.countdown} / {@code pong} / {@code error}</p>
 */
@Component
public class StarwarWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(StarwarWebSocketHandler.class);

    private final ObjectMapper objectMapper;
    private final StarwarRoomEngine engine;

    /** 连接 → 已订阅的房间号集合 */
    private final Map<String, Set<String>> subscriptions = new ConcurrentHashMap<>();
    /** 连接对象（已包 Decorator） */
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public StarwarWebSocketHandler(ObjectMapper objectMapper, StarwarRoomEngine engine) {
        this.objectMapper = objectMapper;
        this.engine = engine;
        this.engine.addTickListener(this::onTick);
        this.engine.addSettleListener(this::onSettle);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Object user = session.getAttributes().get(WsAuthHandshakeInterceptor.ATTR_USER);
        if (user == null) {
            session.close(new CloseStatus(4001, "unauthorized"));
            return;
        }
        WebSocketSession wrapper = new ConcurrentWebSocketSessionDecorator(session, 5000, 256 * 1024);
        sessions.put(session.getId(), wrapper);
        subscriptions.put(session.getId(), ConcurrentHashMap.newKeySet());
        send(wrapper, Map.of("type", "hello", "user", String.valueOf(user), "channel", "starwar"));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        WebSocketSession wrapper = sessions.get(session.getId());
        if (wrapper == null) {
            return;
        }
        Map<String, Object> frame;
        try {
            frame = objectMapper.readValue(message.getPayload(), new com.fasterxml.jackson.core.type.TypeReference<>() { });
        } catch (Exception error) {
            sendError(wrapper, "帧格式错误");
            return;
        }
        String type = String.valueOf(frame.getOrDefault("type", ""));
        String roomId = frame.get("roomId") == null ? "" : String.valueOf(frame.get("roomId"));
        switch (type) {
            case "ping" -> send(wrapper, Map.of("type", "pong"));
            case "join" -> {
                if (roomId.isBlank()) {
                    sendError(wrapper, "缺少 roomId");
                    return;
                }
                subscriptions.computeIfAbsent(session.getId(), key -> ConcurrentHashMap.newKeySet()).add(roomId);
                StarwarWorld world = engine.world(roomId);
                send(wrapper, Map.of("type", "starwar.joined", "roomId", roomId,
                        "world", world == null ? Map.of() : world.snapshot()));
            }
            case "leave" -> {
                Set<String> rooms = subscriptions.get(session.getId());
                if (rooms != null) {
                    rooms.remove(roomId);
                }
            }
            case "input" -> {
                StarwarWorld world = engine.world(roomId);
                if (world == null) {
                    return;
                }
                String user = String.valueOf(session.getAttributes().get(WsAuthHandshakeInterceptor.ATTR_USER));
                int dx = intOf(frame.get("dx"));
                int dy = intOf(frame.get("dy"));
                world.input(user, dx, dy);
                if (Boolean.TRUE.equals(frame.get("item"))) {
                    world.useItem(user);
                }
            }
            default -> sendError(wrapper, "未知帧类型: " + type);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        subscriptions.remove(session.getId());
    }

    /** 每个 tick 推送世界快照到订阅了该房间的连接 */
    private void onTick(StarwarWorld world, List<StarwarWorld.Standing> standings) {
        if (sessions.isEmpty()) {
            return;
        }
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "starwar.world");
        frame.put("roomId", world.roomId);
        frame.put("world", world.snapshot());
        frame.put("standings", standings);
        broadcast(world.roomId, frame);
    }

    /** 结算推送 */
    private void onSettle(StarwarWorld world) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "starwar.settled");
        frame.put("roomId", world.roomId);
        frame.put("matchId", world.matchId);
        frame.put("standings", world.standings());
        broadcast(world.roomId, frame);
    }

    /** 等待聊天室消息广播（由 REST 发言接口触发） */
    public void broadcastChat(String roomId, Map<String, Object> message) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "starwar.chat");
        frame.put("roomId", roomId);
        frame.put("message", message);
        broadcast(roomId, frame);
    }

    /** 房间状态变化广播（成员进出 / 准备 / 倒计时） */
    public void broadcastRoom(String roomId, Map<String, Object> room) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "starwar.room");
        frame.put("roomId", roomId);
        frame.put("room", room);
        broadcast(roomId, frame);
    }

    private void broadcast(String roomId, Map<String, Object> frame) {
        for (Map.Entry<String, Set<String>> entry : subscriptions.entrySet()) {
            if (!entry.getValue().contains(roomId)) {
                continue;
            }
            WebSocketSession session = sessions.get(entry.getKey());
            if (session != null) {
                send(session, frame);
            }
        }
    }

    private void sendError(WebSocketSession session, String message) {
        send(session, Map.of("type", "error", "message", message));
    }

    private void send(WebSocketSession session, Map<String, Object> frame) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(frame)));
        } catch (Exception error) {
            log.debug("星球大战推送失败：{}", error.getMessage());
            sessions.remove(session.getId());
        }
    }

    private static int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception error) {
            return 0;
        }
    }
}
