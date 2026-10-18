package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.EpisodeLibraryService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 本地剧集管理：扫描共享下载目录并提供受限的批量删除。 */
@RestController
@RequestMapping("/api/library")
public class EpisodeLibraryController {
    private final EpisodeLibraryService library;

    public EpisodeLibraryController(EpisodeLibraryService library) {
        this.library = library;
    }

    @GetMapping("/episodes")
    public ApiResult<EpisodeLibraryService.ScanResult> episodes() {
        return ApiResult.ok(library.scan());
    }

    @DeleteMapping("/episodes")
    public ApiResult<EpisodeLibraryService.DeleteResult> delete(@RequestBody EpisodeLibraryService.DeleteRequest body) {
        return ApiResult.ok(library.delete(body));
    }
}
