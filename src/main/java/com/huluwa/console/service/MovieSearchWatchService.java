package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.ws.ChatPushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** 搜索暂无资源时的关键字监听：后端持久化巡检，命中后通知一次并结束。 */
@Service
public class MovieSearchWatchService {

    private static final Logger log = LoggerFactory.getLogger(MovieSearchWatchService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MAX_PER_ROUND = 10;
    private static final RowMapper<SearchWatch> MAPPER = (rs, rowNum) -> new SearchWatch(
            rs.getLong("id"), rs.getString("keyword"), rs.getString("status"), rs.getInt("interval_minutes"),
            rs.getString("created_at"), rs.getString("updated_at"), rs.getString("last_check_at"),
            rs.getString("next_check_at"), rs.getString("found_at"), rs.getInt("result_count"),
            rs.getString("check_error"), rs.getLong("version"));

    private final JdbcTemplate jdbc;
    private final MovieSearchService movieSearch;
    private final ChatService chatService;
    private final ChatPushService chatPush;
    private final TransactionTemplate transaction;
    private final int intervalMinutes;

    public MovieSearchWatchService(JdbcTemplate jdbc, MovieSearchService movieSearch, ChatService chatService,
                                   ChatPushService chatPush, PlatformTransactionManager transactionManager,
                                   @Value("${huluwa.movie.search-watch-interval-minutes:30}") int intervalMinutes) {
        this.jdbc = jdbc;
        this.movieSearch = movieSearch;
        this.chatService = chatService;
        this.chatPush = chatPush;
        this.transaction = new TransactionTemplate(transactionManager);
        this.intervalMinutes = intervalMinutes > 0 ? intervalMinutes : 30;
    }

    public record SearchWatch(long id, String keyword, String status, int intervalMinutes,
                              String createdAt, String updatedAt, String lastCheckAt, String nextCheckAt,
                              String foundAt, int resultCount, String checkError, long version) {
    }

    private record Completion(SearchWatch watch, List<ChatService.ChatMessage> movies,
                              ChatService.ChatMessage notification) {
    }

    public List<SearchWatch> list() {
        return jdbc.query("SELECT * FROM movie_search_watch ORDER BY id DESC", MAPPER);
    }

    /** 同一关键字的重复点击不重置下次检查时间；取消或已结束的任务可重新开启。 */
    public SearchWatch start(String rawKeyword) {
        String keyword = ChatService.normalizeKeyword(rawKeyword);
        if (keyword.isBlank()) {
            throw new BusinessException(400, "请输入监听关键字");
        }
        if (keyword.length() > 200) {
            throw new BusinessException(400, "监听关键字最多 200 个字符");
        }
        LocalDateTime now = LocalDateTime.now();
        String keywordKey = keyword.toLowerCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO movie_search_watch
                    (keyword, keyword_key, status, interval_minutes, created_at, updated_at, next_check_at)
                VALUES (?, ?, 'watching', ?, ?, ?, ?)
                ON CONFLICT(keyword_key) DO UPDATE SET
                    keyword = excluded.keyword, status = 'watching', interval_minutes = excluded.interval_minutes,
                    updated_at = excluded.updated_at, last_check_at = NULL, next_check_at = excluded.next_check_at,
                    found_at = NULL, result_count = 0, check_error = NULL, version = movie_search_watch.version + 1
                WHERE movie_search_watch.status <> 'watching'
                """, keyword, keywordKey, intervalMinutes, stamp(now), stamp(now), stamp(now.plusMinutes(intervalMinutes)));
        SearchWatch watch = jdbc.query("SELECT * FROM movie_search_watch WHERE keyword_key = ?", MAPPER, keywordKey)
                .stream().findFirst().orElseThrow(() -> new BusinessException(500, "保存监听任务失败"));
        chatPush.movieSearchWatch(watch);
        return watch;
    }

    public SearchWatch cancel(long id) {
        jdbc.update("""
                UPDATE movie_search_watch SET status = 'cancelled', next_check_at = NULL, updated_at = ?, version = version + 1
                 WHERE id = ? AND status = 'watching'
                """, stamp(LocalDateTime.now()), id);
        SearchWatch watch = find(id);
        chatPush.movieSearchWatch(watch);
        return watch;
    }

    /** 每分钟唤醒，只执行到期任务；重启后从数据库继续巡检。 */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void scheduledScan() {
        try {
            List<SearchWatch> due = jdbc.query("""
                    SELECT * FROM movie_search_watch
                     WHERE status = 'watching' AND next_check_at <= ?
                     ORDER BY next_check_at, id LIMIT ?
                    """, MAPPER, stamp(LocalDateTime.now()), MAX_PER_ROUND);
            for (SearchWatch watch : due) {
                try {
                    check(watch);
                } catch (Exception e) {
                    log.warn("电影港监听任务 {} 检查失败：{}", watch.id(), safeMessage(e));
                }
            }
        } catch (Exception e) {
            log.warn("电影港监听读取到期任务失败：{}", safeMessage(e));
        }
    }

    private void check(SearchWatch due) {
        LocalDateTime now = LocalDateTime.now();
        // 先推进检查时间并领取新版本，网络调用不占用数据库事务。
        int claimed = jdbc.update("""
                UPDATE movie_search_watch
                   SET last_check_at = ?, next_check_at = ?, updated_at = ?, check_error = NULL, version = version + 1
                 WHERE id = ? AND status = 'watching' AND version = ? AND next_check_at <= ?
                """, stamp(now), stamp(now.plusMinutes(due.intervalMinutes())), stamp(now),
                due.id(), due.version(), stamp(now));
        if (claimed == 0) {
            return;
        }
        long checkVersion = due.version() + 1;
        List<MovieSearchService.MovieItem> found;
        try {
            found = movieSearch.search(due.keyword());
        } catch (Exception e) {
            jdbc.update("""
                    UPDATE movie_search_watch SET check_error = ?, updated_at = ?, version = version + 1
                     WHERE id = ? AND status = 'watching' AND version = ?
                    """, safeMessage(e), stamp(LocalDateTime.now()), due.id(), checkVersion);
            chatPush.movieSearchWatch(find(due.id()));
            log.warn("电影港监听「{}」搜索失败，将按计划重试：{}", due.keyword(), safeMessage(e));
            return;
        }
        if (found.isEmpty()) {
            chatPush.movieSearchWatch(find(due.id()));
            return;
        }
        // 完成标记、资源卡片与系统通知一并提交；取消或重新开启后，旧查询不能发出通知。
        Completion completion = transaction.execute(status -> {
            String finishedAt = stamp(LocalDateTime.now());
            int updated = jdbc.update("""
                    UPDATE movie_search_watch
                       SET status = 'found', next_check_at = NULL, found_at = ?, result_count = ?,
                           check_error = NULL, updated_at = ?, version = version + 1
                     WHERE id = ? AND status = 'watching' AND version = ?
                    """, finishedAt, found.size(), finishedAt, due.id(), checkVersion);
            if (updated == 0) {
                return null;
            }
            List<ChatService.ChatMessage> movies = chatService.appendWatchedMovieResults(due.keyword(), found);
            ChatService.ChatMessage notification = chatService.appendSystemNotification(
                    "电影港监听已找到资源：" + due.keyword(), notificationContent(due.keyword(), found),
                    "success", "电影港监听");
            return new Completion(find(due.id()), movies, notification);
        });
        if (completion == null) {
            return;
        }
        // 推送在提交后发出，离线客户端下次进入会话可读取已持久化的消息。
        chatPush.movieSearchWatch(completion.watch());
        chatPush.messages(ChatService.MOVIE_SESSION, completion.movies());
        chatPush.messages(ChatService.SYSTEM_SESSION, List.of(completion.notification()));
        chatPush.sessions(chatService.sessions());
        log.info("电影港监听「{}」命中 {} 条资源，已发送系统通知", due.keyword(), found.size());
    }

    private SearchWatch find(long id) {
        return jdbc.query("SELECT * FROM movie_search_watch WHERE id = ?", MAPPER, id).stream()
                .findFirst().orElseThrow(() -> new BusinessException(404, "监听任务不存在"));
    }

    private static String notificationContent(String keyword, List<MovieSearchService.MovieItem> found) {
        int shown = Math.min(found.size(), MovieSearchService.DEFAULT_MAX);
        StringBuilder content = new StringBuilder("监听关键字「").append(keyword).append("」已找到 ")
                .append(found.size()).append(" 条资源，本次监听已结束。\n资源卡片已发送到电影港会话。\n");
        for (int i = 0; i < shown; i++) {
            MovieSearchService.MovieItem item = found.get(i);
            content.append("\n• ").append(item.title()).append("\n详情：").append(item.url()).append('\n');
        }
        if (found.size() > shown) {
            content.append("\n展示前 ").append(shown).append(" 条，可在电影港会话继续搜索查看。");
        }
        return content.toString().trim();
    }

    private static String stamp(LocalDateTime time) {
        return time.format(TIME_FORMAT);
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null || e.getMessage().isBlank() ? "网络异常，请稍后重试" : e.getMessage();
    }
}
