package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 懂片帝解析单测：锁定签名串格式、m3u8 判定与作品 ID 校验。
 *
 * <p>签名算法是从站点前端包（{@code movie-card-runtime-*.js} 的 {@code Ta()}）还原的，
 * 格式一旦写错所有请求都会 401，因此这里用固定输入锁定「METHOD\n路径\n时间戳\nnonce」四行拼接。
 */
class DongPianSearchServiceTest {

    /** 密钥与算法固定，同样的输入必须得到同样的签名（小写十六进制 HMAC-SHA256） */
    @Test
    void signMatchesHmacSha256OfFourLines() {
        String signature = DongPianSearchService.sign(
                "GET", "/v1/browse/catalog?q=%E7%8B%82%E9%A3%99&limit=20", "1700000000000",
                "0123456789abcdef0123456789abcdef");

        assertEquals(64, signature.length());
        assertTrue(signature.matches("[0-9a-f]{64}"), "签名必须是小写十六进制");
        // 同一输入可复现（HMAC 不带随机性），换行顺序写错会得到不同结果
        assertEquals(signature, DongPianSearchService.sign(
                "GET", "/v1/browse/catalog?q=%E7%8B%82%E9%A3%99&limit=20", "1700000000000",
                "0123456789abcdef0123456789abcdef"));
        assertFalse(signature.equals(DongPianSearchService.sign(
                "GET", "/v1/browse/catalog?q=%E7%8B%82%E9%A3%99&limit=20", "1700000000001",
                "0123456789abcdef0123456789abcdef")), "时间戳参与签名");
    }

    /** 只认可带 .m3u8 的地址：站点里还有 mp4 直链与 resolve:// 票据，都不能进离线下载 */
    @Test
    void onlyAcceptsM3u8Urls() {
        assertTrue(DongPianSearchService.isM3u8("https://cdn.example.com/a/index.m3u8"));
        assertTrue(DongPianSearchService.isM3u8("https://cdn.example.com/a/index.m3u8?token=1"));
        assertTrue(DongPianSearchService.isM3u8("https://cdn.example.com/A/INDEX.M3U8"));
        assertFalse(DongPianSearchService.isM3u8("https://cdn.example.com/a/index.mp4"));
        assertFalse(DongPianSearchService.isM3u8("resolve://av_abc"));
        assertFalse(DongPianSearchService.isM3u8(""));
    }

    /** 作品 ID 会被拼进请求路径，必须限定字符集 */
    @Test
    void rejectsUnsafeIds() {
        assertEquals("av_abcDEF123", DongPianSearchService.requireId("  av_abcDEF123  "));
        assertThrows(DongPianSearchService.DongPianException.class,
                () -> DongPianSearchService.requireId("../../etc/passwd"));
        assertThrows(DongPianSearchService.DongPianException.class,
                () -> DongPianSearchService.requireId("av abc"));
        assertThrows(DongPianSearchService.DongPianException.class,
                () -> DongPianSearchService.requireId(""));
        assertThrows(DongPianSearchService.DongPianException.class,
                () -> DongPianSearchService.requireId("short"));
    }

    /** 关键字按 UTF-8 百分号编码，空格必须是 %20 而不是 + */
    @Test
    void encodesKeywordAsUtf8Percent() {
        assertEquals("%E7%8B%82%E9%A3%99", DongPianSearchService.encode("狂飙"));
        assertEquals("a%20b", DongPianSearchService.encode("a b"));
    }

    /** 详情页链接走前端固定路由 /player/{作品ID} */
    @Test
    void buildsPlayerUrl() {
        assertEquals("https://dongpian19.com/player/av_xyz",
                DongPianSearchService.playerUrl("av_xyz"));
    }

    /** 卡片载荷与电影天堂同形，source 用 dpd 区分；空字段不下发，避免前端拿到 "null" 字符串 */
    @Test
    void payloadCarriesSourceAndSkipsBlankFields() {
        DongPianSearchService service = new DongPianSearchService(null);
        var payload = service.toPayload(new DongPianSearchService.DpdItem(
                "av_x", "狂飙", "2023", "中国大陆", "剧情,犯罪", "", "https://dongpian19.com/player/av_x",
                null, "全39集"));

        // 用独立的 kind 与字段，安卓端据此走懂片帝自己的渲染，不会落进 FilmCard
        assertEquals(DongPianSearchService.KIND_TITLE, payload.get("kind"));
        assertEquals("av_x", payload.get("dpdId"));
        assertEquals("狂飙", payload.get("title"));
        assertEquals("2023", payload.get("year"));
        assertEquals("全39集", payload.get("remarks"));
        assertEquals("https://dongpian19.com/player/av_x", payload.get("pageUrl"));
        assertFalse(payload.containsKey("score"));
        // 这几个键属于电影港 / 电影天堂的卡片结构，懂片帝的载荷不能带
        assertFalse(payload.containsKey("source"));
        assertFalse(payload.containsKey("playSources"));
        assertFalse(payload.containsKey("url"));
    }

    /** 详情载荷带上线路清单与集数，各集地址留空由前端按线路懒加载 */
    @Test
    void detailPayloadListsLinesWithoutItems() {
        DongPianSearchService service = new DongPianSearchService(null);
        var catalog = new DongPianSearchService.Catalog("av_x", "狂飙", "2023", "中国大陆", "剧情",
                "https://img.example.com/a.webp", "全39集", "8.5",
                List.of());
        var payload = service.toDetail(catalog,
                List.of(new DongPianSearchService.Line("豆瓣资源", 39)));

        assertEquals(DongPianSearchService.KIND_DETAIL, payload.get("kind"));
        assertEquals("https://img.example.com/a.webp", payload.get("cover"));
        assertFalse(payload.containsKey("playSources"));
        @SuppressWarnings("unchecked")
        var sources = (List<java.util.Map<String, Object>>) payload.get("dpdLines");
        assertEquals(1, sources.size());
        assertEquals("豆瓣资源", sources.getFirst().get("name"));
        assertEquals(39, sources.getFirst().get("total"));
        assertTrue(((List<?>) sources.getFirst().get("episodes")).isEmpty());
        assertTrue((Boolean) sources.getFirst().get("lazy"));
    }

    /** 线路各集用 episodes 字段下发，与详情同形 */
    @Test
    void linePayloadUsesEpisodesField() {
        DongPianSearchService service = new DongPianSearchService(null);
        var catalog = new DongPianSearchService.Catalog("av_x", "狂飙", "2023", "中国大陆", "剧情",
                "", "", null, List.of());
        var payload = service.toLinePayload(catalog, "豆瓣资源",
                List.of(new DongPianSearchService.PlayItem("第1集", "https://cdn.example.com/a/index.m3u8")));

        assertEquals(DongPianSearchService.KIND_LINE, payload.get("kind"));
        assertEquals("豆瓣资源", payload.get("line"));
        assertEquals(1, payload.get("total"));
        @SuppressWarnings("unchecked")
        var episodes = (List<java.util.Map<String, Object>>) payload.get("episodes");
        assertEquals("第1集", episodes.getFirst().get("title"));
        assertEquals("https://cdn.example.com/a/index.m3u8", episodes.getFirst().get("url"));
    }
}
