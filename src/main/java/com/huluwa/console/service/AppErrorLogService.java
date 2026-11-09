package com.huluwa.console.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

/**
 * 手机端错误日志：App 的 AppLog W/E 级别条目批量上报入库，便于远程定位
 * 手机上的失败原因（手机无法连电脑看 Logcat）。只增不改，旧数据靠定期清理。
 */
@Service
public class AppErrorLogService {

    /** 单次上报条数上限，防止异常刷库 */
    private static final int MAX_BATCH = 100;

    /** 查询上限 */
    private static final int MAX_LIMIT = 500;

    private final JdbcTemplate jdbc;

    public AppErrorLogService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public int upload(String username, String version, String device,
                      List<Map<String, Object>> entries) {
        if (entries == null || entries.isEmpty()) {
            return 0;
        }
        int inserted = 0;
        Timestamp now = new Timestamp(System.currentTimeMillis());
        for (Map<String, Object> entry : entries) {
            if (entry == null || inserted >= MAX_BATCH) {
                break;
            }
            Object at = entry.get("at");
            Timestamp occurred = at instanceof Number number && number.longValue() > 0
                    ? new Timestamp(number.longValue()) : now;
            jdbc.update("INSERT INTO app_error_log (created_at, username, app_version, device, level, tag, message, detail) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    occurred.toLocalDateTime().toString(),
                    safe(username, 60), safe(version, 40), safe(device, 80),
                    safe(text(entry.get("level"), "E"), 4),
                    safe(text(entry.get("tag"), ""), 40),
                    safe(text(entry.get("message"), ""), 2000),
                    safe(text(entry.get("detail"), ""), 4000));
            inserted++;
        }
        return inserted;
    }

    public List<Map<String, Object>> list(int limit, String level, String tag) {
        int bounded = Math.min(Math.max(limit, 1), MAX_LIMIT);
        StringBuilder where = new StringBuilder(" WHERE 1=1");
        if (level != null && !level.isBlank()) {
            where.append(" AND level = ?");
        }
        if (tag != null && !tag.isBlank()) {
            where.append(" AND tag LIKE ?");
        }
        java.util.ArrayList<Object> params = new java.util.ArrayList<>();
        if (level != null && !level.isBlank()) {
            params.add(level.trim());
        }
        if (tag != null && !tag.isBlank()) {
            params.add("%" + tag.trim() + "%");
        }
        // 参数顺序与 WHERE 拼接顺序一致
        String sql = "SELECT id, created_at, username, app_version, device, level, tag, message, detail " +
                "FROM app_error_log" + where + " ORDER BY id DESC LIMIT " + bounded;
        return jdbc.queryForList(sql, params.toArray());
    }

    private static String text(Object value, String fallback) {
        return value instanceof String s && !s.isBlank() ? s : fallback;
    }

    private static String safe(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
