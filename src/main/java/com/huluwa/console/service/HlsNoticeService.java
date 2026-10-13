package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.ws.ChatPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/** 下载及更新结果写入已有系统通知，并推送到打开的消息页。 */
@Service
public class HlsNoticeService {
    private static final Logger log = LoggerFactory.getLogger(HlsNoticeService.class);
    private final MessageService messages;
    private final ChatPushService push;
    private final ObjectMapper json;
    public HlsNoticeService(MessageService messages, ChatPushService push, ObjectMapper json) {
        this.messages = messages; this.push = push; this.json = json;
    }
    public void publish(String title, String content, boolean failed) {
        try {
            var notification = messages.publishAndGet(title, content, failed ? "error" : "success", "hls");
            String payload = json.writeValueAsString(Map.of("level", notification.level(), "source", "hls", "title", title));
            push.messages(ChatService.SYSTEM_SESSION, List.of(new ChatService.ChatMessage(notification.id(),
                    ChatService.SYSTEM_SESSION, "bot", "notify", content, title, payload, false, notification.createdAt())));
        } catch (Exception e) { log.warn("HLS 通知发送失败：{}", HlsSupport.error(e)); }
    }
}
