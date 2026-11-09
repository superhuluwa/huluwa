package com.huluwa.console.web;

import com.huluwa.console.service.HongguoService;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 电视端接口的字段裁剪测试：锁住电视只依赖的字段，避免上游结构变化后电视静默显示空列表。
 */
class TvHongguoControllerTest {

    private static Map<String, Object> series(String id, String title, int count) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("title", title);
        row.put("cover", "https://example.com/" + id + ".jpg");
        row.put("intro", "简介");
        row.put("episodeCount", count);
        return row;
    }

    @Test
    void rankingDropsActorsButKeepsHeatAndCategories() {
        HongguoService service = mock(HongguoService.class);
        Map<String, Object> response = new LinkedHashMap<>();
        List<Map<String, Object>> items = new java.util.ArrayList<>();
        items.add(series("101", "剧一", 20));
        Map<String, Object> actor = new LinkedHashMap<>();
        actor.put("id", "202");
        actor.put("title", "演员");
        actor.put("kind", "actor");
        items.add(actor);
        items.add(series("103", "剧三", 30));
        response.put("items", items);
        response.put("contents", List.of(Map.of("id", "all", "name", "全部", "selected", true,
                "boards", List.of(Map.of("id", "ranklist_hot_sc", "name", "热播榜", "selected", true)))));
        response.put("content", "all");
        response.put("board", "ranklist_hot_sc");
        response.put("hasMore", true);
        response.put("cursor", "cursor-1");
        response.put("version", "v1");
        when(service.ranking(anyString(), anyString(), anyString())).thenReturn(response);

        Map<String, Object> data = new TvHongguoController(service).ranking("all", "", "").data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) data.get("items");

        assertEquals(List.of("101", "103"), rows.stream().map(row -> String.valueOf(row.get("id"))).toList());
        // 电视端只拿集数，不组装文案。
        assertEquals(20, rows.getFirst().get("episodeCount"));
        assertEquals("cursor-1", data.get("cursor"));
        assertEquals(true, data.get("hasMore"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> contents = (List<Map<String, Object>>) data.get("contents");
        assertEquals("all", contents.getFirst().get("id"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> boards = (List<Map<String, Object>>) contents.getFirst().get("boards");
        assertEquals("热播榜", boards.getFirst().get("name"));
    }

    @Test
    void detailRenumbersEpisodesAndDropsBlankIds() {
        HongguoService service = mock(HongguoService.class);
        when(service.detail(anyString())).thenReturn(Map.of(
                "title", "测试剧", "cover", "https://example.com/c.jpg", "intro", "简介",
                "episodes", List.of(Map.of("id", "501", "locked", false), Map.of("id", "502", "locked", true))));

        Map<String, Object> data = new TvHongguoController(service).detail("9").data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> episodes = (List<Map<String, Object>>) data.get("episodes");

        assertEquals(2, episodes.size());
        assertEquals("501", episodes.getFirst().get("id"));
        assertEquals(1, episodes.getFirst().get("number"));
        assertEquals(2, episodes.get(1).get("number"));
        assertEquals(true, episodes.get(1).get("locked"));
        assertEquals(2, data.get("episodeCount"));
    }

    @Test
    void searchAndPlaybackPassThrough() {
        HongguoService service = mock(HongguoService.class);
        when(service.search(anyString(), anyInt())).thenReturn(Map.of(
                "items", List.of(series("301", "搜索剧", 12)), "nextOffset", 20, "hasMore", false));
        when(service.discover()).thenReturn(Map.of("items", List.of(series("401", "推荐剧", 8))));

        TvHongguoController controller = new TvHongguoController(service);
        Map<String, Object> search = controller.search("剧", 0).data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> searchRows = (List<Map<String, Object>>) search.get("items");
        assertEquals("301", searchRows.getFirst().get("id"));
        assertEquals(false, search.get("hasMore"));

        Map<String, Object> discover = controller.discover().data();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> discoverRows = (List<Map<String, Object>>) discover.get("items");
        assertEquals("401", discoverRows.getFirst().get("id"));
        assertTrue(discover.containsKey("items"));
    }
}
