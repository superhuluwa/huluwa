package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.PushService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/channels")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(pushService.list());
    }

    @PostMapping("/channels")
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(pushService.create(body));
    }

    @PutMapping("/channels/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(pushService.update(id, body));
    }

    @PostMapping("/channels/{id}/toggle")
    public ApiResult<Void> toggle(@PathVariable long id, @RequestParam boolean enabled) {
        pushService.toggle(id, enabled);
        return ApiResult.ok();
    }

    @PostMapping("/channels/{id}/test")
    public ApiResult<Map<String, Object>> test(@PathVariable long id) {
        return ApiResult.ok(pushService.test(id));
    }

    @DeleteMapping("/channels/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        pushService.delete(id);
        return ApiResult.ok();
    }

    @GetMapping("/types")
    public ApiResult<List<Map<String, Object>>> types() {
        return ApiResult.ok(pushService.channelTypes());
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.ok(pushService.summary());
    }
}