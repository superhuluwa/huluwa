package com.huluwa.console.web;

import com.huluwa.console.service.source.BookSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成开源阅读（Legado）书源 JSON。
 *
 * <p>书源接口返回的是扁平 JSON，因此全部使用 JSONPath 规则，不需要 HTML 页面或 JS 规则。</p>
 *
 * <p>地址约定：bookSourceUrl 以 /api/sources/ 结尾，接口返回的 bookUrl、chapterUrl
 * 都是相对该地址的路径（如 fanqie/books/xxx），阅读器按相对路径解析即可。</p>
 */
final class LegadoBookSource {

    private LegadoBookSource() { }

    /**
     * @param origin 葫芦娃的对外地址，如 https://huluwa.example.com
     * @param token  书源令牌；写进请求头，阅读器会给所有请求带上它
     */
    static Map<String, Object> json(BookSource source, String origin, String token) {
        String base = origin + "/api/sources/";

        Map<String, Object> search = new LinkedHashMap<>();
        search.put("bookList", "$.data.books[*]");
        search.put("name", "$.name");
        search.put("author", "$.author");
        search.put("intro", "$.intro");
        search.put("kind", "$.kind");
        search.put("coverUrl", "$.coverUrl");
        search.put("lastChapter", "$.lastChapterName");
        search.put("wordCount", "$.wordCount");
        // 搜索接口返回可直接请求的相对路径
        search.put("bookUrl", "$.bookUrl");

        Map<String, Object> bookInfo = new LinkedHashMap<>();
        bookInfo.put("name", "$.data.name");
        bookInfo.put("author", "$.data.author");
        bookInfo.put("intro", "$.data.intro");
        bookInfo.put("kind", "$.data.kind");
        bookInfo.put("coverUrl", "$.data.coverUrl");
        bookInfo.put("lastChapter", "$.data.lastChapterName");
        bookInfo.put("wordCount", "$.data.wordCount");

        Map<String, Object> toc = new LinkedHashMap<>();
        toc.put("chapterList", "$.data[*]");
        toc.put("chapterName", "$.title");
        // 目录接口每条自带完整相对路径，避免在规则里混用变量和 JSONPath。
        toc.put("chapterUrl", "$.url");

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("content", "$.data.content");

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("bookSourceName", "番茄小说（Huluwa）");
        json.put("bookSourceGroup", "番茄");
        json.put("bookSourceUrl", base);
        json.put("bookSourceType", 0);
        json.put("customOrder", 1);
        json.put("enabled", true);
        // 阅读器只能配置静态请求头，令牌直接写在这份 JSON 里；生成接口只在登录态下返回。
        json.put("header", "{\"Authorization\":\"Bearer " + token + "\"}");
        // {{page}} 从 1 开始，对应接口的页码语义；返回空列表时阅读器自动停止翻页。
        json.put("searchUrl", source.id() + "/search?key={{key}}&page={{page}}");

        json.put("ruleSearch", search);
        json.put("ruleBookInfo", bookInfo);
        json.put("ruleToc", toc);
        json.put("ruleContent", content);
        // 详情页地址由搜索/目录返回，不需要再用正则从页面里抠书号。
        json.put("bookUrlPattern", ".*/books/(\\d{6,24})$");
        return json;
    }
}
