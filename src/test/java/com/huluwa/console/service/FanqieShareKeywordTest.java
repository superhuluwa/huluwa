package com.huluwa.console.service;

import com.huluwa.console.service.fanqie.FanqieShareLinks;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 番茄分享口令的关键字归一化：真实分享文案不能整段拿去搜索（会超过 60 字上限），
 * 带书名号时优先取书名，否则剥掉链接再搜。
 */
class FanqieShareKeywordTest {

    private static final String SHARE =
            "推荐一部好书《神豪：开局暴击美艳少妇双胞胎》https://changdunovel.com/t/UV9Yi-XDcr0/";

    @Test
    void shareTextFallsBackToBookTitle() {
        assertEquals("神豪：开局暴击美艳少妇双胞胎", ChatService.fanqieSearchKeyword(SHARE));
    }

    @Test
    void shareTextWithoutTitleDropsLink() {
        String keyword = ChatService.fanqieSearchKeyword("看看这本 https://changdunovel.com/t/UV9Yi-XDcr0/");
        assertEquals("看看这本", keyword);
        // 原分享文案 61 字，剥掉链接后必须落在上游 60 字上限之内
        assertEquals("推荐一部好书", ChatService.fanqieSearchKeyword(
                "推荐一部好书https://changdunovel.com/t/UV9Yi-XDcr0/"));
    }

    @Test
    void plainKeywordAndPrefixAreUnchanged() {
        assertEquals("斗罗大陆", ChatService.fanqieSearchKeyword("斗罗大陆"));
        assertEquals("斗罗大陆", ChatService.fanqieSearchKeyword("番茄小说搜索 斗罗大陆"));
        assertEquals("斗罗大陆", ChatService.fanqieSearchKeyword("fanqie 斗罗大陆"));
    }

    @Test
    void otherSiteLinksAreKeptVerbatim() {
        // 非番茄链接不属于分享分支，保持原样交给搜索
        String text = "https://www.douyin.com/video/123456";
        assertEquals(text, ChatService.fanqieSearchKeyword(text));
        assertNull(FanqieShareLinks.firstUrl(text));
    }

    @Test
    void shareTextStillHasFanqieLink() {
        assertEquals("https://changdunovel.com/t/UV9Yi-XDcr0/", FanqieShareLinks.firstUrl(SHARE));
    }
}
