package com.huluwa.console.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class UserRepository {

    private static final RowMapper<SysUser> MAPPER = (rs, rowNum) -> new SysUser(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password"),
            rs.getString("nickname"),
            rs.getString("created_at"),
            rs.getString("updated_at"));

    private final JdbcTemplate jdbcTemplate;

    public UserRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public long count() {
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM sys_user", Long.class);
        return total == null ? 0L : total;
    }

    public Optional<SysUser> findByUsername(String username) {
        return jdbcTemplate.query("SELECT * FROM sys_user WHERE username = ?", MAPPER, username)
                .stream().findFirst();
    }

    public void insert(String username, String passwordHash, String nickname, String now) {
        jdbcTemplate.update("""
                INSERT INTO sys_user (username, password, nickname, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, username, passwordHash, nickname, now, now);
    }

    public void updatePassword(String username, String passwordHash, String now) {
        jdbcTemplate.update("UPDATE sys_user SET password = ?, updated_at = ? WHERE username = ?",
                passwordHash, now, username);
    }

    public void updateUsername(String oldUsername, String newUsername, String now) {
        jdbcTemplate.update("UPDATE sys_user SET username = ?, updated_at = ? WHERE username = ?",
                newUsername, now, oldUsername);
    }

    public record SysUser(Long id, String username, String password, String nickname,
                          String createdAt, String updatedAt) {
    }
}