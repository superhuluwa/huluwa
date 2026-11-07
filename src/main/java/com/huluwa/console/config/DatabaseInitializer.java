package com.huluwa.console.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动自检：schema.sql 由 Spring 数据源初始化阶段执行，这里只做结构校验与日志输出。
 */
@Component
@Order(1)
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    /** 所有建表语句都必须幂等，缺失任何一张表都说明 schema.sql 未正确执行 */
    private static final String[] REQUIRED_TABLES = {
            "sys_user", "sys_config", "message", "frp_config", "push_channel", "docker_host",
            "chat_session", "chat_message", "chat_request", "movie_search_watch",
            "hls_subscription", "hls_download_task", "subscription_check_log", "xunlei_win_download_task",
            "dpd_episode_link", "btbtla_detail_cache", "btbtla_magnet_cache",
            "fanqie_read_progress", "video_resource_cache",
            "starwar_player", "starwar_hangar", "starwar_friend", "starwar_phrase",
            "starwar_room", "starwar_room_member", "starwar_chat", "starwar_match",
            "starwar_match_result", "starwar_rank"
    };

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        addColumnIfMissing("android_build_task", "server_id", "TEXT NOT NULL DEFAULT 'local'");
        for (String table : REQUIRED_TABLES) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = ?",
                    Integer.class, table);
            if (found == null || found == 0) {
                throw new IllegalStateException("数据库缺少必需的表: " + table + "，请检查 schema.sql");
            }
        }
        addColumnIfMissing("fanqie_read_progress", "paragraph_index", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("fanqie_read_progress", "scroll_offset", "INTEGER NOT NULL DEFAULT 0");
        migrateMovieDownloadTasks();
        migrateDownloadHistory();
        migrateXunleiWinDownloads();
        refreshMovieMagnetEpisodes();
        refreshMovieSubscriptionMeta();
        // 旧库的 HLS 订阅没有海报字段，补列后由订阅巡检按需回填。
        addColumnIfMissing("hls_subscription", "cover", "TEXT");
        boolean hasSubscriptionTool = jdbcTemplate.queryForList("PRAGMA table_info(movie_subscription)").stream()
                .anyMatch(column -> "downloader".equals(column.get("name")));
        if (!hasSubscriptionTool) {
            jdbcTemplate.execute("ALTER TABLE movie_subscription ADD COLUMN downloader TEXT");
        }
        // 旧订阅把当前生效的工具保存为本剧绑定，后续修改全局默认值不会改变它。
        jdbcTemplate.update("""
                UPDATE movie_subscription SET downloader = COALESCE(
                    (SELECT cfg_value FROM sys_config
                      WHERE cfg_key = 'movie.download.downloader' AND cfg_value IN ('qb', 'xunlei')), 'qb')
                 WHERE downloader IS NULL OR downloader = ''
                """);
        Integer users = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user", Integer.class);
        boolean initialized = users != null && users > 0;
        log.info("数据库结构校验通过，系统{}", initialized ? "已初始化" : "尚未初始化（首次访问需设置密码）");
        if (!initialized) {
            log.info("首次启动：请在浏览器打开控制台并设置管理员密码");
        }
    }
    /** 旧数据库没有下载工具字段，原有任务按 qBittorrent 继续跟踪。 */
    private void migrateMovieDownloadTasks() {
        java.util.Set<String> columns = new java.util.HashSet<>();
        for (java.util.Map<String, Object> column : jdbcTemplate.queryForList("PRAGMA table_info(movie_download_task)")) {
            columns.add(String.valueOf(column.get("name")));
        }
        if (!columns.contains("downloader")) {
            jdbcTemplate.execute("ALTER TABLE movie_download_task ADD COLUMN downloader TEXT NOT NULL DEFAULT 'qb'");
        }
        if (!columns.contains("external_task_id")) {
            jdbcTemplate.execute("ALTER TABLE movie_download_task ADD COLUMN external_task_id TEXT");
        }
    }

    /**
     * 历史基线里剧集号可能是旧规则误判的（「…全集.1080p.mp4」把扩展名 mp4 里的 4 当成第 4 集）。
     * 每次启动结合资源标题、所属剧名与磁力 dn 按当前规则重算，不清空记录、不动基线时间。
     * 记录数只有订阅量级（数百条），一次性全表重算即可。
     */
    private void refreshMovieMagnetEpisodes() {
        var rows = jdbcTemplate.queryForList(
                """
                SELECT m.id, m.title, m.magnet, m.episode, s.title AS series_title
                  FROM movie_subscription_magnet m
                  LEFT JOIN movie_subscription s ON s.id = m.subscription_id
                """);
        int fixed = 0;
        for (var row : rows) {
            String title = String.valueOf(row.get("title") == null ? "" : row.get("title")).trim();
            String magnet = java.util.Objects.toString(row.get("magnet"), "");
            String seriesTitle = java.util.Objects.toString(row.get("series_title"), "");
            if (title.isEmpty() && magnet.isEmpty()) continue;
            String current = row.get("episode") == null ? null : String.valueOf(row.get("episode"));
            String recalculated = com.huluwa.console.service.MovieSearchService.episodeOf(title, seriesTitle, magnet);
            if (!java.util.Objects.equals(current, recalculated)) {
                jdbcTemplate.update("UPDATE movie_subscription_magnet SET episode = ? WHERE id = ?",
                        recalculated, row.get("id"));
                fixed++;
            }
        }
        if (fixed > 0) {
            log.info("已修正 {} 条订阅磁力的历史集号", fixed);
        }
    }

    /**
     * 旧版解析在「◎年　　代:」这种带冒号的模板上会把冒号当值存下来（如「:2026」）。
     * 每次启动剥掉前导冒号：只改文本不改行数，可重复执行。
     */
    private void refreshMovieSubscriptionMeta() {
        int fixed = 0;
        for (String column : new String[]{"year", "origin", "genre"}) {
            fixed += jdbcTemplate.update("""
                    UPDATE movie_subscription
                       SET %s = LTRIM(%s, ':：')
                     WHERE %s LIKE ':%%' OR %s LIKE '：%%'
                    """.formatted(column, column, column, column));
        }
        if (fixed > 0) {
            log.info("已修正 {} 处电影港订阅的年代 / 产地 / 类型字段", fixed);
        }
    }

    /** 「清空下载记录」依赖 hidden_at：旧库启动时补列，历史行保留给订阅去重与重复提交判定。 */
    private void migrateDownloadHistory() {
        addColumnIfMissing("hls_download_task", "hidden_at", "TEXT");
        addColumnIfMissing("movie_download_task", "hidden_at", "TEXT");
        addColumnIfMissing("xunlei_win_download_task", "hidden_at", "TEXT");
        // 「我的下载」的进度与资源信息：旧库补列后，迅雷历史行按需查询回填
        addColumnIfMissing("movie_download_task", "progress", "INTEGER");
        addColumnIfMissing("movie_download_task", "total_bytes", "INTEGER");
        addColumnIfMissing("movie_download_task", "received_bytes", "INTEGER");
        addColumnIfMissing("movie_download_task", "download_speed", "INTEGER");
        addColumnIfMissing("movie_download_task", "speedup_speed", "INTEGER");
        addColumnIfMissing("movie_download_task", "checked_at", "TEXT");
        addColumnIfMissing("movie_download_task", "query_error", "TEXT");
    }

    /** 补入升级前已保存的订阅任务及会话流水号，重复启动不覆盖已同步的状态。 */
    private void migrateXunleiWinDownloads() {
        var urls = jdbcTemplate.queryForList("SELECT cfg_value FROM sys_config WHERE cfg_key = 'xlwin.url'", String.class);
        String apiUrl = urls.isEmpty() ? "" : urls.getFirst().trim();
        if (apiUrl.isBlank()) {
            return;
        }
        jdbcTemplate.update("""
                INSERT OR IGNORE INTO xunlei_win_download_task
                    (api_url, serial, name, magnet, save_path, status, progress, message,
                     created_at, updated_at, completed_at)
                SELECT ?, external_task_id,
                       series_title || CASE WHEN episode IS NULL OR episode = '' THEN '' ELSE ' 第 ' || episode || ' 集' END,
                       magnet, save_path,
                       CASE status WHEN 'completed' THEN 'success' WHEN 'failed' THEN 'failed'
                                   WHEN 'downloading' THEN 'downloading' ELSE 'pending' END,
                       progress, message, created_at, updated_at, completed_at
                  FROM movie_download_task
                 WHERE downloader = 'xunlei-win' AND external_task_id IS NOT NULL AND external_task_id <> ''
                """, apiUrl);
        var serialPattern = java.util.regex.Pattern.compile("（流水号 ([^）\\r\\n]+)）");
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        for (var row : jdbcTemplate.queryForList("""
                SELECT content, payload, created_at FROM chat_message
                 WHERE session_id = 'xunlei-win' AND role = 'bot'
                   AND content LIKE '✅ 已提交迅雷【Win】下载任务（流水号 %'
                 ORDER BY id
                """)) {
            var matcher = serialPattern.matcher(String.valueOf(row.get("content")));
            if (!matcher.find()) {
                continue;
            }
            String serial = matcher.group(1).trim();
            if (serial.isBlank()) {
                continue;
            }
            String url = apiUrl;
            String savePath = "";
            try {
                var settings = json.readTree(String.valueOf(row.get("payload"))).path("settings");
                if (!settings.path("url").asText("").isBlank()) {
                    url = settings.path("url").asText();
                }
                savePath = settings.path("savePath").asText("");
            } catch (Exception ignored) {
                // 旧消息没有结构化配置时使用已保存的服务地址。
            }
            jdbcTemplate.update("""
                    INSERT OR IGNORE INTO xunlei_win_download_task
                        (api_url, serial, name, save_path, status, message, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'pending', '等待查询迅雷下载状态', ?, ?)
                    """, url, serial, "迅雷任务 " + serial, savePath, row.get("created_at"), row.get("created_at"));
        }
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        boolean exists = jdbcTemplate.queryForList("PRAGMA table_info(" + table + ")").stream()
                .anyMatch(item -> column.equals(item.get("name")));
        if (!exists) {
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

}
