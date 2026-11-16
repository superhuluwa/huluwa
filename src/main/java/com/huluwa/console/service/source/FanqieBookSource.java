package com.huluwa.console.service.source;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.FanqieLibraryService;
import com.huluwa.console.service.FanqieSearchService;
import com.huluwa.console.service.fanqie.FanqieChapterParser;
import com.huluwa.console.service.fanqie.FanqieClient;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 番茄小说书源：把现有的搜索、目录、正文能力映射到统一书源契约。
 *
 * <p>全部复用已缓存的设备会话和章节缓存，书源接口本身不新增抓取链路。</p>
 */
@Component
public class FanqieBookSource implements BookSource {

    public static final String ID = "fanqie";

    private final FanqieSearchService searchService;
    private final FanqieLibraryService library;
    /** 按书号串行化「首次打开」，避免并发请求重复拉取上游目录。 */
    private final ConcurrentHashMap<String, Object> openLocks = new ConcurrentHashMap<>();

    public FanqieBookSource(FanqieSearchService searchService, FanqieLibraryService library) {
        this.searchService = searchService;
        this.library = library;
    }

    @Override public String id() { return ID; }

    @Override public String name() { return "番茄小说"; }

    @Override
    public SearchResult search(String keyword, String cursor, int size) {
        FanqieClient.SearchPage page = searchService.searchPage(keyword, cursor, size);
        List<BookInfo> books = new ArrayList<>(page.books().size());
        for (FanqieClient.Book book : page.books()) {
            // 只有高亮文本、没有书号的结果无法定位书籍，书源里必须过滤掉。
            if (book.bookId() == null || book.bookId().isBlank()) continue;
            books.add(toBookInfo(book));
        }
        return new SearchResult(List.copyOf(books), page.cursor());
    }

    @Override
    public BookInfo book(String bookId) {
        FanqieLibraryService.BookIndex index = library.findIndex(bookId);
        if (index != null) {
            // 已加入书架：目录缓存里的书名、简介最可靠，且不需要再访问上游。
            FanqieClient.Book cached = searchService.findCached(bookId);
            FanqieClient.ChapterEntry last = index.chapters().isEmpty()
                    ? null : index.chapters().getLast();
            long words = index.chapters().stream().mapToLong(FanqieClient.ChapterEntry::wordNumber).sum();
            return new BookInfo(index.bookId(), index.title(), index.author(),
                    cached == null ? "" : cached.abstractText(),
                    cached == null ? "" : cached.category(), index.cover(),
                    last == null ? "" : last.title(), words,
                    cached == null ? null : cached.finished());
        }
        FanqieClient.Book detail = searchService.describe(bookId);
        return detail == null ? null : toBookInfo(detail);
    }

    @Override
    public List<Chapter> chapters(String bookId) {
        FanqieLibraryService.BookIndex index = prepare(bookId);
        // 短篇常把多章合并为一个上游条目，需要从完整正文补出可跳转的目录。
        if (index.chapters().size() == 1) {
            List<FanqieChapterParser.Part> parts;
            try {
                parts = FanqieChapterParser.split(readContent(bookId, index, 0));
            } catch (BusinessException error) {
                // 正文暂时不可用时仍返回上游目录，保留稍后重试阅读的入口。
                parts = List.of();
            }
            if (!parts.isEmpty()) {
                String itemId = index.chapters().getFirst().itemId();
                List<Chapter> chapters = new ArrayList<>(parts.size());
                for (int i = 0; i < parts.size(); i++) {
                    chapters.add(new Chapter(itemId + "-" + (i + 1), parts.get(i).title(), i + 1));
                }
                return chapters;
            }
        }
        List<Chapter> chapters = new ArrayList<>(index.chapters().size());
        for (int i = 0; i < index.chapters().size(); i++) {
            FanqieClient.ChapterEntry entry = index.chapters().get(i);
            String title = entry.title() == null || entry.title().isBlank()
                    ? "第 " + (i + 1) + " 章" : entry.title();
            chapters.add(new Chapter(entry.itemId(), title, i + 1));
        }
        return chapters;
    }

    @Override
    public ChapterContent chapter(String bookId, String chapterId) {
        FanqieLibraryService.BookIndex index = prepare(bookId);
        if (index.chapters().size() == 1) {
            String prefix = index.chapters().getFirst().itemId() + "-";
            if (chapterId.startsWith(prefix)) {
                String suffix = chapterId.substring(prefix.length());
                if (!suffix.matches("[1-9][0-9]{0,5}")) {
                    throw new BusinessException(404, "章节不存在，请刷新目录");
                }
                int partIndex = Integer.parseInt(suffix) - 1;
                List<FanqieChapterParser.Part> parts = FanqieChapterParser.split(readContent(bookId, index, 0));
                if (partIndex >= parts.size()) throw new BusinessException(404, "章节不存在，请刷新目录");
                FanqieChapterParser.Part part = parts.get(partIndex);
                return new ChapterContent(chapterId, part.title(), part.content());
            }
        }
        for (int i = 0; i < index.chapters().size(); i++) {
            FanqieClient.ChapterEntry entry = index.chapters().get(i);
            if (!entry.itemId().equals(chapterId)) continue;
            String title = entry.title().isBlank() ? "第 " + (i + 1) + " 章" : entry.title();
            // 旧目录中的上游章节地址继续返回完整正文，已有阅读进度仍可使用。
            return new ChapterContent(chapterId, title, readContent(bookId, index, i));
        }
        throw new BusinessException(404, "章节不存在，请刷新目录");
    }

    private String readContent(String bookId, FanqieLibraryService.BookIndex index, int position) {
        FanqieClient.ChapterEntry entry = index.chapters().get(position);
        String content = library.cachedChapter(bookId, entry.itemId());
        if (content == null || content.isBlank()) {
            // read() 使用目录位置（从 1 开始），并复用现有的按需拉取和落盘缓存。
            Object value = library.read(bookId, position + 1).get("content");
            content = value == null ? "" : value.toString();
        }
        if (content.isBlank()) throw new BusinessException(502, "当前章节暂时无法获取，请重试");
        return content;
    }

    /**
     * 取书籍目录索引；不在书架时先按书号打开。
     *
     * <p>外部阅读器的流程是「搜索结果 → 目录 → 正文」，不会经过 App 的「加入书架」，
     * 因此这里自动调用 open 完成目录缓存，但书籍不会因此出现在书架里。</p>
     */
    private FanqieLibraryService.BookIndex prepare(String bookId) {
        FanqieLibraryService.BookIndex index = library.findIndex(bookId);
        if (index != null) return index;
        // 阅读器会并发请求详情与目录，这里串行化，避免同一本书重复拉取上游目录。
        Object lock = openLocks.computeIfAbsent(bookId, key -> new Object());
        try {
            synchronized (lock) {
                index = library.findIndex(bookId);
                if (index != null) return index;
                FanqieClient.Book known = searchService.findCached(bookId);
                // 外部阅读器只缓存目录，不替用户把书加入书架。
                library.open(bookId,
                        known == null ? "" : known.bookName(),
                        known == null ? "" : known.author(),
                        known == null ? "" : known.thumbUrl(), false);
                index = library.findIndex(bookId);
            }
        } finally {
            openLocks.remove(bookId, lock);
        }
        if (index == null) throw new BusinessException(404, "书籍不存在或无法打开");
        return index;
    }

    private static BookInfo toBookInfo(FanqieClient.Book book) {
        return new BookInfo(book.bookId(), book.bookName(), book.author(), book.abstractText(),
                book.category(), book.thumbUrl(), book.lastChapterTitle(), book.wordNumber(),
                book.finished());
    }
}
