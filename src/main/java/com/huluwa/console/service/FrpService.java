package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.AppProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Frp 内网穿透配置管理（当前阶段：配置的增删改查 + 运行环境探测，暂不拉起 frpc 进程）。
 */
@Service
public class FrpService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final Set<String> PROXY_TYPES = Set.of("tcp", "udp", "http", "https");

    private static final RowMapper<Map<String, Object>> MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", rs.getString("name"));
        row.put("proxyType", rs.getString("proxy_type"));
        row.put("localIp", rs.getString("local_ip"));
        row.put("localPort", rs.getInt("local_port"));
        row.put("remotePort", rs.getObject("remote_port"));
        row.put("customDomain", rs.getString("custom_domain"));
        row.put("subdomain", rs.getString("subdomain"));
        row.put("extra", rs.getString("extra"));
        row.put("enabled", rs.getInt("enabled") == 1);
        row.put("createdAt", rs.getString("created_at"));
        row.put("updatedAt", rs.getString("updated_at"));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public FrpService(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<Map<String, Object>> list() {
        return jdbcTemplate.query("SELECT * FROM frp_config ORDER BY id DESC", MAPPER);
    }

    public Map<String, Object> create(Map<String, Object> body) {
        String name = requireName(body);
        String proxyType = normalizeProxyType(body);
        int localPort = requirePort(body.get("localPort"), "本地端口");
        Integer remotePort = optionalInt(body.get("remotePort"));
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO frp_config (name, proxy_type, local_ip, local_port, remote_port,
                                        custom_domain, subdomain, extra, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, name, proxyType, text(body.get("localIp"), "127.0.0.1"), localPort, remotePort,
                text(body.get("customDomain"), null), text(body.get("subdomain"), null),
                text(body.get("extra"), null), boolInt(body.get("enabled"), true), now, now);
        return findLatest();
    }

    public Map<String, Object> update(long id, Map<String, Object> body) {
        requireExists(id);
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                UPDATE frp_config
                   SET name = ?, proxy_type = ?, local_ip = ?, local_port = ?, remote_port = ?,
                       custom_domain = ?, subdomain = ?, extra = ?, enabled = ?, updated_at = ?
                 WHERE id = ?
                """, requireName(body), normalizeProxyType(body), text(body.get("localIp"), "127.0.0.1"),
                requirePort(body.get("localPort"), "本地端口"), optionalInt(body.get("remotePort")),
                text(body.get("customDomain"), null), text(body.get("subdomain"), null),
                text(body.get("extra"), null), boolInt(body.get("enabled"), true), now, id);
        return byId(id);
    }

    public void toggle(long id, boolean enabled) {
        requireExists(id);
        jdbcTemplate.update("UPDATE frp_config SET enabled = ?, updated_at = ? WHERE id = ?",
                enabled ? 1 : 0, LocalDateTime.now().format(TIME_FORMAT), id);
    }

    public void delete(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM frp_config WHERE id = ?", id);
        if (deleted == 0) {
            throw new BusinessException(404, "隧道配置不存在");
        }
    }

    /** 运行环境信息：frpc 路径是否已配置（后续接入进程管理时扩展） */
    public Map<String, Object> runtime() {
        String binary = properties.getFrp().getBinary();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("binaryConfigured", binary != null && !binary.isBlank());
        data.put("binary", binary);
        data.put("processManaged", false);
        data.put("total", count("SELECT COUNT(1) FROM frp_config"));
        data.put("enabled", count("SELECT COUNT(1) FROM frp_config WHERE enabled = 1"));
        return data;
    }

    private Map<String, Object> findLatest() {
        return jdbcTemplate.query("SELECT * FROM frp_config ORDER BY id DESC LIMIT 1", MAPPER)
                .stream().findFirst().orElseThrow(() -> new BusinessException(500, "保存后读取失败"));
    }

    private Map<String, Object> byId(long id) {
        return jdbcTemplate.query("SELECT * FROM frp_config WHERE id = ?", MAPPER, id)
                .stream().findFirst().orElseThrow(() -> new BusinessException(404, "隧道配置不存在"));
    }

    private void requireExists(long id) {
        Long found = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM frp_config WHERE id = ?", Long.class, id);
        if (found == null || found == 0L) {
            throw new BusinessException(404, "隧道配置不存在");
        }
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static String requireName(Map<String, Object> body) {
        String name = text(body.get("name"), null);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "请填写隧道名称");
        }
        return name.trim();
    }

    private static String normalizeProxyType(Map<String, Object> body) {
        String type = text(body.get("proxyType"), "tcp");
        String normalized = type == null ? "tcp" : type.trim().toLowerCase();
        if (!PROXY_TYPES.contains(normalized)) {
            throw new BusinessException(400, "不支持的代理类型: " + normalized);
        }
        return normalized;
    }

    private static int requirePort(Object value, String label) {
        Integer port = optionalInt(value);
        if (port == null || port < 1 || port > 65535) {
            throw new BusinessException(400, label + "需为 1-65535");
        }
        return port;
    }

    private static Integer optionalInt(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "端口必须为数字");
        }
    }

    private static int boolInt(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue ? 1 : 0;
        }
        if (value instanceof Boolean flag) {
            return flag ? 1 : 0;
        }
        return Boolean.parseBoolean(String.valueOf(value)) ? 1 : 0;
    }

    private static String text(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}