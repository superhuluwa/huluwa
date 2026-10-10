package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息中心：系统消息的写入、查询、已读标记。
 */
@Service
public class MessageService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final RowMapper<Map<String, Object>> MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("title", rs.getString("title"));
        row.put("content", rs.getString("content"));
        row.put("level", rs.getString("level"));
        row.put("source", rs.getString("source"));
        row.put("read", rs.getInt("read_flag") == 1);
        row.put("createdAt", rs.getString("created_at"));
        row.put("readAt", rs.getString("read_at"));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;

    public MessageService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Map<String, Object>> list(String keyword, Boolean read, String level, int limit, int offset) {
        StringBuilder sql = new StringBuilder("SELECT * FROM message WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (title LIKE ? OR content LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
        }
        if (read != null) {
            sql.append(" AND read_flag = ?");
            params.add(read ? 1 : 0);
        }
        if (level != null && !level.isBlank()) {
            sql.append(" AND level = ?");
            params.add(level.trim());
        }
        sql.append(" ORDER BY id DESC LIMIT ? OFFSET ?");
        params.add(Math.max(1, Math.min(limit, 200)));
        params.add(Math.max(0, offset));
        return jdbcTemplate.query(sql.toString(), MAPPER, params.toArray());
    }

    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", count("SELECT COUNT(1) FROM message"));
        data.put("unread", count("SELECT COUNT(1) FROM message WHERE read_flag = 0"));
        data.put("error", count("SELECT COUNT(1) FROM message WHERE level = 'error'"));
        return data;
    }

    public void markRead(long id) {
        jdbcTemplate.update(
                "UPDATE message SET read_flag = 1, read_at = ? WHERE id = ? AND read_flag = 0",
                LocalDateTime.now().format(TIME_FORMAT), id);
    }

    public void markAllRead() {
        jdbcTemplate.update("UPDATE message SET read_flag = 1, read_at = ? WHERE read_flag = 0",
                LocalDateTime.now().format(TIME_FORMAT));
    }

    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM message WHERE id = ?", id);
        if (deleted == 0) {
            throw new BusinessException(404, "消息不存在");
        }
    }

    /** 供其他模块写入消息（预留：Frp / Docker / 推送事件） */
    public void publish(String title, String content, String level, String source) {
        jdbcTemplate.update("""
                INSERT INTO message (title, content, level, source, read_flag, created_at)
                VALUES (?, ?, ?, ?, 0, ?)
                """, title, content, level == null ? "info" : level, source,
                LocalDateTime.now().format(TIME_FORMAT));
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}