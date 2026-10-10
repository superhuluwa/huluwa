package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.FrpService;
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
@RequestMapping("/api/frp")
public class FrpController {

    private final FrpService frpService;

    public FrpController(FrpService frpService) {
        this.frpService = frpService;
    }

    @GetMapping("/configs")
    public ApiResult<List<Map<String, Object>>> list() {
        return ApiResult.ok(frpService.list());
    }

    @PostMapping("/configs")
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(frpService.create(body));
    }

    @PutMapping("/configs/{id}")
    public ApiResult<Map<String, Object>> update(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(frpService.update(id, body));
    }

    @PostMapping("/configs/{id}/toggle")
    public ApiResult<Void> toggle(@PathVariable long id, @RequestParam boolean enabled) {
        frpService.toggle(id, enabled);
        return ApiResult.ok();
    }

    @DeleteMapping("/configs/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        frpService.delete(id);
        return ApiResult.ok();
    }

    @GetMapping("/runtime")
    public ApiResult<Map<String, Object>> runtime() {
        return ApiResult.ok(frpService.runtime());
    }
}