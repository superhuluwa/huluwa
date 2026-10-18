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
            "hls_subscription", "hls_download_task", "subscription_check_log", "xunlei_win_download_task"
    };

    private final JdbcTemplate jdbcTemplate;

    public DatabaseInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (String table : REQUIRED_TABLES) {
            Integer found = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = ?",
                    Integer.class, table);
            if (found == null || found == 0) {
                throw new IllegalStateException("数据库缺少必需的表: " + table + "，请检查 schema.sql");
            }
        }
        migrateMovieDownloadTasks();
        migrateDownloadHistory();
        migrateXunleiWinDownloads();
        refreshMovieMagnetEpisodes();
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
     * 每次启动按当前规则重算一遍：不清空记录、不动基线时间，只把错的集号改对 / 置空。
     * 记录数只有订阅量级（数百条），一次性全表重算即可。
     */
    private void refreshMovieMagnetEpisodes() {
        var rows = jdbcTemplate.queryForList(
                "SELECT id, title, episode FROM movie_subscription_magnet");
        int fixed = 0;
        for (var row : rows) {
            String title = String.valueOf(row.get("title") == null ? "" : row.get("title")).trim();
            if (title.isEmpty()) {
                continue;  // 没有标题就无从重算，保留原值避免误清
            }
            String current = row.get("episode") == null ? null : String.valueOf(row.get("episode"));
            String recalculated = com.huluwa.console.service.MovieSearchService.episodeOf(title);
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

    /** 「清空下载记录」依赖 hidden_at：旧库启动时补列，历史行保留给订阅去重与重复提交判定。 */
    private void migrateDownloadHistory() {
        addColumnIfMissing("hls_download_task", "hidden_at", "TEXT");
        addColumnIfMissing("movie_download_task", "hidden_at", "TEXT");
        addColumnIfMissing("xunlei_win_download_task", "hidden_at", "TEXT");
        // 「我的下载」进度列：迅雷【Win】与 qBittorrent 跟踪时回填，历史行保持空
        addColumnIfMissing("movie_download_task", "progress", "INTEGER");
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
