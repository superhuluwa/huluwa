package com.huluwa.android.data

import org.junit.Assert.*
import org.junit.Test

class BackendMappingTest {
    @Test fun progressIsAlreadyPercentEvenBelowOne() {
        val raw = ApiJson.parseToJsonElement("""{"id":5,"seriesTitle":"测试任务","status":"added","progress":0.5,"receivedBytes":512,"totalBytes":1024,"downloadSpeed":2048}""").asObject()
        val task = DownloadTask.from(raw, DownloadSource.Magnet)
        assertEquals(0.5f, task.progress!!, 0.0001f)
        assertTrue(task.active)
        assertEquals(512.0, task.downloaded!!, 0.0)
        assertEquals("2.0 KB/s", task.speed)
    }
    @Test fun windowsSuccessIsCompleteAndSourceIdsStayDistinct() {
        val raw = ApiJson.parseToJsonElement("""{"id":7,"name":"测试任务","status":"success","progress":1}""").asObject()
        val windows = DownloadTask.from(raw, DownloadSource.XunleiWin)
        assertTrue(windows.completed)
        assertEquals(100f, windows.progress!!, 0f)
        assertNotEquals(windows.key, DownloadTask.from(raw, DownloadSource.Hls).key)
    }
    @Test fun pausedHlsTaskDoesNotOfferRetry() {
        val raw = ApiJson.parseToJsonElement("""{"id":2,"seriesTitle":"测试剧集","status":"failed","paused":1,"progressPercent":null}""").asObject()
        val task = DownloadTask.from(raw, DownloadSource.Hls)
        assertTrue(task.paused)
        assertFalse(task.retryable)
        assertNull(task.progress)
    }
    @Test fun mapsSystemNotifyTitleAndCompleteBody() {
        val raw = ApiJson.parseToJsonElement("""{"id":12,"sessionId":"system","role":"bot","type":"notify","content":"第一行\n第二行\nhttps://example.test/detail","contentText":"电影港更新：测试剧集","createdAt":"2026-10-19 18:20:00","payload":"{\"level\":\"success\",\"source\":\"movie\",\"title\":\"电影港更新：测试剧集\"}"}""").asObject()
        val notice = ChatMessage.from(raw).notify
        assertNotNull(notice)
        assertEquals("success", notice?.level)
        assertEquals("成功", notice?.levelLabel)
        assertEquals("movie", notice?.source)
        assertEquals("电影港更新：测试剧集", notice?.title)
        assertEquals("第一行\n第二行\nhttps://example.test/detail", notice?.body)
    }
    @Test fun notifyFallsBackWhenPayloadHasNoMetadata() {
        val raw = ApiJson.parseToJsonElement("""{"id":13,"sessionId":"system","role":"bot","type":"notify","content":"完整正文","contentText":"通知标题","createdAt":"2026-10-19 18:21:00"}""").asObject()
        val notice = ChatMessage.from(raw).notify
        assertEquals("info", notice?.level)
        assertEquals("通知标题", notice?.title)
        assertEquals("完整正文", notice?.body)
    }
    @Test fun parsesNullableMessageFieldsAndStringEncodedFilmPayload() {
        val raw = ApiJson.parseToJsonElement("""{"id":9,"sessionId":"movie","role":"bot","type":"text","content":null,"contentText":null,"createdAt":"2026-10-19 09:41:00","payload":"{\"id\":\"123\",\"title\":\"测试片名\",\"url\":\"https://example.test/123\",\"cover\":null}"}""").asObject()
        val message = ChatMessage.from(raw)
        assertEquals("", message.content)
        assertEquals("测试片名", message.film?.title)
        assertEquals("", message.film?.cover)
    }
}
