package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * chat_request 表操作：WebSocket 发送幂等与恢复。
 * request_id 由前端生成并持久化到本地 outbox；弱网重连或页面刷新后重放同一 request_id，
 * 服务端只落一条用户消息。状态机：received 已入库 → processing 后台处理中 → done / failed。
 *
 * <p>启动恢复（ApplicationRunner）：进程重启会把内存里的任务队列清空，
 * 残留 received/processing 的请求一律标 failed 并补一条错误气泡——不重新入队：
 * 磁力会话的中断点可能在「qB 已添加成功、尚未落结果消息」之间，重放会在 qB 重复建任务。</p>
 */
@Service
@Order(2)
public class ChatRequestService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ChatRequestService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 一次发送的既有受理记录（幂等重放命中时回读） */
    public record Existing(String requestId, String sessionId, long userMessageId, String status) {
    }

    private final JdbcTemplate jdbcTemplate;

    public ChatRequestService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Existing find(String requestId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT request_id, session_id, user_message_id, status FROM chat_request WHERE request_id = ?",
                requestId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        return new Existing(String.valueOf(row.get("request_id")), String.valueOf(row.get("session_id")),
                ((Number) row.get("user_message_id")).longValue(), String.valueOf(row.get("status")));
    }

    /** 登记一次受理（INSERT OR IGNORE：并发重放同一 requestId 时只有一个赢家） */
    public void insert(String requestId, String sessionId, long userMessageId) {
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO chat_request (request_id, session_id, user_message_id, status, created_at, updated_at)
                VALUES (?, ?, ?, 'received', ?, ?)
                """, requestId, sessionId, userMessageId, now, now);
    }

    public void markProcessing(String requestId) {
        jdbcTemplate.update("UPDATE chat_request SET status = 'processing', updated_at = ? WHERE request_id = ?",
                LocalDateTime.now().format(TIME_FORMAT), requestId);
    }

    public void markDone(String requestId) {
        jdbcTemplate.update("UPDATE chat_request SET status = 'done', updated_at = ? WHERE request_id = ?",
                LocalDateTime.now().format(TIME_FORMAT), requestId);
    }

    public void markFailed(String requestId, String error) {
        jdbcTemplate.update("UPDATE chat_request SET status = 'failed', error = ?, updated_at = ? WHERE request_id = ?",
                error, LocalDateTime.now().format(TIME_FORMAT), requestId);
    }

    /** 回读受理对应的用户消息行（幂等命中时组 ack 用） */
    public ChatService.ChatMessage userMessage(String requestId) {
        Existing existing = find(requestId);
        if (existing == null) {
            throw new BusinessException(500, "受理记录不存在: " + requestId);
        }
        List<ChatService.ChatMessage> rows = jdbcTemplate.query(
                "SELECT * FROM chat_message WHERE id = ?", (rs, rowNum) -> new ChatService.ChatMessage(
                        rs.getLong("id"), rs.getString("session_id"), rs.getString("role"), rs.getString("type"),
                        rs.getString("content"), rs.getString("content_text"), rs.getString("payload"),
                        rs.getInt("read_flag") == 1, rs.getString("created_at")),
                existing.userMessageId());
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 启动恢复：把重启前未完成的请求全部标 failed，并向所属会话补一条错误气泡，
     * 让打开着的页面在重连/轮询时能看到「处理被中断」而不是无限等待。
     */
    @Override
    public void run(ApplicationArguments args) {
        List<Map<String, Object>> pendings = jdbcTemplate.queryForList(
                "SELECT request_id, session_id, user_message_id FROM chat_request "
                        + "WHERE status IN ('received', 'processing')");
        for (Map<String, Object> pending : pendings) {
            String requestId = String.valueOf(pending.get("request_id"));
            String sessionId = String.valueOf(pending.get("session_id"));
            try {
                markFailed(requestId, "服务重启导致处理中断");
                String now = LocalDateTime.now().format(TIME_FORMAT);
                jdbcTemplate.update("""
                        INSERT INTO chat_message (session_id, role, type, content, content_text, payload, read_flag, created_at)
                        VALUES (?, 'bot', 'error', ?, NULL, NULL, 0, ?)
                        """, sessionId, "处理被服务重启中断，请重新发送。", now);
                jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?", now, sessionId);
            } catch (Exception e) {
                log.warn("恢复 chat_request {} 失败：{}", requestId, e.getMessage());
            }
        }
        if (!pendings.isEmpty()) {
            log.info("启动恢复：{} 条未完成的聊天请求已标记为 failed", pendings.size());
        }
    }
}
