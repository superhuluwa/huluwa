package com.huluwa.android.data

import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

/**
 * 请求体构造的回归测试：OkHttp 的 Request.Builder.method() 对 POST / PUT / PATCH
 * 在 payload 为 null 时直接抛 IllegalArgumentException（"method POST must have a request body."），
 * 请求根本发不出去。标记已读、Frp 热加载 / 重启、容器启停、订阅检查都是无 body 的 POST，
 * 一旦退化就会表现为「点进系统通知、未读数不消失」这类静默失败。
 */
class ApiClientRequestBodyTest {
    /** 走与线上一致的构造路径：能 build 出请求才算真的发得出去 */
    private fun build(method: String, body: String?): Request =
        Request.Builder()
            .url(ServerAddress.endpoint("http://example.test:8000", listOf("chat", "sessions", "system", "read")))
            .method(method, payloadFor(method, body?.let { ApiJson.parseToJsonElement(it) }))
            .build()

    /** 无 body 的 POST 必须补空 JSON，否则 OkHttp 在本地就抛错 */
    @Test fun postWithoutBodyGetsEmptyJson() {
        val request = build("POST", null)
        assertEquals("POST", request.method)
        assertNotNull("POST 必须带请求体", request.body)
        assertEquals(2L, request.body!!.contentLength())
    }

    /** 标记已读就是这条路径，单独点名锁住 */
    @Test fun markReadPostIsSendable() {
        val request = build("POST", null)
        assertEquals("/api/chat/sessions/system/read", request.url.encodedPath)
        assertNotNull(request.body)
    }

    /** 带 body 的 POST 原样透传，不做二次包装 */
    @Test fun postWithBodyKeepsPayload() {
        val request = build("POST", """{"content":"hi"}""")
        assertEquals("""{"content":"hi"}""", request.body!!.toString())
    }

    /** DELETE 可以没有正文：批量删除才带 body */
    @Test fun deleteBodyFollowsArgument() {
        assertNull(build("DELETE", null).body)
        assertNotNull(build("DELETE", """[1,2]""").body)
    }

    /** GET 不应携带任何请求体 */
    @Test fun getHasNoBody() {
        assertNull(payloadFor("GET", ApiJson.parseToJsonElement("""{"ignored":true}""")))
    }
}
