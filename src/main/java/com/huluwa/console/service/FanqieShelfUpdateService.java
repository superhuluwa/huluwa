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

/** 书架后台下载：支持整架更新与单本完整缓存，已有正文跳过，离开客户端不影响任务。 */
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
        return start(null);
    }

    /** 单本下载刷新目录并补齐全部正文；同一本书的重复请求复用正在运行的任务。 */
    public synchronized UpdateStatus downloadBook(String bookId) {
        if (stopping) throw new BusinessException(503, "服务正在停止，请稍后重试");
        if (status.running()) {
            if (status.totalBooks() == 1 && status.books().stream().anyMatch(book -> book.bookId().equals(bookId))) return status;
            throw new BusinessException(409, "已有小说正在下载，请完成后再试");
        }
        var index = library.indexForUpdate(bookId);
        return start(new BookUpdate(bookId, index.title(), index.chapters().size(),
                library.downloadedChapterIds(index).size(), 0, 0, 0, null));
    }

    private UpdateStatus start(BookUpdate book) {
        long startedAt = Math.max(System.currentTimeMillis(), status.startedAt() + 1);
        int total = book == null ? 0 : 1;
        List<BookUpdate> books = book == null ? List.of() : List.of(book);
        status = new UpdateStatus(startedAt, true, total, 0,
                book == null ? "" : book.bookId(), book == null ? "" : book.title(), books, null);
        try { worker.execute(() -> run(startedAt, book)); }
        catch (RejectedExecutionException error) {
            status = new UpdateStatus(startedAt, false, total, 0, "", "", books, "下载任务暂时无法启动，请重试");
            throw new BusinessException(503, status.error());
        }
        return status;
    }

    private void run(long startedAt, BookUpdate requestedBook) {
        Map<String, BookUpdate> results = new LinkedHashMap<>();
        int total = 0;
        int checked = 0;
        String failure = null;
        try {
            List<BookUpdate> books = requestedBook == null ? library.list().stream()
                    .map(book -> new BookUpdate(book.bookId(), book.title(), book.chapterCount(),
                            book.downloadedChapterCount(), 0, 0, 0, null)).toList() : List.of(requestedBook);
            total = books.size();
            for (var book : books) {
                ensureRunning();
                results.put(book.bookId(), book);
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
                    String message = error instanceof BusinessException ? error.getMessage() : "下载失败，请重试";
                    results.put(book.bookId(), results.get(book.bookId()).withError(message));
                }
                checked++;
                publish(startedAt, true, total, checked, "", "", results, null);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            failure = "下载已中断，已缓存章节保留，可重试下载";
        } catch (Exception error) {
            log.warn("番茄小说书架更新失败：{}", error.getMessage());
            failure = error instanceof BusinessException ? error.getMessage() : "下载失败，请重试";
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
                lastError = "部分章节下载失败，可重试下载";
                if (consecutiveFailures >= 3) {
                    failed += pending.size() - i - 1;
                    lastError = "连续下载失败，剩余章节可重试下载";
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
