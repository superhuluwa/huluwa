package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.MessageService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> list(@RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) Boolean read,
                                               @RequestParam(required = false) String level,
                                               @RequestParam(defaultValue = "50") int limit,
                                               @RequestParam(defaultValue = "0") int offset) {
        List<Map<String, Object>> items = messageService.list(keyword, read, level, limit, offset);
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("items", items);
        data.put("total", messageService.summary().get("total"));
        return ApiResult.ok(data);
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.ok(messageService.summary());
    }

    @PostMapping("/{id}/read")
    public ApiResult<Void> markRead(@PathVariable long id) {
        messageService.markRead(id);
        return ApiResult.ok();
    }

    @PostMapping("/read-all")
    public ApiResult<Void> markAllRead() {
        messageService.markAllRead();
        return ApiResult.ok();
    }

    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        messageService.delete(id);
        return ApiResult.ok();
    }
}