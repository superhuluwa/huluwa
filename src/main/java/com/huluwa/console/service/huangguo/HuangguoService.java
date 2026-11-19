package com.huluwa.console.service.huangguo;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/** 黄果短剧聚合服务：列表与详情短缓存；播放地址只缓存极短时间并合并同集并发请求。 */
@Service
public class HuangguoService {
    private static final long CACHE_TTL_MS = 3 * 60 * 1000L;
    /** 播放签名是短时的；同一集在几秒内切回时复用，超过 8 秒必须重新换取。 */
    private static final long PLAYBACK_TTL_MS = 8_000L;
    private static final int RECOMMEND_SIZE = 30;

    private final HuangguoClient client;
    private final Map<String, CacheValue> cache = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Map<String, Object>>> playbackRequests = new ConcurrentHashMap<>();

    public HuangguoService(HuangguoClient client) {
        this.client = client;
    }

    public Map<String, Object> recommend() {
        return cached("recommend", CACHE_TTL_MS, () -> {
            List<Map<String, Object>> items = new ArrayList<>();
            for (HuangguoClient.Catalog item : client.recommend(RECOMMEND_SIZE)) {
                items.add(card(item));
            }
            return Map.of("items", items, "source", "huangguo");
        });
    }

    /** 黄果官网一级分类；额外提供“全部”，由客户端作为默认首页。 */
    public Map<String, Object> categories() {
        return cached("categories", CACHE_TTL_MS, () -> {
            List<Map<String, Object>> items = new ArrayList<>();
            items.add(Map.of("name", "全部", "route", ""));
            for (HuangguoClient.Category category : client.categories()) {
                items.add(Map.of("name", category.name(), "route", category.route(),
                        "available", !category.route().equals("ai-mogai") && !category.route().equals("ai-huanlian")));
            }
            return Map.of("items", items);
        });
    }

    /** 二级题材菜单按官网分组下发，界面不再自行维护分类名称。 */
    public Map<String, Object> tagGroups() {
        return cached("tag-groups", CACHE_TTL_MS, () -> {
            List<Map<String, Object>> groups = new ArrayList<>();
            for (HuangguoClient.TagGroup group : client.tagGroups()) {
                List<Map<String, Object>> tags = new ArrayList<>();
                for (HuangguoClient.Tag tag : group.tags()) {
                    tags.add(Map.of("name", tag.name(), "slug", tag.slug()));
                }
                groups.add(Map.of("code", group.code(), "name", group.name(), "tags", tags));
            }
            return Map.of("items", groups);
        });
    }

    /** 一级分类或二级题材列表，返回统一下一页状态。 */
    public Map<String, Object> browse(String category, String tag, int page, int size) {
        String route = category == null ? "" : category.trim();
        String slug = tag == null ? "" : tag.trim();
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 50);
        String key = "browse:" + route + ":" + slug + ":" + safePage + ":" + safeSize;
        return cached(key, CACHE_TTL_MS, () -> {
            HuangguoClient.Page result = !slug.isBlank()
                    ? client.tag(slug, safePage, safeSize)
                    : route.isBlank() ? client.category("ai-duanju", safePage, safeSize)
                    : client.category(route, safePage, safeSize);
            return pageBody(result, route, slug);
        });
    }

    private Map<String, Object> pageBody(HuangguoClient.Page result, String category, String tag) {
        List<Map<String, Object>> items = result.items().stream().map(this::card).collect(Collectors.toList());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("category", category);
        body.put("tag", tag);
        body.put("page", result.page());
        body.put("pages", result.pages());
        body.put("total", result.total());
        body.put("source", "huangguo");
        return body;
    }

    public Map<String, Object> search(String keyword, int page, int size) {
        String key = "search:" + keyword + ":" + page + ":" + size;
        return cached(key, CACHE_TTL_MS, () -> {
            List<Map<String, Object>> items = new ArrayList<>();
            for (HuangguoClient.Catalog item : client.search(keyword, page, size)) {
                items.add(card(item));
            }
            return new LinkedHashMap<>(Map.of("keyword", keyword, "page", page, "items", items));
        });
    }

    public Map<String, Object> detail(String id) {
        return cached("detail:" + id, CACHE_TTL_MS, () -> {
            HuangguoClient.Detail detail = client.detail(id);
            Map<String, Object> body = new LinkedHashMap<>(card(detail.catalog()));
            List<Map<String, Object>> episodes = new ArrayList<>();
            for (HuangguoClient.Episode episode : detail.episodes()) {
                episodes.add(Map.of("ep", episode.ep(), "title", episode.title(), "duration", episode.duration()));
            }
            body.put("episodeCount", detail.episodes().size());
            body.put("episodes", episodes);
            return body;
        });
    }

    /** 同一集并发点击只走一次加密握手，结果最多复用 8 秒。 */
    public Map<String, Object> playback(String id, int ep) {
        String key = "play:" + id + ":" + ep;
        CacheValue hit = cache.get(key);
        if (hit != null && hit.expiresAt.isAfter(Instant.now())) {
            return cast(hit.value);
        }
        CompletableFuture<Map<String, Object>> created = new CompletableFuture<>();
        CompletableFuture<Map<String, Object>> pending = playbackRequests.putIfAbsent(key, created);
        if (pending != null) {
            try {
                return pending.join();
            } catch (RuntimeException error) {
                Throwable cause = error.getCause() == null ? error : error.getCause();
                throw cause instanceof HuangguoException h ? h : new HuangguoException("黄果播放地址解析失败：" + cause.getMessage(), cause);
            }
        }
        try {
            Map<String, Object> body = playbackNow(id, ep);
            cache.put(key, new CacheValue(body, Instant.now().plusMillis(PLAYBACK_TTL_MS)));
            created.complete(body);
            return body;
        } catch (RuntimeException error) {
            created.completeExceptionally(error);
            throw error;
        } finally {
            playbackRequests.remove(key, created);
        }
    }

    private Map<String, Object> playbackNow(String id, int ep) {
        HuangguoClient.Playback playback = client.playback(id, ep);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", playback.id());
        body.put("ep", playback.ep());
        body.put("url", playback.url());
        body.put("hevc", playback.hevc());
        body.put("preview", playback.preview());
        body.put("cdn", playback.cdn());
        body.put("source", "huangguo");
        return body;
    }

    private Map<String, Object> card(HuangguoClient.Catalog item) {
        Map<String, Object> card = new LinkedHashMap<>();
        card.put("id", item.id());
        card.put("title", item.title());
        card.put("cover", item.cover());
        card.put("description", item.description());
        card.put("score", item.score());
        card.put("episodeCount", item.episodeCount());
        card.put("tags", item.tags());
        return card;
    }

    private Map<String, Object> cached(String key, long ttlMs, java.util.function.Supplier<Map<String, Object>> supplier) {
        CacheValue hit = cache.get(key);
        if (hit != null && hit.expiresAt.isAfter(Instant.now())) {
            return cast(hit.value);
        }
        Map<String, Object> value = supplier.get();
        cache.put(key, new CacheValue(value, Instant.now().plusMillis(ttlMs)));
        return value;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> cast(Object value) {
        return (Map<String, Object>) value;
    }

    private record CacheValue(Object value, Instant expiresAt) { }
}
