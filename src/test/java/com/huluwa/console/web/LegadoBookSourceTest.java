package com.huluwa.console.web;

import com.huluwa.console.service.source.BookSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 书源 JSON 结构测试，重点锁住阅读 App 导入要求的结构。 */
class LegadoBookSourceTest {

    private static final String ORIGIN = "http://taihe.678120.cn:8086";
    private static final BookSource SOURCE = new BookSource() {
        @Override public String id() { return "fanqie"; }
        @Override public String name() { return "番茄小说"; }
        @Override public SearchResult search(String keyword, String cursor, int size) {
            return new SearchResult(List.of(), null);
        }
        @Override public BookInfo book(String bookId) { return null; }
        @Override public List<Chapter> chapters(String bookId) { return List.of(); }
        @Override public ChapterContent chapter(String bookId, String chapterId) { return null; }
    };

    /** 阅读 App 本地导入要求顶层是数组，单个书源也要包成数组。 */
    @Test
    void topLevelIsArray() {
        List<Map<String, Object>> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1");

        assertEquals(1, json.size());
        assertTrue(json.getFirst() instanceof Map, "数组元素必须是书源对象");
    }

    @Test
    void sourceNameAndBaseUrl() {
        Map<String, Object> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1").getFirst();

        assertEquals("番茄书源【葫芦娃】", json.get("bookSourceName"));
        assertEquals(ORIGIN + "/api/sources/fanqie", json.get("bookSourceUrl"));
        assertEquals(0, json.get("bookSourceType"));
        assertEquals(Boolean.TRUE, json.get("enabled"));
    }

    @Test
    void addressesAreAbsoluteAndKeepTemplateVariables() {
        Map<String, Object> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1").getFirst();
        String searchUrl = (String) json.get("searchUrl");

        assertTrue(searchUrl.startsWith("http://"), "搜索地址必须是绝对地址");
        assertTrue(searchUrl.contains("{{java.encodeURI(key)}}"), "分享链接和口令必须作为完整关键字编码");
        assertTrue(searchUrl.contains("{{page}}"), "必须保留页码变量");
    }

    @Test
    void headerCarriesBearerToken() {
        Map<String, Object> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1").getFirst();
        String header = (String) json.get("header");

        assertTrue(header.contains("Bearer token-1"));
        assertTrue(header.trim().startsWith("{"), "请求头必须是 JSON 字符串");
        assertTrue(header.trim().endsWith("}"));
    }

    @Test
    void rulesUseJsonPathOnDataEnvelope() {
        Map<String, Object> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1").getFirst();

        @SuppressWarnings("unchecked")
        Map<String, Object> search = (Map<String, Object>) json.get("ruleSearch");
        @SuppressWarnings("unchecked")
        Map<String, Object> toc = (Map<String, Object>) json.get("ruleToc");
        @SuppressWarnings("unchecked")
        Map<String, Object> content = (Map<String, Object>) json.get("ruleContent");

        assertEquals("$.data.books[*]", search.get("bookList"));
        assertEquals("$.bookUrl", search.get("bookUrl"));
        assertEquals("$.data[*]", toc.get("chapterList"));
        assertEquals("$.url", toc.get("chapterUrl"));
        assertEquals("$.data.content", content.get("content"));
    }

    /**
     * 目录在独立接口，详情响应是单个对象。若不显式给出 tocUrl，
     * 阅读器会拿详情页跑 `$.data[*]`，结果目录为空。
     */
    @Test
    void bookInfoDeclaresSeparateTocUrl() {
        Map<String, Object> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1").getFirst();

        @SuppressWarnings("unchecked")
        Map<String, Object> bookInfo = (Map<String, Object>) json.get("ruleBookInfo");

        assertEquals("$.data.tocUrl", bookInfo.get("tocUrl"));
    }

    /** 移除的字段曾被阅读器版本差异影响，不应再出现。 */
    @Test
    void unstableFieldsAreOmitted() {
        Map<String, Object> json = LegadoBookSource.json(SOURCE, ORIGIN, "token-1").getFirst();

        assertFalse(json.containsKey("concurrentRate"));
        assertFalse(json.containsKey("bookUrlPattern"));
        assertTrue(json.containsKey("ruleBookInfo"));
    }
}
