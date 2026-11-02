package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.FanqieLibraryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 番茄小说书架、按需阅读和阅读位置；统一复用 /api 的登录认证。 */
@RestController
@RequestMapping("/api/fanqie")
public class FanqieController {
    private final FanqieLibraryService library;

    public FanqieController(FanqieLibraryService library) { this.library = library; }

    public record OpenRequest(@Size(max = 300) String title, @Size(max = 300) String author,
                              @Size(max = 2048) String cover) { }
    public record ProgressRequest(int order, int paragraphIndex, int scrollOffset) { }

    @GetMapping("/books")
    public ApiResult<List<FanqieLibraryService.BookSummary>> books() {
        return ApiResult.ok(library.list());
    }

    @PostMapping("/books/{bookId}/open")
    public ApiResult<FanqieLibraryService.BookSummary> open(@PathVariable String bookId,
                                                           @Valid @RequestBody OpenRequest request) {
        return ApiResult.ok(library.open(bookId, request.title(), request.author(), request.cover()));
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
}
