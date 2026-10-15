package com.huluwa.console.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用临时目录里的假 /proc 数据验证采样口径：CPU 取两次差值、内存按 MemAvailable 折算。
 * 直接调用包级 refresh()，不启动后台线程。
 */
class ServerMetricsServiceTest {

    @TempDir
    Path proc;

    private void write(String name, String content) throws IOException {
        Files.writeString(proc.resolve(name), content, StandardCharsets.UTF_8);
    }

    private ServerMetricsService service() {
        return new ServerMetricsService(proc);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> child(Map<String, Object> data, String key) {
        return (Map<String, Object>) data.get(key);
    }

    @Test
    void cpuPercentIsTheDeltaBetweenTwoSamples() throws IOException {
        // 累计 100 个时间片全部空闲 -> cpuPercent 应为 null（还没有基线）
        write("stat", "cpu  100 0 0 100 0 0 0 0 0 0\ncpu0 50 0 0 50 0 0 0 0 0 0\n");
        write("meminfo", "MemTotal:  1000 kB\nMemAvailable:  400 kB\n");
        write("loadavg", "0.10 0.20 0.30 1/100 12345\n");
        write("cpuinfo", "processor\t: 0\n\nprocessor\t: 1\n");
        ServerMetricsService service = service();
        service.refresh();

        // 再走 300 个时间片：50 空闲 + 250 忙 -> 忙碌占比 250/300
        write("stat", "cpu  350 0 0 150 0 0 0 0 0 0\ncpu0 175 0 0 75 0 0 0 0 0 0\n");
        service.refresh();

        Map<String, Object> cpu = child(service.snapshot(), "cpu");
        assertEquals(83.3, (Double) cpu.get("percent"), 0.05);
        assertEquals(2, cpu.get("cores"));
        assertEquals(0.10, (Double) cpu.get("load1"));
    }

    @Test
    void memoryCountsOnlyWhatIsNotAvailable() throws IOException {
        write("stat", "cpu  0 0 0 0 0 0 0 0 0 0\n");
        write("meminfo", """
                MemTotal:        4096000 kB
                MemFree:          512000 kB
                MemAvailable:    1024000 kB
                Buffers:           10000 kB
                Cached:          1500000 kB
                """);
        ServerMetricsService service = service();
        service.refresh();

        Map<String, Object> memory = child(service.snapshot(), "memory");
        assertEquals(4096000L * 1024L, memory.get("total"));
        assertEquals(1024000L * 1024L, memory.get("available"));
        // 已用 = MemTotal - MemAvailable（不是 MemTotal - MemFree，缓存不该算成占用）
        assertEquals(3072000L * 1024L, memory.get("used"));
        assertEquals(75.0, (Double) memory.get("percent"), 0.05);
    }

    @Test
    void fallsBackToMemFreeWhenKernelHasNoMemAvailable() throws IOException {
        write("stat", "cpu  0 0 0 0 0 0 0 0 0 0\n");
        write("meminfo", "MemTotal:  2000 kB\nMemFree:  500 kB\n");
        ServerMetricsService service = service();
        service.refresh();

        Map<String, Object> memory = child(service.snapshot(), "memory");
        assertEquals(1500L * 1024L, memory.get("used"));
    }

    @Test
    void idleAndIowaitBothCountAsIdleTime() throws IOException {
        write("stat", "cpu  0 0 0 100 0 0 0 0 0 0\n");
        write("meminfo", "MemTotal:  1000 kB\nMemAvailable:  500 kB\n");
        ServerMetricsService service = service();
        service.refresh();

        // 100 个时间片里 40 iowait + 60 忙：iowait 属于等待而非计算，应计入空闲
        write("stat", "cpu  60 0 0 100 40 0 0 0 0 0\n");
        service.refresh();

        assertEquals(60.0, (Double) child(service.snapshot(), "cpu").get("percent"), 0.05);
    }

    @Test
    void unreadableProcReportsUnavailableInsteadOfZero() {
        ServerMetricsService service = service();
        service.refresh();

        Map<String, Object> data = service.snapshot();
        assertEquals(false, data.get("available"));
        assertNotNull(data.get("message"));
        // 关键：拿不到数据时不能退回 0%，否则首页会被误读成机器很空闲
        assertNull(data.get("cpu"));
        assertNull(data.get("memory"));
    }
}
