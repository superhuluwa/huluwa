package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.SubscriptionCheckLogService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅巡检记录（消息中心 → 巡检记录）：电影港与电影天堂两个来源共用一条时间线，
 * 按时间倒序返回每次巡检的结果快照。
 */
@RestController
@RequestMapping("/api/chat/check-logs")
public class SubscriptionCheckLogController {

    private final SubscriptionCheckLogService checkLogs;

    public SubscriptionCheckLogController(SubscriptionCheckLogService checkLogs) {
        this.checkLogs = checkLogs;
    }

    @GetMapping
    public ApiResult<List<Map<String, Object>>> list(@RequestParam(defaultValue = "100") int limit,
                                                     @RequestParam(required = false) String source) {
        return ApiResult.ok(checkLogs.list(limit, source));
    }

    /** 清空全部巡检记录 */
    @DeleteMapping
    public ApiResult<Map<String, Object>> clear() {
        int deleted = checkLogs.clear();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("deleted", deleted);
        data.put("message", deleted > 0 ? "已清空 " + deleted + " 条巡检记录" : "暂无可清空的巡检记录");
        return ApiResult.ok(data);
    }
}
