package com.huluwa.console.service.fanqie;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FanqieShareLinksTest {

    /** 真实分享文案：说明文字紧贴书名号和短链。 */
    private static final String SHARE =
            "推荐一部好书《神豪：开局暴击美艳少妇双胞胎》https://changdunovel.com/t/UV9Yi-XDcr0/";

    @Test
    void firstUrlFindsShortLinkGluedToChineseText() {
        assertEquals("https://changdunovel.com/t/UV9Yi-XDcr0/", FanqieShareLinks.firstUrl(SHARE));
        assertEquals("https://changdunovel.com/t/UV9Yi-XDcr0/",
                FanqieShareLinks.firstUrl("看看这个 https://changdunovel.com/t/UV9Yi-XDcr0/。"));
    }

    @Test
    void firstUrlIgnoresNonFanqieLinks() {
        assertNull(FanqieShareLinks.firstUrl("https://www.douyin.com/video/123456"));
        assertNull(FanqieShareLinks.firstUrl("只有一段没有链接的文字"));
        assertNull(FanqieShareLinks.firstUrl(""));
        assertNull(FanqieShareLinks.firstUrl(null));
    }

    @Test
    void hostMatchIsSuffixBound() {
        assertTrue(FanqieShareLinks.isFanqieHost("https://changdunovel.com/t/abc/"));
        assertTrue(FanqieShareLinks.isFanqieHost("https://fanqienovel.com/page/7520618609094183960"));
        assertTrue(FanqieShareLinks.isFanqieHost("https://x.zlink.fqnovel.com/dhVGe"));
        // 伪装域名不能被当成番茄
        assertFalse(FanqieShareLinks.isFanqieHost("https://evi-lchangdunovel.com/t/abc/"));
        assertFalse(FanqieShareLinks.isFanqieHost("https://changdunovel.com.evil.test/t/abc/"));
    }

    @Test
    void bookIdComesFromQueryOrPath() {
        // 短链本身没有书号，跟随 302 后的落地页才有
        assertNull(FanqieShareLinks.bookIdIn("https://changdunovel.com/t/UV9Yi-XDcr0/"));
        assertEquals("7520618609094183960", FanqieShareLinks.bookIdIn(
                "https://changdunovel.com/ug/pages/book-share?share_type=11&aid=1967"
                        + "&book_id=7520618609094183960&share_genre=read"));
        // report_params 里的 content_id 是 URL 编码的，不能误当成查询参数
        assertEquals("7520618609094183960", FanqieShareLinks.bookIdIn(
                "https://fanqienovel.com/reader/7520618609094183960?enter_from=search"));
        assertEquals("7520618609094183960", FanqieShareLinks.bookIdIn(
                "https://fanqienovel.com/page/7520618609094183960/"));
        assertNull(FanqieShareLinks.bookIdIn("https://changdunovel.com/ug/pages/book-share?aid=1967"));
    }

    @Test
    void sharedTitlePrefersBookTitleMarks() {
        assertEquals("神豪：开局暴击美艳少妇双胞胎", FanqieShareLinks.sharedTitle(SHARE));
        assertNull(FanqieShareLinks.sharedTitle("没有书名号的一段话"));
        assertNull(FanqieShareLinks.sharedTitle(null));
    }

    @Test
    void stripLinksDropsUrlAndKeepsReadableText() {
        String stripped = FanqieShareLinks.stripLinks(SHARE);
        assertFalse(stripped.contains("http"));
        assertFalse(stripped.contains("changdunovel"));
        assertTrue(stripped.contains("推荐一部好书"));
        // 有书名号时调用方走书名搜索，这里只保证不残留 URL
        assertTrue(FanqieShareLinks.stripLinks(SHARE).startsWith("推荐一部好书"));
        // 只有链接时结果为空，调用方据此保留原文
        assertEquals("", FanqieShareLinks.stripLinks("https://changdunovel.com/t/UV9Yi-XDcr0/"));
    }
}
