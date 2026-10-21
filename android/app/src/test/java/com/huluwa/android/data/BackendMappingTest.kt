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

    @Test fun mapsCheckLogFieldsLabelsAndDuration() {
        val raw = ApiJson.parseToJsonElement(
            """{"id":31,"source":"dytt","seriesTitle":"测试剧集","trigger":"manual","status":"updated",
               "foundCount":2,"submittedCount":1,"message":"发现新剧集","details":["第 5 集","第 6 集"],
               "durationMs":2500,"createdAt":"2026-10-20 08:00:00"}""").asObject()
        val log = CheckLog.from(raw)
        assertEquals("电影天堂", log.sourceLabel)
        assertEquals("立即检查", log.triggerLabel)
        assertEquals("有更新", log.statusLabel)
        assertTrue(log.hasUpdate)
        assertEquals(listOf("第 5 集", "第 6 集"), log.details)
        assertEquals("2.5 s", log.durationText)
    }

    @Test fun checkLogWithoutNewEntriesHidesUpdateCounts() {
        val raw = ApiJson.parseToJsonElement(
            """{"id":32,"source":"dygangs","seriesTitle":"无更新剧集","trigger":"scheduled","status":"ok","foundCount":0,"submittedCount":0}""").asObject()
        val log = CheckLog.from(raw)
        assertEquals("电影港", log.sourceLabel)
        assertEquals("定时巡检", log.triggerLabel)
        assertEquals("无更新", log.statusLabel)
        assertFalse(log.hasUpdate)
        assertEquals("—", log.durationText)
    }

    @Test fun mapsLibraryScanAndDeleteResult() {
        val scan = LibraryScan.from(ApiJson.parseToJsonElement(
            """{"mediaRoot":"/vol1/1000/downloads","writable":true,"totalBytes":2048,"scannedFiles":2,"seriesCount":1,
               "scannedAt":"2026-10-20 09:00:00","warning":"已跳过 1 个非视频文件",
               "entries":[{"id":"tok-1","seriesTitle":"测试剧集","seriesKey":"测试剧集","season":1,"episode":3,
                           "fileName":"测试剧集 S01E03.mp4","relativePath":"测试剧集/测试剧集 S01E03.mp4",
                           "size":2048,"modifiedAt":"2026-10-19 20:00:00","episodeRecognized":true}]}""").asObject())
        assertEquals("/vol1/1000/downloads", scan.mediaRoot)
        assertTrue(scan.writable)
        assertEquals(1, scan.entries.size)
        val entry = scan.entries.first()
        assertEquals("第 3 集", entry.episodeLabel)
        // 集数识别失败时退回文件名主标题，避免整行只剩「集数待识别」
        assertEquals("测试剧集 S01E03", entry.copy(episodeRecognized = false, episode = 0).episodeLabel)

        val result = LibraryDeleteResult.from(ApiJson.parseToJsonElement(
            """{"deleted":1,"failed":0,"freedBytes":2048,"deletedIds":["tok-1"],"message":"已删除 1 个文件"}""").asObject())
        assertEquals(setOf("tok-1"), result.deletedIds)
        assertEquals(2048L, result.freedBytes)
    }
}
