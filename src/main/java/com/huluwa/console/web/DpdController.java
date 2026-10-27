package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.DongPianSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 懂片帝搜索：作品详情与线路按需解析。
 *
 * <p>线路的各集地址只在用户点选某条线路时才解析（见 {@link DongPianSearchService} 的说明），
 * 所以详情接口只返回线路名与集数，{@code /line} 才回填各集地址。复用现有 /api 鉴权。
 */
@RestController
@RequestMapping("/api/dpd")
public class DpdController {

    private final DongPianSearchService source;

    public DpdController(DongPianSearchService source) {
        this.source = source;
    }

    /** 作品详情 + 线路清单（各集地址留空，前端按线路懒加载） */
    @GetMapping("/detail")
    public ApiResult<Map<String, Object>> detail(@RequestParam String resId) {
        DongPianSearchService.Catalog catalog = source.detail(resId);
        List<DongPianSearchService.Line> lines = source.lines(catalog);
        return ApiResult.ok(source.toDetail(catalog, lines));
    }

    /** 指定线路的全部剧集地址 */
    @GetMapping("/line")
    public ApiResult<Map<String, Object>> line(@RequestParam String resId, @RequestParam String line) {
        DongPianSearchService.Catalog catalog = source.detail(resId);
        List<DongPianSearchService.PlayItem> items = source.line(catalog, line);
        return ApiResult.ok(source.toLinePayload(catalog, line, items));
    }

    /** 单集按需解析：长剧首屏之外的集在用户点开时才解析/兑换 */
    @GetMapping("/episode")
    public ApiResult<Map<String, Object>> episode(@RequestParam String resId, @RequestParam String line,
                                                  @RequestParam String title) {
        DongPianSearchService.Catalog catalog = source.detail(resId);
        DongPianSearchService.PlayItem item = source.resolveEpisode(catalog, line, title);
        return ApiResult.ok(source.toLinePayload(catalog, line, List.of(item)));
    }
}
