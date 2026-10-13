package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.DyttSearchService;
import com.huluwa.console.service.HlsDownloadService;
import com.huluwa.console.service.HlsSubscriptionService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** 复用现有 /api 鉴权；读取接口不返回播放签名地址、Cookie 或原始日志路径。 */
@RestController
@RequestMapping("/api/hls")
public class HlsController {
    private final HlsSubscriptionService subscriptions;
    private final HlsDownloadService downloads;
    private final DyttSearchService source;

    public HlsController(HlsSubscriptionService subscriptions, HlsDownloadService downloads, DyttSearchService source) {
        this.subscriptions = subscriptions; this.downloads = downloads; this.source = source;
    }
    @GetMapping("/settings")
    public ApiResult<HlsDownloadService.Settings> settings() { return ApiResult.ok(downloads.settings()); }
    @PostMapping("/settings")
    public ApiResult<HlsDownloadService.Settings> settings(@RequestBody HlsRequests.Settings body) {
        return ApiResult.ok(downloads.saveSettings(body));
    }
    @GetMapping("/detail")
    public ApiResult<DyttSearchService.DyttItem> detail(@RequestParam String resId, @RequestParam(required = false) String apiUrl) {
        return ApiResult.ok(source.detail(apiUrl, resId));
    }
    @GetMapping("/subscriptions")
    public ApiResult<List<Map<String, Object>>> subscriptions() { return ApiResult.ok(subscriptions.list()); }
    @PostMapping("/subscriptions")
    public ApiResult<Map<String, Object>> subscribe(@RequestBody HlsRequests.Subscription body) {
        return ApiResult.ok(subscriptions.subscribe(body));
    }
    @PostMapping("/subscriptions/quick")
    public ApiResult<Map<String, Object>> quickSubscribe(@RequestBody HlsRequests.QuickSubscription body) {
        return ApiResult.ok(subscriptions.quickSubscribe(body));
    }
    @PostMapping("/subscriptions/{id}/check")
    public ApiResult<Map<String, Object>> check(@PathVariable long id) { return ApiResult.ok(subscriptions.check(id)); }
    @PostMapping("/subscriptions/{id}/enabled")
    public ApiResult<Map<String, Object>> enabled(@PathVariable long id, @RequestBody HlsRequests.Enabled body) {
        return ApiResult.ok(subscriptions.enabled(id, body.enabled()));
    }
    /** 取消订阅即删除：清除该订阅与后续追更任务，本地已归档文件保留 */
    @DeleteMapping("/subscriptions/{id}")
    public ApiResult<Map<String, Object>> delete(@PathVariable long id) { return ApiResult.ok(subscriptions.delete(id)); }
    @GetMapping("/downloads")
    public ApiResult<List<Map<String, Object>>> downloads() { return ApiResult.ok(downloads.list()); }
    /** 清空下载记录：隐藏已完成 / 失败 / 跳过的历史行，进行中的任务保留 */
    @DeleteMapping("/downloads")
    public ApiResult<Map<String, Object>> clearDownloads() { return ApiResult.ok(downloads.clearHistory()); }
    @PostMapping("/downloads")
    public ApiResult<Map<String, Object>> download(@RequestBody HlsRequests.Download body) {
        return ApiResult.ok(downloads.enqueue(body));
    }
    @PostMapping("/downloads/{id}/retry")
    public ApiResult<Map<String, Object>> retry(@PathVariable long id) { return ApiResult.ok(downloads.retry(id)); }
    @GetMapping("/downloads/{id}/log")
    public ApiResult<Map<String, String>> log(@PathVariable long id) { return ApiResult.ok(downloads.log(id)); }
}
