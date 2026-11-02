package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.DongPianSearchService;
import com.huluwa.console.service.HlsDownloadService;
import com.huluwa.console.service.VideoResourceCacheService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 懂片帝搜索：作品详情与线路按需解析。
 *
 * <p>详情接口返回线路名与已更新集数，官方线路的 {@code /line} 仅列出已更新剧集及永久保存的链接，
 * 下载任务执行或用户主动解析时才兑换新地址。
 * 复用现有 /api 鉴权。
 */
@RestController
@RequestMapping("/api/dpd")
public class DpdController {

    private final DongPianSearchService source;

    private final HlsDownloadService downloads;
    private final VideoResourceCacheService cache;

    public DpdController(DongPianSearchService source, HlsDownloadService downloads, VideoResourceCacheService cache) {
        this.source = source;
        this.downloads = downloads;
        this.cache = cache;
    }

    public record DownloadRequest(String resId, String line, String title, Integer season, Integer episode) { }

    /** 只持久化作品、线路和集数，轮到任务下载时再取得真实链接。 */
    @PostMapping("/downloads")
    public ApiResult<Map<String, Object>> download(@RequestBody DownloadRequest body) {
        DongPianSearchService.Catalog catalog = source.detail(body.resId());
        return ApiResult.ok(downloads.enqueueDpd(catalog, body.line(), body.title(), body.season(), body.episode()));
    }

    /** 详情先返回持久化目录并后台刷新；cachedOnly 轮询不触发站点请求。 */
    @GetMapping("/detail")
    public ApiResult<Map<String, Object>> detail(@RequestParam String resId,
                                                 @RequestParam(defaultValue = "false") boolean cachedOnly) {
        return ApiResult.ok(cache.dpdDetail(resId, cachedOnly));
    }

    /** 指定线路的全部剧集；后台补齐第三方地址，官方线路不预兑换。 */
    @GetMapping("/line")
    public ApiResult<Map<String, Object>> line(@RequestParam String resId, @RequestParam String line,
                                               @RequestParam(defaultValue = "false") boolean cachedOnly) {
        return ApiResult.ok(cache.dpdLine(resId, line, cachedOnly));
    }

    /** 单集按需解析：长剧首屏之外的集在用户点开时才解析/兑换 */
    @GetMapping("/episode")
    public ApiResult<Map<String, Object>> episode(@RequestParam String resId, @RequestParam String line,
                                                  @RequestParam String title) {
        DongPianSearchService.Catalog catalog = source.detail(resId);
        DongPianSearchService.PlayItem item = source.resolveEpisode(catalog, line, title);
        return ApiResult.ok(source.toLinePayload(catalog, line, List.of(item)));
    }
}
