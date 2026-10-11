package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.ChatService;
import com.huluwa.console.service.QBittorrentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * qBittorrent 连接配置接口（供「磁力下载」会话的配置表单调用）。
 *
 * <p>密码只写不读：查询只返回「是否已设置」，保存时空密码表示沿用原密码。</p>
 */
@RestController
@RequestMapping("/api/qbittorrent")
public class QBittorrentController {

    private final QBittorrentService qBittorrentService;

    public QBittorrentController(QBittorrentService qBittorrentService) {
        this.qBittorrentService = qBittorrentService;
    }

    /** 当前配置（不含密码明文） */
    @GetMapping("/settings")
    public ApiResult<Map<String, Object>> settings() {
        return ApiResult.ok(qBittorrentService.describe());
    }

    /** 保存配置并清除登录会话缓存，下次提交磁力时重新登录 */
    @PostMapping("/settings")
    public ApiResult<Map<String, Object>> save(@RequestBody SettingsRequest body) {
        if (body == null) {
            return ApiResult.fail(400, "缺少配置内容");
        }
        return ApiResult.ok(qBittorrentService.save(body.url(), body.username(), body.password(),
                body.savePath(), body.useAuth()));
    }

    /** 测试连接：字段留空时用已保存的配置（密码留空表示沿用原密码） */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test(@RequestBody(required = false) SettingsRequest body) {
        SettingsRequest value = body == null ? new SettingsRequest(null, null, null, null, false) : body;
        return ApiResult.ok(qBittorrentService.test(value.url(), value.username(), value.password(),
                value.useAuth()));
    }

    /** 磁力链接格式校验：返回每条的 hash 与合法标识，供前端即时提示（不访问下载器） */
    @PostMapping("/parse")
    public ApiResult<List<String>> parse(@RequestBody Map<String, String> body) {
        String content = body == null ? null : body.get("content");
        return ApiResult.ok(QBittorrentService.extractMagnets(content));
    }

    /**
     * 表单载荷：password 为 null / 空串表示「沿用已保存的密码」；
     * useAuth=false 表示清空账号密码、依赖 qB 的 IP 白名单放行。
     */
    public record SettingsRequest(String url, String username, String password, String savePath, Boolean useAuth) {

        @Override
        public Boolean useAuth() {
            return useAuth == null || useAuth;
        }
    }
}
