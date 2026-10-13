package com.huluwa.console.service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** 短期、有界的详情缓存；网络请求在锁外执行，同一 URL 的并发读取共享一次抓取。 */
final class MovieDetailCache {

    private record Entry(MovieSearchService.DetailPage page, long writtenAt) { }

    private final long ttlNanos;
    private final int maxEntries;
    private final LongSupplier clock;
    private final Map<String, Entry> completed = new LinkedHashMap<>(16, 0.75f, true);
    private final Map<String, CompletableFuture<MovieSearchService.DetailPage>> inFlight = new HashMap<>();

    MovieDetailCache(Duration ttl, int maxEntries) {
        this(ttl, maxEntries, System::nanoTime);
    }

    MovieDetailCache(Duration ttl, int maxEntries, LongSupplier clock) {
        this.ttlNanos = Math.max(0L, ttl.toNanos());
        this.maxEntries = Math.max(1, maxEntries);
        this.clock = clock;
    }

    MovieSearchService.DetailPage get(String url, boolean refresh,
                                     Supplier<MovieSearchService.DetailPage> loader) {
        CompletableFuture<MovieSearchService.DetailPage> future;
        boolean owner;
        synchronized (completed) {
            if (!refresh) {
                MovieSearchService.DetailPage cached = cached(url);
                if (cached != null) return cached;
            }
            future = inFlight.get(url);
            owner = future == null;
            if (owner) {
                future = new CompletableFuture<>();
                inFlight.put(url, future);
            }
        }
        if (!owner) return await(future);
        try {
            MovieSearchService.DetailPage page = loader.get();
            if (ttlNanos > 0) {
                synchronized (completed) {
                    completed.put(url, new Entry(page, clock.getAsLong()));
                    while (completed.size() > maxEntries) {
                        completed.remove(completed.keySet().iterator().next());
                    }
                }
            }
            future.complete(page);
            return page;
        } catch (RuntimeException | Error failure) {
            future.completeExceptionally(failure);
            throw failure;
        } finally {
            synchronized (completed) {
                inFlight.remove(url, future);
            }
        }
    }

    private MovieSearchService.DetailPage cached(String url) {
        synchronized (completed) {
            Entry entry = completed.get(url);
            if (entry == null) return null;
            if (clock.getAsLong() - entry.writtenAt() >= ttlNanos) {
                completed.remove(url);
                return null;
            }
            return entry.page();
        }
    }

    private static MovieSearchService.DetailPage await(
            CompletableFuture<MovieSearchService.DetailPage> future) {
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new MovieSearchService.MovieSearchException("等待详情页抓取被中断", ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new MovieSearchService.MovieSearchException("详情页抓取失败", cause);
        }
    }
}
