package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.fanqie.FanqieClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 番茄小说会话的搜索适配。
 *
 * <p>会话内发送的关键字走番茄小说的 App 搜索接口（{@link FanqieClient#searchMore}），
 * 返回书目列表。会话设备缓存在本服务内，搜索无需交换正文密钥；
 * 服务端限流或密钥失效时清空缓存，下次搜索自动重建。</p>
 */
@Service
public class FanqieSearchService {

    /** 单次搜索展示的最大书目数。 */
    public static final int DEFAULT_MAX = 20;
    /** 关键字长度上限。 */
    private static final int MAX_KEYWORD_LENGTH = 60;
    /** 会话内搜索翻页数（每页 10 条）。 */
    private static final int SEARCH_PAGES = 2;

    private static final Logger log = LoggerFactory.getLogger(FanqieSearchService.class);

    private final FanqieClient client = new FanqieClient(Duration.ofSeconds(12), 1);
    /** 最近一次搜索到的书目，按 bookId 索引，供「立即阅读」复用书名与封面。 */
    private final Map<String, FanqieClient.Book> recent = new LinkedHashMap<>();
    private static final int RECENT_LIMIT = 200;

    /**
     * 搜索书目。
     *
     * <p>多线程共用同一个客户端实例：注册与搜索串行执行，避免并发设备注册。
     * 失败时丢弃会话（下次调用重建），再把异常抛给上层转成错误气泡。</p>
     */
    public synchronized List<FanqieClient.Book> search(String keyword) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) {
            throw new BusinessException(400, "请输入要搜索的书名");
        }
        if (query.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(400, "关键字过长，请输入 " + MAX_KEYWORD_LENGTH + " 个字符以内");
        }
        try {
            return remember(client.searchMore(query, SEARCH_PAGES, 10));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "番茄小说搜索已中断，请重试");
        } catch (Exception first) {
            log.warn("番茄小说搜索失败，重建会话后重试：{}", first.getMessage());
            try {
                client.resetSession();
                return remember(client.searchMore(query, SEARCH_PAGES, 10));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new BusinessException(503, "番茄小说搜索已中断，请重试");
            } catch (Exception second) {
                // 会话已不可用，下次调用时重新注册
                reset();
                throw new BusinessException(502, "番茄小说搜索失败：" + safeMessage(second));
            }
        }
    }

    /** 记录搜索结果，供「立即阅读」的卡片复用书名与封面。 */
    private List<FanqieClient.Book> remember(List<FanqieClient.Book> books) {
        for (FanqieClient.Book book : books) {
            recent.remove(book.bookId());
            recent.put(book.bookId(), book);
        }
        while (recent.size() > RECENT_LIMIT) {
            String oldest = recent.keySet().iterator().next();
            recent.remove(oldest);
        }
        return books;
    }

    /** 按书号取最近搜索到的书目信息；未命中返回 null。 */
    public synchronized FanqieClient.Book findCached(String bookId) {
        return recent.get(bookId);
    }

    /**
     * 书源分页游标：页码（从 1 开始），与开源阅读的 <code>{{page}}</code> 语义对齐。
     *
     * <p>页码换算成 offset 后直接请求上游，不依赖上一次返回的 passback，
     * 因此阅读器可以任意跳到某一页，也不会因为服务端实例重启而失效。</p>
     */
    public record SearchCursor(int page) {
        static SearchCursor parse(String raw) {
            if (raw == null || raw.isBlank()) return new SearchCursor(1);
            String head = raw.indexOf(':') >= 0 ? raw.substring(0, raw.indexOf(':')) : raw;
            try {
                return new SearchCursor(Math.max(1, Integer.parseInt(head.trim())));
            } catch (NumberFormatException error) {
                return new SearchCursor(1);
            }
        }

        int offset(int size) { return (page - 1) * size; }
    }

    /**
     * 书源分页搜索：按游标取一页，并记录结果供后续「补全书目信息」复用。
     *
     * <p>与 {@link #search(String)} 共用设备会话，避免书源和会话各自注册一台设备。</p>
     */
    public synchronized FanqieClient.SearchPage searchPage(String keyword, String cursor, int size) {
        String query = keyword == null ? "" : keyword.trim();
        if (query.isEmpty()) throw new BusinessException(400, "请输入要搜索的书名");
        if (query.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(400, "关键字过长，请输入 " + MAX_KEYWORD_LENGTH + " 个字符以内");
        }
        int count = Math.max(1, Math.min(size, 30));
        SearchCursor position = SearchCursor.parse(cursor);
        try {
            return fetchPage(query, position, count);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "番茄小说搜索已中断，请重试");
        } catch (Exception first) {
            log.warn("番茄小说书源搜索失败，重建会话后重试：{}", first.getMessage());
            try {
                client.resetSession();
                return fetchPage(query, position, count);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new BusinessException(503, "番茄小说搜索已中断，请重试");
            } catch (Exception second) {
                reset();
                throw new BusinessException(502, "番茄小说搜索失败：" + safeMessage(second));
            }
        }
    }

    private FanqieClient.SearchPage fetchPage(String query, SearchCursor position, int count)
            throws IOException, InterruptedException {
        // 页码已换算成 offset，passback 传 null 让上游按 offset 返回对应窗口。
        FanqieClient.SearchPage page = client.searchOnePage(query, position.offset(count), count, null);
        List<FanqieClient.Book> books = remember(page.books());
        // 上游仍有续页且本页非空时返回下一页页码，否则结束翻页。
        String next = null;
        if (page.cursor() != null && !books.isEmpty()) {
            next = String.valueOf(position.page() + 1);
        }
        return new FanqieClient.SearchPage(books, next);
    }

    /** 书源补全书目：先查详情，失败时退回最近搜索结果。 */
    public synchronized FanqieClient.Book describe(String bookId) {
        try {
            FanqieClient.Book detail = client.detailBook(bookId);
            if (detail != null) {
                remember(List.of(detail));
                return detail;
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new BusinessException(503, "番茄小说加载已中断，请重试");
        } catch (Exception first) {
            log.warn("番茄小说详情获取失败，尝试重建会话：{}", first.getMessage());
            try {
                client.resetSession();
                FanqieClient.Book detail = client.detailBook(bookId);
                if (detail != null) {
                    remember(List.of(detail));
                    return detail;
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new BusinessException(503, "番茄小说加载已中断，请重试");
            } catch (Exception second) {
                log.warn("番茄小说详情仍不可用，使用搜索缓存：{}", second.getMessage());
            }
        }
        return recent.get(bookId);
    }

    /** 丢弃当前设备会话，下次搜索重新注册设备。 */
    private void reset() { client.resetSession(); }

    /** 书目卡片的结构化载荷（安卓端按 kind=fanqie-book 渲染独立卡片）。 */
    public Map<String, Object> payload(FanqieClient.Book book) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("kind", "fanqie-book");
        map.put("bookId", book.bookId());
        map.put("title", book.bookName());
        map.put("author", book.author());
        map.put("abstract", book.abstractText());
        map.put("wordNumber", book.wordNumber());
        map.put("finished", book.finished());
        map.put("creationStatus", book.creationStatus());
        map.put("score", book.score());
        map.put("readCount", book.readCount());
        map.put("category", book.category());
        map.put("thumbUrl", book.thumbUrl());
        map.put("lastChapterTitle", book.lastChapterTitle());
        return map;
    }

    /** 书目搜索卡片配文：书名（作者）+ 分类 / 字数 / 状态 + 简介摘要。 */
    public static String caption(FanqieClient.Book book) {
        StringBuilder sb = new StringBuilder();
        sb.append('《').append(book.bookName()).append('》');
        if (!book.author().isBlank()) {
            sb.append("  ").append(book.author());
        }
        List<String> meta = new ArrayList<>();
        if (!book.category().isBlank()) {
            meta.add(book.category());
        }
        meta.add(book.finished() ? "完结" : "连载中");
        if (book.wordNumber() > 0) {
            meta.add(String.format("%.0f 万字", book.wordNumber() / 10000.0));
        }
        if (book.score() > 0) {
            meta.add(String.format("评分 %.1f", book.score()));
        }
        sb.append('\n').append(String.join(" · ", meta));
        if (!book.abstractText().isBlank()) {
            String abs = book.abstractText().replaceAll("\\s+", " ").trim();
            sb.append('\n').append(abs.length() > 120 ? abs.substring(0, 120) + "…" : abs);
        }
        if (!book.lastChapterTitle().isBlank()) {
            sb.append('\n').append("最新：").append(book.lastChapterTitle());
        }
        return sb.toString();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        if (message == null || message.isBlank()) {
            return e.getClass().getSimpleName();
        }
        return message.length() > 160 ? message.substring(0, 160) : message;
    }
}
