package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.config.HlsProperties;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** 有界后台探测队列；扫描与轮询只读缓存，不等待 ffprobe。 */
public final class LibraryMediaProbe implements AutoCloseable {
    public record VideoInfo(int width, int height, String resolution, String dynamicRange, String source, String status) { }
    private record Signature(long size, FileTime modified) { }
    private record Cached(Signature signature, VideoInfo info, long retryAt) { }
    private static final Pattern DOLBY_VISION = Pattern.compile(
            "(?i)杜比视界|dolby[ ._-]*vision|(?:^|[^a-z0-9])(?:dv|dovi)(?:$|[^a-z0-9])");
    private static final Pattern HDR = Pattern.compile("(?i)(?:^|[^a-z0-9])HDR(?:10\\+?)?(?:$|[^a-z0-9])");
    private record ResolutionHint(Pattern pattern, String label) { }
    private static final List<ResolutionHint> RESOLUTIONS = List.of(
            hint("8k|4320p", "8K"), hint("4k|2160p", "4K"), hint("1440p", "1440P"),
            hint("1080p", "1080P"), hint("720p", "720P"), hint("480p", "480P"));
    private static final Pattern HDR10_PLUS = Pattern.compile("(?i)(?:^|[^a-z0-9])HDR10\\+");
    private static final Pattern HDR10 = Pattern.compile("(?i)(?:^|[^a-z0-9])HDR10(?![a-z0-9])");
    private static final Pattern HLG = Pattern.compile("(?i)(?:^|[^a-z0-9])HLG(?:$|[^a-z0-9])");
    private final HlsProperties paths;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<Path, Cached> cache = new ConcurrentHashMap<>();
    private final Map<Path, Signature> pending = new ConcurrentHashMap<>();
    private final Set<Process> processes = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(512), runnable -> {
                Thread thread = new Thread(runnable, "library-media-probe");
                thread.setDaemon(true);
                return thread;
            });
    private volatile boolean closed;

    LibraryMediaProbe(HlsProperties paths) { this.paths = paths; }

    VideoInfo get(Path file, long size, FileTime modified) {
        Signature signature = new Signature(size, modified);
        Cached known = cache.get(file);
        if (known != null && known.signature().equals(signature)
                && (known.retryAt() == 0 || known.retryAt() > System.currentTimeMillis())) return known.info();
        if (closed) return fromName(file, "unknown");
        if (pending.putIfAbsent(file, signature) == null) {
            try {
                workers.execute(() -> {
                    try {
                        VideoInfo info = probe(file, signature);
                        cache.put(file, new Cached(signature, info,
                                "ready".equals(info.status()) ? 0 : System.currentTimeMillis() + 300_000));
                    } finally { pending.remove(file, signature); }
                });
            } catch (RejectedExecutionException ignored) {
                // 队列满时留给下一次元数据轮询重试，不能阻塞目录扫描。
                pending.remove(file, signature);
            }
        }
        return fromName(file, "pending");
    }

    void retain(Set<Path> files) { cache.keySet().retainAll(files); }
    void remove(Path file) { cache.remove(file); }

    private VideoInfo probe(Path file, Signature expected) {
        Process process = null;
        try {
            if (closed || !unchanged(file, expected)) return fromName(file, "unknown");
            process = new ProcessBuilder(paths.getFfprobe(), "-v", "error", "-analyzeduration", "10000000",
                    "-probesize", "10000000", "-select_streams", "V:0", "-show_entries",
                    "stream=width,height,codec_tag_string,color_transfer:stream_side_data", "-of", "json",
                    "-i", file.toString()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            processes.add(process);
            if (closed) return fromName(file, "unknown");
            Process running = process;
            AtomicReference<byte[]> output = new AtomicReference<>(new byte[0]);
            Thread reader = Thread.ofVirtual().start(() -> {
                try { output.set(running.getInputStream().readNBytes(524_288)); }
                catch (Exception ignored) { }
            });
            if (!process.waitFor(15, TimeUnit.SECONDS) || process.exitValue() != 0) return fromName(file, "unknown");
            reader.join(1000);
            if (reader.isAlive() || !unchanged(file, expected)) return fromName(file, "unknown");
            JsonNode stream = json.readTree(new String(output.get(), StandardCharsets.UTF_8)).path("streams").path(0);
            int width = stream.path("width").asInt(), height = stream.path("height").asInt();
            if (width <= 0 || height <= 0) return fromName(file, "unknown");
            String range = "";
            String transfer = stream.path("color_transfer").asText();
            if ("smpte2084".equals(transfer)) range = "HDR10";
            if ("arib-std-b67".equals(transfer)) range = "HLG";
            String codecTag = stream.path("codec_tag_string").asText();
            boolean dolby = Set.of("dvhe", "dvh1", "dvav", "dva1", "dav1").contains(codecTag.toLowerCase(java.util.Locale.ROOT));
            for (JsonNode side : stream.path("side_data_list")) {
                if (side.path("dv_profile").asInt() > 0 || side.path("rpu_present_flag").asInt() == 1) dolby = true;
                if (side.path("side_data_type").asText().toLowerCase(java.util.Locale.ROOT).contains("hdr10+")) range = "HDR10+";
            }
            if (dolby) range = "杜比视界";
            return new VideoInfo(width, height, resolution(width, height), range, "probe", "ready");
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            return fromName(file, "unknown");
        } catch (Exception ignored) {
            return fromName(file, "unknown");
        } finally {
            if (process != null) {
                process.destroyForcibly();
                processes.remove(process);
            }
        }
    }

    private boolean unchanged(Path file, Signature expected) throws java.io.IOException {
        BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return attributes.isRegularFile()
                && file.toRealPath().startsWith(Path.of(paths.getMediaRoot()).toRealPath())
                && attributes.size() == expected.size()
                && attributes.lastModifiedTime().equals(expected.modified());
    }

    private static String resolution(int width, int height) {
        int longEdge = Math.max(width, height), shortEdge = Math.min(width, height);
        if (longEdge >= 7680 || shortEdge >= 4320) return "8K";
        if (longEdge >= 3840 || shortEdge >= 2160) return "4K";
        if (shortEdge >= 1440) return "1440P";
        if (longEdge >= 1920 || shortEdge >= 1080) return "1080P";
        if (longEdge >= 1280 || shortEdge >= 720) return "720P";
        if (shortEdge >= 480) return "480P";
        return height + "P";
    }

    private static ResolutionHint hint(String pattern, String label) {
        return new ResolutionHint(Pattern.compile("(?i)(?:^|[^a-z0-9])(?:" + pattern + ")(?:$|[^a-z0-9])"), label);
    }

    private static VideoInfo fromName(Path file, String status) {
        // 只采信文件名；父目录可能同时存放不同清晰度、不同动态范围的版本。
        String name = file.getFileName().toString();
        String resolution = "";
        for (ResolutionHint hint : RESOLUTIONS) {
            if (hint.pattern().matcher(name).find()) { resolution = hint.label(); break; }
        }
        String range = DOLBY_VISION.matcher(name).find() ? "杜比视界" : HDR.matcher(name).find() ? "HDR" : "";
        if (!"杜比视界".equals(range)) {
            if (HDR10_PLUS.matcher(name).find()) range = "HDR10+";
            else if (HDR10.matcher(name).find()) range = "HDR10";
            else if (HLG.matcher(name).find()) range = "HLG";
        }
        return new VideoInfo(0, 0, resolution, range,
                resolution.isBlank() && range.isBlank() ? "unknown" : "filename", status);
    }

    @Override public void close() {
        closed = true;
        workers.shutdownNow();
        processes.forEach(Process::destroyForcibly);
        pending.clear();
        cache.clear();
    }
}
