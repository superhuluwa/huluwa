package com.huluwa.android.data

import org.junit.Assert.*
import org.junit.Test

/**
 * 会话缓存的读写规则回归测试。
 *
 * 重点锁住三件事：
 * 1. 只缓存已落库（id > 0）且发送成功的消息，否则「发送中 / 发送失败」的气泡会在下次进入时复活；
 * 2. 只保留最新一页，缓存文件不会随聊天记录无限增长；
 * 3. 编码后再解码必须与原消息一致，界面依赖的正文、payload 与时间都要完整。
 */
class ChatCacheTest {
    private fun message(id: Long, content: String = "消息 $id", delivery: Delivery = Delivery.Sent, payload: String = "{}") =
        ChatMessage(id, "session-a", if (id % 2 == 0L) "bot" else "user", "text", content, content,
            ApiJson.parseToJsonElement(payload).asObject(), "2026-10-21 10:00:0${id % 10}", delivery)

    /** 发送中与发送失败的本地消息（负 id）不能进缓存，否则重进会话会出现「发送中」的幽灵气泡 */
    @Test fun pendingAndFailedMessagesAreSkipped() {
        val rows = listOf(
            message(1), message(-1, delivery = Delivery.Sending), message(-2, delivery = Delivery.Failed),
            message(2), message(0),
        )
        val page = ChatCache.latestPage(rows)
        assertEquals(listOf(1L, 2L), page.map { it.id })
    }

    /** 只保留最新一页：超出分页大小时裁掉更早的消息 */
    @Test fun keepsOnlyLatestPage() {
        val rows = (1L..80L).map { message(it) }
        val page = ChatCache.latestPage(rows)
        assertEquals(ChatCache.PAGE_SIZE, page.size)
        assertEquals(80L, page.last().id)
        assertEquals(31L, page.first().id)
    }

    /** 乱序与重复（轮询合并后可能重复）都要按 id 去重并排好序 */
    @Test fun deduplicatesAndSortsById() {
        val page = ChatCache.latestPage(listOf(message(3), message(1), message(3), message(2)))
        assertEquals(listOf(1L, 2L, 3L), page.map { it.id })
    }

    /** 编码 → 解码 round-trip：正文、payload、时间与角色都不能丢 */
    @Test fun encodeDecodeRoundTripsMessage() {
        val original = message(42, content = "带 <b>标签</b> 的正文", payload = """{"title":"片名","cover":"http://x/c.jpg"}""")
        val decoded = ChatCache.decode(ChatCache.encode(listOf(original))).single()
        assertEquals(original.id, decoded.id)
        assertEquals(original.sessionId, decoded.sessionId)
        assertEquals(original.role, decoded.role)
        assertEquals(original.type, decoded.type)
        assertEquals(original.content, decoded.content)
        assertEquals(original.contentText, decoded.contentText)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.payload, decoded.payload)
        assertEquals(Delivery.Sent, decoded.delivery)
    }

    /** 空列表编码后必须能读回空列表，而不是抛异常 */
    @Test fun emptyListRoundTrips() {
        assertEquals(emptyList<ChatMessage>(), ChatCache.decode(ChatCache.encode(emptyList())))
        assertTrue(ChatCache.latestPage(emptyList()).isEmpty())
    }

    /** payload 里嵌套的对象与数组要原样保留（影视卡片、图片列表依赖它） */
    @Test fun decodeKeepsNestedPayload() {
        val raw = """[{"id":7,"sessionId":"s","role":"bot","type":"text","content":"hi","contentText":"hi",""" +
            """"payload":{"kind":"video","images":[{"url":"http://x/1.jpg"}]},"createdAt":"2026-10-21 10:00:00"}]"""
        val decoded = ChatCache.decode(raw).single()
        assertEquals("video", decoded.payload.text("kind"))
        assertEquals(1, decoded.payload.rows("images").size)
        assertEquals("http://x/1.jpg", decoded.payload.rows("images").first().asObject().text("url"))
    }
}
