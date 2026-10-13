package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HlsSupportTest {
    @Test
    void oneClickDefaultsRecognizeSeasonsAndKeepDigitsInSeriesNames() {
        assertEquals(2, HlsSupport.defaultSeason("剧名（第二季）更新至第03集"));
        assertEquals("剧名", HlsSupport.defaultTitle("剧名（第二季）更新至第03集"));
        assertEquals(3, HlsSupport.defaultSeason("剧名 Season 3"));
        assertEquals("剧名", HlsSupport.defaultTitle("剧名 S03"));
        assertEquals(1, HlsSupport.defaultSeason("庆余年2"));
        assertEquals("庆余年2", HlsSupport.defaultTitle("庆余年2"));
        assertEquals("剧名", HlsSupport.defaultTitle("剧名（全12集）"));
    }

    @Test
    void recognizesExplicitNumbersAndRejectsExtrasAndCombinedEpisodes() {
        assertEquals(2, HlsSupport.episodeNumber("第02集"));
        assertEquals(123, HlsSupport.episodeNumber("第一百二十三集"));
        assertEquals(101, HlsSupport.episodeNumber("第一〇一集"));
        assertEquals(12, HlsSupport.episodeNumber("剧名 S02E12"));
        assertEquals(6, HlsSupport.episodeNumber("EP06"));
        assertEquals(8, HlsSupport.episodeNumber("008"));
        assertNull(HlsSupport.episodeNumber("第01集预告"));
        assertNull(HlsSupport.episodeNumber("特别篇 第01集"));
        assertNull(HlsSupport.episodeNumber("S01E01-E02"));
        assertNull(HlsSupport.episodeNumber("S01E01-02"));
        assertNull(HlsSupport.episodeNumber("第1-2集"));
        assertNull(HlsSupport.episodeNumber("1080P"));
        assertFalse(HlsSupport.matchesSeason("S02E01", 1));
        assertFalse(HlsSupport.matchesSeason("第二季第01集", 1));
        assertTrue(HlsSupport.matchesSeason("第01集", 1));
    }

    @Test
    void keysAndNamesDoNotDependOnSignedUrls() {
        assertEquals(HlsSupport.seriesKey(null, "42", "原标题", "2026"),
                HlsSupport.seriesKey("https://dyttzyw.com/api.php/provide/vod", "42", "标准标题", ""));
        assertNotEquals(HlsSupport.seriesKey(null, "42", "剧名", "2026"),
                HlsSupport.seriesKey(null, "43", "剧名", "2026"));
        assertEquals("剧_名 S01E02", HlsSupport.filename("剧/名", 1, 2));
        assertThrows(BusinessException.class, () -> HlsSupport.safeName(".."));
        assertTrue(HlsSupport.safeName("剧".repeat(200)).getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= 180);
    }

    @Test
    void singleFeatureMovieUsesDefaultEpisodeOne() {
        DyttSearchService.PlaySource movie = new DyttSearchService.PlaySource("dyttm3u8", 1,
                java.util.List.of(new DyttSearchService.PlayItem("HD国语", "https://cdn.example/movie/index.m3u8")));
        assertEquals("https://cdn.example/movie/index.m3u8", HlsDownloadService.episodeUrl(movie, 1, 1));
        // 剧集线路仍然必须按集号严格匹配
        DyttSearchService.PlaySource series = new DyttSearchService.PlaySource("dyttm3u8", 1,
                java.util.List.of(new DyttSearchService.PlayItem("第01集", "https://cdn.example/1.m3u8")));
        assertThrows(BusinessException.class, () -> HlsDownloadService.episodeUrl(series, 1, 2));
        assertThrows(BusinessException.class, () -> HlsDownloadService.episodeUrl(movie, 1, 2));
    }

    /** 多版本无集号正片：默认第 1 集优先使用刚解析出的卡片地址，不靠列表位置猜。 */
    @Test
    void singleFeatureMoviePrefersTheResolvedAddress() {
        DyttSearchService.PlaySource versions = new DyttSearchService.PlaySource("dyttm3u8", 3,
                java.util.List.of(
                        new DyttSearchService.PlayItem("HD国语", "https://cdn.example/hd.m3u8?sign=1"),
                        new DyttSearchService.PlayItem("BD国语", "https://cdn.example/bd.m3u8?sign=1"),
                        new DyttSearchService.PlayItem("抢先版", "https://cdn.example/cam.m3u8?sign=1")));
        // 标题最稳定：签名刷新后仍能定位所选版本
        assertEquals("https://cdn.example/bd.m3u8?sign=1",
                HlsDownloadService.episodeUrl(versions, 1, 1, "https://cdn.example/bd.m3u8?sign=old", "BD国语"));
        // 标题缺失时回退完整地址，再回退忽略签名查询串的路径
        assertEquals("https://cdn.example/hd.m3u8?sign=1",
                HlsDownloadService.episodeUrl(versions, 1, 1, "https://cdn.example/hd.m3u8?sign=1", null));
        assertEquals("https://cdn.example/cam.m3u8?sign=1",
                HlsDownloadService.episodeUrl(versions, 1, 1, "https://cdn.example/cam.m3u8?sign=old", null));
        // 没有任何线索的多版本正片不按列表位置猜
        assertThrows(BusinessException.class, () -> HlsDownloadService.episodeUrl(versions, 1, 1));
    }

    @Test
    void validatesDirectLinksAndRedactsSecrets() {
        assertTrue(HlsDownloadService.isM3u8("https://cdn.example/1.m3u8?token=temporary"));
        assertThrows(BusinessException.class, () -> HlsDownloadService.m3u8("https://example/play/1.html"));
        assertThrows(BusinessException.class, () -> HlsSupport.httpUrl("file:///etc/passwd", "地址"));
        assertThrows(BusinessException.class, () -> HlsSupport.headers(null, "agent\r\nCookie: secret", null, null));
        String sanitized = HlsSupport.redact("https://cdn.example/1.m3u8?token=secret Cookie: private");
        assertFalse(sanitized.contains("secret"));
        assertFalse(sanitized.contains("private"));
    }
}
