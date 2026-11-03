package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.fanqie.FanqieClient;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** 书架一键更新：单任务后台下载，已有正文跳过，离开客户端不影响任务。 */
@Service
public class FanqieShelfUpdateService {
    private static final Logger log = LoggerFactory.getLogger(FanqieShelfUpdateService.class);
    private final FanqieLibraryService library;
    // 后台下载使用独立设备会话，不占用交互阅读的远程请求锁。
    private final FanqieClient client = new FanqieClient(Duration.ofSeconds(10), 0);
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("fanqie-shelf-update").factory());
    private volatile boolean stopping;
    private volatile UpdateStatus status = new UpdateStatus(0, false, 0, 0, "", "", List.of(), null);

    public FanqieShelfUpdateService(FanqieLibraryService library) { this.library = library; }

    public record BookUpdate(String bookId, String title, int chapterCount, int downloadedChapterCount,
                             int newChapters, int downloadedChapters, int failedChapters, String error) {
        BookUpdate withError(String message) {
            return new BookUpdate(bookId, title, chapterCount, downloadedChapterCount,
                    newChapters, downloadedChapters, failedChapters, message);
        }
    }
    public record UpdateStatus(long startedAt, boolean running, int totalBooks, int checkedBooks,
                               String currentBookId, String currentTitle, List<BookUpdate> books, String error) { }

    public UpdateStatus status() { return status; }

    public synchronized UpdateStatus checkUpdates() {
        if (stopping) throw new BusinessException(503, "服务正在停止，请稍后重试");
        if (status.running()) return status;
        long startedAt = Math.max(System.currentTimeMillis(), status.startedAt() + 1);
        status = new UpdateStatus(startedAt, true, 0, 0, "", "", List.of(), null);
        try { worker.execute(() -> run(startedAt)); }
        catch (RejectedExecutionException error) {
            status = new UpdateStatus(startedAt, false, 0, 0, "", "", List.of(), "更新任务暂时无法启动，请重试");
            throw new BusinessException(503, status.error());
        }
        return status;
    }

    private void run(long startedAt) {
        Map<String, BookUpdate> results = new LinkedHashMap<>();
        int total = 0;
        int checked = 0;
        String failure = null;
        try {
            List<FanqieLibraryService.BookSummary> books = library.list();
            total = books.size();
            for (var book : books) {
                ensureRunning();
                results.put(book.bookId(), new BookUpdate(book.bookId(), book.title(), book.chapterCount(),
                        book.downloadedChapterCount(), 0, 0, 0, null));
                publish(startedAt, true, total, checked, book.bookId(), book.title(), results, null);
                int completed = checked;
                int bookCount = total;
                try {
                    BookUpdate result = updateBook(book.bookId(), update -> {
                        results.put(book.bookId(), update);
                        publish(startedAt, true, bookCount, completed, book.bookId(), book.title(), results, null);
                    });
                    results.put(book.bookId(), result);
                } catch (InterruptedException error) { throw error; }
                catch (Exception error) {
                    log.warn("番茄小说 {} 检查更新失败：{}", book.bookId(), error.getMessage());
                    String message = error instanceof BusinessException ? error.getMessage() : "检查更新失败，请重试";
                    results.put(book.bookId(), results.get(book.bookId()).withError(message));
                }
                checked++;
                publish(startedAt, true, total, checked, "", "", results, null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failure = "更新已中断，已下载章节保留，可再次检查更新";
        } catch (Exception error) {
            log.warn("番茄小说书架更新失败：{}", error.getMessage());
            failure = error instanceof BusinessException ? error.getMessage() : "检查更新失败，请重试";
        } finally {
            publish(startedAt, false, total, checked, "", "", results, failure);
        }
    }

    private BookUpdate updateBook(String bookId, Consumer<BookUpdate> progress) throws IOException, InterruptedException {
        var previous = library.indexForUpdate(bookId);
        Set<String> oldIds = previous.chapters().stream().map(FanqieClient.ChapterEntry::itemId).collect(Collectors.toSet());
        var index = library.refreshCatalog(bookId, remote(() -> client.catalog(bookId)));
        Set<String> downloaded = library.downloadedChapterIds(index);
        int added = (int) index.chapters().stream().filter(c -> !oldIds.contains(c.itemId())).count();
        var pending = new ArrayList<>(index.chapters().stream().filter(c -> !downloaded.contains(c.itemId())).toList());
        // 优先把最新章节准备好，再逐步补齐旧章节。
        Collections.reverse(pending);
        int saved = 0;
        int failed = 0;
        int consecutiveFailures = 0;
        String lastError = null;
        BookUpdate result = new BookUpdate(bookId, index.title(), index.chapters().size(), downloaded.size(), added, 0, 0, null);
        progress.accept(result);
        for (int i = 0; i < pending.size(); i++) {
            ensureRunning();
            var chapter = pending.get(i);
            try {
                if (!library.chapterDownloaded(bookId, chapter.itemId())) {
                    String text = remote(() -> client.chapter(chapter.itemId())).text();
                    if (library.cacheChapter(bookId, chapter.itemId(), text)) saved++;
                }
                downloaded.add(chapter.itemId());
                consecutiveFailures = 0;
            } catch (InterruptedException error) { throw error; }
            catch (IOException | RuntimeException error) {
                // 删除书目、目录变化或磁盘错误时终止本书，避免恢复已删除的书籍。
                if (error instanceof BusinessException business &&
                        (business.getCode() == 404 || business.getCode() == 409 || business.getCode() == 500)) throw business;
                log.warn("番茄小说 {} 章节 {} 更新下载失败：{}", bookId, chapter.itemId(), error.getMessage());
                failed++;
                consecutiveFailures++;
                lastError = "部分章节下载失败，可再次检查更新重试";
                if (consecutiveFailures >= 3) {
                    failed += pending.size() - i - 1;
                    lastError = "连续下载失败，剩余章节可再次检查更新重试";
                }
            }
            result = new BookUpdate(bookId, index.title(), index.chapters().size(), downloaded.size(),
                    added, saved, failed, lastError);
            progress.accept(result);
            if (consecutiveFailures >= 3) break;
            Thread.sleep(100);
        }
        return result;
    }

    private void publish(long startedAt, boolean running, int total, int checked, String bookId, String title,
                         Map<String, BookUpdate> results, String error) {
        status = new UpdateStatus(startedAt, running, total, checked, bookId, title, List.copyOf(results.values()), error);
    }

    @FunctionalInterface
    private interface RemoteCall<T> { T get() throws IOException, InterruptedException; }

    private <T> T remote(RemoteCall<T> call) throws IOException, InterruptedException {
        try { return call.get(); }
        catch (IOException | RuntimeException error) {
            client.resetSession();
            return call.get();
        }
    }

    private void ensureRunning() throws InterruptedException {
        if (stopping || Thread.currentThread().isInterrupted()) throw new InterruptedException("书架更新已停止");
    }

    @PreDestroy
    public void close() {
        stopping = true;
        worker.shutdownNow();
    }
}
