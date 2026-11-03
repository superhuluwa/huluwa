package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.source.BookExportService;
import com.huluwa.console.service.source.BookSource;
import com.huluwa.console.service.source.BookSourceOrigin;
import com.huluwa.console.service.source.BookSourceRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 书源接口：面向外部阅读器（如开源阅读）的只读接口。
 *
 * <p>响应刻意保持扁平结构（字段直接放在 data 下），便于用 JSONPath 写书源规则。
 * 鉴权只接受书源令牌，见 {@code BookSourceAuthInterceptor}。</p>
 */
@RestController
@RequestMapping("/api/sources")
public class BookSourceController {

    private final BookSourceRegistry registry;
    private final BookExportService exports;
    private final BookSourceOrigin origins;

    public BookSourceController(BookSourceRegistry registry, BookExportService exports,
                                BookSourceOrigin origins) {
        this.registry = registry;
        this.exports = exports;
        this.origins = origins;
    }

    /** 当前可用的书源。 */
    @GetMapping
    public ApiResult<List<Map<String, Object>>> sources() {
        List<Map<String, Object>> list = registry.all().stream()
                .map(source -> Map.<String, Object>of("id", source.id(), "name", source.name()))
                .toList();
        return ApiResult.ok(list);
    }

    /**
     * 搜索。
     *
     * <p>两种翻页方式都支持：页码（<code>page</code>，从 1 开始，对应开源阅读的 {{page}}）
     * 和服务端游标（<code>cursor</code>，优先）。返回的 nextCursor 为 null 表示没有更多。</p>
     */
    @GetMapping("/{sourceId}/search")
    public ApiResult<Map<String, Object>> search(@PathVariable String sourceId,
                                                 @RequestParam(defaultValue = "") String key,
                                                 @RequestParam(required = false) String cursor,
                                                 @RequestParam(required = false) Integer page,
                                                 @RequestParam(defaultValue = "20") int size,
                                                 HttpServletRequest request) {
        BookSource source = registry.require(sourceId);
        String position = cursor != null && !cursor.isBlank()
                ? cursor
                : (page == null ? null : String.valueOf(Math.max(1, page)));
        BookSource.SearchResult result = source.search(key, position, size);
        String origin = origins.resolve(null, request);
        List<Map<String, Object>> books = new ArrayList<>(result.books().size());
        for (BookSource.BookInfo book : result.books()) {
            books.add(describeBook(origin, sourceId, book));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("books", books);
        data.put("nextCursor", result.nextCursor());
        return ApiResult.ok(data);
    }

    /** 书籍详情；书籍不在书架时实时查一次上游。 */
    @GetMapping("/{sourceId}/books/{bookId}")
    public ApiResult<Map<String, Object>> book(@PathVariable String sourceId, @PathVariable String bookId,
                                               HttpServletRequest request) {
        BookSource.BookInfo book = registry.require(sourceId).book(bookId);
        if (book == null) throw new BusinessException(404, "书籍不存在");
        return ApiResult.ok(describeBook(origins.resolve(null, request),
                sourceId, book));
    }

    /**
     * 完整目录。
     *
     * <p>除统一字段外，每条还带完整的 <code>url</code>，
     * 让只能按 JSONPath 取值的阅读器直接得到可请求的章节地址。</p>
     */
    @GetMapping("/{sourceId}/books/{bookId}/chapters")
    public ApiResult<List<Map<String, Object>>> chapters(@PathVariable String sourceId,
                                                         @PathVariable String bookId,
                                                         HttpServletRequest request) {
        List<BookSource.Chapter> chapters = registry.require(sourceId).chapters(bookId);
        String origin = origins.resolve(null, request);
        List<Map<String, Object>> items = new ArrayList<>(chapters.size());
        for (BookSource.Chapter chapter : chapters) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("chapterId", chapter.chapterId());
            item.put("title", chapter.title());
            item.put("order", chapter.order());
            item.put("url", origin + "/api/sources/" + sourceId
                    + "/books/" + bookId + "/chapters/" + chapter.chapterId());
            items.add(item);
        }
        return ApiResult.ok(items);
    }

    @GetMapping("/{sourceId}/books/{bookId}/chapters/{chapterId}")
    public ApiResult<BookSource.ChapterContent> chapter(@PathVariable String sourceId,
                                                        @PathVariable String bookId,
                                                        @PathVariable String chapterId) {
        return ApiResult.ok(registry.require(sourceId).chapter(bookId, chapterId));
    }

    /** 导出整本 TXT：返回任务状态，完成后用 /exports/{taskId}/download 下载。 */
    @PostMapping("/{sourceId}/books/{bookId}/export")
    public ApiResult<BookExportService.ExportStatus> export(@PathVariable String sourceId,
                                                            @PathVariable String bookId) {
        BookSource source = registry.require(sourceId);
        // 先走一次目录，书籍不在书架时会被自动打开，随后导出才能读到章节缓存。
        source.chapters(bookId);
        return ApiResult.ok(exports.export(bookId));
    }

    @GetMapping("/exports/{taskId}")
    public ApiResult<BookExportService.ExportStatus> exportStatus(@PathVariable String taskId) {
        return ApiResult.ok(exports.status(taskId));
    }

    /** 书目统一输出为扁平字段，并附上完整可请求的 bookUrl。 */
    private static Map<String, Object> describeBook(String origin, String sourceId, BookSource.BookInfo book) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("bookId", book.bookId());
        item.put("name", book.name());
        item.put("author", book.author());
        item.put("intro", book.intro());
        item.put("kind", book.kind());
        item.put("coverUrl", book.coverUrl());
        item.put("lastChapterName", book.lastChapterName());
        item.put("wordCount", book.wordCount());
        item.put("finished", book.finished());
        item.put("bookUrl", origin + "/api/sources/" + sourceId + "/books/" + book.bookId());
        return item;
    }

    @GetMapping("/exports/{taskId}/download")
    public ResponseEntity<Resource> download(@PathVariable String taskId) throws IOException {
        Path file = exports.file(taskId);
        if (file == null) {
            throw new BusinessException(404, "导出文件尚未生成或已过期");
        }
        BookExportService.ExportStatus status = exports.status(taskId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/plain; charset=utf-8"))
                .contentLength(Files.size(file))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(status.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
