package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.HongguoService;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 红果 TV 原生版使用的只读接口。
 *
 * <p>与 /api/hongguo/** 共用同一份数据逻辑，但只暴露电视播放需要的字段，
 * 并且只接受长期只读电视令牌（见 {@code TvTokenAuthInterceptor}），不暴露签名服务配置。</p>
 */
@RestController
@RequestMapping("/api/tv")
public class TvHongguoController {

    private final HongguoService service;

    public TvHongguoController(HongguoService service) { this.service = service; }

    /** 首页推荐，电视只展示剧集卡片。 */
    @GetMapping("/discover")
    public ApiResult<Map<String, Object>> discover() {
        Map<String, Object> data = service.discover();
        return ApiResult.ok(Map.of("items", tvItems(rows(data.get("items")))));
    }

    /** 搜索短剧。 */
    @GetMapping("/search")
    public ApiResult<Map<String, Object>> search(@RequestParam String query,
            @RequestParam(defaultValue = "0") int offset) {
        Map<String, Object> data = service.search(query, offset);
        return ApiResult.ok(Map.of("items", tvItems(rows(data.get("items"))),
                "nextOffset", data.getOrDefault("nextOffset", offset + 20),
                "hasMore", Boolean.TRUE.equals(data.get("hasMore"))));
    }

    /**
     * 榜单。电视端只有「短剧 / 漫剧」两种内容类型，未指定分类时按短剧的全部榜单返回；
     * 演员榜条目没有可播放分集，直接过滤掉，但保留分类与子榜供换榜使用。
     */
    @GetMapping("/ranking")
    public ApiResult<Map<String, Object>> ranking(@RequestParam(required = false) String content,
            @RequestParam(required = false) String board, @RequestParam(required = false) String cursor) {
        Map<String, Object> data = service.ranking(content, board, cursor);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", tvItems(rows(data.get("items"))));
        result.put("contents", contents(data.get("contents")));
        result.put("content", data.getOrDefault("content", ""));
        result.put("board", data.getOrDefault("board", ""));
        result.put("hasMore", Boolean.TRUE.equals(data.get("hasMore")));
        result.put("cursor", data.getOrDefault("cursor", ""));
        result.put("version", data.getOrDefault("version", ""));
        return ApiResult.ok(result);
    }

    /** 只保留电视需要的分类字段，子榜同样精简。 */
    private static List<Map<String, Object>> contents(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : rows(value)) {
            Object id = row.get("id");
            if (id == null || id.toString().isBlank()) continue;
            List<Map<String, Object>> boards = new ArrayList<>();
            for (Map<String, Object> board : rows(row.get("boards"))) {
                Object boardId = board.get("id");
                if (boardId == null || boardId.toString().isBlank()) continue;
                boards.add(Map.of("id", boardId.toString(),
                        "name", String.valueOf(board.getOrDefault("name", "")),
                        "selected", Boolean.TRUE.equals(board.get("selected"))));
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id.toString());
            item.put("name", String.valueOf(row.getOrDefault("name", "")));
            item.put("selected", Boolean.TRUE.equals(row.get("selected")));
            item.put("boards", boards);
            result.add(item);
        }
        return result;
    }

    /** 剧集详情：只返回分集编号，电视按序号自行生成「第 N 集」。 */
    @GetMapping("/series/{id}")
    public ApiResult<Map<String, Object>> detail(@PathVariable String id) {
        Map<String, Object> data = service.detail(id);
        List<Map<String, Object>> episodes = new ArrayList<>();
        for (Map<String, Object> row : rows(data.get("episodes"))) {
            Object episodeId = row.get("id");
            if (episodeId == null || episodeId.toString().isBlank()) continue;
            episodes.add(Map.of("id", episodeId.toString(), "number", episodes.size() + 1,
                    "locked", Boolean.TRUE.equals(row.get("locked"))));
        }
        if (episodes.isEmpty()) throw new BusinessException(502, "该剧暂未返回可用分集，请稍后重试");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("title", data.getOrDefault("title", ""));
        result.put("cover", data.getOrDefault("cover", ""));
        result.put("intro", data.getOrDefault("intro", ""));
        result.put("episodeCount", episodes.size());
        result.put("episodes", episodes);
        return ApiResult.ok(result);
    }

    /** 播放信息：电视需要 url 与 spade_a 才能在本机解密播放。 */
    @GetMapping("/episodes/{id}/playback")
    public ApiResult<Map<String, Object>> playback(@PathVariable String id) {
        return ApiResult.ok(service.playback(id));
    }

    /** 电视端不区分演员等其它条目，只保留有分集编号的剧集。 */
    private static List<Map<String, Object>> tvItems(List<Map<String, Object>> source) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (Map<String, Object> row : source) {
            Object id = row.get("id");
            Object title = row.get("title");
            if (id == null || title == null || title.toString().isBlank()) continue;
            if (!id.toString().matches("[0-9]{1,24}")) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id.toString());
            item.put("title", title.toString());
            item.put("cover", String.valueOf(row.getOrDefault("cover", "")));
            item.put("intro", String.valueOf(row.getOrDefault("intro", "")));
            Object count = row.get("episodeCount");
            item.put("episodeCount", count instanceof Number ? ((Number) count).intValue() : 0);
            // 榜单热度：演员条目被过滤，所以只对保留下来的剧集条目取 score。
            Object score = row.get("score");
            item.put("score", score == null ? "" : score.toString());
            items.add(item);
        }
        return items;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rows(Object value) {
        if (!(value instanceof List<?> list)) return List.of();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : list) if (item instanceof Map<?, ?> map) rows.add((Map<String, Object>) map);
        return rows;
    }
}
