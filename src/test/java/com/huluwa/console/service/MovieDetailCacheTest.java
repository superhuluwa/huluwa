package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class MovieDetailCacheTest {

    private static MovieSearchService.DetailPage page(String title) {
        return new MovieSearchService.DetailPage(title, List.of());
    }

    @Test
    void entriesExpireAndRefreshBypassesCompletedCache() {
        AtomicLong clock = new AtomicLong();
        MovieDetailCache cache = new MovieDetailCache(Duration.ofSeconds(60), 2, clock::get);
        AtomicInteger reads = new AtomicInteger();
        var loader = (java.util.function.Supplier<MovieSearchService.DetailPage>)
                () -> page("version-" + reads.incrementAndGet());
        assertEquals("version-1", cache.get("url", false, loader).title());
        clock.set(Duration.ofSeconds(59).toNanos());
        assertEquals("version-1", cache.get("url", false, loader).title());
        clock.set(Duration.ofSeconds(60).toNanos());
        assertEquals("version-2", cache.get("url", false, loader).title());
        assertEquals("version-3", cache.get("url", true, loader).title());
        assertEquals("version-3", cache.get("url", false, loader).title());
        assertEquals(3, reads.get());
    }

    @Test
    void concurrentRequestsIncludingRefreshShareOneFetch() throws Exception {
        MovieDetailCache cache = new MovieDetailCache(Duration.ofSeconds(60), 2);
        CountDownLatch fetchStarted = new CountDownLatch(1);
        CountDownLatch releaseFetch = new CountDownLatch(1);
        AtomicInteger reads = new AtomicInteger();
        try (var callers = Executors.newFixedThreadPool(8)) {
            var first = callers.submit(() -> cache.get("url", false, () -> {
                reads.incrementAndGet();
                fetchStarted.countDown();
                try {
                    if (!releaseFetch.await(5, TimeUnit.SECONDS)) throw new AssertionError("fetch release timeout");
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(ex);
                }
                return page("shared");
            }));
            assertTrue(fetchStarted.await(2, TimeUnit.SECONDS));
            List<Future<MovieSearchService.DetailPage>> followers = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                followers.add(callers.submit(() -> cache.get("url", false, () -> {
                    throw new AssertionError("duplicate fetch");
                })));
            }
            var refresh = callers.submit(() -> cache.get("url", true, () -> {
                reads.incrementAndGet();
                return page("fresh");
            }));
            // 强制刷新也是进行中请求的等待者：释放首个抓取前不能完成。
            assertThrows(java.util.concurrent.TimeoutException.class, () -> refresh.get(100, TimeUnit.MILLISECONDS));
            releaseFetch.countDown();
            assertEquals("shared", first.get(2, TimeUnit.SECONDS).title());
            for (var follower : followers) assertEquals("shared", follower.get(2, TimeUnit.SECONDS).title());
            assertEquals("shared", refresh.get(2, TimeUnit.SECONDS).title());
            assertEquals(1, reads.get());
        } finally {
            releaseFetch.countDown();
        }
    }

    @Test
    void failuresAreNotCachedAndCapacityEvictsLeastRecentlyUsed() {
        MovieDetailCache cache = new MovieDetailCache(Duration.ofMinutes(1), 2);
        assertThrows(IllegalStateException.class, () -> cache.get("a", false, () -> {
            throw new IllegalStateException("temporary failure");
        }));
        assertEquals("a", cache.get("a", false, () -> page("a")).title());
        cache.get("b", false, () -> page("b"));
        cache.get("a", false, () -> { throw new AssertionError("should be cached"); });
        cache.get("c", false, () -> page("c"));
        assertEquals("b-new", cache.get("b", false, () -> page("b-new")).title());
    }
}
