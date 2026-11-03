package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.VideoResourceCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 电影港合并会话中的磁力 / 在线资源标签页。 */
@RestController
@RequestMapping("/api/chat/movie")
public class MovieCatalogController {
    private final VideoResourceCacheService cache;

    public MovieCatalogController(VideoResourceCacheService cache) {
        this.cache = cache;
    }

    @GetMapping("/resources")
    public ApiResult<Map<String, Object>> resources(@RequestParam String type,
                                                   @RequestParam(defaultValue = "") String url,
                                                   @RequestParam String title,
                                                   @RequestParam(defaultValue = "") String year,
                                                   @RequestParam(defaultValue = "false") boolean cachedOnly) {
        return ApiResult.ok(cache.movieResources(type, url, title, year, cachedOnly));
    }
}
