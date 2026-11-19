package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.huangguo.HuangguoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 黄果竖版短剧：推荐流、搜索、详情和单集播放地址。
 * 复用现有 /api 登录鉴权；播放地址由服务端实时完成源站加密握手。
 */
@RestController
@RequestMapping("/api/huangguo")
public class HuangguoController {
    private final HuangguoService service;

    public HuangguoController(HuangguoService service) {
        this.service = service;
    }

    @GetMapping("/recommend")
    public ApiResult<Map<String, Object>> recommend() {
        return ApiResult.ok(service.recommend());
    }

    @GetMapping("/search")
    public ApiResult<Map<String, Object>> search(@RequestParam String keyword,
                                                  @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int size) {
        return ApiResult.ok(service.search(keyword, page, Math.min(Math.max(size, 1), 50)));
    }

    @GetMapping("/detail")
    public ApiResult<Map<String, Object>> detail(@RequestParam String id) {
        return ApiResult.ok(service.detail(id));
    }

    @GetMapping("/play")
    public ApiResult<Map<String, Object>> play(@RequestParam String id,
                                                @RequestParam(defaultValue = "1") int ep) {
        return ApiResult.ok(service.playback(id, ep));
    }
}
