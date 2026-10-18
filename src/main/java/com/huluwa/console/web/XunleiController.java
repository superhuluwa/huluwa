package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.XunleiService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 迅雷远程下载（飞牛）连接配置接口（供「迅雷下载【飞牛】」会话的配置表单调用）。
 *
 * <p>飞牛账号密码与认证值只写不读；查询只返回账号和「是否已设置」，
 * 密码与认证值留空表示沿用原值。两步验证码只用于当前请求。</p>
 */
@RestController
@RequestMapping("/api/xunlei")
public class XunleiController {

    private final XunleiService xunleiService;

    public XunleiController(XunleiService xunleiService) {
        this.xunleiService = xunleiService;
    }

    /** 当前配置（不含认证值明文） */
    @GetMapping("/settings")
    public ApiResult<Map<String, Object>> settings() {
        return ApiResult.ok(xunleiService.describe());
    }

    /** 保存配置 */
    @PostMapping("/settings")
    public ApiResult<Map<String, Object>> save(@RequestBody SettingsRequest body) {
        if (body == null) {
            return ApiResult.fail(400, "缺少配置内容");
        }
        return ApiResult.ok(xunleiService.save(body.url(), body.savePath(), body.token(),
                body.fnosUsername(), body.fnosPassword(), body.twofaCode()));
    }

    /** 测试连接：地址 / 认证值留空时用已保存的配置 */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test(@RequestBody(required = false) SettingsRequest body) {
        SettingsRequest value = body == null ? new SettingsRequest(null, null, null, null, null, null) : body;
        return ApiResult.ok(xunleiService.test(value.url(), value.token(),
                value.fnosUsername(), value.fnosPassword(), value.twofaCode()));
    }

    /** 表单载荷：token 为 null / 空串表示「沿用已保存的认证值」 */
    public record SettingsRequest(String url, String savePath, String token,
                                  String fnosUsername, String fnosPassword, String twofaCode) {
    }
}
