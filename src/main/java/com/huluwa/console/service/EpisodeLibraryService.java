package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.HlsProperties;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 下载目录剧集盘点：只扫描媒体根目录下的普通视频文件，跳过工作区、隐藏目录和符号链接。
 * 删除接口只接收扫描时生成的短期 ID，不接收客户端路径，避免任意文件删除。
 */
@Service
public class EpisodeLibraryService {
    private static final Logger log = LoggerFactory.getLogger(EpisodeLibraryService.class);
    private static final Set<String> VIDEO_EXTENSIONS = Set.of(
            ".mp4", ".mkv", ".avi", ".mov", ".wmv", ".flv", ".ts", ".m2ts",
            ".webm", ".m4v", ".mpg", ".mpeg", ".rmvb", ".rm", ".3gp");
    private static final Set<String> IGNORED_DIRECTORIES = Set.of(
            ".huluwa-work", "@eadir", "#recycle", "#snapshot", "$recycle.bin");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final long SCAN_TOKEN_TTL = 30 * 60 * 1000L;
    private static final int MAX_SCAN_FILES = 50_000;

    private final HlsProperties paths;
    private final LibraryMediaProbe mediaProbe;
    private final Map<String, ScanToken> tokens = new ConcurrentHashMap<>();
    private final AtomicLong scanSequence = new AtomicLong();

    public EpisodeLibraryService(HlsProperties paths) {
        this.paths = paths;
        this.mediaProbe = new LibraryMediaProbe(paths);
    }

    private record ScanToken(Path file, long size, FileTime modified, long createdAt) { }

    public record Entry(String id, String seriesTitle, String seriesKey, int season, int episode,
                        String fileName, String relativePath, String extension, long size, String modifiedAt,
                        boolean episodeRecognized, String showKey, boolean seasonExplicit,
                        boolean seriesRecognized, int episodeEnd, LibraryMediaProbe.VideoInfo videoInfo) { }

    public record VideoInfoRequest(List<String> ids) { }

    public record VideoInfoUpdate(String id, LibraryMediaProbe.VideoInfo videoInfo) { }

    public record ScanResult(String mediaRoot, boolean writable, List<Entry> entries, long totalBytes,
                             int scannedFiles, int skippedFiles, int seriesCount, String scannedAt, String warning) { }

    public record DeleteRequest(List<String> ids) { }

    public record DeleteFailure(String id, String fileName, String message) { }

    public record DeleteResult(int requested, int deleted, int failed, long freedBytes, List<String> deletedIds,
                               List<DeleteFailure> failures, String message) { }

    /** 扫描下载根目录；扫描失败时给出可操作的错误，而不是返回空列表。 */
    public ScanResult scan() {
        Path root = root();
        if (!Files.exists(root)) {
            throw new BusinessException(400, "下载目录 " + root + " 不存在，请检查 Docker 目录挂载");
        }
        if (!Files.isDirectory(root)) {
            throw new BusinessException(400, "下载路径 " + root + " 不是目录，请检查 Docker 目录挂载");
        }
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(entry -> now - entry.getValue().createdAt() > SCAN_TOKEN_TTL);
        long scanId = scanSequence.incrementAndGet();
        Map<String, ScanToken> found = new HashMap<>();
        List<Entry> entries = new ArrayList<>();
        int[] counts = new int[2];
        int[] filesFound = new int[1];
        boolean[] truncated = new boolean[1];

        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public java.nio.file.FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root) && shouldSkipDirectory(directory)) {
                        return java.nio.file.FileVisitResult.SKIP_SUBTREE;
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    // 目录不算扫描文件；只对普通文件应用数量上限。
                    if (!attributes.isRegularFile()) {
                        counts[1]++;
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    if (isHiddenTree(root, file) || !isVideo(file)) {
                        counts[1]++;
                        return java.nio.file.FileVisitResult.CONTINUE;
                    }
                    counts[0]++;
                    try {
                        Entry entry = entry(root, file, attributes);
                        if (filesFound[0] >= MAX_SCAN_FILES) {
                            truncated[0] = true;
                            return java.nio.file.FileVisitResult.TERMINATE;
                        }
                        entries.add(entry);
                        found.put(entry.id(), new ScanToken(file, entry.size(), attributes.lastModifiedTime(), now));
                        filesFound[0]++;
                    } catch (Exception e) {
                        counts[1]++;
                        log.debug("跳过无法读取的剧集文件 {}：{}", file, e.getMessage());
                    }
                    return java.nio.file.FileVisitResult.CONTINUE;
                }

                @Override
                public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException exc) {
                    counts[1]++;
                    log.debug("扫描下载目录时跳过 {}：{}", file, exc.getMessage());
                    return java.nio.file.FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new BusinessException(400, "无法扫描下载目录：" + HlsSupport.error(e));
        }

        // 扫描完成后替换清单；并发的旧扫描结果不能覆盖较新的扫描。
        if (scanSequence.get() == scanId) {
            tokens.clear();
            tokens.putAll(found);
            mediaProbe.retain(found.values().stream().map(ScanToken::file).collect(Collectors.toSet()));
        }

        entries.sort((left, right) -> {
            int title = left.seriesTitle().compareToIgnoreCase(right.seriesTitle());
            if (title != 0) return title;
            int season = Integer.compare(left.season(), right.season());
            if (season != 0) return season;
            int episode = Integer.compare(left.episode(), right.episode());
            if (episode != 0) return episode;
            return left.fileName().compareToIgnoreCase(right.fileName());
        });
        long totalBytes = entries.stream().mapToLong(Entry::size).sum();
        long seriesCount = entries.stream().filter(Entry::seriesRecognized).map(Entry::showKey).distinct().count();
        String warning = truncated[0]
                ? "可管理文件数量已达到 " + MAX_SCAN_FILES + " 上限，本次扫描已截断"
                : counts[1] > 0 ? "已跳过 " + counts[1] + " 个非视频、隐藏或不可读文件" : null;
        return new ScanResult(root.toString(), Files.isWritable(root), List.copyOf(entries), totalBytes,
                counts[0], counts[1], (int) Math.min(Integer.MAX_VALUE, seriesCount), HlsSupport.now(), warning);
    }

    /** 批量删除；逐项校验扫描令牌、文件类型与大小/时间，单条失败不会中断其余删除。 */
    public synchronized DeleteResult delete(DeleteRequest request) {
        List<String> ids = request == null || request.ids() == null ? List.of() : request.ids();
        if (ids.isEmpty()) throw new BusinessException(400, "请选择要删除的剧集");
        if (ids.size() > 500) throw new BusinessException(400, "一次最多删除 500 个文件");
        List<String> unique = new ArrayList<>(new LinkedHashSet<>(ids));
        Path root = root();
        if (!Files.isDirectory(root)) throw new BusinessException(400, "下载目录不可访问，无法删除文件");

        int deleted = 0;
        long freedBytes = 0;
        List<String> deletedIds = new ArrayList<>();
        List<DeleteFailure> failures = new ArrayList<>();
        for (String rawId : unique) {
            String id = HlsSupport.text(rawId).trim();
            String fileName = "";
            try {
                ScanToken token = tokens.get(id);
                if (id.isBlank() || token == null) throw new BusinessException(409, "文件清单已过期，请刷新后重试");
                if (System.currentTimeMillis() - token.createdAt() > SCAN_TOKEN_TTL) {
                    tokens.remove(id);
                    throw new BusinessException(409, "文件清单已过期，请刷新后重试");
                }
                fileName = token.file().getFileName().toString();
                Path target = safeTarget(root, token.file());
                BasicFileAttributes attributes = Files.readAttributes(
                        target, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                if (!attributes.isRegularFile()) throw new BusinessException(409, "目标不是普通文件，请刷新后重试");
                if (attributes.size() != token.size() || !attributes.lastModifiedTime().equals(token.modified())) {
                    throw new BusinessException(409, "文件已发生变化，请刷新后重试");
                }
                Files.delete(target);
                tokens.remove(id);
                mediaProbe.remove(target);
                deleted++;
                deletedIds.add(id);
                freedBytes += token.size();
                removeEmptyParents(root, target.getParent());
            } catch (BusinessException e) {
                failures.add(new DeleteFailure(id, fileName, e.getMessage()));
            } catch (Exception e) {
                failures.add(new DeleteFailure(id, fileName, "删除失败：" + HlsSupport.error(e)));
            }
        }
        int failed = failures.size();
        String message = deleted == 0
                ? "没有文件被删除" + (failed > 0 ? "，请查看失败原因" : "")
                : "已删除 " + deleted + " 个文件" + (failed > 0 ? "，" + failed + " 个失败" : "");
        return new DeleteResult(unique.size(), deleted, failed, freedBytes,
                List.copyOf(deletedIds), List.copyOf(failures), message);
    }

    /** 仅读取本次扫描令牌对应的视频信息，不重新扫描，也不使删除令牌失效。 */
    public List<VideoInfoUpdate> videoInfo(VideoInfoRequest request) {
        List<String> ids = request == null || request.ids() == null ? List.of() : request.ids();
        if (ids.size() > 500) throw new BusinessException(400, "一次最多读取 500 个文件的视频信息");
        List<VideoInfoUpdate> updates = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (String id : new LinkedHashSet<>(ids)) {
            ScanToken token = id == null ? null : tokens.get(id);
            if (token == null || now - token.createdAt() > SCAN_TOKEN_TTL) continue;
            updates.add(new VideoInfoUpdate(id, mediaProbe.get(token.file(), token.size(), token.modified())));
        }
        return List.copyOf(updates);
    }

    private Entry entry(Path root, Path file, BasicFileAttributes attributes) {
        String fileName = file.getFileName().toString();
        LibraryFileName.Identity identity = LibraryFileName.parse(root, file);
        String relative = root.relativize(file).toString().replace('\\', '/');
        // 保留旧客户端使用的季度分组键；新客户端使用不含季度的 showKey。
        String seriesKey = identity.title().toLowerCase(Locale.ROOT) + "|" + identity.season();
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        return new Entry(UUID.randomUUID().toString(), identity.title(), seriesKey, identity.season(), identity.episode(),
                fileName, relative, extension, attributes.size(), TIME.format(attributes.lastModifiedTime().toInstant()),
                identity.episode() > 0, identity.showKey(), identity.seasonExplicit(), identity.seriesRecognized(),
                identity.episodeEnd(), mediaProbe.get(file, attributes.size(), attributes.lastModifiedTime()));
    }

    private static boolean isVideo(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return VIDEO_EXTENSIONS.stream().anyMatch(name::endsWith);
    }

    private static boolean isHiddenTree(Path root, Path file) {
        for (Path part : root.relativize(file)) {
            String name = part.toString();
            if (name.startsWith(".") || IGNORED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private static boolean shouldSkipDirectory(Path directory) {
        String name = directory.getFileName().toString();
        return name.startsWith(".") || IGNORED_DIRECTORIES.contains(name.toLowerCase(Locale.ROOT));
    }

    private Path safeTarget(Path root, Path candidate) throws IOException {
        Path normalizedRoot = root.toRealPath();
        Path absolute = candidate.toAbsolutePath().normalize();
        if (!absolute.startsWith(root) || Files.isSymbolicLink(absolute)) throw new IOException("文件路径无效");
        Path real = absolute.toRealPath();
        if (!real.startsWith(normalizedRoot)) throw new IOException("文件超出下载目录");
        return real;
    }

    private static void removeEmptyParents(Path root, Path parent) {
        Path current = parent;
        while (current != null && !current.equals(root) && current.startsWith(root)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                if (stream.iterator().hasNext()) return;
            } catch (IOException e) {
                return;
            }
            try {
                Files.deleteIfExists(current);
            } catch (IOException e) {
                return;
            }
            current = current.getParent();
        }
    }

    private Path root() {
        return Path.of(paths.getMediaRoot()).toAbsolutePath().normalize();
    }

    @PreDestroy
    public void clearTokens() {
        tokens.clear();
        mediaProbe.close();
    }
}
