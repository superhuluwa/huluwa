package com.huluwa.console.service.source;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.FanqieLibraryService;
import com.huluwa.console.service.fanqie.FanqieClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 小说 TXT 导出：把书架里一本书的章节拼成单文件，供阅读器或外部设备下载。
 *
 * <p>优先使用已有章节缓存，缺失章节按批补齐并写回缓存，因此中途失败后重新导出只补缺章。
 * 任务在后台线程执行，接口只返回任务 ID 和进度。</p>
 */
@Service
public class BookExportService {

    /** 同时保留的任务记录上限。 */
    private static final int MAX_TASKS = 64;
    /** 批量下载章节数，与 {@link FanqieClient} 的批量接口保持一致。 */
    private static final int BATCH_SIZE = FanqieClient.APP_BATCH_SIZE;
    /** 每批之间的短暂停顿，降低触发上游风控的概率。 */
    private static final long BATCH_INTERVAL_MS = 120;
    /** 已结束任务的保留时长。 */
    private static final Duration KEEP_AFTER_FINISH = Duration.ofDays(1);

    private static final Logger log = LoggerFactory.getLogger(BookExportService.class);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final FanqieLibraryService library;
    private final Path exportRoot;
    private final FanqieClient client = new FanqieClient(Duration.ofSeconds(30), 2);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("book-export").factory());
    private final Map<String, Slot> tasks = new ConcurrentHashMap<>();
    private final AtomicInteger sequence = new AtomicInteger();
    private volatile boolean stopping;

    public BookExportService(FanqieLibraryService library,
                             @Value("${huluwa.data-dir:./data}") String dataDir) {
        this.library = library;
        this.exportRoot = Path.of(dataDir).resolve("exports").toAbsolutePath().normalize();
    }

    /**
     * 导出任务状态。
     *
     * @param state            running / done / failed
     * @param totalChapters    目录总章数
     * @param cachedChapters   启动时已缓存的章数
     * @param missingChapters  本次需要补下载的章数
     * @param processedChapters 本次已处理（成功或失败）的章数，用于算进度
     * @param fileName         完成后可下载的文件名
     */
    public record ExportStatus(String taskId, String bookId, String title, String state,
                               int totalChapters, int cachedChapters, int missingChapters,
                               int processedChapters, String error, String fileName, long size,
                               String updatedAt) {
        public boolean running() { return "running".equals(state); }
        public boolean done() { return "done".equals(state); }
        /** 进度：0..1，没有待下载章节时视为完成。 */
        public double progress() {
            if (missingChapters <= 0) return done() ? 1 : 0;
            return Math.min(1, (double) processedChapters / missingChapters);
        }
    }

    /** 任务槽位：文件名与创建时间在成功前为 null/当前时间。 */
    private record Slot(ExportStatus status, Path file, Instant createdAt) { }

    /** 启动一次导出；同一本书已有进行中的任务时直接复用。 */
    public ExportStatus export(String bookId) {
        if (stopping) throw new BusinessException(503, "服务正在停止，请稍后重试");
        FanqieLibraryService.BookIndex index = library.findIndex(bookId);
        if (index == null) throw new BusinessException(404, "书籍尚未加入书店，请先搜索打开");
        return start(bookId, index);
    }

    /** 指定任务起点；索引由调用方保证已存在。 */
    private ExportStatus start(String bookId, FanqieLibraryService.BookIndex index) {
        for (Slot slot : tasks.values()) {
            if (slot.status().running() && bookId.equals(slot.status().bookId())) return slot.status();
        }
        trim();
        String taskId = "exp-" + sequence.incrementAndGet() + "-"
                + UUID.randomUUID().toString().substring(0, 8);
        Set<String> downloaded = library.downloadedChapterIds(index);
        int missing = index.chapters().size() - downloaded.size();
        ExportStatus initial = new ExportStatus(taskId, bookId, index.title(), "running",
                index.chapters().size(), downloaded.size(), missing, 0, null, null, 0,
                Instant.now().toString());
        tasks.put(taskId, new Slot(initial, null, Instant.now()));
        try {
            worker.execute(() -> run(taskId, bookId));
        } catch (RuntimeException error) {
            // 线程池已关闭（服务停止中）时不要把任务挂在 running 状态。
            ExportStatus failed = withState(initial, "failed", "导出任务暂时无法启动，请重试", null, 0);
            tasks.put(taskId, new Slot(failed, null, Instant.now()));
            throw new BusinessException(503, "导出任务暂时无法启动，请重试");
        }
        return initial;
    }

    public ExportStatus status(String taskId) {
        Slot slot = tasks.get(taskId);
        if (slot == null) throw new BusinessException(404, "导出任务不存在或已过期");
        return slot.status();
    }

    /** 取已完成任务的导出文件；未完成或文件丢失时返回 null。 */
    public Path file(String taskId) {
        Slot slot = tasks.get(taskId);
        if (slot == null || slot.file() == null || !slot.status().done()) return null;
        return Files.isRegularFile(slot.file()) ? slot.file() : null;
    }

    private void run(String taskId, String bookId) {
        Slot slot = tasks.get(taskId);
        if (slot == null) return;
        Path temp = null;
        try {
            FanqieLibraryService.BookIndex index = library.findIndex(bookId);
            if (index == null) throw new BusinessException(404, "书籍已被删除，导出已取消");
            List<FanqieClient.ChapterEntry> chapters = index.chapters();

            // 第一步：补齐缺失章节正文（已缓存的直接跳过）。
            List<String> pending = new ArrayList<>();
            for (FanqieClient.ChapterEntry chapter : chapters) {
                if (!library.chapterDownloaded(bookId, chapter.itemId())) pending.add(chapter.itemId());
            }
            downloadMissing(bookId, pending, taskId);

            // 第二步：按目录顺序拼接成 TXT。
            Files.createDirectories(exportRoot);
            String fileName = safeName(index.title()) + "-" + STAMP.format(LocalDateTime.now()) + ".txt";
            Path target = exportRoot.resolve(fileName);
            temp = Files.createTempFile(exportRoot, ".export-", ".tmp");
            int exported = 0;
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                writer.write("《" + index.title() + "》");
                writer.newLine();
                if (!index.author().isBlank()) {
                    writer.write("作者：" + index.author());
                    writer.newLine();
                }
                writer.newLine();
                for (int i = 0; i < chapters.size(); i++) {
                    ensureRunning();
                    FanqieClient.ChapterEntry chapter = chapters.get(i);
                    String title = chapter.title() == null || chapter.title().isBlank()
                            ? "第 " + (i + 1) + " 章" : chapter.title();
                    String content = library.cachedChapter(bookId, chapter.itemId());
                    writer.write(title);
                    writer.newLine();
                    writer.newLine();
                    if (content == null || content.isBlank()) {
                        writer.write("[本章未能下载，可重新导出补齐]");
                    } else {
                        writer.write(content);
                        exported++;
                    }
                    writer.newLine();
                    writer.newLine();
                }
            }
            moveAtomically(temp, target);
            temp = null;
            Slot current = tasks.get(taskId);
            if (current == null) { Files.deleteIfExists(target); return; }
            ExportStatus done = new ExportStatus(taskId, bookId, index.title(), "done",
                    chapters.size(), exported, 0, 0, null, fileName, Files.size(target),
                    Instant.now().toString());
            tasks.put(taskId, new Slot(done, target, current.createdAt()));
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            fail(taskId, "导出已中断，已缓存章节保留，可重试", temp);
        } catch (BusinessException error) {
            fail(taskId, error.getMessage(), temp);
        } catch (IOException error) {
            log.warn("番茄小说 {} 导出失败：{}", bookId, error.getMessage());
            fail(taskId, "写入导出文件失败，请检查数据目录空间", temp);
        } catch (RuntimeException error) {
            log.warn("番茄小说 {} 导出异常：{}", bookId, error.getMessage());
            fail(taskId, "导出失败，请稍后重试", temp);
        }
    }

    /** 按批补齐缺失章节，每批结束后刷新进度；单批失败只跳过该批。 */
    private void downloadMissing(String bookId, List<String> pending, String taskId)
            throws IOException, InterruptedException {
        int total = pending.size();
        int finished = 0;
        for (int i = 0; i < total; i += BATCH_SIZE) {
            ensureRunning();
            List<String> batch = List.copyOf(pending.subList(i, Math.min(i + BATCH_SIZE, total)));
            try {
                Map<String, FanqieClient.Fetched> got = remote(() -> client.chaptersBatch(batch));
                for (String itemId : batch) {
                    FanqieClient.Fetched chapter = got.get(itemId);
                    if (chapter == null || chapter.error() != null || chapter.text().isBlank()) continue;
                    try {
                        library.cacheChapter(bookId, itemId, chapter.text());
                    } catch (BusinessException ignore) {
                        // 目录已变化时跳过该章，不影响其余章节。
                    }
                }
            } catch (IOException error) {
                // 单批失败不终止整本导出：剩余批次继续，失败章节留到下次导出补齐。
                log.warn("番茄小说 {} 批量下载第 {}/{} 章失败：{}",
                        bookId, i + 1, total, error.getMessage());
            }
            finished = Math.min(i + batch.size(), total);
            publishProgress(taskId, finished);
            Thread.sleep(BATCH_INTERVAL_MS);
        }
    }

    /** 运行中只推进「已处理章数」，其余字段保持不变。 */
    private void publishProgress(String taskId, int processed) {
        // 任务可能已被 trim 清掉（例如服务停止），此时不能再写回，否则会留下孤儿记录。
        tasks.computeIfPresent(taskId, (id, slot) -> {
            ExportStatus base = slot.status();
            ExportStatus next = new ExportStatus(base.taskId(), base.bookId(), base.title(), base.state(),
                    base.totalChapters(), base.cachedChapters(), base.missingChapters(), processed,
                    base.error(), base.fileName(), base.size(), Instant.now().toString());
            return new Slot(next, slot.file(), slot.createdAt());
        });
    }

    private void fail(String taskId, String message, Path temp) {
        if (temp != null) {
            try { Files.deleteIfExists(temp); } catch (IOException ignore) { }
        }
        Slot slot = tasks.get(taskId);
        if (slot == null) return;
        ExportStatus failed = withState(slot.status(), "failed", message, null, 0);
        tasks.put(taskId, new Slot(failed, null, slot.createdAt()));
    }

    private static ExportStatus withState(ExportStatus base, String state, String error,
                                          String fileName, long size) {
        return new ExportStatus(base.taskId(), base.bookId(), base.title(), state,
                base.totalChapters(), base.cachedChapters(), base.missingChapters(),
                base.processedChapters(), error, fileName, size, Instant.now().toString());
    }

    /** 清理超额且已结束超过保留期的任务记录和文件。 */
    private void trim() {
        if (tasks.size() < MAX_TASKS) return;
        // 按创建时间从旧到新清理，避免误删刚完成、用户还没下载的任务。
        List<Map.Entry<String, Slot>> expired = new ArrayList<>(tasks.entrySet());
        expired.sort((a, b) -> a.getValue().createdAt().compareTo(b.getValue().createdAt()));
        Instant threshold = Instant.now().minus(KEEP_AFTER_FINISH);
        for (Map.Entry<String, Slot> entry : expired) {
            if (tasks.size() < MAX_TASKS) break;
            Slot slot = entry.getValue();
            if (slot.status().running() || slot.createdAt().isAfter(threshold)) continue;
            if (slot.file() != null) {
                try { Files.deleteIfExists(slot.file()); } catch (IOException ignore) { }
            }
            tasks.remove(entry.getKey());
        }
    }

    private static String safeName(String title) {
        String name = title == null ? "" : title.replaceAll("[\\\\/:*?\"<>|\\r\\n]", "_").trim();
        if (name.isEmpty()) name = "未命名";
        return name.length() > 80 ? name.substring(0, 80) : name;
    }

    private static void moveAtomically(Path temp, Path target) throws IOException {
        try {
            Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException error) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    @FunctionalInterface
    private interface RemoteCall<T> { T get() throws IOException, InterruptedException; }

    /** 设备失效时重建会话并重试一次。 */
    private <T> T remote(RemoteCall<T> call) throws IOException, InterruptedException {
        try {
            return call.get();
        } catch (IOException | RuntimeException error) {
            client.resetSession();
            return call.get();
        }
    }

    private void ensureRunning() throws InterruptedException {
        if (stopping || Thread.currentThread().isInterrupted()) throw new InterruptedException("导出已停止");
    }

    @PreDestroy
    public void close() {
        stopping = true;
        worker.shutdownNow();
    }
}
