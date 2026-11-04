package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.huluwa4.Huluwa4NovelService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 4hu 小说分类查询与正文批量下载任务。 */
@RestController
@RequestMapping("/api/4hu/novels")
public class Huluwa4NovelController {

    private final Huluwa4NovelService novels;

    public Huluwa4NovelController(Huluwa4NovelService novels) {
        this.novels = novels;
    }

    @GetMapping("/categories")
    public ApiResult<Map<String, Object>> categories() {
        return ApiResult.ok(Map.of(
                "root", novels.outputRoot(),
                "categories", novels.categories()));
    }

    @GetMapping("/categories/{categoryId}")
    public ApiResult<Map<String, Object>> category(@PathVariable int categoryId) {
        Huluwa4NovelService.Category category = novels.category(categoryId);
        Huluwa4NovelService.CategoryTotal total = novels.categoryTotal(categoryId);
        List<Huluwa4NovelService.Chapter> firstPage = novels.chapters(categoryId, 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", category.id());
        data.put("name", total.name());
        data.put("path", category.path());
        data.put("total", total.total());
        data.put("perPage", total.perPage());
        data.put("lastPage", total.lastPage());
        data.put("preview", firstPage);
        return ApiResult.ok(data);
    }

    @GetMapping("/categories/{categoryId}/chapters")
    public ApiResult<Map<String, Object>> chapters(@PathVariable int categoryId,
                                                    @RequestParam(defaultValue = "1") int page) {
        Huluwa4NovelService.CategoryTotal total = novels.categoryTotal(categoryId);
        List<Huluwa4NovelService.Chapter> list = novels.chapters(categoryId, page);
        return ApiResult.ok(Map.of(
                "categoryId", categoryId,
                "name", total.name(),
                "page", page,
                "lastPage", total.lastPage(),
                "total", total.total(),
                "chapters", list));
    }

    public record DownloadRequest(String outputDir) { }

    /** 一键下载该分类下的全部小说。保留 /download 兼容旧入口。 */
    @PostMapping({"/categories/{categoryId}/download-all", "/categories/{categoryId}/download"})
    public ApiResult<Huluwa4NovelService.TaskStatus> downloadAll(@PathVariable int categoryId,
                                                                  @RequestBody(required = false) DownloadRequest request) {
        return ApiResult.ok(novels.startDownload(categoryId, request == null ? null : request.outputDir()));
    }

    @GetMapping("/tasks")
    public ApiResult<List<Huluwa4NovelService.TaskStatus>> tasks() {
        return ApiResult.ok(novels.activeTasks());
    }

    @GetMapping("/tasks/{taskId}")
    public ApiResult<Huluwa4NovelService.TaskStatus> task(@PathVariable String taskId) {
        if (taskId == null || taskId.isBlank()) throw new BusinessException(400, "任务 ID 不能为空");
        return ApiResult.ok(novels.status(taskId));
    }

    @PostMapping("/tasks/{taskId}/cancel")
    public ApiResult<Huluwa4NovelService.TaskStatus> cancel(@PathVariable String taskId) {
        return ApiResult.ok(novels.cancel(taskId));
    }
}
