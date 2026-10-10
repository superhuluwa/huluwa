package com.huluwa.console.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 启动自检：schema.sql 由 Spring 数据源初始化阶段执行，这里只做结构校验与日志输出。
 */
@Component
public class DatabaseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    /** 所有建表语句都必须幂等，缺失任何一张表都说明 schema.sql 未正确执行 */
    private static final String[] REQUIRED_TABLES = {
            "sys_user", "sys_config", "message", "frp_config", "push_channel", "docker_host"
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
        Integer users = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user", Integer.class);
        boolean initialized = users != null && users > 0;
        log.info("数据库结构校验通过，系统{}", initialized ? "已初始化" : "尚未初始化（首次访问需设置密码）");
        if (!initialized) {
            log.info("首次启动：请在浏览器打开控制台并设置管理员密码");
        }
    }
}