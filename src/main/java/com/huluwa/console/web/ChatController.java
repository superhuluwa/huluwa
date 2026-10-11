package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.ChatService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 消息中心会话接口：会话列表 / 消息流 / 发送（电影港搜索、磁力下载）/ 已读 / 清空 / 跨会话检索。
 * 与既有 /api/messages（系统通知消息）并存：本接口服务聊天式会话 UI。
 */
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /** 会话列表（含未读与最后一条预览） */
    @GetMapping("/sessions")
    public ApiResult<List<Map<String, Object>>> sessions() {
        return ApiResult.ok(chatService.sessions());
    }

    /** 会话消息：beforeId 向上翻页（仅取更早的消息），limit 默认 50 */
    @GetMapping("/sessions/{sessionId}/messages")
    public ApiResult<List<ChatService.ChatMessage>> messages(@PathVariable String sessionId,
                                                             @RequestParam(required = false) Long beforeId,
                                                             @RequestParam(defaultValue = "50") int limit) {
        return ApiResult.ok(chatService.messages(sessionId, beforeId, limit));
    }

    /** 发送消息：电影港会话同步搜索、磁力下载会话同步提交 qB，返回本轮新增的用户与机器人消息 */
    @PostMapping("/sessions/{sessionId}/messages")
    public ApiResult<List<ChatService.ChatMessage>> send(@PathVariable String sessionId,
                                                         @RequestBody SendRequest body) {
        if (body == null || body.content() == null) {
            throw new BusinessException(400, "请输入消息内容");
        }
        return ApiResult.ok(chatService.send(sessionId, body.content()));
    }

    /** 标记会话已读（系统通知会话同步推进 message 表的已读状态） */
    @PostMapping("/sessions/{sessionId}/read")
    public ApiResult<Void> markRead(@PathVariable String sessionId) {
        chatService.markRead(sessionId);
        return ApiResult.ok();
    }

    /** 清空会话消息（系统通知会话不支持） */
    @DeleteMapping("/sessions/{sessionId}/messages")
    public ApiResult<Void> clear(@PathVariable String sessionId) {
        chatService.clear(sessionId);
        return ApiResult.ok();
    }

    /** 跨会话消息检索（顶栏搜索） */
    @GetMapping("/search")
    public ApiResult<List<Map<String, Object>>> search(@RequestParam String keyword,
                                                       @RequestParam(defaultValue = "20") int limit) {
        return ApiResult.ok(chatService.search(keyword, limit));
    }

    public record SendRequest(String content) {
    }
}
