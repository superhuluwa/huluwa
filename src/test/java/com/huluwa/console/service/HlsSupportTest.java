package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HlsSupportTest {
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
