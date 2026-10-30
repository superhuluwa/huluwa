package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.BtbtlaSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** BT影视搜索、按清晰度分组的目录与单条磁力；只接受站点数字 ID。 */
@RestController
@RequestMapping("/api/btbtla")
public class BtbtlaController {
    private final BtbtlaSearchService search;
    public BtbtlaController(BtbtlaSearchService search) { this.search = search; }

    @GetMapping("/search")
    public ApiResult<List<Map<String, Object>>> search(@RequestParam String q) {
        return ApiResult.ok(search.search(q).stream().map(search::payload).toList());
    }
    @GetMapping("/detail")
    public ApiResult<BtbtlaSearchService.Detail> detail(@RequestParam String id) {
        return ApiResult.ok(search.detail(id));
    }
    @GetMapping("/magnet")
    public ApiResult<Map<String, String>> magnet(@RequestParam String id) {
        return ApiResult.ok(search.magnet(id));
    }
}
