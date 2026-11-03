package com.huluwa.console.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 分享短链跟随必须自己读 302 的 Location。
 *
 * <p>用本地 HTTP 服务模拟番茄的 {@code /t/xxxx} → 落地页跳转。如果 HttpClient 配成
 * 自动跟随重定向，拿到的 Location 恒为 null，书号就永远解析不出来——正是线上
 * 「分享链接退化成书名搜索」的原因，这里把这个回归盯住。</p>
 */
class FanqieShareResolveTest {

    private HttpServer server;
    private final FanqieSearchService service = new FanqieSearchService();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        // 短链：302 到带 book_id 的落地页
        server.createContext("/t/abc/", exchange -> {
            exchange.getResponseHeaders().add("Location",
                    "/ug/pages/book-share?share_type=11&book_id=7404527225137024025&share_genre=read");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        // 落地页：跟到这一步说明前面的书号没取到，直接回 200 让循环自然结束
        server.createContext("/ug/pages/book-share", exchange -> {
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void followsRedirectAndReadsBookId() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/t/abc/";
        assertEquals("7404527225137024025", service.followShortLink(url));
    }

    @Test
    void returnsNullWhenChainHasNoBookId() {
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/missing/";
        assertNull(service.followShortLink(url));
    }

    @Test
    void nonFanqieHostIsRejectedBeforeAnyRequest() {
        assertNull(service.resolveShareUrl("https://example.com/t/abc/"));
        assertNull(service.resolveShareUrl(""));
        assertNull(service.resolveShareUrl(null));
    }
}
