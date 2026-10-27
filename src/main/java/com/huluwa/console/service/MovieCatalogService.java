package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/** 电影港统一目录：磁力结果优先，同一作品的在线资源按需读取。 */
@Service
public class MovieCatalogService {
    private final MovieSearchService magnets;
    private final MovieHlsService online;
    private final MovieSubscriptionService subscriptions;

    public MovieCatalogService(MovieSearchService magnets, MovieHlsService online,
                               MovieSubscriptionService subscriptions) {
        this.magnets = magnets;
        this.online = online;
        this.subscriptions = subscriptions;
    }

    public record SearchResult(List<Map<String, Object>> films, List<String> errors) {}
    private record SourceResult(List<MovieSearchService.MovieItem> items, String error) {}

    public SearchResult search(String keyword) {
        // 新版详情链接直接读取作品；另一个来源在打开资源标签页时按片名匹配。
        if (MovieHlsService.isDetailUrl(keyword)) {
            return new SearchResult(online.search(keyword).stream().map(online::payload).toList(), List.of());
        }
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var magnetTask = executor.submit(() -> searchSource("磁力", () -> magnets.search(keyword)));
            var onlineTask = executor.submit(() -> searchSource("在线", () -> online.search(keyword)));
            SourceResult magnetResult = magnetTask.get();
            SourceResult onlineResult = onlineTask.get();
            List<Map<String, Object>> films = new ArrayList<>();
            Set<String> paired = new HashSet<>();
            for (var item : magnetResult.items()) {
                Map<String, Object> payload = MovieSearchService.toPayload(item);
                List<MovieSearchService.MovieItem> matches = matches(onlineResult.items(), item.title(), item.year());
                // 两边均唯一才合并，避免同名不同年份或多个版本串片。
                if (matches.size() == 1 && matches(magnetResult.items(), matches.getFirst().title(),
                        matches.getFirst().year()).size() == 1) {
                    String url = matches.getFirst().url();
                    payload.put("onlineUrl", url);
                    paired.add(url);
                }
                films.add(payload);
            }
            onlineResult.items().stream().filter(item -> !paired.contains(item.url()))
                    .map(online::payload).forEach(films::add);
            List<String> errors = new ArrayList<>();
            if (magnetResult.error() != null) errors.add(magnetResult.error());
            if (onlineResult.error() != null) errors.add(onlineResult.error());
            return new SearchResult(films, errors);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(502, "电影港搜索已中断，请重试");
        } catch (java.util.concurrent.ExecutionException e) {
            throw new BusinessException(502, "电影港搜索失败，请稍后重试");
        }
    }

    private SourceResult searchSource(String name, Supplier<List<MovieSearchService.MovieItem>> search) {
        try { return new SourceResult(search.get(), null); }
        catch (Exception e) {
            return new SourceResult(List.of(), name + "资源搜索失败：" +
                    (e.getMessage() == null ? "请稍后重试" : e.getMessage()));
        }
    }

    /** 每个标签页独立请求，某个站点失败不影响另一个标签页。 */
    public Map<String, Object> resources(String type, String url, String title, String year) {
        boolean isOnline = "online".equals(type);
        if (!isOnline && !"magnet".equals(type)) {
            throw new BusinessException(400, "请选择磁力或在线资源");
        }
        String detailUrl = url == null ? "" : url.trim();
        if (detailUrl.isBlank()) {
            String keyword = MovieHlsService.websiteSearchTitle(HlsSupport.required(title, "影片名称"));
            List<MovieSearchService.MovieItem> found = isOnline ? online.search(keyword) : magnets.search(keyword);
            List<MovieSearchService.MovieItem> matches = matches(found, title, year);
            if (matches.isEmpty()) {
                Map<String, Object> empty = new LinkedHashMap<>();
                empty.put("title", title);
                empty.put(isOnline ? "playSources" : "magnets", List.of());
                return empty;
            }
            if (matches.size() != 1) {
                throw new BusinessException(400, "找到多个同名" + (isOnline ? "在线" : "磁力") + "版本，请在会话中搜索并选择对应结果");
            }
            detailUrl = matches.getFirst().url();
        }
        return isOnline ? online.detail(detailUrl) : subscriptions.detailMagnets(detailUrl, title);
    }

    private List<MovieSearchService.MovieItem> matches(List<MovieSearchService.MovieItem> items, String title, String year) {
        String key = MovieHlsService.websiteTitleKey(title);
        return items.stream().filter(item -> MovieHlsService.websiteTitleKey(item.title()).equals(key))
                .filter(item -> year == null || year.isBlank() || item.year() == null || item.year().isBlank()
                        || year.equals(item.year())).toList();
    }
}
