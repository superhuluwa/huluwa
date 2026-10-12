package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.VideoParseService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 视频解析服务配置与试解析接口（供「视频解析」会话的配置表单与顶栏入口调用）。
 *
 * <p>密码只写不读：查询只返回「是否已设置」，保存时空密码表示沿用原密码。</p>
 */
@RestController
@RequestMapping("/api/video")
public class VideoParseController {

    private final VideoParseService videoParseService;

    public VideoParseController(VideoParseService videoParseService) {
        this.videoParseService = videoParseService;
    }

    /** 当前配置（不含密码明文） */
    @GetMapping("/settings")
    public ApiResult<Map<String, Object>> settings() {
        return ApiResult.ok(videoParseService.describe());
    }

    /** 保存配置（下次解析生效，无需清缓存：每次解析前都会重新登录） */
    @PostMapping("/settings")
    public ApiResult<Map<String, Object>> save(@RequestBody SettingsRequest body) {
        if (body == null) {
            return ApiResult.fail(400, "缺少配置内容");
        }
        return ApiResult.ok(videoParseService.save(body.url(), body.username(), body.password()));
    }

    /** 测试连接：账号留空视为匿名调用；密码留空表示沿用已保存的密码 */
    @PostMapping("/test")
    public ApiResult<Map<String, Object>> test(@RequestBody(required = false) SettingsRequest body) {
        SettingsRequest value = body == null ? new SettingsRequest(null, null, null) : body;
        return ApiResult.ok(videoParseService.test(value.url(), value.username(), value.password()));
    }

    /**
     * 表单里的「试解析」：直接解析一条口令，返回归一化后的作品信息（不下发解析服务的原始响应）。
     * 解析是长耗时操作，前端调用时需放宽超时。
     */
    @PostMapping("/parse")
    public ApiResult<Map<String, Object>> parse(@RequestBody ParseRequest body) {
        String text = body == null ? null : body.content();
        VideoParseService.VideoItem item = videoParseService.parse(text);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", item.title());
        data.put("author", item.author());
        data.put("cover", item.coverUrl());
        data.put("video", item.videoUrl());
        data.put("music", item.musicUrl());
        data.put("imageCount", item.images().size());
        return ApiResult.ok(data);
    }

    /** 表单载荷：password 为 null / 空串表示「沿用已保存的密码」 */
    public record SettingsRequest(String url, String username, String password) {
    }

    /** 试解析载荷：content 为用户粘贴的分享口令原文 */
    public record ParseRequest(String content) {
    }
}
