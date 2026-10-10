package com.huluwa.console.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.command.ListVolumesResponse;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotModifiedException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.Network;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.github.dockerjava.core.command.PullImageResultCallback;
import com.github.dockerjava.transport.DockerHttpClient;
import com.github.dockerjava.zerodep.ZerodepDockerHttpClient;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.AppProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Docker 管理服务。
 *
 * <p>通过 docker-java SDK（对标参考工程的 Docker.DotNet）直连 Docker 守护进程，覆盖容器 / 镜像 /
 * 网络 / 卷的完整管理能力：不依赖宿主机或容器内安装 docker CLI，也不经过 shell，避免命令注入。</p>
 *
 * <p>连接地址优先取 {@code huluwa.docker.host}，其次取 {@code DOCKER_HOST} 环境变量，最后使用
 * docker-java 的系统默认值（Linux 为 unix:///var/run/docker.sock，Windows 为 npipe）。
 * CentOS 容器化部署时由 compose 注入 {@code DOCKER_HOST=unix:///var/run/docker.sock} 并挂载该 socket。</p>
 */
@Service
public class DockerService {

    private static final Logger log = LoggerFactory.getLogger(DockerService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    /** 常规查询超时（秒） */
    private static final int QUERY_TIMEOUT_SECONDS = 30;
    /** 启停 / 删除 / 创建等写操作超时（秒） */
    private static final int MUTATION_TIMEOUT_SECONDS = 120;
    /** 镜像拉取超时（秒） */
    private static final int PULL_TIMEOUT_SECONDS = 900;

    /**
     * docker-java 3.4.1 已实现的最高 Docker Remote API 版本（Docker Engine 27.x / API 1.44）。
     * 版本协商取该值与守护进程 ApiVersion 的较小者，避免向旧版 CentOS Docker 发送过新的 API 路径。
     */
    private static final int CLIENT_API_VERSION_MINOR = 44;

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

    /** 批量采样最多同时执行 8 个请求，避免随容器数线性增长的等待和无限制线程创建。 */
    private final ExecutorService statsExecutor = Executors.newFixedThreadPool(8,
            Thread.ofPlatform().daemon().name("docker-stats-", 0).factory());
    private final ScheduledExecutorService logTimer = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("docker-log-window-", 0).factory());

    private final JdbcTemplate jdbcTemplate;
    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    private volatile DockerClient client;
    private volatile DockerHttpClient httpClient;
    /** 已协商的 API 版本；空串表示守护进程未提供，沿用 docker-java 默认（无版本前缀）。 */
    private volatile String resolvedApiVersion;

    public DockerService(JdbcTemplate jdbcTemplate, AppProperties properties, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 客户端
    // ------------------------------------------------------------------

    private DockerClient client() {
        DockerClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    DockerClientConfig config = buildConfig(apiVersion());
                    httpClient = new ZerodepDockerHttpClient.Builder()
                            .dockerHost(config.getDockerHost())
                            .sslConfig(config.getSSLConfig())
                            .maxConnections(50)
                            .connectionTimeout(Duration.ofSeconds(properties.getDocker().getConnectionTimeoutSeconds()))
                            .responseTimeout(Duration.ofSeconds(properties.getDocker().getResponseTimeoutSeconds()))
                            .build();
                    client = DockerClientImpl.getInstance(config, httpClient);
                    log.info("Docker 客户端已初始化: host={}, apiVersion={}", config.getDockerHost(),
                            config.getApiVersion());
                }
                current = client;
            }
        }
        return current;
    }

    private DockerClientConfig buildConfig(String apiVersion) {
        DefaultDockerClientConfig.Builder builder = DefaultDockerClientConfig.createDefaultConfigBuilder();
        String host = resolveHost();
        if (host != null) {
            builder.withDockerHost(host);
        }
        if (!apiVersion.isBlank()) {
            builder.withApiVersion(apiVersion);
        }
        return builder.build();
    }

    /**
     * 解析 Docker 守护进程地址。
     *
     * <p>优先使用 {@code huluwa.docker.host}，其次使用 {@code DOCKER_HOST} 环境变量，
     * 最后回退到 docker-java 默认值（CentOS 上为 {@code unix:///var/run/docker.sock}）。
     * 除本机 Unix Socket 外，也支持 {@code tcp://}（含 TLS 参数由 SDK 从 DOCKER_* 环境变量读取）。</p>
     */
    private String resolveHost() {
        // 运行期优先使用「Docker 主机」页面登记的启用主机，其次 yml，再次 DOCKER_HOST，最后 SDK 默认。
        String managed = activeHost();
        if (managed != null) {
            return managed;
        }
        String configured = text(properties.getDocker().getHost(), null);
        if (configured != null) {
            return configured;
        }
        return text(System.getenv("DOCKER_HOST"), null);
    }

    /** 读取首个启用的托管主机地址；local / 未配置 / 查询失败均返回 null 以回退到默认连接。 */
    private String activeHost() {
        try {
            return jdbcTemplate.query(
                            "SELECT * FROM docker_host WHERE enabled = 1 ORDER BY id ASC LIMIT 1", HOST_MAPPER)
                    .stream()
                    .map(row -> text(row.get("host"), null))
                    .filter(value -> value != null && !"local".equalsIgnoreCase(value))
                    .findFirst()
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.debug("读取 Docker 主机配置失败，回退默认连接: {}", ex.getMessage());
            return null;
        }
    }

    /** 主机配置变更后丢弃已建连接，下一次请求按新地址重建（含 API 版本重新协商）。 */
    private void resetClient() {
        synchronized (this) {
            DockerClient current = client;
            DockerHttpClient currentHttp = httpClient;
            client = null;
            httpClient = null;
            resolvedApiVersion = null;
            if (current != null) {
                try {
                    current.close();
                } catch (Exception ignored) {
                    // 重建连接时旧客户端关闭失败无需处理
                }
            }
            if (currentHttp != null) {
                try {
                    currentHttp.close();
                } catch (Exception ignored) {
                    // 同上
                }
            }
        }
    }

    /**
     * 协商 Docker API 版本。
     *
     * <p>docker-java 在未配置 {@code api.version} 时不会自动协商；对 CentOS 上常见的
     * Docker 19.03 / 20.10，直接用最新客户端常量请求 {@code /v1.44/...} 会被守护进程拒绝。
     * 这里先用不带版本前缀的 {@code /version} 读取守护进程可用版本，再取客户端与守护进程
     * 都支持的最大版本；<b>仅当显式配置了 {@code huluwa.docker.api-version} 时跳过协商</b>。</p>
     *
     * <p>{@code /version} 在所有受支持版本上都不需要认证且不会拒绝“过新的客户端版本头”，
     * 因此可作为安全的探活与版本来源。协商失败时回退到 docker-java 默认行为，
     * 由守护进程按默认版本处理。</p>
     */
    private String apiVersion() {
        String cached = resolvedApiVersion;
        if (cached == null) {
            synchronized (this) {
                if (resolvedApiVersion == null) {
                    resolvedApiVersion = doResolveApiVersion();
                }
                cached = resolvedApiVersion;
            }
        }
        return cached;
    }

    private String doResolveApiVersion() {
        String configured = text(properties.getDocker().getApiVersion(), null);
        if (configured != null) {
            return configured;
        }
        try {
            DockerClientConfig probeConfig = buildConfig("");
            DockerHttpClient probeHttp = new ZerodepDockerHttpClient.Builder()
                    .dockerHost(probeConfig.getDockerHost())
                    .sslConfig(probeConfig.getSSLConfig())
                    .maxConnections(2)
                    .connectionTimeout(Duration.ofSeconds(properties.getDocker().getConnectionTimeoutSeconds()))
                    .responseTimeout(Duration.ofSeconds(properties.getDocker().getResponseTimeoutSeconds()))
                    .build();
            try {
                DockerHttpClient.Request request = DockerHttpClient.Request.builder()
                        .method(DockerHttpClient.Request.Method.GET)
                        .path("/version")
                        .build();
                try (DockerHttpClient.Response response = probeHttp.execute(request);
                     InputStream body = response.getBody()) {
                    if (response.getStatusCode() >= 400) {
                        log.warn("Docker API 版本协商失败：/version 返回 {}", response.getStatusCode());
                        return "";
                    }
                    JsonNode node = objectMapper.readTree(body.readAllBytes());
                    String daemonVersion = text(node.path("ApiVersion").asText(null), null);
                    return negotiateApiVersion(daemonVersion);
                }
            } finally {
                try {
                    probeHttp.close();
                } catch (Exception ignored) {
                    // 探测连接关闭失败无需处理
                }
            }
        } catch (Exception ex) {
            log.warn("Docker API 版本协商失败，将使用守护进程默认版本: {}", firstLine(rootMessage(ex)));
            return "";
        }
    }

    /**
     * 计算客户端与守护进程都支持的最大 API 版本。
     *
     * <p>如果守护进程版本高于 docker-java 3.4.1 已知的最大版本，则使用该最大版本
     * （Docker 守护进程对同一大版本内的更高 minor 请求是向后兼容的，且其会按自身
     * 支持的版本处理）；如果守护进程版本低于客户端已知最大版本，则使用守护进程版本。</p>
     */
    private static String negotiateApiVersion(String daemonVersion) {
        if (daemonVersion == null || daemonVersion.isBlank()) {
            return "";
        }
        String normalized = daemonVersion.trim();
        String[] parts = normalized.split("\\.");
        if (parts.length < 2) {
            return "";
        }
        try {
            int daemonMinor = Integer.parseInt(parts[1].trim());
            int clientMinor = CLIENT_API_VERSION_MINOR;
            return "1." + Math.min(daemonMinor, clientMinor);
        } catch (NumberFormatException ex) {
            return "";
        }
    }

    /** 裸 HTTP 流式接口（日志 / 统计）使用的客户端，与 {@link #client()} 懒加载共用同一连接配置。 */
    private DockerHttpClient httpClient() {
        client();
        return httpClient;
    }
    @PreDestroy
    public void close() {
        statsExecutor.shutdownNow();
        logTimer.shutdownNow();
        DockerClient current = client;
        if (current != null) {
            try {
                current.close();
            } catch (Exception ignored) {
                // 关闭异常无需处理
            }
        }
        DockerHttpClient currentHttp = httpClient;
        if (currentHttp != null) {
            try {
                currentHttp.close();
            } catch (Exception ignored) {
                // 关闭异常无需处理
            }
        }
    }

    /** 统一包装 SDK 异常，转换为项目统一信封；资源不存在映射为 404。 */
    private <T> T invoke(String action, Supplier<T> supplier) {
        try {
            return supplier.get();
        } catch (BusinessException ex) {
            throw ex;
        } catch (NotFoundException ex) {
            throw new BusinessException(404, "资源不存在或已被删除");
        } catch (NotModifiedException ex) {
            throw new BusinessException(409, "资源已处于目标状态");
        } catch (DockerException ex) {
            throw new BusinessException(500, action + "失败：" + firstLine(ex.getMessage()));
        } catch (Exception ex) {
            throw new BusinessException(500, action + "失败：" + firstLine(rootMessage(ex)));
        }
    }

    private static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        String message = current.getMessage();
        return message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "未知错误";
        }
        String value = message.trim();
        int index = value.indexOf('\n');
        return index > 0 ? value.substring(0, index) : value;
    }

    // ------------------------------------------------------------------
    // 主机配置
    // ------------------------------------------------------------------

    public List<Map<String, Object>> hosts() {
        return jdbcTemplate.query("SELECT * FROM docker_host ORDER BY id ASC", HOST_MAPPER);
    }

    public Map<String, Object> createHost(Map<String, Object> body) {
        String name = text(body.get("name"), null);
        if (name == null) {
            throw new BusinessException(400, "请填写主机名称");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbcTemplate.update("""
                INSERT INTO docker_host (name, host, enabled, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, name, text(body.get("host"), "local"),
                Boolean.FALSE.equals(body.get("enabled")) ? 0 : 1, now, now);
        Map<String, Object> saved = jdbcTemplate.query("SELECT * FROM docker_host ORDER BY id DESC LIMIT 1", HOST_MAPPER)
                .stream().findFirst().orElseThrow(() -> new BusinessException(500, "保存后读取主机配置失败"));
        resetClient();
        return saved;
    }

    public void deleteHost(long id) {
        int affected = jdbcTemplate.update("DELETE FROM docker_host WHERE id = ?", id);
        if (affected == 0) {
            throw new BusinessException(404, "主机配置不存在");
        }
        resetClient();
    }

    // ------------------------------------------------------------------
    // 容器
    // ------------------------------------------------------------------

    public List<Map<String, Object>> containers(boolean all) {
        List<Container> containers = invoke("查询容器列表",
                () -> client().listContainersCmd().withShowAll(all).exec());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Container container : containers) {
            rows.add(normalizeContainer(toMap(container)));
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> normalizeContainer(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>(raw);
        String id = stringValue(raw.get("Id"));
        row.put("Id", id);
        row.put("id", id);
        row.put("Names", raw.get("Names") == null ? List.of() : raw.get("Names"));
        row.put("Image", stringValue(raw.get("Image")));
        row.put("State", resolveState(raw));
        row.put("Status", stringValue(raw.get("Status")));
        row.put("Ports", normalizePorts(raw.get("Ports")));
        row.put("Created", formatEpoch(raw.get("Created")));
        row.put("CreatedAt", formatEpoch(raw.get("Created")));
        row.put("Command", stringValue(raw.get("Command")));
        return row;
    }

    /**
     * 解析容器状态。
     *
     * <p>旧版守护进程（CentOS 常见的 docker 19.03 / 20.10）在部分 SDK / 字段组合下不返回 State，
     * 此时从 Status 文本（如 "Up 2 hours"、"Exited (0) 3 minutes ago"）推导，避免运行中的容器被误判。</p>
     */
    @SuppressWarnings("unchecked")
    private static String resolveState(Map<String, Object> raw) {
        String state = stringValue(raw.get("State")).toLowerCase();
        if (!state.isBlank()) {
            return state;
        }
        String status = stringValue(raw.get("Status")).toLowerCase();
        if (status.startsWith("up") || status.contains("(healthy)") || status.contains("(unhealthy)")) {
            return "running";
        }
        if (status.startsWith("exited")) {
            return "exited";
        }
        if (status.startsWith("created")) {
            return "created";
        }
        if (status.startsWith("paused")) {
            return "paused";
        }
        if (status.startsWith("restarting")) {
            return "restarting";
        }
        if (status.startsWith("dead")) {
            return "dead";
        }
        return status.isBlank() ? "" : "unknown";
    }

    /** 统一端口结构为 [{PrivatePort, PublicPort, Type, IP}]，与前端表格 / 网格保持一致。 */
    private static List<Map<String, Object>> normalizePorts(Object value) {
        List<Map<String, Object>> ports = new ArrayList<>();
        if (!(value instanceof List<?> list)) {
            return ports;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> port = new LinkedHashMap<>();
            port.put("PrivatePort", numberValue(map.get("PrivatePort")));
            port.put("PublicPort", numberValue(map.get("PublicPort")));
            port.put("Type", map.get("Type") == null ? "tcp" : String.valueOf(map.get("Type")));
            port.put("IP", map.get("IP") == null ? "" : String.valueOf(map.get("IP")));
            ports.add(port);
        }
        return ports;
    }

    public Map<String, Object> inspectContainer(String containerId) {
        return invoke("获取容器详情", () -> toMap(client().inspectContainerCmd(
                requireReference(containerId, "容器")).exec()));
    }

    public Map<String, Object> containerStats(String containerId) {
        String id = requireReference(containerId, "容器");
        Map<String, Object> raw = readJson("/containers/" + encodePath(id) + "/stats",
                Map.of("stream", "false", "one-shot", "true"));
        Map<String, Object> data = new LinkedHashMap<>(raw);
        data.put("Id", id);
        data.put("id", id);
        data.put("Name", stringValue(raw.get("name")));
        data.putAll(summarizeStats(raw));
        return data;
    }

    /** 有界并行采样，单个容器停止/删除不影响其余容器。 */
    public Map<String, Object> containerStatsMap(List<String> containerIds) {
        Map<String, Object> data = new LinkedHashMap<>();
        if (containerIds == null || containerIds.isEmpty()) {
            return data;
        }
        if (containerIds.size() > 200) {
            throw new BusinessException(400, "每次最多查询 200 个容器的统计信息");
        }
        List<String> ids = containerIds.stream().filter(id -> id != null && !id.isBlank())
                .map(String::trim).distinct().toList();
        List<CompletableFuture<Map<String, Object>>> tasks = ids.stream().map(id ->
                CompletableFuture.supplyAsync(() -> {
                    try {
                        return containerStats(id);
                    } catch (BusinessException ex) {
                        log.debug("跳过容器 {} 的统计信息: {}", id, ex.getMessage());
                        return (Map<String, Object>) null;
                    }
                }, statsExecutor)).toList();
        for (int i = 0; i < ids.size(); i++) {
            Map<String, Object> stats = tasks.get(i).join();
            if (stats != null) {
                data.put(ids.get(i), stats);
            }
        }
        return data;
    }

    /** 将 Docker 原始统计 JSON 折算为前端直接可用的 CPU / 内存 / 网络 / 磁盘 / PIDs 指标。 */
    private static Map<String, Object> summarizeStats(Map<String, Object> raw) {
        Map<String, Object> data = new LinkedHashMap<>();
        double cpuPercent = 0d;
        try {
            Map<String, Object> cpu = asMap(raw.get("cpu_stats"));
            Map<String, Object> preCpu = asMap(raw.get("precpu_stats"));
            Map<String, Object> cpuUsage = asMap(cpu.get("cpu_usage"));
            Map<String, Object> preCpuUsage = asMap(preCpu.get("cpu_usage"));
            double cpuDelta = numberValue(cpuUsage.get("total_usage")) - numberValue(preCpuUsage.get("total_usage"));
            double systemDelta = numberValue(cpu.get("system_cpu_usage")) - numberValue(preCpu.get("system_cpu_usage"));
            double onlineCpus = numberValue(cpu.get("online_cpus"));
            if (onlineCpus <= 0) {
                Object perCpu = cpuUsage.get("percpu_usage");
                onlineCpus = perCpu instanceof List<?> list ? list.size() : 1;
            }
            if (systemDelta > 0 && cpuDelta > 0) {
                cpuPercent = cpuDelta / systemDelta * onlineCpus * 100d;
            }
        } catch (RuntimeException ex) {
            log.debug("计算容器 CPU 使用率失败: {}", ex.getMessage());
        }
        data.put("CPUPercRaw", round(cpuPercent));
        data.put("CPUPerc", String.format("%.2f%%", cpuPercent));

        long memoryUsage = 0L;
        long memoryLimit = 0L;
        Map<String, Object> memory = asMap(raw.get("memory_stats"));
        if (!memory.isEmpty()) {
            memoryUsage = (long) numberValue(memory.get("usage"));
            Map<String, Object> memoryDetail = asMap(memory.get("stats"));
            Object cache = memoryDetail.get("total_inactive_file");
            if (cache == null) cache = memoryDetail.get("inactive_file");
            if (cache == null) cache = memoryDetail.get("cache");
            memoryUsage = Math.max(0L, memoryUsage - (long) numberValue(cache));
            memoryLimit = (long) numberValue(memory.get("limit"));
        }
        double memoryPercent = memoryLimit > 0 ? (double) memoryUsage / memoryLimit * 100d : 0d;
        data.put("MemoryUsageRaw", memoryUsage);
        data.put("MemoryLimitRaw", memoryLimit);
        data.put("MemPercRaw", round(memoryPercent));
        data.put("MemPerc", String.format("%.2f%%", memoryPercent));
        data.put("MemUsage", formatBytes(memoryUsage) + " / " + formatBytes(memoryLimit));

        long rx = 0L;
        long tx = 0L;
        Map<String, Object> networks = asMap(raw.get("networks"));
        for (Object value : networks.values()) {
            Map<String, Object> item = asMap(value);
            rx += (long) numberValue(item.get("rx_bytes"));
            tx += (long) numberValue(item.get("tx_bytes"));
        }
        data.put("NetIO", formatBytes(rx) + " / " + formatBytes(tx));

        long read = 0L;
        long write = 0L;
        Map<String, Object> blkio = asMap(raw.get("blkio_stats"));
        Object entries = blkio.get("io_service_bytes_recursive");
        if (entries instanceof List<?> list) {
            for (Object item : list) {
                Map<String, Object> entry = asMap(item);
                String op = stringValue(entry.get("op")).toLowerCase();
                long value = (long) numberValue(entry.get("value"));
                if ("read".equals(op)) {
                    read += value;
                } else if ("write".equals(op)) {
                    write += value;
                }
            }
        }
        data.put("BlockIO", formatBytes(read) + " / " + formatBytes(write));

        Map<String, Object> pids = asMap(raw.get("pids_stats"));
        data.put("PIDs", String.valueOf((long) numberValue(pids.get("current"))));
        return data;
    }

    public void startContainer(String containerId) {
        String id = requireReference(containerId, "容器");
        try {
            invoke("启动容器", () -> client().startContainerCmd(id).exec());
        } catch (BusinessException ex) {
            // 容器已在运行时守护进程返回 304，这里视为成功
            if (ex.getCode() != 409) {
                throw ex;
            }
        }
    }

    public void stopContainer(String containerId) {
        String id = requireReference(containerId, "容器");
        try {
            invoke("停止容器", () -> client().stopContainerCmd(id).withTimeout(10).exec());
        } catch (BusinessException ex) {
            if (ex.getCode() != 409) {
                throw ex;
            }
        }
    }

    public void restartContainer(String containerId) {
        String id = requireReference(containerId, "容器");
        invoke("重启容器", () -> client().restartContainerCmd(id).withTimeout(10).exec());
    }

    public void removeContainer(String containerId, boolean force, boolean removeVolumes) {
        String id = requireReference(containerId, "容器");
        invoke("删除容器", () -> client().removeContainerCmd(id)
                .withForce(force)
                .withRemoveVolumes(removeVolumes)
                .exec());
    }

    /**
     * 读取容器日志。
     *
     * <p>参考工程在 CentOS 真机上踩过 TTY 容器日志解析失败的问题：非 TTY 容器的日志是多路复用帧
     * （8 字节帧头），TTY 容器是裸流。这里直接从底层 HTTP 流读取原始字节，再按 TTY 标记决定是否解帧，
     * 两种容器都能正确输出。</p>
     */
    public String containerLogs(String containerId, String tail) {
        return containerLogs(containerId, false, true, true, tail);
    }

    /** follow 使用最多 5 秒的采集窗口，避免 JSON 接口等待永不结束的流。 */
    public String containerLogs(String containerId, boolean follow, boolean stdout, boolean stderr, String tail) {
        String id = requireReference(containerId, "容器");
        String tailValue = text(tail, "all");
        if (!"all".equals(tailValue) && !tailValue.matches("\\d{1,9}")) {
            throw new BusinessException(400, "日志行数必须为非负整数或 all");
        }
        if (!stdout && !stderr) {
            return "";
        }
        boolean tty = isTty(inspectContainer(id));
        Map<String, String> query = new LinkedHashMap<>();
        query.put("stdout", stdout ? "1" : "0");
        query.put("stderr", stderr ? "1" : "0");
        query.put("follow", follow ? "1" : "0");
        query.put("tail", tailValue);
        return demuxLogs(readBytes("/containers/" + encodePath(id) + "/logs", query, follow), tty);
    }

    @SuppressWarnings("unchecked")
    private static boolean isTty(Map<String, Object> inspect) {
        Object config = inspect.get("Config") != null ? inspect.get("Config") : inspect.get("config");
        if (!(config instanceof Map<?, ?> map)) {
            return false;
        }
        Object tty = map.get("Tty") != null ? map.get("Tty") : map.get("tty");
        if (tty instanceof Boolean value) {
            return value;
        }
        return tty != null && Boolean.parseBoolean(String.valueOf(tty));
    }

    /** 非 TTY 容器按 Docker 复用协议解帧：1 字节流类型 + 3 字节保留 + 4 字节大端长度。 */
    private static String demuxLogs(byte[] data, boolean tty) {
        if (tty || data.length < 8) {
            return new String(data, StandardCharsets.UTF_8);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(data.length);
        int index = 0;
        boolean framed = false;
        while (index + 8 <= data.length) {
            int type = data[index] & 0xFF;
            if (type != 0 && type != 1 && type != 2) {
                break;
            }
            int size = ((data[index + 4] & 0xFF) << 24) | ((data[index + 5] & 0xFF) << 16)
                    | ((data[index + 6] & 0xFF) << 8) | (data[index + 7] & 0xFF);
            int start = index + 8;
            int end = start + size;
            if (size < 0 || end > data.length) {
                break;
            }
            out.write(data, start, size);
            index = end;
            framed = true;
        }
        if (!framed) {
            return new String(data, StandardCharsets.UTF_8);
        }
        if (index < data.length) {
            out.write(data, index, data.length - index);
        }
        return out.toString(StandardCharsets.UTF_8);
    }

    public Map<String, Object> createContainer(Map<String, Object> body) {
        String image = text(body.get("image"), text(body.get("Image"), null));
        if (image == null) {
            throw new BusinessException(400, "请填写镜像名称");
        }
        String name = text(body.get("name"), text(body.get("Name"), "huluwa-" + System.currentTimeMillis()));
        return invoke("创建容器", () -> {
            CreateContainerCmd command = client().createContainerCmd(image).withName(name);
            String restart = text(body.get("restartPolicy"), null);
            if (restart != null) {
                command.withRestartPolicy(RestartPolicy.parse(restart));
            }
            List<String> env = stringList(body.get("env"));
            if (!env.isEmpty()) {
                command.withEnv(env);
            }
            List<String> networks = stringList(body.get("networks"));
            if (networks.size() == 1) {
                command.withNetworkMode(networks.get(0));
            }
            List<Bind> binds = new ArrayList<>();
            for (String volume : stringList(body.get("volumes"))) {
                binds.add(Bind.parse(volume));
            }
            if (!binds.isEmpty()) {
                command.withBinds(binds);
            }
            Ports portBindings = new Ports();
            boolean hasPorts = false;
            for (String mapping : stringList(body.get("ports"))) {
                PortBinding binding = PortBinding.parse(mapping);
                portBindings.bind(binding.getExposedPort(), binding.getBinding());
                hasPorts = true;
            }
            if (hasPorts) {
                command.withPortBindings(portBindings);
            }
            String rawCommand = text(body.get("cmd"), text(body.get("Command"), null));
            if (rawCommand != null) {
                command.withCmd(rawCommand.split("\\s+"));
            }
            CreateContainerResponse response = command.exec();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("Id", response.getId());
            return data;
        });
    }

    /** 在容器内执行命令，返回退出码与输出（对应参考工程的 execute 能力）。 */
    public Map<String, Object> execContainer(String containerId, Object payload) {
        String id = requireReference(containerId, "容器");
        List<String> command = new ArrayList<>();
        if (payload instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    command.add(String.valueOf(item));
                }
            }
        } else if (payload instanceof String value) {
            command.addAll(Arrays.asList(value.trim().split("\\s+")));
        } else if (payload instanceof Map<?, ?> map) {
            Object value = map.get("command");
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && !String.valueOf(item).isBlank()) {
                        command.add(String.valueOf(item));
                    }
                }
            } else if (value != null) {
                command.addAll(Arrays.asList(String.valueOf(value).trim().split("\\s+")));
            }
        }
        if (command.isEmpty()) {
            throw new BusinessException(400, "请填写要执行的命令");
        }
        return invoke("执行容器命令", () -> {
            ExecCreateCmdResponse exec = client().execCreateCmd(id)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(command.toArray(new String[0]))
                    .exec();
            ByteArrayOutputStream stdout = new ByteArrayOutputStream();
            ByteArrayOutputStream stderr = new ByteArrayOutputStream();
            ExecStartResultCallback callback = client().execStartCmd(exec.getId())
                    .exec(new ExecStartResultCallback(stdout, stderr));
            boolean finished;
            try {
                finished = callback.awaitCompletion(MUTATION_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException(500, "执行容器命令被中断");
            }
            Integer exitCode = client().inspectExecCmd(exec.getId()).exec().getExitCode();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exitCode", exitCode == null ? (finished ? 0 : 124) : exitCode);
            data.put("output", stdout.toString(StandardCharsets.UTF_8)
                    + stderr.toString(StandardCharsets.UTF_8));
            return data;
        });
    }

    // ------------------------------------------------------------------
    // 镜像
    // ------------------------------------------------------------------

    public List<Map<String, Object>> images(boolean all) {
        List<Image> images = invoke("查询镜像列表",
                () -> client().listImagesCmd().withShowAll(all).exec());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Image image : images) {
            rows.add(normalizeImage(toMap(image)));
        }
        return rows;
    }

    private static Map<String, Object> normalizeImage(Map<String, Object> raw) {
        Map<String, Object> row = new LinkedHashMap<>(raw);
        String id = stringValue(raw.get("Id"));
        row.put("Id", id);
        row.put("id", id);
        Object repoTags = raw.get("RepoTags");
        row.put("RepoTags", repoTags == null ? List.of() : repoTags);
        long size = (long) numberValue(raw.get("Size"));
        row.put("SizeRaw", size);
        row.put("Size", formatBytes(size));
        row.put("Created", formatEpoch(raw.get("Created")));
        return row;
    }

    public void pullImage(String image, String tag) {
        String reference = text(image, null);
        if (reference == null) {
            throw new BusinessException(400, "请填写镜像名称");
        }
        String imageTag = text(tag, "latest");
        invoke("拉取镜像", () -> {
            PullImageResultCallback callback = client().pullImageCmd(reference)
                    .withTag(imageTag)
                    .exec(new PullImageResultCallback());
            try {
                if (!callback.awaitCompletion(PULL_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    throw new BusinessException(500, "拉取镜像失败：超时");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new BusinessException(500, "拉取镜像被中断");
            }
            return null;
        });
    }

    public void removeImage(String imageId, boolean force) {
        String id = requireReference(imageId, "镜像");
        invoke("删除镜像", () -> client().removeImageCmd(id).withForce(force).exec());
    }

    // ------------------------------------------------------------------
    // 网络
    // ------------------------------------------------------------------

    public List<Map<String, Object>> networks() {
        List<Network> networks = invoke("查询网络列表", () -> client().listNetworksCmd().exec());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Network network : networks) {
            Map<String, Object> row = toMap(network);
            String id = stringValue(row.get("Id"));
            row.put("Id", id);
            row.put("id", id);
            rows.add(row);
        }
        return rows;
    }

    public Map<String, Object> createNetwork(String name, String driver) {
        String networkName = text(name, null);
        if (networkName == null) {
            throw new BusinessException(400, "请填写网络名称");
        }
        String networkDriver = text(driver, "bridge");
        return invoke("创建网络", () -> {
            CreateNetworkResponse response = client().createNetworkCmd()
                    .withName(networkName)
                    .withDriver(networkDriver)
                    .exec();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("Id", response.getId());
            data.put("Name", networkName);
            data.put("Driver", networkDriver);
            return data;
        });
    }

    public void removeNetwork(String networkId) {
        String id = requireReference(networkId, "网络");
        invoke("删除网络", () -> client().removeNetworkCmd(id).exec());
    }

    public void connectNetwork(String networkId, String containerId) {
        String id = requireReference(networkId, "网络");
        String container = requireReference(containerId, "容器");
        invoke("连接网络", () -> client().connectToNetworkCmd()
                .withNetworkId(id)
                .withContainerId(container)
                .exec());
    }

    public void disconnectNetwork(String networkId, String containerId) {
        String id = requireReference(networkId, "网络");
        String container = requireReference(containerId, "容器");
        invoke("断开网络", () -> client().disconnectFromNetworkCmd()
                .withNetworkId(id)
                .withContainerId(container)
                .exec());
    }

    public Map<String, Object> inspectNetwork(String networkId) {
        return invoke("获取网络详情", () -> toMap(client().inspectNetworkCmd()
                .withNetworkId(requireReference(networkId, "网络")).exec()));
    }

    // ------------------------------------------------------------------
    // 卷
    // ------------------------------------------------------------------

    public Map<String, Object> volumes() {
        // 原始协议保留 Scope / CreatedAt / Status / UsageData / Warnings，旧 SDK 的 DTO 会丢掉字段。
        Map<String, Object> data = readJson("/volumes", Map.of());
        if (!(data.get("Volumes") instanceof List<?>)) {
            data.put("Volumes", List.of());
        }
        return data;
    }

    public Map<String, Object> createVolume(String name, String driver) {
        String volumeName = text(name, null);
        if (volumeName == null) {
            throw new BusinessException(400, "请填写卷名称");
        }
        String volumeDriver = text(driver, "local");
        return invoke("创建卷", () -> {
            CreateVolumeResponse response = client().createVolumeCmd()
                    .withName(volumeName)
                    .withDriver(volumeDriver)
                    .exec();
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("Name", volumeName);
            data.put("Driver", volumeDriver);
            return data;
        });
    }

    public void removeVolume(String volumeName) {
        String name = requireReference(volumeName, "卷");
        invoke("删除卷", () -> client().removeVolumeCmd(name).exec());
    }

    // ------------------------------------------------------------------
    // 环境与汇总
    // ------------------------------------------------------------------

    public Map<String, Object> environment() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("transport", "docker-java");
        data.put("host", describeHost());
        try {
            Map<String, Object> version = toMap(client().versionCmd().exec());
            data.put("daemonOnline", true);
            data.put("serverVersion", stringValue(version.get("Version")));
            data.put("clientVersion", clientVersion());
            data.put("apiVersion", stringValue(version.get("ApiVersion")));
            data.put("minApiVersion", stringValue(version.get("MinAPIVersion")));
            data.put("negotiatedApiVersion", apiVersion());
            data.put("os", stringValue(version.get("Os")));
            data.put("arch", stringValue(version.get("Arch")));
            data.put("kernelVersion", stringValue(version.get("KernelVersion")));
            data.put("message", "Docker 守护进程在线");
        } catch (RuntimeException ex) {
            data.put("daemonOnline", false);
            data.put("serverVersion", "");
            data.put("clientVersion", clientVersion());
            data.put("apiVersion", "");
            data.put("message", firstLine(ex.getMessage()));
        }
        return data;
    }

    private static String clientVersion() {
        String version = DockerClient.class.getPackage() == null
                ? null : DockerClient.class.getPackage().getImplementationVersion();
        return version == null || version.isBlank() ? "docker-java" : version;
    }

    private String describeHost() {
        String resolved = resolveHost();
        if (resolved != null) {
            return resolved;
        }
        String os = System.getProperty("os.name", "").toLowerCase();
        return os.contains("win") ? "npipe:////./pipe/docker_engine" : "unix:///var/run/docker.sock";
    }

    public Map<String, Object> versionInfo() {
        return invoke("获取 Docker 版本", () -> toMap(client().versionCmd().exec()));
    }

    public Map<String, Object> systemInfo() {
        return invoke("获取 Docker 系统信息", () -> toMap(client().infoCmd().exec()));
    }

    public Map<String, Object> summary() {
        Map<String, Object> data = new LinkedHashMap<>();
        try {
            List<Container> containers = client().listContainersCmd().withShowAll(true).exec();
            long total = containers.size();
            long running = 0L;
            for (Container container : containers) {
                Map<String, Object> raw = toMap(container);
                if ("running".equalsIgnoreCase(resolveState(raw))) {
                    running++;
                }
            }
            data.put("running", running);
            data.put("total", total);
            data.put("stopped", Math.max(0L, total - running));
            data.put("images", client().listImagesCmd().exec().size());
            data.put("networks", client().listNetworksCmd().exec().size());
            data.put("available", true);
        } catch (Exception ex) {
            data.put("running", 0L);
            data.put("total", 0L);
            data.put("stopped", 0L);
            data.put("images", 0L);
            data.put("networks", 0L);
            data.put("available", false);
            data.put("message", firstLine(ex.getMessage()));
        }
        return data;
    }

    // ------------------------------------------------------------------
    // 底层 HTTP（日志 / 统计等流式接口）
    // ------------------------------------------------------------------

    /**
     * 为裸 HTTP 请求补上当前客户端实际使用的 API 版本前缀。
     *
     * <p>docker-java 的 Cmd 接口会自行加 {@code /v1.x}，但日志 / 统计等直接走
     * {@link DockerHttpClient} 的路径不会。若省略前缀，旧版 Docker（CentOS 19.03/20.10）
     * 会按“未指定版本”处理，部分接口返回 400；这里统一复用同一配置，保证行为一致。</p>
     */
    private String apiPath(String path) {
        String version = apiVersion();
        if (version.isBlank()) {
            return path.startsWith("/") ? path : "/" + path;
        }
        return "/v" + version + (path.startsWith("/") ? path : "/" + path);
    }
    private Map<String, Object> readJson(String path, Map<String, String> query) {
        byte[] bytes = readBytes(path, query);
        try {
            JsonNode node = objectMapper.readTree(bytes);
            return objectMapper.convertValue(node, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException ex) {
            throw new BusinessException(500, "解析 Docker 返回数据失败");
        }
    }

    private byte[] readBytes(String path, Map<String, String> query) {
        return readBytes(path, query, false);
    }

    private byte[] readBytes(String path, Map<String, String> query, boolean follow) {
        return invoke("读取 Docker 数据", () -> {
            String target = apiPath(path);
            if (query != null && !query.isEmpty()) {
                StringBuilder queryString = new StringBuilder();
                for (Map.Entry<String, String> entry : query.entrySet()) {
                    if (entry.getValue() == null) {
                        continue;
                    }
                    if (queryString.length() > 0) {
                        queryString.append('&');
                    }
                    queryString.append(urlEncode(entry.getKey())).append('=').append(urlEncode(entry.getValue()));
                }
                if (queryString.length() > 0) {
                    target = target + (target.contains("?") ? "&" : "?") + queryString;
                }
            }
            DockerHttpClient.Request.Builder builder = DockerHttpClient.Request.builder()
                    .method(DockerHttpClient.Request.Method.GET)
                    .path(target);
            try (DockerHttpClient.Response response = httpClient().execute(builder.build());
                 InputStream body = response.getBody()) {
                if (response.getStatusCode() >= 400) {
                    int status = response.getStatusCode();
                    String detail = new String(body.readAllBytes(), StandardCharsets.UTF_8);
                    try {
                        detail = objectMapper.readTree(detail).path("message").asText(detail);
                    } catch (IOException ignored) {
                        // 非 JSON 错误仍保留第一行诊断。
                    }
                    throw new BusinessException(status == 404 ? 404 : status == 409 ? 409 : 500,
                            "Docker 接口失败：" + firstLine(detail));
                }
                if (!follow) return body.readAllBytes();
                AtomicBoolean expired = new AtomicBoolean(false);
                ScheduledFuture<?> deadline = logTimer.schedule(() -> {
                    expired.set(true);
                    // DockerHttpClient.Response.close() 不声明受检异常，直接关闭即可。
                    response.close();
                }, 5, TimeUnit.SECONDS);
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                try {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = body.read(buffer)) != -1) {
                        output.write(buffer, 0, count);
                    }
                } catch (IOException ex) {
                    if (!expired.get()) throw ex;
                } finally {
                    deadline.cancel(false);
                }
                return output.toByteArray();
            } catch (IOException ex) {
                throw new BusinessException(500, "读取 Docker 数据失败：" + firstLine(ex.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------
    // 工具方法
    // ------------------------------------------------------------------

    private Map<String, Object> toMap(Object value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        try {
            return objectMapper.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (RuntimeException ex) {
            throw new BusinessException(500, "解析 Docker 返回数据失败：" + firstLine(ex.getMessage()));
        }
    }

    private static Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private static double numberValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0d;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0d;
        }
    }

    private static double round(double value) {
        return Math.round(value * 100d) / 100d;
    }

    private static String stringValue(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static String formatEpoch(Object value) {
        double epoch = numberValue(value);
        if (epoch <= 0) {
            return "";
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond((long) epoch), ZONE).format(TIME_FORMAT);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KiB", "MiB", "GiB", "TiB"};
        int index = 0;
        double value = bytes;
        while (value >= 1024 && index < units.length - 1) {
            value /= 1024;
            index++;
        }
        return String.format("%.2f %s", value, units[index]);
    }

    private static List<String> stringList(Object value) {
        List<String> items = new ArrayList<>();
        if (value == null) {
            return items;
        }
        if (value instanceof List<?> list) {
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    items.add(String.valueOf(item).trim());
                }
            }
        } else if (value instanceof String text) {
            for (String item : text.split(",")) {
                if (!item.isBlank()) {
                    items.add(item.trim());
                }
            }
        }
        return items;
    }

    private static String encodePath(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** 查询参数编码：与路径一致使用 UTF-8，避免中文 / 特殊字符导致 Docker 解析失败。 */
    private static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String requireReference(String value, String label) {
        String result = text(value, null);
        if (result == null) {
            throw new BusinessException(400, "缺少" + label + "标识");
        }
        return result;
    }

    private static String text(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }
}
