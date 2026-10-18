package com.huluwa.console.service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** 解析固定版本下载器的进度输出；只保留数字，不向接口暴露原始输出。 */
final class HlsDownloadProgress {
    private static final Pattern ANSI = Pattern.compile("\\x1B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern BOUNDARY = Pattern.compile("(?:Vid|Aud|Sub)\\b|[\\r\\n]");
    private static final Pattern ROW = Pattern.compile(
            "^(.*?)\\s+(\\d+(?:[.,]\\d+)?)\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s+(\\d+(?:[.,]\\d+)?)\\s*%\\s*(.*)$",
            Pattern.DOTALL);
    /** 部分输出只有已完成量/总量，百分比由字节大小推导。 */
    private static final Pattern BYTES_ROW = Pattern.compile(
            "^(.*?)\\s+(\\d+(?:[.,]\\d+)?)\\s*((?:[KMGT]i?)?B)\\s*/\\s*(\\d+(?:[.,]\\d+)?)\\s*((?:[KMGT]i?)?B)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SPEED = Pattern.compile("(?i)(\\d+(?:[.,]\\d+)?)\\s*([KMGT]?)(?:i?B)(?:ps|/s)");
    private static final int MAX_PENDING_CHARS = 8192;
    private final Map<String, Track> tracks = new LinkedHashMap<>();
    private final StringBuilder pending = new StringBuilder();
    private String phase = "parsing";
    private record Track(double percent, double bytesPerSecond, long seenAt) { }
    record Snapshot(Double percent, String speed, String phase) { }

    synchronized void accept(String output) {
        // 重定向输出可能没有换行；保留最后一条状态，兼容轨道名、数字和 ANSI 序列跨读取块。
        String text = ANSI.matcher(pending.append(output)).replaceAll("");
        var boundary = BOUNDARY.matcher(text);
        int rowStart = -1;
        int tailStart = 0;
        while (boundary.find()) {
            if (rowStart >= 0) acceptRow(text.substring(rowStart, boundary.start()));
            rowStart = boundary.group().length() == 3 ? boundary.start() : -1;
            tailStart = rowStart >= 0 ? rowStart : boundary.end();
        }
        // 当前状态只要已有百分比就立即发布，不等下一次换行或下一条轨道状态。
        if (rowStart >= 0) acceptRow(text.substring(rowStart));
        pending.setLength(0);
        pending.append(text, Math.max(tailStart, text.length() - MAX_PENDING_CHARS), text.length());
        if (!tracks.isEmpty()) {
            phase = tracks.values().stream().allMatch(value -> value.percent() >= 100) ? "merging" : "downloading";
        }
    }

    private void acceptRow(String line) {
        var row = ROW.matcher(line);
        String description;
        String remainder;
        double total;
        double percent;
        if (row.matches()) {
            description = row.group(1);
            total = decimal(row.group(3));
            percent = decimal(row.group(4));
            remainder = row.group(5);
        } else {
            var bytes = BYTES_ROW.matcher(line);
            if (!bytes.matches()) return;
            description = bytes.group(1);
            total = bytes(bytes.group(4), bytes.group(5));
            double completed = bytes(bytes.group(2), bytes.group(3));
            percent = total > 0 ? completed / total * 100 : -1;
            remainder = bytes.group(6) == null ? "" : bytes.group(6);
        }
        if (total <= 0 || !Double.isFinite(total) || percent < 0 || percent > 100 || !Double.isFinite(percent)) return;
        // 进度条形状会随下载变化，只用稳定的轨道描述作为键。
        String track = description.replaceAll("[\\u2500-\\u259F].*$", "").replaceAll("\\s+", " ").trim();
        var speed = SPEED.matcher(remainder);
        double bytesPerSecond = 0;
        if (speed.find()) {
            int unit = "KMGT".indexOf(speed.group(2).toUpperCase(Locale.ROOT)) + 1;
            if (speed.group(2).isEmpty()) unit = 0;
            bytesPerSecond = decimal(speed.group(1)) * Math.pow(1024, unit);
            if (!Double.isFinite(bytesPerSecond)) bytesPerSecond = 0;
        }
        tracks.put(track, new Track(percent, percent >= 100 ? 0 : bytesPerSecond, System.nanoTime()));
    }

    private static double bytes(String value, String unit) {
        int power = "KMGT".indexOf(Character.toUpperCase(unit.charAt(0))) + 1;
        return decimal(value) * Math.pow(1024, power);
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
