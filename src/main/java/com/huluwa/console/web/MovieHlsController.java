package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.HlsDownloadService;
import com.huluwa.console.service.MovieHlsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 电影港在线视频资源，沿用 /api 鉴权与现有离线下载队列。 */
@RestController
@RequestMapping("/api/chat/movie-hls")
public class MovieHlsController {
    private final MovieHlsService movies;
    private final HlsDownloadService downloads;

    public MovieHlsController(MovieHlsService movies, HlsDownloadService downloads) {
        this.movies = movies;
        this.downloads = downloads;
    }

    @GetMapping("/detail")
    public ApiResult<Map<String, Object>> detail(@RequestParam String url) {
        return ApiResult.ok(movies.detail(url));
    }

    @GetMapping("/resolve")
    public ApiResult<Map<String, String>> resolve(@RequestParam String url) {
        return ApiResult.ok(Map.of("url", movies.resolve(url)));
    }

    @PostMapping("/downloads")
    public ApiResult<Map<String, Object>> download(@RequestBody HlsRequests.Download body) {
        String player = movies.playUrl(body.url());
        String url = movies.resolve(player);
        // 电影港的数字 ID 与 MacCMS 不共用；按手动 HLS 任务入队，携带本站 Referer。
        return ApiResult.ok(downloads.enqueue(new HlsRequests.Download(null, null, body.title(), body.year(),
                body.season(), body.episode(), body.episodeTitle(), url, body.sourceName(), player, null, null)));
    }
}
