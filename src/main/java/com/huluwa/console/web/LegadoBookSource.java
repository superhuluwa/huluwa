package com.huluwa.console.web;

import com.huluwa.console.service.source.BookSource;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 生成开源阅读（Legado）书源 JSON。
 *
 * <p>书源接口返回的是扁平 JSON，因此这里全部用 JSONPath 规则，
 * 不需要另外提供 HTML 页面或 JS 规则。</p>
 */
final class LegadoBookSource {

    private LegadoBookSource() { }

    /**
     * @param origin 葫芦娃的对外地址，如 https://huluwa.example.com
     * @param token  书源令牌；写进请求头，阅读器会给所有相对路径请求带上它
     */
    static Map<String, Object> json(BookSource source, String origin, String token) {
        String base = origin + "/api/sources/" + source.id();

        Map<String, Object> search = new LinkedHashMap<>();
        search.put("bookList", "$.data.books[*]");
        search.put("name", "$.name");
        search.put("author", "$.author");
        search.put("intro", "$.intro");
        search.put("kind", "$.kind");
        search.put("coverUrl", "$.coverUrl");
        search.put("lastChapter", "$.lastChapterName");
        search.put("wordCount", "$.wordCount");
        search.put("bookUrl", "$.bookId");

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
        // 章节地址使用接口直接返回的完整相对路径（见 BookSourceController 的 chapters 接口），
        // 避免在规则里混用变量和 JSONPath。
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
        // 阅读器的 {{page}} 从 1 开始，正好对应接口的页码语义；返回空列表时它会自动停止翻页。
        json.put("searchUrl", "/search?key={{key}}&page={{page}}");
        json.put("ruleSearch", search);

        json.put("bookUrlPattern", "^\\d{6,24}$");
        json.put("ruleBookInfo", bookInfo);

        json.put("ruleToc", toc);
        json.put("ruleContent", content);
        return json;
    }
}
