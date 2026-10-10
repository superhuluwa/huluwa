package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 推送设置：推送渠道的维护与测试（当前阶段：渠道配置落库 + 测试占位，暂不真正发起外部请求）。
 */
@Service
public class PushService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> TYPES = Set.of(
            "webhook", "serverchan", "bark", "telegram", "dingtalk", "wecom");

    private static final RowMapper<Map<String, Object>> MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", rs.getString("name"));
        row.put("type", rs.getString("type"));
        row.put("enabled", rs.getInt("enabled") == 1);
        row.put("config", rs.getString("config"));
        row.put("subscribe", rs.getString("subscribe"));
        row.put("createdAt", rs.getString("created_at"));
        row.put("updatedAt", rs.getString("updated_at"));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;

    public PushService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.query("SELECT * FROM push_channel ORDER BY id DESC", MAPPER);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO push_channel (name, type, enabled, config, subscribe, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, requireName(body), requireType(body), boolInt(body.get("enabled"), true),
                text(body.get("config"), null), text(body.get("subscribe"), null), now, now);
        return jdbcTemplate.query("SELECT * FROM push_channel ORDER BY id DESC LIMIT 1", MAPPER)
                .stream().findFirst().orElseThrow(() -> new BusinessException(500, "保存后读取失败"));
    }

    public Map<String, Object> update(long id, Map<String, Object> body) {
        requireExists(id);
        jdbcTemplate.update("""
                UPDATE push_channel
                   SET name = ?, type = ?, enabled = ?, config = ?, subscribe = ?, updated_at = ?
                 WHERE id = ?
                """, requireName(body), requireType(body), boolInt(body.get("enabled"), true),
                text(body.get("config"), null), text(body.get("subscribe"), null),
                LocalDateTime.now().format(TIME_FORMAT), id);
        return jdbcTemplate.query("SELECT * FROM push_channel WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(404, "推送渠道不存在"));
    }

    public void toggle(long id, boolean enabled) {
        requireExists(id);
        jdbcTemplate.update("UPDATE push_channel SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, LocalDateTime.now().format(TIME_FORMAT), id);
    }

    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM push_channel WHERE id = ?", id);
        if (deleted == 0) {
            throw new BusinessException(404, "推送渠道不存在");
        }
    }

    /** 可选的渠道类型清单，供前端下拉渲染 */
    public List<Map<String, Object>> channelTypes() {
        return List.of(
                type("webhook", "Webhook", "自定义 HTTP 回调"),
                type("serverchan", "Server 酱", "微信推送"),
                type("bark", "Bark", "iOS 推送"),
                type("telegram", "Telegram", "Bot 消息"),
                type("dingtalk", "钉钉机器人", "群机器人 Webhook"),
                type("wecom", "企业微信机器人", "群机器人 Webhook"));
    }

    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", count("SELECT COUNT(1) FROM push_channel"));
        data.put("enabled", count("SELECT COUNT(1) FROM push_channel WHERE enabled = 1"));
        return data;
    }

    /** 测试推送：占位实现，后续在此对接各渠道真实发送逻辑 */
    public Map<String, Object> test(long id) {
        Map<String, Object> channel = jdbcTemplate.query("SELECT * FROM push_channel WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(404, "推送渠道不存在"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("channel", channel.get("name"));
        result.put("success", false);
        result.put("message", "推送发送能力尚未接入，当前仅完成配置管理");
        return result;
    }

    private void requireExists(long id) {
        Long found = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM push_channel WHERE id = ?", Long.class, id);
        if (found == null || found == 0L) {
            throw new BusinessException(404, "推送渠道不存在");
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static Map<String, Object> type(String value, String label, String description) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("value", value);
        item.put("label", label);
        item.put("description", description);
        return item;
    }

    private static String requireName(Map<String, Object> body) {
        String name = text(body.get("name"), null);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "请填写渠道名称");
        }
        return name.trim();
    }

    private static String requireType(Map<String, Object> body) {
        String type = text(body.get("type"), null);
        if (type == null || type.isBlank()) {
            throw new BusinessException(400, "请选择推送类型");
        }
        String normalized = type.trim().toLowerCase();
        if (!TYPES.contains(normalized)) {
            throw new BusinessException(400, "不支持的推送类型: " + normalized);
        }
        return normalized;
    }

    private static int boolInt(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue ? 1 : 0;
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        return Boolean.parseBoolean(String.valueOf(value)) ? 1 : 0;
    }

    private static String text(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}