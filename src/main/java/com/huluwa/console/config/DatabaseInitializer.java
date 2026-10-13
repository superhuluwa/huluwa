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
            "chat_session", "chat_message", "chat_request"
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

}
