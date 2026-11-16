package com.huluwa.console.service.source;

import java.util.List;

/**
 * 书源契约：参考开源阅读（Legado）的书源模型，抽象「搜索 → 详情 → 目录 → 正文」四段能力。
 *
 * <p>实现方负责抓取与解析，接口层只做统一的字段映射和分页，
 * 这样新增站点时不必再改控制器和客户端。</p>
 */
public interface BookSource {

    /** 书源标识，用于接口路径，如 fanqie。 */
    String id();

    /** 展示名称。 */
    String name();

    /** 搜索书目；cursor 为上一页返回的 {@link SearchResult#nextCursor()}，首页传 null。 */
    SearchResult search(String keyword, String cursor, int size);

    /** 书籍详情；找不到时返回 null。 */
    BookInfo book(String bookId);

    /** 完整目录。 */
    List<Chapter> chapters(String bookId);

    /** 单章正文。 */
    ChapterContent chapter(String bookId, String chapterId);

    /** 搜索结果。 */
    record SearchResult(List<BookInfo> books, String nextCursor) { }

    /**
     * 统一书目信息。
     *
     * @param bookId          书号（书源内部唯一 ID）
     * @param name            书名
     * @param author          作者
     * @param intro           简介
     * @param kind            分类
     * @param coverUrl        封面
     * @param lastChapterName 最新章节名
     * @param wordCount       字数
     * @param finished        是否完结，null 表示未知
     * @param alternateNames  同一书号的原书名或别名，用于跨书源换源时匹配
     */
    record BookInfo(String bookId, String name, String author, String intro, String kind,
                    String coverUrl, String lastChapterName, long wordCount, Boolean finished,
                    List<String> alternateNames) {
        public BookInfo {
            alternateNames = alternateNames == null ? List.of() : List.copyOf(alternateNames);
        }

        public BookInfo(String bookId, String name, String author, String intro, String kind,
                        String coverUrl, String lastChapterName, long wordCount, Boolean finished) {
            this(bookId, name, author, intro, kind, coverUrl, lastChapterName, wordCount, finished, List.of());
        }
    }

    /** 目录章节。 */
    record Chapter(String chapterId, String title, int order) { }

    /**
     * 章节正文。
     *
     * @param chapterId 章节 ID
     * @param title     章节标题
     * @param content   纯文本正文，段落之间用 \n 分隔
     */
    record ChapterContent(String chapterId, String title, String content) { }
}
