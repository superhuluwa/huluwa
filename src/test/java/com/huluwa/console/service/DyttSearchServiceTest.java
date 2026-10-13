package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电影天堂搜索解析单测：用采集接口真实响应片段锁定解析契约
 * （剧集 play_url 的多线路 / 多集切分、评分 0.0 归一、详情页链接拼装）。
 */
class DyttSearchServiceTest {

    private final DyttSearchService service = new DyttSearchService();

    /** 正片：单线路单条，play 名称「HD国语」 */
    private static final String MOVIE_JSON = """
            {"code":1,"msg":"数据列表","page":1,"pagecount":1,"limit":20,"total":1,
             "list":[{"vod_id":2354,"vod_name":"流浪地球","vod_year":"2019","vod_area":"大陆",
             "vod_class":"科幻,冒险,灾难","vod_pic":"https://img.dytt-tupian.com/a.jpg",
             "vod_score":"7.9","vod_remarks":"HD国语","vod_play_from":"dytt$$$dyttm3u8",
             "vod_play_url":"HD国语$https://vip.dytt-live.com/share/aaa$$$HD国语$https://vip.dytt-live.com/20250121/1309_x/index.m3u8"}]}
            """;

    @Test
    void parseExtractsCardFields() {
        List<DyttSearchService.DyttItem> items = service.parse(MOVIE_JSON);

        assertEquals(1, items.size());
        DyttSearchService.DyttItem item = items.get(0);
        assertEquals("2354", item.id());
        assertEquals("流浪地球", item.title());
        assertEquals("2019", item.year());
        assertEquals("大陆", item.origin());
        assertEquals("科幻,冒险,灾难", item.genre());
        assertEquals("https://img.dytt-tupian.com/a.jpg", item.cover());
        assertEquals("7.9", item.score());
        assertEquals("HD国语", item.remarks());
        assertEquals("https://dyttzyw.com/index.php/vod/detail/id/2354.html", item.url());
    }

    /** $$$ 分隔多条线路，各自独立解析 */
    @Test
    void parseSplitsMultiplePlaySources() {
        List<DyttSearchService.DyttItem> items = service.parse(MOVIE_JSON);
        List<DyttSearchService.PlaySource> sources = items.get(0).playSources();

        assertEquals(2, sources.size());
        assertEquals("dytt", sources.get(0).name());
        assertEquals("dyttm3u8", sources.get(1).name());
        assertEquals(1, sources.get(0).total());
        assertTrue(sources.get(1).items().get(0).url().endsWith("index.m3u8"));
    }

    /** 剧集：源内 # 分隔多集，每集「名称$地址」 */
    @Test
    void parseSplitsEpisodesWithinSource() {
        String json = """
                {"code":1,"list":[{"vod_id":84511,"vod_name":"捡钱换宝","vod_year":"2026","vod_area":"大陆",
                "vod_class":"短剧","vod_pic":"https://img.dytt-tupian.com/b.jpg","vod_score":"0.0",
                "vod_remarks":"更新至03集","vod_play_from":"dyttm3u8",
                "vod_play_url":"第01集$https://vip.dytt-tvs.com/a.m3u8#第02集$https://vip.dytt-tvs.com/b.m3u8#第03集$https://vip.dytt-tvs.com/c.m3u8"}]}
                """;
        List<DyttSearchService.PlaySource> sources = service.parse(json).get(0).playSources();

        assertEquals(1, sources.size());
        assertEquals(3, sources.get(0).total());
        assertEquals("第01集", sources.get(0).items().get(0).title());
        assertEquals("第03集", sources.get(0).items().get(2).title());
        // 评分 0.0 视为「暂无评分」
        assertNull(service.parse(json).get(0).score());
    }

    /** 地址里带 $ 时只按第一个 $ 切分，地址保持完整 */
    @Test
    void parseKeepsUrlContainingDollar() {
        List<DyttSearchService.PlayItem> items =
                DyttSearchService.parsePlayItems("正片$https://cdn.example.com/a$b/index.m3u8");
        assertEquals(1, items.size());
        assertEquals("https://cdn.example.com/a$b/index.m3u8", items.get(0).url());
    }

    /** 非 http 地址（损坏数据）与 no 占位符都要被丢弃 */
    @Test
    void parseDropsInvalidPlayUrls() {
        assertTrue(DyttSearchService.parsePlayItems("正片$not-a-url").isEmpty());
        assertTrue(DyttSearchService.parsePlaySources("dytt", "no").isEmpty());
        assertTrue(DyttSearchService.parsePlaySources("dytt", "").isEmpty());
    }

    /** 关键字归一化：指令前缀被剥离，纯片名保持原样 */
    @Test
    void normalizeDyttKeywordStripsCommandPrefixOnly() {
        assertEquals("凡人", ChatService.normalizeDyttKeyword("电影天堂搜索 凡人"));
        assertEquals("凡人", ChatService.normalizeDyttKeyword("电影天堂 凡人"));
        assertEquals("凡人", ChatService.normalizeDyttKeyword("dytt 凡人"));
        assertEquals("2046", ChatService.normalizeDyttKeyword("2046"));
        assertEquals("复仇者 联盟", ChatService.normalizeDyttKeyword("复仇者 联盟"));
    }

    /** 载荷：source=dytt 供前端区分卡片，播放源按上限截断 */
    @Test
    void payloadCarriesDyttMarkerAndPlaySources() {
        DyttSearchService.DyttItem item = service.parse(MOVIE_JSON).get(0);
        var payload = service.toPayload(item);

        assertEquals("dytt", payload.get("source"));
        assertEquals("2354", payload.get("id"));
        assertEquals("7.9", payload.get("score"));
        assertEquals("HD国语", payload.get("remarks"));
        assertFalse(((List<?>) payload.get("playSources")).isEmpty());
    }

    /** 关键字按 UTF-8 百分号编码，空格不做成 + */
    @Test
    void encodeUsesPercentEscapes() {
        assertEquals("%E6%B5%81%E6%B5%AA%E5%9C%B0%E7%90%83", DyttSearchService.encode("流浪地球"));
        assertEquals("a%20b", DyttSearchService.encode("a b"));
    }
}