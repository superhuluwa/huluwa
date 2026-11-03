package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.FanqieLibraryService;
import com.huluwa.console.service.FanqieShelfUpdateService;
import com.huluwa.console.service.source.BookExportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;
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
import java.util.List;
import java.util.Map;

/** 番茄小说书架、按需阅读和阅读位置；统一复用 /api 的登录认证。 */
@RestController
@RequestMapping("/api/fanqie")
public class FanqieController {
    private final FanqieLibraryService library;
    private final FanqieShelfUpdateService updates;
    private final BookExportService exports;

    public FanqieController(FanqieLibraryService library, FanqieShelfUpdateService updates,
                            BookExportService exports) {
        this.library = library;
        this.updates = updates;
        this.exports = exports;
    }

    public record OpenRequest(@Size(max = 300) String title, @Size(max = 300) String author,
                              @Size(max = 2048) String cover,
                              /** 默认 true 保持旧版客户端与网页端行为；App 阅读页传 false。 */
                              Boolean addToShelf) { }
    public record DeleteRequest(@NotEmpty @Size(max = 500) List<String> bookIds) { }
    public record ProgressRequest(int order, int paragraphIndex, int scrollOffset) { }

    @GetMapping("/books")
    public ApiResult<List<FanqieLibraryService.BookSummary>> books() {
        return ApiResult.ok(library.list());
    }

    @PostMapping("/books/check-updates")
    public ApiResult<FanqieShelfUpdateService.UpdateStatus> checkUpdates() {
        return ApiResult.ok(updates.checkUpdates());
    }

    @PostMapping("/books/{bookId}/download")
    public ApiResult<FanqieShelfUpdateService.UpdateStatus> downloadBook(@PathVariable String bookId) {
        return ApiResult.ok(updates.downloadBook(bookId));
    }

    @GetMapping("/books/update-status")
    public ApiResult<FanqieShelfUpdateService.UpdateStatus> updateStatus() {
        return ApiResult.ok(updates.status());
    }

    @PostMapping("/books/delete")
    public ApiResult<FanqieLibraryService.DeleteResult> deleteBooks(@Valid @RequestBody DeleteRequest request) {
        return ApiResult.ok(library.deleteBooks(request.bookIds()));
    }

    @DeleteMapping("/books/{bookId}")
    public ApiResult<FanqieLibraryService.DeleteResult> deleteBook(@PathVariable String bookId) {
        return ApiResult.ok(library.deleteBooks(List.of(bookId)));
    }

    @PostMapping("/books/{bookId}/open")
    public ApiResult<FanqieLibraryService.BookSummary> open(@PathVariable String bookId,
                                                           @Valid @RequestBody OpenRequest request) {
        boolean addToShelf = request.addToShelf() == null || request.addToShelf();
        return ApiResult.ok(library.open(bookId, request.title(), request.author(), request.cover(), addToShelf));
    }

    /** 加入书架：只翻转书架标记，不重新拉取目录。 */
    @PostMapping("/books/{bookId}/shelf")
    public ApiResult<FanqieLibraryService.BookSummary> addToShelf(@PathVariable String bookId,
                                                                 @Valid @RequestBody(required = false) OpenRequest request) {
        return ApiResult.ok(library.addToShelf(bookId,
                request == null ? "" : request.title(), request == null ? "" : request.author(),
                request == null ? "" : request.cover()));
    }

    /** 移出书架：保留目录与正文缓存，只从书架列表隐藏。 */
    @DeleteMapping("/books/{bookId}/shelf")
    public ApiResult<FanqieLibraryService.BookSummary> removeFromShelf(@PathVariable String bookId) {
        return ApiResult.ok(library.removeFromShelf(bookId));
    }

    /** order<=0 时恢复到最近一次保存的章节和滚动位置。 */
    @GetMapping("/books/{bookId}/read")
    public ApiResult<Map<String, Object>> read(@PathVariable String bookId,
                                              @RequestParam(defaultValue = "0") int order) {
        return ApiResult.ok(library.read(bookId, order));
    }

    @GetMapping("/books/{bookId}/catalog")
    public ApiResult<Map<String, Object>> catalog(@PathVariable String bookId) {
        return ApiResult.ok(library.catalog(bookId));
    }

    @PutMapping("/books/{bookId}/progress")
    public ApiResult<Void> progress(@PathVariable String bookId, @RequestBody ProgressRequest request) {
        library.saveProgress(bookId, request.order(), request.paragraphIndex(), request.scrollOffset());
        return ApiResult.ok();
    }

    /** 导出整本 TXT（异步）：先取任务状态，完成后从 /exports/{taskId}/download 下载。 */
    @PostMapping("/books/{bookId}/export")
    public ApiResult<BookExportService.ExportStatus> export(@PathVariable String bookId) {
        return ApiResult.ok(exports.export(bookId));
    }

    @GetMapping("/exports/{taskId}")
    public ApiResult<BookExportService.ExportStatus> exportStatus(@PathVariable String taskId) {
        return ApiResult.ok(exports.status(taskId));
    }

    /** 下载已完成的 TXT；走登录鉴权，安卓端和网页端无需书源令牌。 */
    @GetMapping("/exports/{taskId}/download")
    public ResponseEntity<Resource> exportDownload(@PathVariable String taskId) throws IOException {
        Path file = exports.file(taskId);
        if (file == null) throw new BusinessException(404, "导出文件尚未生成或已过期");
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
