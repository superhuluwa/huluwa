package com.huluwa.console.web;

import com.huluwa.console.service.source.BookSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生成开源阅读（Legado）书源 JSON。
 *
 * <p>搜索和目录先将每个条目转为独立 JSON 文本，再以 JSONPath 读取字段，
 * 避免阅读不同版本对 Map 条目的处理差异使书籍地址为空、回退为同一个搜索页地址。</p>
 *
 * <p>两点容易踩坑，务必保持：</p>
 * <ul>
 *   <li>本地导入的文件顶层必须是数组，单个书源也要包成 <code>[{...}]</code>。</li>
 *   <li>书源地址、搜索地址、书目与章节地址统一使用绝对 URL，
 *       不依赖阅读器对相对路径的拼接规则。</li>
 * </ul>
 */
final class LegadoBookSource {

    private LegadoBookSource() { }

    /** 书源 JSON 数组（阅读器本地导入要求的顶层结构）。 */
    static List<Map<String, Object>> json(BookSource source, String origin, String token) {
        return List.of(single(source, origin, token));
    }

    /**
     * @param origin 葫芦娃的对外地址，如 https://huluwa.example.com
     * @param token  书源令牌；写进请求头
     */
    static Map<String, Object> single(BookSource source, String origin, String token) {
        String base = origin + "/api/sources/" + source.id();

        Map<String, Object> search = new LinkedHashMap<>();
        search.put("bookList", searchItemsRule());
        search.put("name", "$.name");
        search.put("author", "$.author");
        search.put("intro", "$.intro");
        search.put("kind", "$.kind");
        search.put("coverUrl", "$.coverUrl");
        search.put("lastChapter", "$.lastChapterName");
        search.put("wordCount", "$.wordCount");
        search.put("bookUrl", "$.bookUrl");

        Map<String, Object> bookInfo = new LinkedHashMap<>();
        // 允许详情响应纠正阅读本地已经保存过的错误书名/作者。
        bookInfo.put("canReName", "true");
        bookInfo.put("name", "$.data.name");
        bookInfo.put("author", "$.data.author");
        bookInfo.put("intro", "$.data.intro");
        bookInfo.put("kind", "$.data.kind");
        bookInfo.put("coverUrl", "$.data.coverUrl");
        bookInfo.put("lastChapter", "$.data.lastChapterName");
        bookInfo.put("wordCount", "$.data.wordCount");
        // 目录在独立接口，必须显式指定；否则阅读器会拿详情页（单个对象）去跑目录规则，
        // $.data[*] 取不到数组，结果就是「目录为空」。
        bookInfo.put("tocUrl", "$.data.tocUrl");

        Map<String, Object> toc = new LinkedHashMap<>();
        toc.put("chapterList", chapterItemsRule());
        toc.put("chapterName", "$.title");
        toc.put("chapterUrl", "$.url");

        Map<String, Object> content = new LinkedHashMap<>();
        content.put("content", "$.data.content");

        Map<String, Object> json = new LinkedHashMap<>();
        json.put("bookSourceName", "番茄书源【葫芦娃】");
        json.put("bookSourceGroup", "番茄");
        json.put("bookSourceComment", "支持按书名、分享链接或书号搜索；搜索条目独立解析并校验书号与地址，详情同步书名作者，目录按原章节完整显示。更新后请覆盖导入本书源。");
        json.put("lastUpdateTime", System.currentTimeMillis());
        json.put("bookSourceUrl", base);
        json.put("bookSourceType", 0);
        json.put("customOrder", 1);
        json.put("enabled", true);
        // 阅读器只能配置静态请求头，令牌直接写在这份 JSON 里。
        json.put("header", "{\"Authorization\":\"Bearer " + token + "\"}");
        // {{page}} 从 1 开始，对应接口的页码语义；返回空列表时阅读器自动停止翻页。
        // 分享链接本身可能有 &、+、#，先编码整个关键字，防止它被拆成额外查询参数。
        json.put("searchUrl", base + "/search?key={{java.encodeURI(key)}}&page={{page}}");

        json.put("ruleSearch", search);
        json.put("ruleBookInfo", bookInfo);
        json.put("ruleToc", toc);
        json.put("ruleContent", content);
        return json;
    }

    private static String searchItemsRule() {
        return """
                @js:
                var payload = JSON.parse(String(result));
                var rows = payload.data && payload.data.books;
                if (!Array.isArray(rows)) throw new Error("书籍列表格式不正确");
                rows.map(function (row) {
                    var id = String(row.bookId || "");
                    var url = String(row.bookUrl || "");
                    var path = url.split(/[?#]/)[0];
                    var suffix = "/books/" + encodeURIComponent(id);
                    if (!id || !/^https?:\\/\\//i.test(url) ||
                        path.slice(-suffix.length) !== suffix) {
                        throw new Error("书籍地址与书号不一致，请更新葫芦娃书源");
                    }
                    return JSON.stringify(row);
                });
                """.strip();
    }

    private static String chapterItemsRule() {
        return """
                @js:
                var rows = JSON.parse(String(result)).data;
                if (!Array.isArray(rows)) throw new Error("章节列表格式不正确");
                rows.map(function (row) {
                    var id = String(row.chapterId || "");
                    var url = String(row.url || "");
                    var path = url.split(/[?#]/)[0];
                    var suffix = "/chapters/" + encodeURIComponent(id);
                    if (!id || !/^https?:\\/\\//i.test(url) ||
                        path.slice(-suffix.length) !== suffix) {
                        throw new Error("章节地址与章节号不一致，请刷新目录");
                    }
                    return JSON.stringify(row);
                });
                """.strip();
    }
}
