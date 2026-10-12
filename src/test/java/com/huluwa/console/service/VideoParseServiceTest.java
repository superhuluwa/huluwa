package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 视频解析会话的纯函数单测：分享口令里链接的提取与清洗、服务地址归一化。
 * 登录 / 解析路径依赖真实的解析服务，不在此覆盖（由配置表单里的「测试连接 / 试解析」联调）。
 */
class VideoParseServiceTest {

    @Test
    void extractShareUrlFromDouyinCopyText() {
        String text = "7.68 复制打开抖音，看看【某某某】的作品 https://v.douyin.com/iRNBho6/ 复制此链接，打开抖音搜索";
        assertEquals("https://v.douyin.com/iRNBho6/", VideoParseService.extractShareUrl(text));
    }

    /** 链接后面紧贴中文句读时不能把标点吞进 url 参数 */
    @Test
    void extractShareUrlTrimsTrailingPunctuation() {
        assertEquals("https://v.douyin.com/abc123/",
                VideoParseService.extractShareUrl("看看这个 https://v.douyin.com/abc123/。"));
        assertEquals("https://www.douyin.com/video/123456",
                VideoParseService.extractShareUrl("https://www.douyin.com/video/123456，谢谢"));
    }

    @Test
    void extractShareUrlKeepsQueryAndFragment() {
        String url = "https://www.kuaishou.com/short-video/3xabc?authorId=1&streamSource=1#detail";
        assertEquals(url, VideoParseService.extractShareUrl("口令 " + url + " 结束"));
    }

    @Test
    void extractShareUrlReturnsNullWhenNoLinkOrBlank() {
        assertNull(VideoParseService.extractShareUrl("只有一段没有链接的口令"));
        assertNull(VideoParseService.extractShareUrl(""));
        assertNull(VideoParseService.extractShareUrl(null));
        // 只有协议头没有主机名不算链接
        assertNull(VideoParseService.extractShareUrl("https:// 后面没有内容"));
    }

    @Test
    void normalizeBaseUrlTrimsSlashAndSpace() {
        assertEquals("http://192.168.3.202:8080", VideoParseService.normalizeBaseUrl(" http://192.168.3.202:8080/ "));
        assertEquals("http://video.local:9000", VideoParseService.normalizeBaseUrl("http://video.local:9000//"));
        assertEquals("", VideoParseService.normalizeBaseUrl(null));
    }

    @Test
    void videoSessionKeyIsStable() {
        // 「视频解析」会话键与 schema 内置 id 必须一致，前端 query 与后端路由都依赖它
        assertEquals("video", ChatService.VIDEO_SESSION);
    }
}
