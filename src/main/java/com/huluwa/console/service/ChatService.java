package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 消息中心会话服务：会话列表 / 消息流 / 发送（电影港搜索）/ 已读 / 清空 / 检索。
 *
 * 数据来源两条线：
 *  - 「电影港」等聊天会话：chat_session + chat_message（用户发送与机器人回复都落库）；
 *  - 「系统通知」虚拟会话：直接读既有 message 表（Frp / Docker / 推送等系统事件），不复制数据。
 * 发送电影港消息即同步触发站点搜索（约 1-2 秒），把结果按参考脚本的形态逐条落成机器人气泡：
 * 摘要一条 + 每条结果一个「封面图 + 配文」气泡（配文含标题/年代/产地/类型与详情页链接）。
 */
@Service
public class ChatService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 系统通知虚拟会话键（不落 chat_session，读 message 表） */
    public static final String SYSTEM_SESSION = "system";
    /** 电影港会话键（schema 内置） */
    public static final String MOVIE_SESSION = "movie";
    /** 单条搜索最多展示的结果条数（与参考脚本 scripts_dygangs_max 缺省一致） */
    private static final int MOVIE_SHOW_MAX = MovieSearchService.DEFAULT_MAX;

    private final JdbcTemplate jdbcTemplate;
    private final MovieSearchService movieSearchService;
    private final MessageService messageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(JdbcTemplate jdbcTemplate, MovieSearchService movieSearchService,
                       MessageService messageService) {
        this.jdbcTemplate = jdbcTemplate;
        this.movieSearchService = movieSearchService;
        this.messageService = messageService;
    }

    /** 会话消息（前后端统一形状，camelCase 下发） */
    public record ChatMessage(long id, String sessionId, String role, String type, String content,
                              String contentText, String payload, boolean read, String createdAt) {
    }

    // ------------------------------------------------------------------ 会话列表

    /** 会话列表（含未读与最后一条预览），按最后更新时间倒序 */
    public List<Map<String, Object>> sessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, title, kind, updated_at FROM chat_session ORDER BY updated_at DESC, id")) {
            String id = String.valueOf(row.get("id"));
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("title", row.get("title"));
            item.put("kind", row.get("kind"));
            item.put("unread", unread(id));
            item.put("last", lastMessage(id));
            item.put("updatedAt", row.get("updated_at"));
            list.add(item);
        }

        // 系统通知虚拟会话：始终排在最后（用户新建的聊天对象优先展示）
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("id", SYSTEM_SESSION);
        system.put("title", "系统通知");
        system.put("kind", "system");
        system.put("unread", countSystemUnread());
        system.put("last", lastSystemMessage());
        system.put("updatedAt", string(system.get("last") == null ? null
                : ((Map<?, ?>) system.get("last")).get("createdAt")));
        list.add(system);
        return list;
    }

    /** 会话存在性校验（系统通知为虚拟会话，不查表） */
    public void requireSession(String sessionId) {
        if (SYSTEM_SESSION.equals(sessionId)) {
            return;
        }
        Long found = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_session WHERE id = ?", Long.class, sessionId);
        if (found == null || found == 0L) {
            throw new BusinessException(404, "会话不存在");
        }
    }

    public String sessionTitle(String sessionId) {
        if (SYSTEM_SESSION.equals(sessionId)) {
            return "系统通知";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT title FROM chat_session WHERE id = ?", sessionId);
        return rows.isEmpty() ? sessionId : String.valueOf(rows.get(0).get("title"));
    }

    private long unread(String sessionId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_message WHERE session_id = ? AND role = 'bot' AND read_flag = 0",
                Long.class, sessionId);
        return value == null ? 0L : value;
    }

    private long countSystemUnread() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM message WHERE read_flag = 0", Long.class);
        return value == null ? 0L : value;
    }

    private Map<String, Object> lastMessage(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role, type, content, content_text, created_at FROM chat_message "
                        + "WHERE session_id = ? ORDER BY id DESC LIMIT 1", sessionId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> last = new LinkedHashMap<>();
        last.put("role", row.get("role"));
        last.put("type", row.get("type"));
        last.put("content", row.get("content"));
        last.put("contentText", row.get("content_text"));
        last.put("createdAt", row.get("created_at"));
        return last;
    }

    private Map<String, Object> lastSystemMessage() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, content, level, created_at FROM message ORDER BY id DESC LIMIT 1");
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> last = new LinkedHashMap<>();
        last.put("role", "bot");
        last.put("type", "notify");
        // 与 messages() 的系统通知映射保持同一形状：正文放 content，标题放 contentText，
        // 列表预览按标题展示（sessionPreview 的 notify 分支取 contentText）。
        last.put("content", row.get("content"));
        last.put("contentText", row.get("title"));
        last.put("createdAt", row.get("created_at"));
        return last;
    }

    // ------------------------------------------------------------------ 消息流

    /**
     * 会话消息（按 id 升序返回，前端从末尾开始渲染）。
     *
     * @param beforeId 向上翻页游标：仅返回 id 小于该值的更早消息（可为 null）
     * @param limit    本页条数（1-200）
     */
    public List<ChatMessage> messages(String sessionId, Long beforeId, int limit) {
        requireSession(sessionId);
        int size = Math.max(1, Math.min(limit, 200));
        if (SYSTEM_SESSION.equals(sessionId)) {
            return systemMessages(beforeId, size);
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, session_id, role, type, content, content_text, payload, read_flag, created_at "
                        + "FROM chat_message WHERE session_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(sessionId);
        if (beforeId != null) {
            sql.append(" AND id < ?");
            params.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<ChatMessage> list = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            list.add(toChatMessage(rows.get(i)));
        }
        return list;
    }

    /** 系统通知以 message 表映射为 notify 类型气泡（保留级别与来源） */
    private List<ChatMessage> systemMessages(Long beforeId, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, content, level, source, read_flag, created_at FROM message WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (beforeId != null) {
            sql.append(" AND id < ?");
            params.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<ChatMessage> list = new ArrayList<>();
        Map<String, Object> meta;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            meta = new LinkedHashMap<>();
            meta.put("level", row.get("level"));
            meta.put("source", row.get("source"));
            meta.put("title", row.get("title"));
            list.add(new ChatMessage(number(row.get("id")), SYSTEM_SESSION, "bot", "notify",
                    string(row.get("content")), string(row.get("title")), toJson(meta),
                    number(row.get("read_flag")) == 1, string(row.get("created_at"))));
        }
        return list;
    }

    private ChatMessage toChatMessage(Map<String, Object> row) {
        return new ChatMessage(number(row.get("id")), string(row.get("session_id")), string(row.get("role")),
                string(row.get("type")), string(row.get("content")), string(row.get("content_text")),
                string(row.get("payload")), number(row.get("read_flag")) == 1, string(row.get("created_at")));
    }

    // ------------------------------------------------------------------ 发送（搜索）

    /**
     * 发送消息。电影港会话同步执行站点搜索，返回本轮新增的全部消息（用户气泡 + 机器人回复），
     * 前端拿到后直接追加渲染；其它聊天会话只落一条用户气泡。
     */
    public List<ChatMessage> send(String sessionId, String content) {
        requireSession(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            throw new BusinessException(400, "系统通知为只读会话，不支持发送消息");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, "请输入搜索内容");
        }

        List<ChatMessage> produced = new ArrayList<>();
        produced.add(append(sessionId, "user", "text", text, null, null));

        String kind = sessionKind(sessionId);
        if ("movie".equals(kind)) {
            // 站点关键字用归一化后的文本（用户气泡仍保留原始输入）
            produced.addAll(runMovieSearch(sessionId, normalizeKeyword(text)));
        }
        touch(sessionId);
        return produced;
    }

    /**
     * 关键字归一化（对齐参考脚本 NormalizeKeyword 的用法）：
     * 兼容用户手打指令前缀「电影港搜索 凡人」/「电影港 凡人」→「凡人」，
     * 纯片名（含数字片名，如「2046」）保持原样。
     */
    static String normalizeKeyword(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        int space = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\u3000' || ch == '\t' || ch == '\n' || ch == '\r') {
                space = i;
                break;
            }
        }
        if (space <= 0) {
            return text;
        }
        String head = text.substring(0, space);
        if ("电影港搜索".equals(head) || "电影港".equals(head) || "dyg".equalsIgnoreCase(head)) {
            String rest = text.substring(space + 1).trim();
            return rest.isEmpty() ? text : rest;
        }
        return text;
    }

    /** 电影港搜索：对齐参考脚本「摘要一条 + 每条结果一条气泡（封面+配文）」的回复形态 */
    private List<ChatMessage> runMovieSearch(String sessionId, String keyword) {
        List<ChatMessage> replies = new ArrayList<>();
        List<MovieSearchService.MovieItem> found;
        try {
            found = movieSearchService.search(keyword);
        } catch (Exception e) {
            replies.add(append(sessionId, "bot", "error", "搜索失败：" + safeMessage(e), null, null));
            return replies;
        }

        if (found.isEmpty()) {
            replies.add(append(sessionId, "bot", "error",
                    "未搜索到「" + keyword + "」相关资源，换个片名试试（支持中英文片名）。", null, null));
            return replies;
        }

        int shown = Math.min(found.size(), MOVIE_SHOW_MAX);
        replies.add(append(sessionId, "bot", "text",
                "关键字「" + keyword + "」命中 " + found.size() + " 条，展示前 " + shown + " 条：", null, null));
        for (int i = 0; i < shown; i++) {
            MovieSearchService.MovieItem item = found.get(i);
            String caption = caption(item);
            String payload = toJson(MovieSearchService.toPayload(item));
            if (item.cover() == null || item.cover().isBlank()) {
                replies.add(append(sessionId, "bot", "text", caption, null, payload));
            } else {
                replies.add(append(sessionId, "bot", "image", item.cover(), caption, payload));
            }
        }
        return replies;
    }

    /** 配文：标题（年代 / 产地 / 类型）+ 详情页链接（前端自动识别为可点链接） */
    private static String caption(MovieSearchService.MovieItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(item.id()).append(']').append(item.title());
        List<String> meta = new ArrayList<>();
        if (notBlank(item.year())) {
            meta.add(item.year());
        }
        if (notBlank(item.origin())) {
            meta.add(item.origin());
        }
        if (notBlank(item.genre())) {
            meta.add(item.genre());
        }
        if (!meta.isEmpty()) {
            sb.append('（').append(String.join(" / ", meta)).append('）');
        }
        sb.append('\n').append("详情：").append(item.url());
        return sb.toString();
    }

    private ChatMessage append(String sessionId, String role, String type, String content,
                               String contentText, String payload) {
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO chat_message (session_id, role, type, content, content_text, payload, read_flag, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, sessionId, role, type, content, contentText, payload, "user".equals(role) ? 1 : 0, now);
        Long id = jdbcTemplate.queryForObject("SELECT last_insert_rowid()", Long.class);
        return new ChatMessage(id == null ? 0L : id, sessionId, role, type, content, contentText, payload,
                "user".equals(role), now);
    }

    private void touch(String sessionId) {
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().format(TIME_FORMAT), sessionId);
    }

    private String sessionKind(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT kind FROM chat_session WHERE id = ?", sessionId);
        return rows.isEmpty() ? "chat" : string(rows.get(0).get("kind"));
    }

    // ------------------------------------------------------------------ 已读 / 清空 / 检索

    /** 会话标记已读（系统通知同步推进既有 message 表，与「全部已读」同语义） */
    public void markRead(String sessionId) {
        requireSession(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            messageService.markAllRead();
            return;
        }
        // 标记已读不改动会话 updated_at：会话排序只随消息产生变化，避免已读操作把会话顶到列表最前
        jdbcTemplate.update(
                "UPDATE chat_message SET read_flag = 1 WHERE session_id = ? AND role = 'bot' AND read_flag = 0",
                sessionId);
    }

    /** 清空会话消息（仅聊天会话；系统通知由消息中心原有删除/已读入口管理） */
    public void clear(String sessionId) {
        requireSession(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            throw new BusinessException(400, "系统通知不支持清空，请使用消息中心的消息管理入口");
        }
        jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ?", sessionId);
        touch(sessionId);
    }

    /**
     * 跨会话消息检索（顶栏搜索）：按正文/配文/标题匹配，
     * 命中电影港等聊天会话的 chat_message 与系统通知的 message 表，按时间倒序返回。
     */
    public List<Map<String, Object>> search(String keyword, int limit) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, Math.min(limit, 50));
        String like = "%" + trimmed + "%";
        List<Map<String, Object>> list = new ArrayList<>();

        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT m.id, m.session_id, s.title, m.role, m.type, m.content, m.content_text, m.created_at
                  FROM chat_message m
                  JOIN chat_session s ON s.id = m.session_id
                 WHERE m.content LIKE ? OR IFNULL(m.content_text, '') LIKE ?
                 ORDER BY m.id DESC LIMIT ?
                """, like, like, size)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", row.get("session_id"));
            item.put("sessionTitle", row.get("title"));
            item.put("messageId", row.get("id"));
            item.put("role", row.get("role"));
            item.put("type", row.get("type"));
            item.put("content", row.get("content"));
            item.put("contentText", row.get("content_text"));
            item.put("createdAt", row.get("created_at"));
            list.add(item);
        }

        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT id, title, content, created_at
                  FROM message
                 WHERE title LIKE ? OR IFNULL(content, '') LIKE ?
                 ORDER BY id DESC LIMIT ?
                """, like, like, size)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", SYSTEM_SESSION);
            item.put("sessionTitle", "系统通知");
            item.put("messageId", row.get("id"));
            item.put("role", "bot");
            item.put("type", "notify");
            item.put("content", row.get("content"));
            item.put("contentText", row.get("title"));
            item.put("createdAt", row.get("created_at"));
            list.add(item);
        }

        list.sort((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
        return list.size() > size ? new ArrayList<>(list.subList(0, size)) : list;
    }

    // ------------------------------------------------------------------ 工具

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "网络异常，请稍后重试" : message;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
