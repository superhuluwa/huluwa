package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 书源搜索游标：页码从 1 开始，换算成上游 offset。 */
class FanqieSearchCursorTest {

    @Test
    void blankCursorStartsFromFirstPage() {
        var cursor = FanqieSearchService.SearchCursor.parse(null);

        assertEquals(1, cursor.page());
        assertEquals(0, cursor.offset(20));
    }

    @Test
    void pageNumberIsUsedDirectly() {
        assertEquals(3, FanqieSearchService.SearchCursor.parse("3").page());
        assertEquals(40, FanqieSearchService.SearchCursor.parse("3").offset(20));
    }

    @Test
    void invalidValuesFallBackToFirstPage() {
        assertEquals(1, FanqieSearchService.SearchCursor.parse("abc").page());
        assertEquals(1, FanqieSearchService.SearchCursor.parse("").page());
        assertEquals(1, FanqieSearchService.SearchCursor.parse("0").page());
        assertEquals(1, FanqieSearchService.SearchCursor.parse("-5").page());
    }

    @Test
    void legacyCursorWithPassbackOnlyReadsPagePart() {
        // 兼容早期 "offset:passback" 形式的游标，只取前半段页码
        assertEquals(2, FanqieSearchService.SearchCursor.parse("2:abcdef").page());
    }
}
