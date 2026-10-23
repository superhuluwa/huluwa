package com.huluwa.console.service;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 宿主机（飞牛 NAS）整机资源用量：CPU 使用率、内存占用与系统负载。
 *
 * <p>数据取自容器内可见的 /proc。飞牛上的 Docker 没有隔离宿主机的 proc 视图，容器里读到的
 * /proc/stat、/proc/meminfo 就是整机数据，因此不需要额外挂载或特权。若某个部署环境改成了
 * 隔离视图，页面照常可用，只是数值变成容器自身指标。</p>
 *
 * <p>CPU 使用率是两次采样的差值，单次读取得不到，所以由后台线程每 2 秒采样一帧并缓存，
 * 接口只读缓存、不占用请求线程。采样失败时保留上一帧，避免页面数值闪空。</p>
 */
@Service
public class ServerMetricsService {

    private static final Logger log = LoggerFactory.getLogger(ServerMetricsService.class);

    /** 后台采样间隔（秒）：与首页资源轮询周期一致，接口读取最近一帧快照 */
    private static final long SAMPLE_SECONDS = 2L;
    /** 首帧建立 CPU 基线的等待时长（毫秒） */
    private static final long FIRST_BASELINE_MILLIS = 200L;
    /** /proc/stat 的 CPU 汇总行：cpu user nice system idle iowait irq softirq steal ... */
    private static final int IDLE_FIELD = 4;
    private static final int IOWAIT_FIELD = 5;
    private static final int STEAL_FIELD = 8;
    /** /proc/meminfo 的单行：Key:  12345 kB */
    private static final Pattern MEMORY_FIELD = Pattern.compile("^(\\w+):\\s+(\\d+) kB", Pattern.MULTILINE);

    private final Path procRoot;
    private final ScheduledExecutorService sampler = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().daemon().name("server-metrics-", 0).factory());

    private volatile Map<String, Object> snapshot = unavailable("正在采样");
    private volatile boolean sampled;
    private CpuTicks previousTicks;

    public ServerMetricsService() {
        this(Path.of("/proc"));
        sampler.scheduleWithFixedDelay(this::refresh, 0L, SAMPLE_SECONDS, TimeUnit.SECONDS);
    }

    /** 指定 /proc 根目录（测试用临时目录中的假数据）；此构造器不启动后台采样。 */
    ServerMetricsService(Path procRoot) {
        this.procRoot = procRoot;
    }

    /** 首页与 App 使用的整机快照，包含采集时间及过期状态。 */
    public Map<String, Object> snapshot() {
        Map<String, Object> current = new LinkedHashMap<>(snapshot);
        Object collected = current.get("sampledAt");
        if (collected instanceof Number time && System.currentTimeMillis() - time.longValue() > 10_000L) {
            current.put("stale", true);
            current.putIfAbsent("message", "资源采样已过期");
        }
        return current;
    }

    @PreDestroy
    void shutdown() {
        sampler.shutdownNow();
    }

    /** 采样一帧并更新缓存：后台线程按周期调用，测试也可直接调用。 */
    void refresh() {
        try {
            CpuTicks current = readCpuTicks();
            CpuTicks previous = previousTicks;
            if (previous == null) {
                // 首帧没有基线算不出使用率，短暂等待后重采一次，让首页首次打开就能看到真实数值
                previous = current;
                Thread.sleep(FIRST_BASELINE_MILLIS);
                current = readCpuTicks();
            }
            previousTicks = current;
            snapshot = build(previous, current);
            sampled = true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            log.debug("采样宿主机资源失败: {}", ex.getMessage());
            if (!sampled) {
                snapshot = unavailable("读取 /proc 失败：" + ex.getMessage());
            } else {
                Map<String, Object> previous = new LinkedHashMap<>(snapshot);
                previous.put("stale", true);
                previous.put("lastAttemptAt", System.currentTimeMillis());
                previous.put("message", "资源采样失败，显示上次数据");
                snapshot = previous;
            }
        }
    }

    private Map<String, Object> build(CpuTicks previous, CpuTicks current) throws IOException {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", true);
        data.put("cpu", readCpu(previous, current));
        data.put("memory", readMemory());
        long now = System.currentTimeMillis();
        data.put("sampledAt", now);
        data.put("lastAttemptAt", now);
        data.put("stale", false);
        return data;
    }

    private Map<String, Object> readCpu(CpuTicks previous, CpuTicks current) {
        Map<String, Object> cpu = new LinkedHashMap<>();
        long totalDelta = current.total() - previous.total();
        // 两次读数之间时间没有推进时不给使用率，避免页面出现假的 0%
        cpu.put("percent", totalDelta > 0
                ? round1((totalDelta - (current.idle() - previous.idle())) * 100d / totalDelta) : null);
        cpu.put("cores", readCores());
        double[] load = readLoadAverage();
        if (load != null) {
            cpu.put("load1", load[0]);
            cpu.put("load5", load[1]);
            cpu.put("load15", load[2]);
        }
        return cpu;
    }

    private Map<String, Object> readMemory() throws IOException {
        String content = Files.readString(procRoot.resolve("meminfo"), StandardCharsets.UTF_8);
        Map<String, Long> fields = new LinkedHashMap<>();
        Matcher matcher = MEMORY_FIELD.matcher(content);
        while (matcher.find()) {
            fields.put(matcher.group(1), Long.parseLong(matcher.group(2)) * 1024L);
        }
        long total = fields.getOrDefault("MemTotal", 0L);
        // MemAvailable 是内核给出的「还能给新程序用多少」；老内核没有该字段时退回 MemFree
        long available = fields.containsKey("MemAvailable")
                ? fields.get("MemAvailable") : fields.getOrDefault("MemFree", 0L);
        long used = Math.max(0L, total - available);
        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("total", total);
        memory.put("available", available);
        memory.put("used", used);
        memory.put("percent", total > 0 ? round1(used * 100d / total) : null);
        return memory;
    }

    private CpuTicks readCpuTicks() throws IOException {
        List<String> lines = Files.readAllLines(procRoot.resolve("stat"), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (!line.startsWith("cpu ")) {
                continue;
            }
            String[] fields = line.trim().split("\\s+");
            long total = 0L;
            long idle = 0L;
            // 只累加到 steal：guest / guest_nice 已经计入 user / nice，再加会重复
            for (int i = 1; i <= STEAL_FIELD && i < fields.length; i++) {
                long value = Long.parseLong(fields[i]);
                total += value;
                if (i == IDLE_FIELD || i == IOWAIT_FIELD) {
                    idle += value;
                }
            }
            return new CpuTicks(total, idle);
        }
        throw new IOException("/proc/stat 缺少 cpu 汇总行");
    }

    /** CPU 核心数：/proc/cpuinfo 的 processor 行数，读不到时退回 JVM 可见核数。 */
    private int readCores() {
        try {
            long cores = Files.readAllLines(procRoot.resolve("cpuinfo"), StandardCharsets.UTF_8).stream()
                    .filter(line -> line.startsWith("processor")).count();
            if (cores > 0) {
                return (int) cores;
            }
        } catch (IOException ex) {
            log.debug("读取 CPU 核心数失败: {}", ex.getMessage());
        }
        return Runtime.getRuntime().availableProcessors();
    }

    /** 最近 1 / 5 / 15 分钟平均负载；读不到时省略该组字段（页面显示占位符）。 */
    private double[] readLoadAverage() {
        try {
            String[] fields = Files.readString(procRoot.resolve("loadavg"), StandardCharsets.UTF_8)
                    .trim().split("\\s+");
            return new double[]{Double.parseDouble(fields[0]), Double.parseDouble(fields[1]),
                    Double.parseDouble(fields[2])};
        } catch (Exception ex) {
            log.debug("读取系统负载失败: {}", ex.getMessage());
            return null;
        }
    }

    private static double round1(double value) {
        return Math.round(value * 10d) / 10d;
    }

    private static Map<String, Object> unavailable(String message) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("available", false);
        data.put("lastAttemptAt", System.currentTimeMillis());
        data.put("stale", false);
        data.put("message", message);
        return data;
    }

    /** /proc/stat 的 CPU 累计值：total 为全部时间片，idle 为 idle + iowait。 */
    private record CpuTicks(long total, long idle) {
    }
}
