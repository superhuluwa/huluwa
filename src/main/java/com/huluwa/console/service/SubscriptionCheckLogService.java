package com.huluwa.console.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅巡检记录：电影港与电影天堂每次巡检（定时 / 手动）的结果快照，
 * 供「消息中心 → 巡检记录」按时间倒序翻看。
 *
 * <p>剧名与来源直接冗余存储在记录行里：订阅被取消或删除后，历史记录仍然完整可读；
 * 删除订阅时只把 subscription_id 置空，记录本身保留。</p>
 */
@Service
public class SubscriptionCheckLogService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionCheckLogService.class);

    /** 来源标识：电影港（磁力订阅） */
    public static final String SOURCE_DYGANG = "dygangs";
    /** 来源标识：电影天堂（离线剧集订阅） */
    public static final String SOURCE_DYTT = "dytt";

    /** 定时巡检 */
    public static final String TRIGGER_SCHEDULED = "scheduled";
    /** 页面上的「立即检查」 */
    public static final String TRIGGER_MANUAL = "manual";

    /** 有新增条目并已按需提交下载 */
    public static final String STATUS_UPDATED = "updated";
    /** 巡检成功但没有新条目 */
    public static final String STATUS_OK = "ok";
    /** 基线未建立，本轮只记录基线不触发下载 */
    public static final String STATUS_BASELINE = "baseline";
    /** 巡检失败（站点不可达、解析异常等） */
    public static final String STATUS_FAILED = "failed";

    /** 单次结果明细最多保留的行数（列表里每条记录都会展开这些文字） */
    private static final int MAX_DETAIL_LINES = 12;
    /** 明细行最大字符数 */
    private static final int MAX_DETAIL_LINE_LENGTH = 200;
    /** 单次结果明细最大字符数 */
    private static final int MAX_DETAIL_LENGTH = 2000;

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionCheckLogService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 写一条巡检记录；记录失败只写日志，绝不影响巡检本身 */
    public void record(String source, Long subscriptionId, String seriesTitle, String triggerType,
                       String status, int foundCount, int submittedCount, String message,
                       List<String> detail, long durationMs) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO subscription_check_log
                        (source, subscription_id, series_title, trigger_type, status,
                         found_count, submitted_count, message, detail, duration_ms, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, source, subscriptionId, title(seriesTitle),
                    triggerType == null || triggerType.isBlank() ? TRIGGER_SCHEDULED : triggerType,
                    status, foundCount, submittedCount, truncate(message, MAX_DETAIL_LENGTH),
                    joinDetail(detail), Math.max(durationMs, 0), HlsSupport.now());
        } catch (Exception e) {
            log.warn("写入订阅巡检记录失败（{} / {}）：{}", source, seriesTitle, e.getMessage());
        }
    }

    /**
     * 巡检记录列表（倒序）。
     *
     * @param limit 最多返回多少条，1 ~ 500，默认 100
     * @param source 只看某个来源（可为空：不限来源）
     */
    public List<Map<String, Object>> list(int limit, String source) {
        int size = limit <= 0 ? 100 : Math.min(limit, 500);
        String normalized = source == null ? "" : source.trim().toLowerCase(java.util.Locale.ROOT);
        StringBuilder sql = new StringBuilder("SELECT * FROM subscription_check_log");
        List<Object> params = new ArrayList<>();
        if (!normalized.isBlank()) {
            sql.append(" WHERE source = ?");
            params.add(normalized);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(size);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(sql.toString(), params.toArray())) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", row.get("id"));
            item.put("source", row.get("source"));
            item.put("subscriptionId", row.get("subscription_id"));
            item.put("seriesTitle", row.get("series_title"));
            item.put("trigger", row.get("trigger_type"));
            item.put("status", row.get("status"));
            item.put("foundCount", row.get("found_count"));
            item.put("submittedCount", row.get("submitted_count"));
            item.put("message", row.get("message"));
            item.put("details", splitDetail(row.get("detail")));
            item.put("durationMs", row.get("duration_ms"));
            item.put("createdAt", row.get("created_at"));
            rows.add(item);
        }
        return rows;
    }

    /** 记录条数（含两个来源），供页面显示统计摘要 */
    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM subscription_check_log", Long.class);
        return total == null ? 0L : total;
    }

    /** 清空巡检记录，返回删除条数 */
    public int clear() {
        return jdbcTemplate.update("DELETE FROM subscription_check_log");
    }

    /**
     * 订阅删除时保留历史记录：只断开与订阅行的关联，记录本身继续可读。
     * 订阅被取消（enabled=0）时记录保留，便于回溯；重新订阅后继续累加。
     */
    public void detach(long subscriptionId) {
        try {
            jdbcTemplate.update(
                    "UPDATE subscription_check_log SET subscription_id = NULL WHERE subscription_id = ?",
                    subscriptionId);
        } catch (Exception e) {
            log.warn("解除巡检记录与订阅 {} 的关联失败：{}", subscriptionId, e.getMessage());
        }
    }

    /** 明细行拼接：去掉空行、限制单行与整体长度 */
    private static String joinDetail(List<String> detail) {
        if (detail == null || detail.isEmpty()) {
            return null;
        }
        List<String> lines = new ArrayList<>();
        int size = 0;
        for (String line : detail) {
            String value = truncate(line, MAX_DETAIL_LINE_LENGTH);
            if (value == null || value.isBlank()) {
                continue;
            }
            if (lines.size() >= MAX_DETAIL_LINES || size + value.length() > MAX_DETAIL_LENGTH) {
                lines.add("…（其余条目请在「我的下载」查看）");
                break;
            }
            lines.add(value);
            size += value.length() + 1;
        }
        return lines.isEmpty() ? null : String.join("\n", lines);
    }

    private static List<String> splitDetail(Object value) {
        List<String> lines = new ArrayList<>();
        if (value == null) {
            return lines;
        }
        for (String line : String.valueOf(value).split("\n")) {
            String text = line.trim();
            if (!text.isEmpty()) {
                lines.add(text);
            }
        }
        return lines;
    }

    private static String title(String value) {
        String text = value == null ? "" : value.trim();
        return text.isEmpty() ? "未知订阅" : truncate(text, 200);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
