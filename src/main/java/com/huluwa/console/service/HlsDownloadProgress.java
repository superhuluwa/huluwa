package com.huluwa.console.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 解析固定版本下载器的进度行；只保留数字，不向接口暴露原始输出。 */
final class HlsDownloadProgress {
    private static final Pattern ANSI = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern ROW = Pattern.compile(
            "^\\s*((?:Vid|Aud|Sub)\\b.*?)\\s+(\\d+(?:[.,]\\d+)?)/(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)%\\s*(.*)$");
    private static final Pattern SPEED = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*([KMGT]?)(?:i?B)(?:ps|/s)");
    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private String phase = "parsing";
    private record Track(double percent, double bytesPerSecond, long seenAt) { }
    record Snapshot(Double percent, String speed, String phase) { }

    synchronized void accept(String output) {
        String line = ANSI.matcher(output).replaceAll("").trim();
        var row = ROW.matcher(line);
        if (!row.matches()) return;
        // 进度条形状会随下载变化，只用稳定的轨道描述作为键。
        String track = row.group(1).replaceAll("[\\u2500-\\u259F].*$", "").trim();
        double total = decimal(row.group(3));
        double percent = decimal(row.group(4));
        if (total <= 0 || !Double.isFinite(total) || percent < 0 || percent > 100 || !Double.isFinite(percent)) return;
        var speed = SPEED.matcher(row.group(5));
        double bytesPerSecond = 0;
        if (speed.find()) {
            int unit = "KMGT".indexOf(speed.group(2).toUpperCase(Locale.ROOT)) + 1;
            if (speed.group(2).isEmpty()) unit = 0;
            bytesPerSecond = decimal(speed.group(1)) * Math.pow(1024, unit);
            if (!Double.isFinite(bytesPerSecond)) bytesPerSecond = 0;
        }
        tracks.put(track, new Track(percent, percent >= 100 ? 0 : bytesPerSecond, System.nanoTime()));
        phase = tracks.values().stream().allMatch(value -> value.percent() >= 100) ? "merging" : "downloading";
    }

    synchronized Snapshot snapshot() {
        if (tracks.isEmpty()) return new Snapshot(null, "", phase);
        double percent = tracks.values().stream().mapToDouble(Track::percent).average().orElse(0);
        long now = System.nanoTime();
        double speed = tracks.values().stream().filter(track -> now - track.seenAt() < 5_000_000_000L)
                .mapToDouble(Track::bytesPerSecond).sum();
        // 分片完成后还需合并、探测和归档，任务成功前最多显示 99%。
        return new Snapshot(Math.min(99, Math.floor(percent * 10) / 10), formatSpeed(speed), phase);
    }

    private static double decimal(String value) { return Double.parseDouble(value.replace(',', '.')); }
    private static String formatSpeed(double value) {
        String[] units = {"B/s", "KB/s", "MB/s", "GB/s", "TB/s"};
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) { value /= 1024; unit++; }
        return String.format(Locale.ROOT, "%.1f %s", value, units[unit]);
    }
}
