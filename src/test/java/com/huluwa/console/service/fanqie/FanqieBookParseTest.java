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
        // 旧版 multi-detail 接口的 data.book_info 结构
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
    void renamedBookKeepsItsOriginalNameWithoutChangingItsAuthorOrId() throws Exception {
        var node = JSON.readTree("""
                {"book_id":"7652970460308589592","book_name":"我是伪人惊悚世界唯一预言家",
                 "original_book_name":"伪人降临！我是全场唯一预言家",
                 "modified_reputation_book_name":"伪人降临！我是全场唯一预言家",
                 "author":"蘑菇炖猪头"}
                """);

        var book = FanqieClient.parseBookNode(node).getFirst();
        assertEquals("7652970460308589592", book.bookId());
        assertEquals("我是伪人惊悚世界唯一预言家", book.bookName());
        assertEquals("蘑菇炖猪头", book.author());
        assertEquals(List.of("伪人降临！我是全场唯一预言家"), book.alternateNames());
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

    /**
     * 实测 multi-detail 的 data 是书目对象数组，不是 book_info 包装对象。
     * 之前只按对象取，导致分享直达时书名退化成「书号 xxx」、封面和简介全空。
     */
    @Test
    void detailBookNodeAcceptsArrayData() throws Exception {
        var data = JSON.readTree("""
                [{"book_id":"7404527225137024025","book_name":"有钱后，我每天都很快乐",
                  "author":"花家的宝贝","abstract":"简介","thumb_url":"https://x/c.jpg",
                  "category":"都市脑洞","score":"6.7","creation_status":"1",
                  "word_number":"621984","last_chapter_title":"第285章，三姐妹的邀约"}]
                """);

        var book = FanqieClient.parseBookNode(FanqieClient.detailBookNode(data)).getFirst();

        assertEquals("7404527225137024025", book.bookId());
        assertEquals("有钱后，我每天都很快乐", book.bookName());
        assertEquals("花家的宝贝", book.author());
        assertEquals("https://x/c.jpg", book.thumbUrl());
        assertEquals("都市脑洞", book.category());
        assertEquals(6.7, book.score(), 1e-9);
        assertEquals(621984, book.wordNumber());
    }

    @Test
    void detailBookNodeStillSupportsWrappedObjects() throws Exception {
        var wrapped = JSON.readTree("""
                {"book_info":{"book_id":"123456789012","book_name":"新结构"}}
                """);
        var legacy = JSON.readTree("""
                {"book_data":{"book_id":"123456789012","book_name":"旧结构"}}
                """);
        var bare = JSON.readTree("""
                {"book_id":"123456789012","book_name":"扁平结构"}
                """);

        assertEquals("新结构", FanqieClient.detailBookNode(wrapped).path("book_name").asText());
        assertEquals("旧结构", FanqieClient.detailBookNode(legacy).path("book_name").asText());
        assertEquals("扁平结构", FanqieClient.detailBookNode(bare).path("book_name").asText());
    }

    @Test
    void detailBookNodeRejectsUnexpectedShapes() throws Exception {
        assertNull(FanqieClient.detailBookNode(null));
        assertNull(FanqieClient.detailBookNode(JSON.readTree("[]")));
        assertNull(FanqieClient.detailBookNode(JSON.readTree("\"text\"")));
        assertNull(FanqieClient.detailBookNode(JSON.readTree("{\"book_info\":\"bad\"}").path("book_info")));
    }
}
