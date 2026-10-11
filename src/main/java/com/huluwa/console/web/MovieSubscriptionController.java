package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.MovieSubscriptionService;
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
 * 新剧集取最高清晰度提交 qBittorrent，并把更新 / 下载结果写入系统通知与「磁力下载」会话。</p>
 */
@RestController
@RequestMapping("/api/chat/subscriptions")
public class MovieSubscriptionController {

    private final MovieSubscriptionService subscriptionService;

    public MovieSubscriptionController(MovieSubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

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
                body.title(), body.url(), body.id(), body.year(), body.origin(), body.genre(), body.cover());
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
        int added = subscriptionService.scan(id);
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

    /** 某订阅触发的下载任务（默认最近 100 条；id=0 表示全部） */
    @GetMapping("/downloads")
    public ApiResult<List<Map<String, Object>>> downloads(@RequestParam(defaultValue = "0") long id) {
        return ApiResult.ok(subscriptionService.downloads(id));
    }

    /** 订阅请求体：与聊天消息里的影视卡片载荷同形（id/title/year/origin/genre/cover/url） */
    public record SubscribeRequest(String id, String title, String year, String origin, String genre,
                                   String cover, String url) {
    }
}
