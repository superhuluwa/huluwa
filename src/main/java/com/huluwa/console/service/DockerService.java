package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Docker 管理：主机连接配置 + 通过本机 docker CLI 执行只读查询。
 * 容器启停等写操作在后续迭代接入，当前仅提供查询与环境探测。
 */
@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int COMMAND_TIMEOUT_SECONDS = 15;

    private static final RowMapper<Map<String, Object>> HOST_MAPPER = (rs, rowNum) -> {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", rs.getLong("id"));
        row.put("name", rs.getString("name"));
        row.put("host", rs.getString("host"));
        row.put("enabled", rs.getInt("enabled") == 1);
        row.put("createdAt", rs.getString("created_at"));
        row.put("updatedAt", rs.getString("updated_at"));
        return row;
    };

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;

    public DockerService(JdbcTemplate jdbcTemplate, AppProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
    }

    public List<Map<String, Object>> hosts() {
        return jdbcTemplate.query("SELECT * FROM docker_host ORDER BY id ASC", HOST_MAPPER);
    }

    public Map<String, Object> createHost(Map<String, Object> body) {
        String name = text(body.get("name"), null);
        if (name == null || name.isBlank()) {
            throw new BusinessException(400, "请填写主机名称");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO docker_host (name, host, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, name.trim(), text(body.get("host"), "local"),
                Boolean.FALSE.equals(body.get("enabled")) ? 0 : 1, now, now);
        return jdbcTemplate.query("SELECT * FROM docker_host ORDER BY id DESC LIMIT 1", HOST_MAPPER)
                .stream().findFirst().orElseThrow(() -> new BusinessException(500, "保存后读取失败"));
    }

    public void deleteHost(long id) {
        int deleted = jdbcTemplate.update("DELETE FROM docker_host WHERE id = ?", id);
        if (deleted == 0) {
            throw new BusinessException(404, "主机不存在");
        }
    }

    /** 环境探测：docker CLI 是否可用、守护进程是否在线 */
    public Map<String, Object> environment() {
        Map<String, Object> data = new LinkedHashMap<>();
        CommandResult version = exec("version", "--format", "{{.Server.Version}}");
        boolean cliAvailable = version.exitCode() != 126;
        data.put("cliAvailable", cliAvailable);
        data.put("daemonOnline", version.exitCode() == 0);
        data.put("serverVersion", version.exitCode() == 0 ? version.stdout().trim() : null);
        data.put("binary", resolveBinary());
        data.put("message", version.exitCode() == 0 ? "Docker 守护进程连接正常" :
                (cliAvailable ? "Docker CLI 已安装，但守护进程不可用" : "未检测到 docker 命令"));
        return data;
    }

    /** 容器列表（只读） */
    public List<Map<String, Object>> containers(boolean all) {
        List<String> args = new ArrayList<>();
        args.add("ps");
        if (all) {
            args.add("-a");
        }
        args.add("--format");
        args.add("{{.ID}}\t{{.Names}}\t{{.Image}}\t{{.State}}\t{{.Status}}\t{{.Ports}}");
        CommandResult result = exec(args.toArray(String[]::new));
        if (result.exitCode() != 0) {
            throw new BusinessException(500, "Docker 命令执行失败: " + result.errorMessage());
        }
        return result.stdout().lines()
                .filter(line -> !line.isBlank())
                .map(line -> {
                    String[] parts = line.split("\t", -1);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", parts.length > 0 ? parts[0] : "");
                    row.put("name", parts.length > 1 ? parts[1] : "");
                    row.put("image", parts.length > 2 ? parts[2] : "");
                    row.put("state", parts.length > 3 ? parts[3] : "");
                    row.put("status", parts.length > 4 ? parts[4] : "");
                    row.put("ports", parts.length > 5 ? parts[5] : "");
                    return row;
                })
                .collect(Collectors.toList());
    }

    /** 镜像列表（只读） */
    public List<Map<String, Object>> images() {
        CommandResult result = exec("images", "--format",
                "{{.Repository}}\t{{.Tag}}\t{{.ID}}\t{{.Size}}\t{{.CreatedSince}}");
        if (result.exitCode() != 0) {
            throw new BusinessException(500, "Docker 命令执行失败: " + result.errorMessage());
        }
        return result.stdout().lines()
                .filter(line -> !line.isBlank())
                .map(line -> {
                    String[] parts = line.split("\t", -1);
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("repository", parts.length > 0 ? parts[0] : "");
                    row.put("tag", parts.length > 1 ? parts[1] : "");
                    row.put("id", parts.length > 2 ? parts[2] : "");
                    row.put("size", parts.length > 3 ? parts[3] : "");
                    row.put("created", parts.length > 4 ? parts[4] : "");
                    return row;
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        CommandResult running = exec("ps", "-q");
        CommandResult total = exec("ps", "-aq");
        data.put("running", countLines(running));
        data.put("total", countLines(total));
        data.put("available", running.exitCode() == 0);
        return data;
    }

    private static long countLines(CommandResult result) {
        if (result.exitCode() != 0) {
            return 0L;
        }
        return result.stdout().lines().filter(line -> !line.isBlank()).count();
    }

    private String resolveBinary() {
        String configured = properties.getDocker().getBinary();
        return configured == null || configured.isBlank() ? "docker" : configured;
    }

    private CommandResult exec(String... args) {
        List<String> command = new ArrayList<>();
        command.add(resolveBinary());
        command.addAll(List.of(args));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(false);
        try {
            Process process = builder.start();
            // 必须在 waitFor 之前并发读取两个流，否则输出超过管道缓冲区时子进程会阻塞导致死锁
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() -> readAll(process.getInputStream()));
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() -> readAll(process.getErrorStream()));
            boolean finished;
            try {
                finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
                return new CommandResult(130, "", "命令执行被中断");
            }
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult(124, "", "命令执行超时");
            }
            return new CommandResult(process.exitValue(), stdoutFuture.join(), stderrFuture.join());
        } catch (java.io.IOException ex) {
            log.debug("docker 命令不可用: {}", ex.getMessage());
            return new CommandResult(126, "", "未检测到 docker 命令");
        }
    }

    private static String readAll(java.io.InputStream stream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining(System.lineSeparator()));
        } catch (java.io.IOException ex) {
            return "";
        }
    }

    private static String text(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
        String errorMessage() {
            if (stderr != null && !stderr.isBlank()) {
                return stderr.trim();
            }
            return "退出码 " + exitCode;
        }
    }
}