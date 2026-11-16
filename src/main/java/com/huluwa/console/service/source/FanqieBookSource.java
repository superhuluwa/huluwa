package com.huluwa.console.service.source;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.FanqieLibraryService;
import com.huluwa.console.service.FanqieSearchService;
import com.huluwa.console.service.fanqie.FanqieClient;
import com.huluwa.console.service.fanqie.FanqieShareLinks;
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
    /** 按书号串行化打开与刷新，避免并发请求重复拉取上游目录。 */
    private final ConcurrentHashMap<String, Object> openLocks = new ConcurrentHashMap<>();

    public FanqieBookSource(FanqieSearchService searchService, FanqieLibraryService library) {
        this.searchService = searchService;
        this.library = library;
    }

    @Override public String id() { return ID; }

    @Override public String name() { return "番茄小说"; }

    @Override
    public SearchResult search(String keyword, String cursor, int size) {
        String query = keyword == null ? "" : keyword.strip();
        String shareUrl = FanqieShareLinks.firstUrl(query);
        boolean bookIdQuery = query.matches("[0-9]{6,24}");
        if (shareUrl != null || bookIdQuery) {
            // 分享口令可能超过普通关键字长度限制，优先按链接中的书号定位，避免命中同名短篇。
            if (FanqieSearchService.SearchCursor.parse(cursor).page() > 1) {
                return new SearchResult(List.of(), null);
            }
            String bookId = bookIdQuery ? query : searchService.resolveShareUrl(shareUrl);
            if (bookId == null) throw new BusinessException(502, "分享链接暂时无法解析，请稍后重试或输入书号");
            FanqieClient.Book book = searchService.describe(bookId);
            if (book == null) throw new BusinessException(404, "分享书籍暂时无法获取，请稍后重试");
            return new SearchResult(List.of(toBookInfo(book)), null);
        }
        FanqieClient.SearchPage page = searchService.searchPage(query, cursor, size);
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
            // 目录只保存打开时的书名；优先用书目元数据补齐当前书名、作者及原书名。
            FanqieClient.Book cached = searchService.findCached(bookId);
            if (cached == null) cached = searchService.describe(bookId);
            FanqieClient.ChapterEntry last = index.chapters().isEmpty()
                    ? null : index.chapters().getLast();
            long words = index.chapters().stream().mapToLong(FanqieClient.ChapterEntry::wordNumber).sum();
            return new BookInfo(index.bookId(),
                    cached == null || cached.bookName().isBlank() ? index.title() : cached.bookName(),
                    cached == null || cached.author().isBlank() ? index.author() : cached.author(),
                    cached == null ? "" : cached.abstractText(),
                    cached == null ? "" : cached.category(), index.cover(),
                    last == null ? "" : last.title(), words,
                    cached == null ? null : cached.finished(),
                    cached == null ? List.of() : cached.alternateNames());
        }
        FanqieClient.Book detail = searchService.describe(bookId);
        return detail == null ? null : toBookInfo(detail);
    }

    @Override
    public List<Chapter> chapters(String bookId) {
        // 阅读器主动更新目录时刷新上游；正文请求只读缓存，避免逐章重复拉目录。
        FanqieLibraryService.BookIndex index = prepare(bookId, true);
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
        // 兼容上一版生成过的拆分地址，统一返回原上游整章正文，不再截取片段。
        String itemId = chapterId.matches("[0-9]{6,24}-[1-9][0-9]{0,5}")
                ? chapterId.substring(0, chapterId.indexOf('-')) : chapterId;
        for (int i = 0; i < index.chapters().size(); i++) {
            FanqieClient.ChapterEntry entry = index.chapters().get(i);
            if (!entry.itemId().equals(itemId)) continue;
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
     * 取书籍目录索引；首次访问按书号打开，目录更新请求重新拉取上游。
     *
     * <p>外部阅读器的流程是「搜索结果 → 目录 → 正文」，不会经过 App 的「加入书架」，
     * 因此这里自动调用 open 完成目录缓存，但书籍不会因此出现在书架里。</p>
     */
    private FanqieLibraryService.BookIndex prepare(String bookId) {
        return prepare(bookId, false);
    }

    private FanqieLibraryService.BookIndex prepare(String bookId, boolean refresh) {
        if (bookId == null || !bookId.matches("[0-9]{6,24}")) {
            throw new BusinessException(400, "小说书号不正确");
        }
        FanqieLibraryService.BookIndex previous = library.findIndex(bookId);
        if (previous != null && !refresh) return previous;
        FanqieLibraryService.BookIndex index;
        // 阅读器会并发请求详情与目录，这里串行化，避免同一本书重复拉取上游目录。
        Object lock = openLocks.computeIfAbsent(bookId, key -> new Object());
        try {
            synchronized (lock) {
                index = library.findIndex(bookId);
                // 等待期间已有请求更新过目录时，直接复用它的结果。
                if (index != null && (!refresh || index != previous)) return index;
                FanqieClient.Book known = searchService.findCached(bookId);
                if (known == null && index == null) known = searchService.describe(bookId);
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
                book.finished(), book.alternateNames());
    }
}
