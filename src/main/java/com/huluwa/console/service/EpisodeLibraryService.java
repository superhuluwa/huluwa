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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final Pattern SEASON_EPISODE = Pattern.compile(
            "(?i)(?:^|[^a-z0-9])S(\\d{1,3})[ ._-]*E(\\d{1,4})(?!\\d)");
    private static final Pattern CHINESE_EPISODE = Pattern.compile(
            "第\\s*([0-9零〇一二两三四五六七八九十百千]+)\\s*[集话期]");
    private static final Pattern CHINESE_SEASON = Pattern.compile(
            "第\\s*([0-9零〇一二两三四五六七八九十百千]+)\\s*季");
    private static final Pattern EXPLICIT_EPISODE = Pattern.compile(
            "(?i)(?:^|[^a-z0-9])(?:EP?|E)(\\d{1,4})(?!\\d)");
    private static final Pattern TRAILING_EPISODE = Pattern.compile("(?<!\\d)(\\d{1,4})(?!\\d)\\s*$");
    private static final Pattern BRACKETED_EPISODE = Pattern.compile(
            "(?i)(?:\\[|【)\\s*(?:EP?|第)?\\s*(\\d{1,4})\\s*(?:集|话|期)?\\s*(?:\\]|】)");
    private static final Pattern DOTTED_SEASON_EPISODE = Pattern.compile(
            "(?:^|[\\s._-])S(\\d{1,3})[ ._-]?(\\d{1,4})(?!\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SEPARATED_EPISODE = Pattern.compile(
            "(?i)(?:^|[\\s._-])(?:EP?[ ._-]?)?(\\d{1,4})(?!\\d)");
    private static final Pattern QUALITY_BEFORE_EPISODE = Pattern.compile(
            "(?i)(?:2160p|1080p|720p|480p|4k|8k|web-?dl|bluray|bdrip|hdtv)[\\s._-]*(\\d{1,4})(?!\\d)");
    private static final Pattern ENGLISH_SEASON = Pattern.compile("(?i)(?:^|[^a-z0-9])S(\\d{1,3})(?!\\d)");
    private static final Pattern SEASON_WORD = Pattern.compile("(?i)Season\\s*(\\d{1,3})");
    private static final Pattern RELEASE_NOISE = Pattern.compile(
            "(?i)[.\\s_-]*(?:2160p|1080p|720p|480p|4k|8k|web-?dl|bluray|bdrip|hdtv|h264|h265|hevc|x264|x265|aac|ddp?5\\.1)(?:[.\\s_-].*)?$");
    private static final Pattern GENERIC_EPISODE_TITLE = Pattern.compile(
            "(?i)^(?:第?[0-9零〇一二两三四五六七八九十百千]+[集话期]?|EP?\\d+|S\\d+(?:E\\d+)?)$");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.of("Asia/Shanghai"));
    private static final long SCAN_TOKEN_TTL = 30 * 60 * 1000L;
    private static final int MAX_SCAN_FILES = 50_000;

    private final HlsProperties paths;
    private final Map<String, ScanToken> tokens = new ConcurrentHashMap<>();
    private final AtomicLong scanSequence = new AtomicLong();

    public EpisodeLibraryService(HlsProperties paths) {
        this.paths = paths;
    }

    private record ScanToken(Path file, long size, FileTime modified, long createdAt) { }

    public record Entry(String id, String seriesTitle, String seriesKey, int season, int episode,
                        String fileName, String relativePath, String extension, long size, String modifiedAt,
                        boolean episodeRecognized) { }

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
        long seriesCount = entries.stream().map(Entry::seriesKey).distinct().count();
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

    private Entry entry(Path root, Path file, BasicFileAttributes attributes) {
        String fileName = file.getFileName().toString();
        String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
        String relative = root.relativize(file).toString().replace('\\', '/');
        Integer season = null;
        Integer episode = null;
        String title = null;

        Matcher standard = SEASON_EPISODE.matcher(baseName);
        if (standard.find()) {
            season = positive(standard.group(1));
            episode = positive(standard.group(2));
            title = baseName.substring(0, standard.start()).trim();
        }
        if (episode == null) {
            Matcher chinese = CHINESE_EPISODE.matcher(baseName);
            if (chinese.find()) {
                episode = positive(chineseNumber(chinese.group(1)));
                title = baseName.substring(0, chinese.start()).trim();
            }
        }
        if (episode == null) {
            Matcher explicit = EXPLICIT_EPISODE.matcher(baseName);
            if (explicit.find()) {
                episode = positive(explicit.group(1));
                title = baseName.substring(0, explicit.start()).trim();
            }
        }
        if (episode == null) {
            Matcher dotted = DOTTED_SEASON_EPISODE.matcher(baseName);
            if (dotted.find()) {
                season = positive(dotted.group(1));
                episode = positive(dotted.group(2));
                title = baseName.substring(0, dotted.start()).trim();
            }
        }
        if (episode == null) {
            Matcher bracketed = BRACKETED_EPISODE.matcher(baseName);
            if (bracketed.find() && plausibleTrailingEpisode(bracketed.group(1))) {
                episode = positive(bracketed.group(1));
                title = baseName.substring(0, bracketed.start()).trim();
            }
        }
        if (episode == null) {
            Matcher quality = QUALITY_BEFORE_EPISODE.matcher(baseName);
            if (quality.find() && plausibleTrailingEpisode(quality.group(1))) {
                episode = positive(quality.group(1));
                title = baseName.substring(0, quality.start()).trim();
            }
        }
        if (episode == null) {
            // 常见发布名：剧名.01.1080p、剧名 01-02、剧名[03]。只取紧邻标题的普通数字，
            // 年份、分辨率、编码等数字在候选筛选阶段排除。
            Matcher separated = SEPARATED_EPISODE.matcher(baseName);
            while (separated.find()) {
                int start = separated.start();
                String before = baseName.substring(0, start);
                char separator = start == 0 ? ' ' : baseName.charAt(start - 1);
                boolean parenthesized = before.matches(".*[\\[【]");
                if (" \t._-".indexOf(separator) < 0 && !parenthesized) continue;
                if (!plausibleSeriesEpisode(separated.group(1), before)) continue;
                episode = positive(separated.group(1));
                title = before.trim();
                break;
            }
        }
        if (episode == null) {
            Matcher trailing = TRAILING_EPISODE.matcher(baseName);
            if (trailing.find() && plausibleSeriesEpisode(trailing.group(1),
                    baseName.substring(0, trailing.start()))) {
                episode = positive(trailing.group(1));
                title = baseName.substring(0, trailing.start()).trim();
            }
        }
        if (season == null) season = seasonOf(baseName);
        String clean = cleanTitle(title);
        if (clean.isBlank() || GENERIC_EPISODE_TITLE.matcher(clean).matches()
                || episode == null && clean.matches("[\\s._-]*\\d{1,4}[\\s._-]*")) {
            clean = fallbackSeriesTitle(root, file);
        }
        if (clean.isBlank()) clean = baseName;
        int resolvedSeason = season == null || season <= 0 ? 1 : season;
        int resolvedEpisode = episode == null || episode <= 0 ? 0 : episode;
        String normalizedTitle = clean.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        String seriesKey = normalizedTitle + "|" + resolvedSeason;
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toUpperCase(Locale.ROOT);
        return new Entry(UUID.randomUUID().toString(), clean, seriesKey, resolvedSeason, resolvedEpisode,
                fileName, relative, extension, attributes.size(), TIME.format(attributes.lastModifiedTime().toInstant()),
                episode != null && episode > 0);
    }

    /** 文件名只含集号时，用父目录补剧名；Season/第一季等目录继续向上找。 */
    private static String fallbackSeriesTitle(Path root, Path file) {
        Path directory = file.getParent();
        while (directory != null && directory.startsWith(root) && !directory.equals(root)) {
            String name = cleanTitle(directory.getFileName().toString());
            if (!name.isBlank() && seasonOf(name) == null && !GENERIC_EPISODE_TITLE.matcher(name).matches()) {
                return name;
            }
            directory = directory.getParent();
        }
        return "";
    }

    private static Integer seasonOf(String value) {
        Matcher english = ENGLISH_SEASON.matcher(value);
        if (english.find()) return positive(english.group(1));
        Matcher word = SEASON_WORD.matcher(value);
        if (word.find()) return positive(word.group(1));
        Matcher chinese = CHINESE_SEASON.matcher(value);
        if (chinese.find()) return positive(chineseNumber(chinese.group(1)));
        return null;
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

    private static boolean plausibleTrailingEpisode(String value) {
        try {
            int number = Integer.parseInt(value);
            // 结尾的 19xx/20xx 通常是电影年份，不按集号处理。
            return number > 0 && number <= 9999 && !(value.length() == 4 && number >= 1900 && number <= 2099);
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 普通数字只有在标题之后、且不像分辨率/编码时，才作为候选集号。 */
    private static boolean plausibleSeriesEpisode(String value, String before) {
        if (!plausibleTrailingEpisode(value)) return false;
        String preceding = HlsSupport.text(before).toLowerCase(Locale.ROOT);
        if (preceding.matches(".*(?:2160p|1080p|720p|480p|4k|8k|h264|h265|x264|x265|hevc|aac|ddp?).*")) {
            return false;
        }
        String tail = preceding.length() > 8 ? preceding.substring(preceding.length() - 8) : preceding;
        return !tail.matches(".*(?:^|[\\s._-])(?:19|20)\\d{2}\\s*$");
    }

    private static Integer positive(String value) {
        try {
            int number = Integer.parseInt(value);
            return number > 0 && number <= 9999 ? number : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer positive(int value) {
        return value > 0 && value <= 9999 ? value : null;
    }

    private static String cleanTitle(String value) {
        String result = HlsSupport.text(value).trim();
        result = RELEASE_NOISE.matcher(result).replaceAll("");
        // 仅从剧名中剥离季号等结构信息，不用清洁后的标题回填扫描结果。
        result = result.replaceAll("\\[[^\\]]*\\]$", "")
                .replaceAll("[\\[\\](){}]+$", "")
                .replaceAll("[._-]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return result.isBlank() ? HlsSupport.text(value).trim() : result;
    }

    private static int chineseNumber(String value) {
        if (value == null || value.isBlank()) return 0;
        if (value.matches("[0-9]+")) return Integer.parseInt(value);
        if (value.matches("[零〇一二两三四五六七八九]+")) {
            StringBuilder digits = new StringBuilder();
            for (char character : value.toCharArray()) {
                int digit = "零一二三四五六七八九".indexOf(
                        character == '两' ? '二' : character == '〇' ? '零' : character);
                if (digit < 0) return 0;
                digits.append(digit);
            }
            try { return Integer.parseInt(digits.toString()); } catch (NumberFormatException e) { return 0; }
        }
        int result = 0, digit = 0;
        for (char character : value.toCharArray()) {
            int unit = switch (character) { case '十' -> 10; case '百' -> 100; case '千' -> 1000; default -> 0; };
            if (unit > 0) { result += (digit == 0 ? 1 : digit) * unit; digit = 0; }
            else { digit = "零一二三四五六七八九".indexOf(character == '两' ? '二' : character == '〇' ? '零' : character); }
        }
        return result + Math.max(digit, 0);
    }

    @PreDestroy
    public void clearTokens() {
        tokens.clear();
    }
}
