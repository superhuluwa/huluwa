package com.huluwa.console.web;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.source.BookExportService;
import com.huluwa.console.service.source.BookSource;
import com.huluwa.console.service.source.BookSourceOrigin;
import com.huluwa.console.service.source.BookSourceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 改名长篇与同名短篇在搜索、详情和目录间必须保留各自的书号与作者。 */
class BookSourceAliasTest {
    private static final String OLD_NAME = "伪人降临！我是全场唯一预言家";
    private static final String LONG_ID = "7652970460308589592";
    private static final String SHORT_ID = "7664448249784257561";
    private static final BookSource.BookInfo LONG_BOOK = new BookSource.BookInfo(
            LONG_ID, "我是伪人惊悚世界唯一预言家", "蘑菇炖猪头", "", "", "", "第490章 快枪手",
            1034681, false, List.of(OLD_NAME));
    private static final BookSource.BookInfo SHORT_BOOK = new BookSource.BookInfo(
            SHORT_ID, OLD_NAME, "小北梦鸭", "", "", "", OLD_NAME, 21340, true);

    private final BookSource source = mock(BookSource.class);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private BookSourceController controller;

    @BeforeEach
    void setUp() {
        when(source.id()).thenReturn("fanqie");
        when(source.book(LONG_ID)).thenReturn(LONG_BOOK);
        when(source.book(SHORT_ID)).thenReturn(SHORT_BOOK);
        controller = new BookSourceController(new BookSourceRegistry(List.of(source)),
                mock(BookExportService.class), new BookSourceOrigin("https://books.example.com"));
    }

    @Test
    void aliasSearchKeepsBothAuthorsAndCarriesTheSelectedNameIntoTheLongBookDetail() {
        when(source.search("伪人降临", null, 20))
                .thenReturn(new BookSource.SearchResult(List.of(LONG_BOOK, SHORT_BOOK), null));
        when(source.chapters(LONG_ID)).thenReturn(List.of(
                new BookSource.Chapter("7654492096971162136", "第1章 我是预言家", 1),
                new BookSource.Chapter("7654833807182266904", "第2章 新的预言家", 2)));

        List<Map<String, Object>> books = search("伪人降临");
        assertEquals(2, books.size());
        assertEquals(OLD_NAME, books.getFirst().get("name"));
        assertEquals(OLD_NAME, books.getLast().get("name"));
        assertEquals("蘑菇炖猪头", books.getFirst().get("author"));
        assertEquals("小北梦鸭", books.getLast().get("author"));
        assertNotEquals(books.getFirst().get("bookUrl"), books.getLast().get("bookUrl"));

        URI url = URI.create((String) books.getFirst().get("bookUrl"));
        assertEquals("/api/sources/fanqie/books/" + LONG_ID, url.getPath());
        String selectedName = URLDecoder.decode(url.getRawQuery().substring("title=".length()), StandardCharsets.UTF_8);
        Map<String, Object> detail = controller.book("fanqie", LONG_ID, selectedName, request).data();
        assertEquals(OLD_NAME, detail.get("name"));
        assertEquals("蘑菇炖猪头", detail.get("author"));
        assertEquals(LONG_ID, detail.get("bookId"));
        assertEquals("https://books.example.com/api/sources/fanqie/books/" + LONG_ID + "/chapters", detail.get("tocUrl"));
        List<Map<String, Object>> chapters = controller.chapters("fanqie", LONG_ID, request).data();
        assertEquals(List.of("第1章 我是预言家", "第2章 新的预言家"),
                chapters.stream().map(chapter -> chapter.get("title")).toList());
    }

    @Test
    void currentTitleSearchKeepsZuiMingBuXiuAndItsSeparateShortStory() {
        BookSource.BookInfo novel = new BookSource.BookInfo("7623686597409508376", "罪名不朽",
                "我爱吃猫片哇", "", "", "", "第461章", 1033764, false,
                List.of("全球死考：刚下刑场你让我去考试"));
        BookSource.BookInfo shortStory = new BookSource.BookInfo("7665194585055906841", "罪名不朽",
                "小北梦鸭", "", "", "", "罪名不朽", 18944, true);
        when(source.search("罪名不朽", null, 20))
                .thenReturn(new BookSource.SearchResult(List.of(novel, shortStory), null));

        List<Map<String, Object>> books = search("罪名不朽");
        assertEquals(List.of("罪名不朽", "罪名不朽"), books.stream().map(book -> book.get("name")).toList());
        assertEquals(List.of("我爱吃猫片哇", "小北梦鸭"), books.stream().map(book -> book.get("author")).toList());
        assertNull(URI.create((String) books.getFirst().get("bookUrl")).getRawQuery());
        assertNotEquals(books.getFirst().get("bookUrl"), books.getLast().get("bookUrl"));
    }

    @Test
    void aDifferentBooksTitleCannotBeAttachedToTheShortStoryId() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> controller.book("fanqie", SHORT_ID, LONG_BOOK.name(), request));
        assertEquals(400, error.getCode());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> search(String keyword) {
        return (List<Map<String, Object>>) controller.search("fanqie", keyword, null, null, 20, request)
                .data().get("books");
    }
}
