package com.huluwa.console.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.HongguoService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/hongguo")
public class HongguoController {
    private final HongguoService service;
    public HongguoController(HongguoService service) { this.service = service; }

    @GetMapping("/settings")
    public ApiResult<Map<String, Object>> settings() { return ApiResult.ok(service.settings()); }
    @PostMapping("/settings")
    public ApiResult<Map<String, Object>> save(@RequestBody SettingsRequest request) {
        if (request == null) throw new BusinessException(400, "缺少连接配置");
        return ApiResult.ok(service.saveSettings(request.signUrl(), request.deviceProfile()));
    }
    @PostMapping("/settings/sync")
    public ApiResult<Map<String, Object>> sync() { return ApiResult.ok(service.syncProfile()); }
    @GetMapping("/search")
    public ApiResult<Map<String, Object>> search(@RequestParam String query, @RequestParam(defaultValue = "0") int offset) {
        return ApiResult.ok(service.search(query, offset));
    }
    @GetMapping("/series/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id) { return ApiResult.ok(service.detail(id)); }
    @GetMapping("/episodes/{id}/playback")
    public ApiResult<Map<String, Object>> playback(@PathVariable String id) { return ApiResult.ok(service.playback(id)); }
    public record SettingsRequest(String signUrl, JsonNode deviceProfile) { }
}
