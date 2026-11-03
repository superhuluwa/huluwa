package com.huluwa.console.service.fanqie;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 书目解析的纯逻辑测试：不访问网络，只校验字段映射。 */
class FanqieBookParseTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void parsesDetailBookInfo() throws Exception {
        // multi-detail 接口的 data.book_info 结构
        var node = JSON.readTree("""
                {"book_id":"7143038691944959011","book_name":"十日终焉","author":"杀虫队队员",
                 "abstract":"简介文本","word_number":123456,"creation_status":0,
                 "score":8.9,"read_count":12345,"category":"悬疑","thumb_url":"https://x/a.jpg",
                 "last_chapter_title":"第100章"}
                """);

        List<FanqieClient.Book> books = FanqieClient.parseBookNode(node);

        assertEquals(1, books.size());
        var book = books.getFirst();
        assertEquals("7143038691944959011", book.bookId());
        assertEquals("十日终焉", book.bookName());
        assertEquals("杀虫队队员", book.author());
        assertEquals("简介文本", book.abstractText());
        assertEquals(123456, book.wordNumber());
        assertEquals(8.9, book.score(), 1e-9);
        assertEquals("悬疑", book.category());
        assertEquals("第100章", book.lastChapterTitle());
        assertFalse(book.finished());
    }

    @Test
    void finishedIsTrueWhenCreationStatusIsOne() throws Exception {
        var node = JSON.readTree("""
                {"book_id":"123456789012","book_name":"完结书","creation_status":1}
                """);

        assertTrue(FanqieClient.parseBookNode(node).getFirst().finished());
    }

    @Test
    void unknownCreationStatusIsNotTreatedAsFinished() throws Exception {
        var node = JSON.readTree("""
                {"book_id":"123456789012","book_name":"状态未知"}
                """);

        var book = FanqieClient.parseBookNode(node).getFirst();
        assertFalse(book.finished());
        assertNull(book.creationStatus());
    }

    @Test
    void bookWithoutIdIsDropped() throws Exception {
        // 缺少书号无法定位书籍，书源里必须过滤
        assertTrue(FanqieClient.parseBookNode(JSON.readTree("{\"book_name\":\"无编号\"}")).isEmpty());
        assertTrue(FanqieClient.parseBookNode(JSON.readTree("[]")).isEmpty());
        assertTrue(FanqieClient.parseBookNode(null).isEmpty());
    }

    @Test
    void highlightTextIsUsedAsFallback() throws Exception {
        var book = JSON.readTree("""
                {"author":"某作者"}
                """);
        var highlight = JSON.readTree("""
                {"title":{"text":"高亮书名"},"abstract":{"text":"高亮简介"}}
                """);

        var parsed = FanqieClient.toBook(book, highlight, "999999999999");

        assertEquals("高亮书名", parsed.bookName());
        assertEquals("高亮简介", parsed.abstractText());
        assertEquals("某作者", parsed.author());
    }

    @Test
    void numericFieldsAcceptStringValues() throws Exception {
        // 部分响应把数字序列化成字符串
        var node = JSON.readTree("""
                {"book_id":"123456789012","word_number":"50000","score":"7.5",
                 "creation_status":"1","read_count":"88"}
                """);

        var book = FanqieClient.parseBookNode(node).getFirst();
        assertEquals(50000, book.wordNumber());
        assertEquals(7.5, book.score(), 1e-9);
        assertEquals(88, book.readCount());
        assertTrue(book.finished());
    }
}
