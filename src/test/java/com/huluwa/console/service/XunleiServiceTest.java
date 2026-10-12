package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 迅雷下载会话的纯函数单测：pan_auth 提取 / resource/list 文件清单解析 / 服务地址归一化。
 * 迅雷接口路径依赖真实飞牛迅雷，不在此覆盖（由「测试连接」在前端联调）。
 */
class XunleiServiceTest {

    private final XunleiService service = new XunleiService(null);

    @Test
    void panAuthPrefersUiauthFunction() {
        String html = "<script>function uiauth(value){ return \"abc.def.ghi\"; }</script>";
        assertEquals("abc.def.ghi", XunleiService.extractPanAuth(html));
    }

    /** 首页没有 uiauth 函数时，兜底匹配页面里的 JWT 形态串（三段 base64url） */
    @Test
    void panAuthFallsBackToJwtShape() {
        assertEquals("aaa.bbb.ccc", XunleiService.extractPanAuth("var token = 'aaa.bbb.ccc';"));
    }

    @Test
    void panAuthMissingReturnsNull() {
        assertNull(XunleiService.extractPanAuth("<html>没有令牌</html>"));
        assertNull(XunleiService.extractPanAuth(""));
        assertNull(XunleiService.extractPanAuth(null));
    }

    /** 磁力解析出的文件树要按目录下钻成叶子清单，顺序与页面展示一致（深度优先） */
    @Test
    void leavesWalksNestedDirs() {
        String body = """
                {"list":{"resources":[
                  {"name":"dir1","is_dir":true,"dir":{"resources":[
                    {"name":"a.mkv","file_size":100},
                    {"name":"dir2","is_dir":true,"dir":{"resources":[{"name":"b.mp4","file_size":50}]}}
                  ]}},
                  {"name":"c.txt","file_size":5}
                ]}}""";
        List<JsonNode> leaves = service.extractLeaves(body);
        assertEquals(3, leaves.size());
        assertEquals("a.mkv", leaves.get(0).path("name").asText());
        assertEquals("b.mp4", leaves.get(1).path("name").asText());
        assertEquals("c.txt", leaves.get(2).path("name").asText());
    }

    @Test
    void leavesEmptyOnInvalidOrEmptyList() {
        assertTrue(service.extractLeaves("{}").isEmpty());
        assertTrue(service.extractLeaves("{\"list\":{\"resources\":[]}}").isEmpty());
        assertTrue(service.extractLeaves("not-json").isEmpty());
        assertTrue(service.extractLeaves(null).isEmpty());
    }

    @Test
    void normalizeBaseUrlTrimsSlashAndSpace() {
        assertEquals("http://192.168.3.202:2345", XunleiService.normalizeBaseUrl(" http://192.168.3.202:2345/ "));
        assertEquals("http://xl.local:2345", XunleiService.normalizeBaseUrl("http://xl.local:2345//"));
        assertEquals("", XunleiService.normalizeBaseUrl(null));
    }

    @Test
    void xunleiSessionKeyIsStable() {
        // 「迅雷下载」会话键与 schema 内置 id 必须一致，前端 query 与后端路由都依赖它
        assertEquals("xunlei", ChatService.XUNLEI_SESSION);
    }
}
