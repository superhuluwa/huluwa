package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电影港搜索结果解析单测：用真实结果页快照（src/test/resources/dygangs-search-fanren.html，
 * 抓取关键字「凡人」）锁定解析契约，站点模板变动时这里会先失败。
 */
class MovieSearchServiceTest {

    private static String fixture() throws Exception {
        try (InputStream in = MovieSearchServiceTest.class.getResourceAsStream("/dygangs-search-fanren.html")) {
            assertNotNull(in, "缺少测试用例资源 dygangs-search-fanren.html");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parseExtractsIdTitleIntroAndCover() throws Exception {
        List<MovieSearchService.MovieItem> items = MovieSearchService.parse(fixture());

        assertFalse(items.isEmpty(), "应解析出结果");

        MovieSearchService.MovieItem first = items.get(0);
        assertEquals("45148", first.id());
        assertEquals("凡人修仙传", first.title());
        assertEquals("2020", first.year());
        assertEquals("中国大陆", first.origin());
        assertEquals("动画/奇幻/武侠", first.genre().replace(" ", ""));
        assertEquals("https://www.dygangs.net/dmq/20200727/45148.htm", first.url());
        assertEquals("https://tu.66tutup.com:667/2022/8904.jpg", first.cover());

        // 第二条是真人版（剧集），ID 与简介字段独立解析
        MovieSearchService.MovieItem second = items.get(1);
        assertEquals("57617", second.id());
        assertEquals("2025", second.year());
        assertTrue(second.cover().startsWith("https://"));
    }

    /** 封面必须按条目切块：取「上一条锚点结束」到「本条锚点」之间的 img，避免串用封面 */
    @Test
    void coverIsScopedToItsOwnEntry() throws Exception {
        List<MovieSearchService.MovieItem> items = MovieSearchService.parse(fixture());
        MovieSearchService.MovieItem first = items.stream()
                .filter(i -> "45148".equals(i.id())).findFirst().orElseThrow();
        MovieSearchService.MovieItem second = items.stream()
                .filter(i -> "57617".equals(i.id())).findFirst().orElseThrow();
        assertEquals("https://tu.66tutup.com:667/2022/8904.jpg", first.cover());
        assertEquals("https://tu.66tutup.com:667/2025/1570.jpg", second.cover());
        assertFalse(first.cover().equals(second.cover()), "每条封面应各自对应自己的条目");
    }

    @Test
    void idFromAcceptsNumericDetailPagesOnly() {
        assertEquals("45148", MovieSearchService.idFrom("/dmq/20200727/45148.htm"));
        assertEquals("45148", MovieSearchService.idFrom("https://www.dygangs.net/dmq/20200727/45148.html"));
        assertNull(MovieSearchService.idFrom("/dyzt/20190801/abc.htm"));
        assertNull(MovieSearchService.idFrom(null));
        assertNull(MovieSearchService.idFrom(""));
    }

    @Test
    void percentEncodingUsesGb2312Bytes() {
        // 「凡人」在 GB2312 下为 B7 B2 C8 CB
        assertEquals("%B7%B2%C8%CB", MovieSearchService.percentEncodeGb("凡人"));
    }

    @Test
    void fieldAfterStopsAtNextSection() {
        String flat = MovieSearchService.flatten("◎年代　2020\r\n◎产地　中国大陆\r\n◎类别　动画 / 奇幻");
        assertEquals("2020", MovieSearchService.fieldAfter(flat, "年代"));
        assertEquals("中国大陆", MovieSearchService.fieldAfter(flat, "产地"));
        assertEquals("动画/奇幻", MovieSearchService.fieldAfter(flat, "类别").replace(" ", ""));
    }

    @Test
    void normalizeKeywordStripsCommandPrefixOnly() {
        assertEquals("凡人", ChatService.normalizeKeyword("电影港搜索 凡人"));
        assertEquals("凡人", ChatService.normalizeKeyword("电影港 凡人"));
        assertEquals("2046", ChatService.normalizeKeyword("2046"));
        assertEquals("复仇者联盟", ChatService.normalizeKeyword("复仇者联盟"));
        // 非指令前缀保持原样（只按空白切分，不做模糊匹配）
        assertEquals("复仇者 联盟", ChatService.normalizeKeyword("复仇者 联盟"));
    }
}
