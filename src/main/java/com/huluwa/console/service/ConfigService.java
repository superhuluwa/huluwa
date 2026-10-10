package com.huluwa.console.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * sys_config 键值配置读写。
 */
@Service
public class ConfigService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final JdbcTemplate jdbcTemplate;

    public ConfigService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public String get(String key) {
        return get(key, null);
    }

    public String get(String key, String defaultValue) {
        return find(key).orElse(defaultValue);
    }

    public Optional<String> find(String key) {
        return jdbcTemplate.query(
                "SELECT cfg_value FROM sys_config WHERE cfg_key = ?",
                rs -> rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty(),
                key);
    }

    public void put(String key, String value) {
        jdbcTemplate.update("""
                INSERT INTO sys_config (cfg_key, cfg_value, updated_at) VALUES (?, ?, ?)
                ON CONFLICT(cfg_key) DO UPDATE SET cfg_value = excluded.cfg_value, updated_at = excluded.updated_at
                """, key, value, LocalDateTime.now().format(TIME_FORMAT));
    }

    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return defaultValue;
        }
    }
}