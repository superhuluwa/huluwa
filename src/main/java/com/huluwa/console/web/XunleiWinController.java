package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.XunleiWinService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 迅雷下载【Win】连接配置接口（供「迅雷下载【Win】」会话的配置表单调用）。
 *
 * <p>对接 Windows 上运行的 xunlei-win-api 服务：只保存服务地址与保存根目录，无敏感凭据。</p>
 */
@RestController
@RequestMapping("/api/xunlei-win")
public class XunleiWinController {

    private final XunleiWinService xunleiWinService;

    public XunleiWinController(XunleiWinService xunleiWinService) {
        this.xunleiWinService = xunleiWinService;
    }

    /** 当前配置 */
    @GetMapping("/settings")
    public ApiResult<Map<String, Object>> settings() {
        return ApiResult.ok(xunleiWinService.describe());
    }

    /** 保存配置 */
    @PostMapping("/settings")
    public ApiResult<Map<String, Object>> save(@RequestBody SettingsRequest body) {
        if (body == null) {
            return ApiResult.fail(400, "缺少配置内容");
        }
        return ApiResult.ok(xunleiWinService.save(body.url(), body.savePath()));
    }

    /** 测试连接：地址留空时用已保存的配置 */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test(@RequestBody(required = false) SettingsRequest body) {
        return ApiResult.ok(xunleiWinService.test(body == null ? null : body.url()));
    }

    /** 表单载荷：保存根目录可留空，表示使用迅雷客户端自己的默认目录 */
    public record SettingsRequest(String url, String savePath) {
    }
}
