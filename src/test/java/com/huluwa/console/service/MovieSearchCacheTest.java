package com.huluwa.console.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MovieSearchCacheTest {

    @Test
    void repeatedDetailsReuseFetchButScanReadsFreshAndErrorsAreRetried() throws Exception {
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger status = new AtomicInteger(200);
        upstream.createContext("/detail", exchange -> {
            int version = reads.incrementAndGet();
            byte[] body = ("<title>剧 " + version + "_电影港</title><a href=\"magnet:?xt=urn:btih:"
                    + String.valueOf(version).repeat(40) + "\">剧01.1080p</a>").getBytes(Charset.forName("GB18030"));
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        upstream.start();
        try {
            MovieSearchService service = new MovieSearchService();
            String url = "http://127.0.0.1:" + upstream.getAddress().getPort() + "/detail";
            assertEquals("剧 1", service.fetchDetail(url).title());
            assertEquals("剧 1", service.fetchDetail(url).title());
            assertEquals(1, reads.get());
            assertThrows(UnsupportedOperationException.class, () -> service.fetchDetail(url).magnets().clear());
            assertTrue(service.fetchMagnets(url).get(0).magnet().contains("2".repeat(40)));
            assertEquals("剧 2", service.fetchDetail(url).title());
            status.set(503);
            assertThrows(MovieSearchService.MovieSearchException.class, () -> service.fetchDetail(url + "?retry"));
            status.set(200);
            assertEquals("剧 4", service.fetchDetail(url + "?retry").title());
            assertEquals(4, reads.get());
        } finally {
            upstream.stop(0);
        }
    }
}
