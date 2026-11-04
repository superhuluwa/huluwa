package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.fanqie.FanqieClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/** 番茄小说书库：缓存目录，正文按章获取；失败章节保留原序号，可单独重试。 */
@Service
public class FanqieLibraryService {
    private static final Logger log = LoggerFactory.getLogger(FanqieLibraryService.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json = new ObjectMapper();
    private final Path root;
    // 交互阅读不使用整本下载的长时间退避，失败交给阅读页重试。
    private final FanqieClient client = new FanqieClient(Duration.ofSeconds(10), 0);
    private final ReentrantLock remoteLock = new ReentrantLock();
    private final Object[] bookLocks = new Object[32];
    private final Map<String, BookIndex> cache = new LinkedHashMap<>(64, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, BookIndex> eldest) {
            return size() > 64;
        }
    };

    public FanqieLibraryService(JdbcTemplate jdbc, @Value("${huluwa.data-dir:./data}") String dataDir) {
        this.jdbc = jdbc;
        this.root = Path.of(dataDir).resolve("fanqie").toAbsolutePath().normalize();
        for (int i = 0; i < bookLocks.length; i++) bookLocks[i] = new Object();
    }

    public record BookIndex(String bookId, String title, String author, String cover,
                            List<FanqieClient.ChapterEntry> chapters, long updatedAt, Boolean inShelf) {
        public BookIndex {
            // 旧版本缓存的目录没有 inShelf 字段，按已加入书架处理，避免升级后书架清空。
            if (inShelf == null) inShelf = true;
        }
    }
    public record BookSummary(String bookId, String title, String author, String cover,
                              int chapterCount, long totalChars, long updatedAt, int progressChapter,
                              int downloadedChapterCount, boolean inShelf) { }
    public record ReadProgress(int order, int paragraphIndex, int scrollOffset, long updatedAt) { }

    /** 只准备目录即可进入阅读，无需等整本下载；重开书籍时更新连载目录。 */
    public BookSummary open(String bookId, String title, String author, String cover) {
        return open(bookId, title, author, cover, true);
    }

    /**
     * 打开书籍并缓存目录。
     *
     * <p>阅读页进入时传 {@code addToShelf=false}：目录仍然落盘（阅读、导出、书源都依赖它），
     * 但书籍不会出现在书架里，是否收藏由阅读页右上角的「加入书架」决定。</p>
     */
    public BookSummary open(String bookId, String title, String author, String cover, boolean addToShelf) {
        validateId(bookId);
        synchronized (bookLock(bookId)) {
            BookIndex existing = loadIndex(bookId);
            List<FanqieClient.ChapterEntry> chapters;
            try {
                chapters = normalizeChapters(remote(() -> client.catalog(bookId)));
            } catch (BusinessException error) {
                // 已缓存的书即使上游暂时不可达，也可以继续阅读。
                if (existing == null) throw error;
                chapters = existing.chapters();
                log.info("番茄小说 {} 暂时沿用缓存目录：{}", bookId, error.getMessage());
            }
            // 只读打开时沿用原有的书架状态，避免刷新目录顺手把书加进书架。
            boolean inShelf = addToShelf || (existing != null && existing.inShelf());
            BookIndex index = new BookIndex(bookId,
                    metadata(title, existing == null ? "书号 " + bookId : existing.title(), 300),
                    metadata(author, existing == null ? "" : existing.author(), 300),
                    metadata(cover, existing == null ? "" : existing.cover(), 2048),
                    List.copyOf(chapters), System.currentTimeMillis(), inShelf);
            writeIndex(index);
            return summary(index);
        }
    }

    /** 加入书架：目录缺失时先打开书籍，已有目录只翻转书架标记。 */
    public BookSummary addToShelf(String bookId, String title, String author, String cover) {
        validateId(bookId);
        synchronized (bookLock(bookId)) {
            BookIndex existing = loadIndex(bookId);
            if (existing == null) return open(bookId, title, author, cover, true);
            if (existing.inShelf()) return summary(existing);
            BookIndex updated = new BookIndex(existing.bookId(), existing.title(), existing.author(), existing.cover(),
                    existing.chapters(), existing.updatedAt(), true);
            writeIndex(updated);
            return summary(updated);
        }
    }

    /** 移出书架：保留目录与正文缓存，只把书籍从书架列表隐藏，下次打开仍可续读。 */
    public BookSummary removeFromShelf(String bookId) {
        validateId(bookId);
        synchronized (bookLock(bookId)) {
            BookIndex existing = requireIndex(bookId);
            if (!existing.inShelf()) return summary(existing);
            BookIndex updated = new BookIndex(existing.bookId(), existing.title(), existing.author(), existing.cover(),
                    existing.chapters(), existing.updatedAt(), false);
            writeIndex(updated);
            return summary(updated);
        }
    }

    /** 书架列表只返回已加入书架的书籍；未收藏的阅读缓存不展示。 */
    public List<BookSummary> list() {
        return allBooks().stream().filter(BookSummary::inShelf).toList();
    }

    /** 最近打开或阅读过的书（含未加入书架的），用于会话里的「继续阅读」。 */
    public List<BookSummary> recent() {
        return allBooks();
    }

    private List<BookSummary> allBooks() {
        List<BookSummary> books = new ArrayList<>();
        if (!Files.isDirectory(root)) return books;
        try (var dirs = Files.list(root)) {
            for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                String bookId = dir.getFileName().toString();
                if (!validId(bookId)) continue;
                BookIndex index = loadIndex(bookId);
                if (index != null) books.add(summary(index));
            }
        } catch (IOException error) {
            throw new BusinessException(500, "读取书架失败，请稍后重试");
        }
        books.sort(Comparator.comparingLong(BookSummary::updatedAt).reversed());
        return books;
    }

    public record DeleteResult(List<String> deletedIds, Map<String, String> failures) { }

    /** 批量操作逐本返回结果，某一本清理失败不会掩盖其他书籍的删除结果。 */
    public DeleteResult deleteBooks(List<String> bookIds) {
        if (bookIds == null || bookIds.isEmpty() || bookIds.size() > 500) {
            throw new BusinessException(400, "请选择 1 至 500 本小说");
        }
        bookIds.forEach(FanqieLibraryService::validateId);
        List<String> deleted = new ArrayList<>();
        Map<String, String> failures = new LinkedHashMap<>();
        for (String bookId : new LinkedHashSet<>(bookIds)) {
            synchronized (bookLock(bookId)) {
                try {
                    Path dir = bookDir(bookId);
                    if (Files.exists(dir)) {
                        // 先清理正文，最后移除目录索引；中途失败仍可从书架重试。
                        if (Files.isSymbolicLink(dir)) {
                            Files.delete(dir);
                        } else {
                            Path catalog = dir.resolve("catalog.json");
                            try (var paths = Files.walk(dir)) {
                                for (Path path : paths.filter(p -> !p.equals(dir) && !p.equals(catalog))
                                        .sorted(Comparator.reverseOrder()).toList()) {
                                    Files.deleteIfExists(path);
                                }
                            }
                            Files.deleteIfExists(catalog);
                            Files.deleteIfExists(dir);
                        }
                    }
                    jdbc.update("DELETE FROM fanqie_read_progress WHERE book_id = ?", bookId);
                    deleted.add(bookId);
                } catch (IOException | RuntimeException error) {
                    log.warn("删除番茄小说 {} 失败", bookId, error);
                    failures.put(bookId, "删除失败，请重试");
                } finally {
                    synchronized (cache) { cache.remove(bookId); }
                }
            }
        }
        return new DeleteResult(List.copyOf(deleted), Map.copyOf(failures));
    }

    /** 读取完整的一章；GET 不推进阅读进度，避免取消请求或目录加载覆盖续读位置。 */
    public Map<String, Object> read(String bookId, int order) {
        validateId(bookId);
        synchronized (bookLock(bookId)) {
            BookIndex index = requireIndex(bookId);
            ReadProgress progress = progress(bookId);
            int current = order <= 0 ? Math.max(1, Math.min(progress.order(), index.chapters().size())) : order;
            if (current < 1 || current > index.chapters().size()) {
                throw new BusinessException(400, "章节不存在，请刷新目录后重试");
            }
            FanqieClient.ChapterEntry entry = index.chapters().get(current - 1);
            Path file = bookDir(bookId).resolve(entry.itemId() + ".txt");
            String content;
            try {
                // 旧缓存可能还带有数字实体，读取时还原即可，无需重新下载章节。
                content = Files.isRegularFile(file)
                        ? FanqieClient.decodeHtmlEntities(Files.readString(file, StandardCharsets.UTF_8)) : "";
                if (content.isBlank()) {
                    content = remote(() -> client.chapter(entry.itemId())).text();
                    if (content.isBlank()) throw new BusinessException(502, "当前章节暂时无法获取，请重试");
                    atomicWrite(file, content);
                }
            } catch (IOException error) {
                log.warn("番茄小说 {} 第 {} 章缓存失败", bookId, current, error);
                throw new BusinessException(500, "读取或保存章节失败，请重试");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("bookId", bookId);
            result.put("title", index.title());
            result.put("author", index.author());
            result.put("order", current);
            result.put("chapterTitle", chapterTitle(entry, current));
            result.put("content", content);
            result.put("chapterCount", index.chapters().size());
            result.put("hasPrev", current > 1);
            result.put("hasNext", current < index.chapters().size());
            result.put("prevChapter", current > 1 ? chapterTitle(index.chapters().get(current - 2), current - 1) : "");
            result.put("nextChapter", current < index.chapters().size() ? chapterTitle(index.chapters().get(current), current + 1) : "");
            result.put("paragraphIndex", order <= 0 && progress.order() == current ? progress.paragraphIndex() : 0);
            result.put("scrollOffset", order <= 0 && progress.order() == current ? progress.scrollOffset() : 0);
            return result;
        }
    }

    public Map<String, Object> catalog(String bookId) {
        BookIndex index = requireIndex(bookId);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < index.chapters().size(); i++) {
            items.add(Map.of("order", i + 1, "title", chapterTitle(index.chapters().get(i), i + 1)));
        }
        return Map.of("bookId", bookId, "title", index.title(), "chapterCount", items.size(),
                "progressChapter", progressChapter(bookId), "chapters", items);
    }

    public int progressChapter(String bookId) { return progress(bookId).order(); }

    private ReadProgress progress(String bookId) {
        validateId(bookId);
        List<ReadProgress> rows = jdbc.query("""
                SELECT chapter_order, paragraph_index, scroll_offset, updated_at
                  FROM fanqie_read_progress WHERE book_id = ?
                """, (rs, row) -> new ReadProgress(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getLong(4)), bookId);
        return rows.isEmpty() ? new ReadProgress(0, 0, 0, 0) : rows.getFirst();
    }

    /** 由客户端在正文实际显示、滚动停下或离开阅读页时保存。 */
    public void saveProgress(String bookId, int order, int paragraphIndex, int scrollOffset) {
        validateId(bookId);
        synchronized (bookLock(bookId)) {
            BookIndex index = requireIndex(bookId);
            if (order < 1 || order > index.chapters().size() || paragraphIndex < 0 || paragraphIndex > 100000
                    || scrollOffset < 0 || scrollOffset > 100000) {
                throw new BusinessException(400, "阅读位置不正确");
            }
            jdbc.update("""
                    INSERT INTO fanqie_read_progress (book_id, chapter_order, paragraph_index, scroll_offset, updated_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(book_id) DO UPDATE SET chapter_order = excluded.chapter_order,
                        paragraph_index = excluded.paragraph_index, scroll_offset = excluded.scroll_offset,
                        updated_at = excluded.updated_at
                    """, bookId, order, paragraphIndex, scrollOffset, System.currentTimeMillis());
        }
    }

    private BookSummary summary(BookIndex index) {
        ReadProgress progress = progress(index.bookId());
        long words = index.chapters().stream().mapToLong(FanqieClient.ChapterEntry::wordNumber).sum();
        return new BookSummary(index.bookId(), index.title(), index.author(), index.cover(), index.chapters().size(),
                words, Math.max(index.updatedAt(), progress.updatedAt()), progress.order(),
                downloadedChapterIds(index).size(), index.inShelf());
    }

    /** 更新任务只短暂持有书籍锁，网络请求由独立后台客户端完成。 */
    BookIndex indexForUpdate(String bookId) { return requireIndex(bookId); }

    /** 书源使用：书号非法或书籍不在书架时返回 null，不抛异常。 */
    public BookIndex findIndex(String bookId) {
        if (!validId(bookId)) return null;
        try { return loadIndex(bookId); }
        catch (RuntimeException error) { return null; }
    }

    /** 书源使用：读取已缓存的一章正文，未缓存返回 null。 */
    public String cachedChapter(String bookId, String itemId) {
        if (!validId(bookId) || !validId(itemId)) return null;
        Path file = bookDir(bookId).resolve(itemId + ".txt");
        try {
            if (!Files.isRegularFile(file) || Files.size(file) == 0) return null;
            return FanqieClient.decodeHtmlEntities(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException error) {
            log.warn("读取番茄小说 {} 章节 {} 缓存失败：{}", bookId, itemId, error.getMessage());
            return null;
        }
    }

    BookIndex refreshCatalog(String bookId, List<FanqieClient.ChapterEntry> chapters) {
        validateId(bookId);
        List<FanqieClient.ChapterEntry> normalized = normalizeChapters(chapters);
        synchronized (bookLock(bookId)) {
            BookIndex existing = requireIndex(bookId);
            BookIndex updated = new BookIndex(bookId, existing.title(), existing.author(), existing.cover(),
                    normalized, existing.updatedAt(), existing.inShelf());
            writeIndex(updated);
            return updated;
        }
    }

    public Set<String> downloadedChapterIds(BookIndex index) {
        Set<String> validIds = index.chapters().stream().map(FanqieClient.ChapterEntry::itemId)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> downloaded = new LinkedHashSet<>();
        Path dir = bookDir(index.bookId());
        if (!Files.isDirectory(dir)) return downloaded;
        try (var files = Files.list(dir)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".txt")) continue;
                String itemId = name.substring(0, name.length() - 4);
                if (validIds.contains(itemId) && Files.isRegularFile(file) && Files.size(file) > 0) downloaded.add(itemId);
            }
            return downloaded;
        } catch (IOException error) {
            throw new BusinessException(500, "读取已下载章节数量失败，请重试");
        }
    }

    public boolean chapterDownloaded(String bookId, String itemId) {
        validateId(bookId);
        validateId(itemId);
        Path file = bookDir(bookId).resolve(itemId + ".txt");
        try { return Files.isRegularFile(file) && Files.size(file) > 0; }
        catch (IOException error) { throw new BusinessException(500, "读取章节缓存失败，请重试"); }
    }

    public boolean cacheChapter(String bookId, String itemId, String content) {
        validateId(bookId);
        validateId(itemId);
        if (content == null || content.isBlank()) throw new BusinessException(502, "章节正文为空，请重试");
        synchronized (bookLock(bookId)) {
            BookIndex current = requireIndex(bookId);
            if (current.chapters().stream().noneMatch(c -> c.itemId().equals(itemId))) {
                throw new BusinessException(409, "章节目录已变更，请重新检查更新");
            }
            if (chapterDownloaded(bookId, itemId)) return false;
            try { atomicWrite(bookDir(bookId).resolve(itemId + ".txt"), content); }
            catch (IOException error) { throw new BusinessException(500, "保存章节失败，请重试"); }
            return true;
        }
    }

    private static List<FanqieClient.ChapterEntry> normalizeChapters(List<FanqieClient.ChapterEntry> chapters) {
        List<FanqieClient.ChapterEntry> normalized = chapters.stream()
                .filter(c -> validId(c.itemId()))
                .sorted(Comparator.comparingLong(FanqieClient.ChapterEntry::order))
                .collect(java.util.stream.Collectors.toMap(FanqieClient.ChapterEntry::itemId,
                        c -> c, (first, duplicate) -> first, LinkedHashMap::new))
                .values().stream().toList();
        if (normalized.isEmpty()) throw new BusinessException(502, "这本书暂无可阅读章节，请稍后重试");
        return normalized;
    }

    private BookIndex requireIndex(String bookId) {
        BookIndex index = loadIndex(bookId);
        if (index == null) throw new BusinessException(404, "请先从搜索结果打开这本小说");
        return index;
    }

    private BookIndex loadIndex(String bookId) {
        validateId(bookId);
        synchronized (bookLock(bookId)) {
            synchronized (cache) {
                BookIndex index = cache.get(bookId);
                if (index != null) return index;
            }
            Path file = bookDir(bookId).resolve("catalog.json");
            if (!Files.isRegularFile(file)) return null;
            try {
                BookIndex index = json.readValue(Files.readString(file, StandardCharsets.UTF_8), BookIndex.class);
                if (!bookId.equals(index.bookId()) || index.chapters() == null || index.chapters().isEmpty()
                        || index.chapters().stream().anyMatch(c -> !validId(c.itemId()))) return null;
                synchronized (cache) { cache.put(bookId, index); }
                return index;
            } catch (IOException | RuntimeException error) {
                log.warn("读取番茄小说 {} 的目录缓存失败：{}", bookId, error.getMessage());
                return null;
            }
        }
    }

    private void writeIndex(BookIndex index) {
        try {
            atomicWrite(bookDir(index.bookId()).resolve("catalog.json"), json.writeValueAsString(index));
            synchronized (cache) { cache.put(index.bookId(), index); }
        } catch (IOException error) {
            throw new BusinessException(500, "保存小说目录失败，请重试");
        }
    }

    private static void atomicWrite(Path target, String content) throws IOException {
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), ".fanqie-", ".tmp");
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8);
            try {
                Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException error) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally { Files.deleteIfExists(temp); }
    }

    @FunctionalInterface
    private interface RemoteCall<T> { T get() throws IOException, InterruptedException; }

    /** 共用设备串行访问，快速拒绝排队；密钥或配额失败时最多重建一次。 */
    private <T> T remote(RemoteCall<T> call) {
        boolean locked = false;
        try {
            locked = remoteLock.tryLock(1, TimeUnit.SECONDS);
            if (!locked) throw new BusinessException(503, "小说服务正在加载其他章节，请稍后重试");
            try {
                return call.get();
            } catch (IOException | RuntimeException error) {
                if (error instanceof BusinessException business) throw business;
                client.resetSession();
                return call.get();
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "小说加载已中断，请重试");
        } catch (IOException | RuntimeException error) {
            if (error instanceof BusinessException business) throw business;
            client.resetSession();
            log.warn("番茄小说接口暂时不可用：{}", error.getMessage());
            throw new BusinessException(502, "小说服务暂时无法加载，请稍后重试");
        } finally { if (locked) remoteLock.unlock(); }
    }

    private Object bookLock(String bookId) { return bookLocks[(bookId.hashCode() & Integer.MAX_VALUE) % bookLocks.length]; }
    private Path bookDir(String bookId) { return root.resolve(bookId); }
    private static boolean validId(String id) { return id != null && id.matches("[0-9]{6,24}"); }
    private static void validateId(String id) {
        if (!validId(id)) throw new BusinessException(400, "小说书号不正确");
    }
    private static String metadata(String value, String fallback, int limit) {
        if (value == null || value.isBlank()) return fallback;
        String trimmed = value.trim();
        return trimmed.substring(0, Math.min(trimmed.length(), limit));
    }
    private static String chapterTitle(FanqieClient.ChapterEntry entry, int order) {
        return entry.title() == null || entry.title().isBlank() ? "第 " + order + " 章" : entry.title();
    }
}
