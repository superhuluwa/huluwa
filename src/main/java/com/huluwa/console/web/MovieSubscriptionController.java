package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.MovieSubscriptionService;
import com.huluwa.console.service.SubscriptionCheckLogService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 电影港订阅接口：消息中心搜索结果卡片上的「订阅 / 已订阅」按钮与订阅清单都走这里。
 *
 * <p>订阅后：片名与电影港具体链接入库、详情页每集磁力抓取入库作为基线，
 * 之后由 {@link MovieSubscriptionService} 每小时巡检差异（默认 60 分钟），
 * 新剧集取最高清晰度提交默认下载工具，并把更新 / 下载结果写入系统通知与「磁力下载」会话。</p>
 */
@RestController
@RequestMapping("/api/chat/subscriptions")
public class MovieSubscriptionController {

    private final MovieSubscriptionService subscriptionService;
    private final SubscriptionCheckLogService checkLogs;

    public MovieSubscriptionController(MovieSubscriptionService subscriptionService,
                                       SubscriptionCheckLogService checkLogs) {
        this.subscriptionService = subscriptionService;
        this.checkLogs = checkLogs;
    }

    @GetMapping("/settings")
    public ApiResult<MovieSubscriptionService.DownloadSettings> settings() {
        return ApiResult.ok(subscriptionService.downloadSettings());
    }

    @PostMapping("/settings")
    public ApiResult<MovieSubscriptionService.DownloadSettings> saveSettings(@RequestBody SettingsRequest body) {
        return ApiResult.ok(subscriptionService.saveDownloadSettings(body == null ? null : body.downloader()));
    }

    public record SettingsRequest(String downloader) { }

    /** 订阅清单（默认只回启用中的；includeDisabled=true 时含已取消） */
    @GetMapping
    public ApiResult<List<MovieSubscriptionService.Subscription>> list(
            @RequestParam(defaultValue = "false") boolean includeDisabled) {
        return ApiResult.ok(subscriptionService.list(includeDisabled));
    }

    /** 已订阅的详情页地址集合：搜索结果卡片据此把按钮渲染成「已订阅」 */
    @GetMapping("/urls")
    public ApiResult<Set<String>> urls() {
        return ApiResult.ok(subscriptionService.subscribedUrls());
    }

    /** 单个链接的订阅状态（卡片点开详情时按需查） */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status(@RequestParam String url) {
        MovieSubscriptionService.Subscription found = subscriptionService.find(url);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("subscribed", found != null && found.enabled());
        data.put("subscription", found);
        return ApiResult.ok(data);
    }

    /** 订阅（搜索结果卡片按钮）：参数缺片名时用详情页标题兜底 */
    @PostMapping
    public ApiResult<Map<String, Object>> subscribe(@RequestBody SubscribeRequest body) {
        if (body == null || body.url() == null || body.url().isBlank()) {
            throw new BusinessException(400, "缺少电影港详情页链接，无法订阅");
        }
        MovieSubscriptionService.SubscribeResult result = subscriptionService.subscribe(
                body.title(), body.url(), body.id(), body.year(), body.origin(), body.genre(), body.cover(), body.downloader());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("created", result.created());
        data.put("reenabled", result.reenabled());
        data.put("baselineCount", result.baselineCount());
        data.put("subscription", result.subscription());
        String title = result.subscription().title();
        String message;
        if (result.created()) {
            message = "已订阅「" + title + "」，已记录 " + result.baselineCount()
                    + " 条磁力作为对比基线，之后每小时巡检一次更新。";
        } else if (result.reenabled()) {
            message = "已重新订阅「" + title + "」，沿用此前记录的 "
                    + result.subscription().magnetCount() + " 条磁力基线（不会把老剧集重复下载）。";
        } else {
            message = "「" + title + "」已在订阅中，未重复添加。";
        }
        data.put("message", message);
        return ApiResult.ok(data);
    }

    @PostMapping("/{id}/settings")
    public ApiResult<MovieSubscriptionService.Subscription> updateSettings(
            @PathVariable long id, @RequestBody SettingsRequest body) {
        return ApiResult.ok(subscriptionService.updateSubscriptionSettings(id, body == null ? null : body.downloader()));
    }

    /** 取消订阅（保留磁力基线与下载记录，之后可以重新订阅） */
    @PostMapping("/{id}/unsubscribe")
    public ApiResult<Map<String, Object>> unsubscribe(@PathVariable long id) {
        subscriptionService.unsubscribe(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ok", true);
        data.put("message", "已取消订阅，历史磁力与下载记录仍保留。");
        return ApiResult.ok(data);
    }

    /** 立即巡检一次（订阅页的「立即检查」按钮）：返回本轮新增的磁力条数 */
    @PostMapping("/{id}/check")
    public ApiResult<Map<String, Object>> check(@PathVariable long id) {
        int added = subscriptionService.scan(id, SubscriptionCheckLogService.TRIGGER_MANUAL);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("added", added);
        data.put("message", added > 0
                ? "巡检完成：发现 " + added + " 条新磁力，最高清晰度已按需提交下载。"
                : "巡检完成：暂未发现新磁力。");
        return ApiResult.ok(data);
    }

    /** 删除订阅（连同磁力基线与下载记录） */
    @DeleteMapping("/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        subscriptionService.delete(id);
        return ApiResult.ok();
    }

    /** 某订阅已采集的磁力（详情页展开查看） */
    @GetMapping("/{id}/magnets")
    public ApiResult<List<Map<String, Object>>> magnets(@PathVariable long id) {
        return ApiResult.ok(subscriptionService.magnets(id));
    }

    /**
     * 详情页磁力清单（搜索结果卡片的「资源列表」）：实时抓取该剧详情页，不写入基线库。
     *
     * <p>返回体里每条磁力附带 {@code downloaded} 标记，前端据此禁用「已下载」按钮。</p>
     */
    @GetMapping("/magnets/preview")
    public ApiResult<Map<String, Object>> previewMagnets(@RequestParam String url,
                                                         @RequestParam(required = false) String title) {
        if (url == null || url.isBlank()) {
            throw new BusinessException(400, "缺少电影港详情页链接");
        }
        return ApiResult.ok(subscriptionService.detailMagnets(url, title));
    }

    /**
     * 手动下载一条磁力：保存目录仍是「下载根目录 / 剧名」，结果推送到「磁力下载」会话。
     *
     * <p>两种调用方式：{@code magnet} 直接给磁力原文（资源列表），
     * 或 {@code magnetId} 指定订阅库里的磁力记录（订阅页磁力列表）。</p>
     */
    @PostMapping("/magnets/download")
    public ApiResult<Map<String, Object>> downloadMagnet(@RequestBody DownloadMagnetRequest body) {
        if (body == null) {
            throw new BusinessException(400, "缺少请求参数");
        }
        if (body.magnetId() != null && body.magnetId() > 0) {
            return ApiResult.ok(subscriptionService.downloadStoredMagnet(body.magnetId()));
        }
        if (body.magnet() == null || body.magnet().isBlank()) {
            throw new BusinessException(400, "缺少磁力链接");
        }
        return ApiResult.ok(subscriptionService.downloadMagnet(
                body.url(), body.title(), body.magnet(), body.quality(), body.episode()));
    }

    /** 某订阅触发的下载任务（默认最近 100 条；id=0 表示全部） */
    @GetMapping("/downloads")
    public ApiResult<List<Map<String, Object>>> downloads(@RequestParam(defaultValue = "0") long id,
                                                          @RequestParam(defaultValue = "false") boolean refresh) {
        return ApiResult.ok(subscriptionService.downloads(id, refresh));
    }

    /** 清空下载记录：隐藏已完成 / 失败的历史行，进行中的任务保留 */
    @DeleteMapping("/downloads")
    public ApiResult<Map<String, Object>> clearDownloads() {
        return ApiResult.ok(subscriptionService.clearDownloads());
    }

    /** 手动下载请求体：magnet 直传原文；magnetId 表示用订阅库里的磁力记录 */
    public record DownloadMagnetRequest(Long magnetId, String url, String title,
                                        String magnet, String quality, String episode) {
    }

    /** 订阅请求体：与聊天消息里的影视卡片载荷同形（id/title/year/origin/genre/cover/url） */
    public record SubscribeRequest(String id, String title, String year, String origin, String genre,
                                   String cover, String url, String downloader) {
    }
}
