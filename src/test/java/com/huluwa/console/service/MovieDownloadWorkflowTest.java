package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.DatabaseInitializer;
import com.huluwa.console.ws.ChatPushService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovieDownloadWorkflowTest {
    private static final String URL = "https://www.dygang.cc/dmq/20211031/48089.htm";
    private static final String TITLE = "斗破苍穹 第5季";
    private static final String PATH = "/vol1/1000/downloads/" + TITLE;
    private final MovieSearchService search = mock(MovieSearchService.class);
    private final QBittorrentService qb = mock(QBittorrentService.class);
    private final XunleiService xl = mock(XunleiService.class);
    private final XunleiWinService xlwin = mock(XunleiWinService.class);
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private MovieSubscriptionService service;

    @BeforeEach
    void setup() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        service = new MovieSubscriptionService(jdbc, search, qb, xl, xlwin,
                mock(MessageService.class), mock(ChatPushService.class),
                new SubscriptionCheckLogService(jdbc));
        when(search.fetchDetail(anyString())).thenReturn(new MovieSearchService.DetailPage(TITLE, List.of()));
        when(qb.settings()).thenReturn(new QBittorrentService.Settings("http://qb", null, null, "/old/qb"));
        when(xl.settings()).thenReturn(new XunleiService.Settings("http://xl", "/old/xl", null));
        // magnetNotice 会把 describe() 写进载荷，桩成与真实实现同形的最小配置视图
        when(qb.describe()).thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                "url", "http://qb", "configured", true)));
        when(xl.describe()).thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                "url", "http://xl", "configured", true)));
        when(qb.add(anyList(), anyString())).thenReturn(new QBittorrentService.AddResult(true, false, "已提交"));
        when(xl.add(anyList(), anyString())).thenReturn(List.of(new XunleiService.TaskResult(true, "已提交", "episode.mp4", 1, 1, "xl-1")));
    }

    @AfterEach
    void close() { dataSource.destroy(); }

    private MovieSubscriptionService.Subscription subscribe(String downloader) {
        return service.subscribe(TITLE, URL, "48089", null, null, null, null, downloader).subscription();
    }

    private static String magnet(char hash) { return "magnet:?xt=urn:btih:" + String.valueOf(hash).repeat(40); }

    @Test
    void subscriptionChoiceOverridesDefaultAndCanBeModifiedForManualDownloads() {
        service.saveDownloadSettings("qb");
        MovieSubscriptionService.Subscription subscription = subscribe("xunlei");
        new ConfigService(jdbc).put(MovieSubscriptionService.CFG_DOWNLOAD_ROOT, "/old/movie");
        Map<String, Object> result = service.downloadMagnet(URL, "别名", magnet('a'), "2160P", "1");
        assertEquals(true, result.get("ok"));
        assertEquals(PATH, result.get("savePath"));
        verify(xl).add(List.of(magnet('a')), PATH);
        verify(qb, never()).add(anyList(), anyString());
        assertEquals("xl-1", jdbc.queryForObject("SELECT external_task_id FROM movie_download_task", String.class));

        service.updateSubscriptionSettings(subscription.id(), "qb");
        service.downloadMagnet(URL, TITLE, magnet('b'), "1080P", "2");
        verify(qb).add(List.of(magnet('b')), PATH);
        assertEquals("qb", service.find(URL).downloader());
        assertEquals("xunlei", jdbc.queryForObject("SELECT downloader FROM movie_download_task WHERE magnet = ?", String.class, magnet('a')));
        assertEquals("xunlei", jdbc.queryForObject("SELECT session_id FROM chat_message ORDER BY id LIMIT 1", String.class));
    }

    @Test
    void subscriptionListDownloadsUseEachSeriesBindingAfterDefaultAndToolChanges() {
        String otherUrl = "https://www.dygang.cc/dmq/20211031/48090.htm";
        String otherTitle = "另一部剧";
        when(search.fetchDetail(URL)).thenReturn(new MovieSearchService.DetailPage(TITLE, List.of(
                new MovieSearchService.MagnetItem(TITLE + "01.2160p.mp4", magnet('2'), "2".repeat(40), "2160P", "1"),
                new MovieSearchService.MagnetItem(TITLE + "02.1080p.mp4", magnet('3'), "3".repeat(40), "1080P", "2"))));
        when(search.fetchDetail(otherUrl)).thenReturn(new MovieSearchService.DetailPage(otherTitle, List.of(
                new MovieSearchService.MagnetItem(otherTitle + "01.1080p.mp4", magnet('4'), "4".repeat(40), "1080P", "1"))));
        MovieSubscriptionService.Subscription first = subscribe("xunlei");
        service.subscribe(otherTitle, otherUrl, "48090", null, null, null, null, "qb");
        service.saveDownloadSettings("xunlei");

        long firstMagnet = jdbc.queryForObject("SELECT id FROM movie_subscription_magnet WHERE magnet = ?", Long.class, magnet('2'));
        long secondMagnet = jdbc.queryForObject("SELECT id FROM movie_subscription_magnet WHERE magnet = ?", Long.class, magnet('4'));
        assertEquals("xunlei", service.downloadStoredMagnet(firstMagnet).get("downloader"));
        assertEquals("qb", service.downloadStoredMagnet(secondMagnet).get("downloader"));
        verify(xl).add(List.of(magnet('2')), PATH);
        verify(qb).add(List.of(magnet('4')), "/vol1/1000/downloads/" + otherTitle);

        service.updateSubscriptionSettings(first.id(), "qb");
        long nextMagnet = jdbc.queryForObject("SELECT id FROM movie_subscription_magnet WHERE magnet = ?", Long.class, magnet('3'));
        assertEquals("qb", service.downloadStoredMagnet(nextMagnet).get("downloader"));
        verify(qb).add(List.of(magnet('3')), PATH);
        verify(xl, times(1)).add(anyList(), anyString());
    }

    @Test
    void automaticScanUsesSelectedToolAndHighestQualityAndDoesNotDuplicate() {
        when(search.fetchDetail(URL)).thenReturn(new MovieSearchService.DetailPage(TITLE, List.of(
                new MovieSearchService.MagnetItem(TITLE + "00.1080p.mp4", magnet('9'), "9".repeat(40), "1080P", "0"))));
        MovieSubscriptionService.Subscription subscription = subscribe("xunlei");
        when(search.fetchMagnets(URL)).thenReturn(List.of(
                new MovieSearchService.MagnetItem(TITLE + "01.1080p.mp4", magnet('c'), "c".repeat(40), "1080P", "1"),
                new MovieSearchService.MagnetItem(TITLE + "01.2160p.mp4", magnet('d'), "d".repeat(40), "2160P", "1")));
        assertEquals(2, service.scan(subscription.id()));
        assertEquals(0, service.scan(subscription.id()));
        verify(xl, times(1)).add(List.of(magnet('d')), PATH);
        verify(qb, never()).add(anyList(), anyString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM movie_download_task", Integer.class));
    }

    @Test
    void failedSubmissionRetriesWithModifiedToolAndUnifiedPath() {
        MovieSubscriptionService.Subscription subscription = subscribe("xunlei");
        when(xl.add(anyList(), anyString())).thenReturn(List.of(new XunleiService.TaskResult(false, "临时失败", null, 0, 0, null)));
        service.downloadMagnet(URL, TITLE, magnet('e'), "2160P", "3");
        service.updateSubscriptionSettings(subscription.id(), "qb");
        jdbc.update("UPDATE movie_download_task SET save_path = '/old/path'");
        service.scan(subscription.id());
        verify(qb).add(List.of(magnet('e')), PATH);
        assertEquals("added", jdbc.queryForObject("SELECT status FROM movie_download_task", String.class));
        assertEquals(PATH, jdbc.queryForObject("SELECT save_path FROM movie_download_task", String.class));
    }

    @Test
    void xunleiWinDownloadsUseWindowsSavePathAndTrackProgressBySerial() {
        // 迅雷【Win】：Windows 根目录 + 剧名子目录提交，流水号落 external_task_id，
        // 跟踪时按 xunlei-win-api 的实时状态回填 status / message / progress
        when(xlwin.settings()).thenReturn(new XunleiWinService.Settings("http://xlwin:8899", "D:\\迅雷下载"));
        when(xlwin.describe()).thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                "url", "http://xlwin:8899", "configured", true)));
        when(xlwin.add(anyList(), anyString())).thenReturn(List.of(
                new XunleiWinService.TaskResult(true, "已提交迅雷【Win】下载", null, 0, 0, "SN-1")));
        subscribe("xunlei-win");

        Map<String, Object> result = service.downloadMagnet(URL, TITLE, magnet('w'), "2160P", "5");
        assertEquals(true, result.get("ok"));
        String winPath = "D:\\迅雷下载\\斗破苍穹 第5季";
        assertEquals(winPath, result.get("savePath"));
        verify(xlwin).add(List.of(magnet('w')), winPath);
        verify(xl, never()).add(anyList(), anyString());
        assertEquals("SN-1", jdbc.queryForObject("SELECT external_task_id FROM movie_download_task", String.class));
        assertEquals("xunlei-win", jdbc.queryForObject("SELECT downloader FROM movie_download_task", String.class));

        when(xlwin.task("SN-1")).thenReturn(new XunleiWinService.TaskStatus(
                "PHASE_TYPE_RUNNING", "下载中 42.5%", 42.5));
        service.refreshDownloads();
        assertEquals("downloading", jdbc.queryForObject("SELECT status FROM movie_download_task", String.class));
        assertEquals("下载中 42.5%", jdbc.queryForObject("SELECT message FROM movie_download_task", String.class));
        assertEquals(43, jdbc.queryForObject("SELECT progress FROM movie_download_task", Integer.class));
        // 通知写入「迅雷下载【Win】」会话，而不是磁力会话或飞牛迅雷会话
        assertEquals("xunlei-win", jdbc.queryForObject(
                "SELECT session_id FROM chat_message WHERE role = 'bot' ORDER BY id LIMIT 1", String.class));

        when(xlwin.task("SN-1")).thenReturn(new XunleiWinService.TaskStatus(
                "PHASE_TYPE_COMPLETE", "下载完成", 100));
        service.refreshDownloads();
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM movie_download_task", String.class));
        assertEquals(100, jdbc.queryForObject("SELECT progress FROM movie_download_task", Integer.class));
        assertNotNull(jdbc.queryForObject("SELECT completed_at FROM movie_download_task", String.class));
    }

    @Test
    void alreadySubmittedXunleiTaskContinuesTrackingAfterToolChange() {
        MovieSubscriptionService.Subscription subscription = subscribe("xunlei");
        service.downloadMagnet(URL, TITLE, magnet('f'), "2160P", "4");
        service.updateSubscriptionSettings(subscription.id(), "qb");
        when(xl.task("xl-1")).thenReturn(new XunleiService.TaskStatus("PHASE_TYPE_COMPLETE", "完成"));
        service.refreshDownloads();
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM movie_download_task", String.class));
        assertNotNull(jdbc.queryForObject("SELECT completed_at FROM movie_download_task", String.class));
        verify(qb, never()).torrent(anyString());
        service.downloadMagnet(URL, TITLE, magnet('f'), "2160P", "4");
        verify(qb, never()).add(anyList(), anyString());
        verify(xl, times(1)).add(anyList(), anyString());
    }

    @Test
    void failedDownloadNoticeIsNotMarkedAsMissingConfigurationWhenToolIsConfigured() {
        MovieSubscriptionService.Subscription subscription = subscribe("qb");
        service.downloadMagnet(URL, TITLE, magnet('b'), "2160P", "9");
        // qB 已配置（setup 里 settings().configured() 为真）：下载失败只标记 failed，不再冒充「未配置」
        when(qb.torrent("b".repeat(40))).thenReturn(new QBittorrentService.Torrent(
                "b".repeat(40), TITLE, 0, "error", 0, 0, 0));
        service.refreshDownloads();

        String payload = jdbc.queryForObject(
                "SELECT payload FROM chat_message WHERE session_id = 'magnet' ORDER BY id DESC LIMIT 1", String.class);
        assertTrue(payload.contains("\"status\":\"failed\""), payload);
        assertFalse(payload.contains("\"status\":\"config\""), payload);
        assertTrue(payload.contains("\"configured\":true"), payload);

        // 真正没有配置下载器时仍然标记 config，前端据此弹配置引导
        when(qb.settings()).thenReturn(new QBittorrentService.Settings(null, null, null, null));
        when(qb.describe()).thenReturn(new java.util.LinkedHashMap<>(java.util.Map.of(
                "configured", false)));
        when(qb.add(anyList(), anyString())).thenReturn(new QBittorrentService.AddResult(false, false, "未配置"));
        service.downloadMagnet(URL, TITLE, magnet('c'), "1080P", "10");
        String configPayload = jdbc.queryForObject(
                "SELECT payload FROM chat_message WHERE session_id = 'magnet' ORDER BY id DESC LIMIT 1", String.class);
        assertTrue(configPayload.contains("\"status\":\"config\""), configPayload);
        assertTrue(configPayload.contains("\"configured\":false"), configPayload);
    }

    @Test
    void defaultsPersistAndInvalidChoicesAreRejected() {
        assertEquals("qb", service.downloadSettings().downloader());
        service.saveDownloadSettings("xunlei");
        assertEquals("xunlei", subscribe(null).downloader());
        assertEquals(MovieSubscriptionService.DEFAULT_SAVE_ROOT, service.downloadSettings().saveRoot());
        assertThrows(BusinessException.class, () -> service.saveDownloadSettings("other"));
        assertThrows(BusinessException.class, () -> service.updateSubscriptionSettings(1, "other"));
    }

    @Test
    void clearingDownloadsHidesAllStatusesAndKeepsTrackingAndDeduplication() {
        MovieSubscriptionService.Subscription subscription = subscribe("qb");
        List<String> statuses = List.of("pending", "added", "downloading", "completed", "failed", "removed");
        for (int i = 0; i < statuses.size(); i++) {
            String magnet = magnet((char) ('1' + i));
            service.downloadMagnet(URL, TITLE, magnet, "1080P", String.valueOf(i + 1));
            jdbc.update("UPDATE movie_download_task SET status = ? WHERE magnet = ?", statuses.get(i), magnet);
        }

        Map<String, Object> cleared = service.clearDownloads();
        assertEquals(statuses.size(), cleared.get("hidden"));
        assertEquals(0, cleared.get("kept"));
        assertTrue(service.downloads(0).isEmpty());
        assertEquals(statuses.size(), jdbc.queryForObject("SELECT COUNT(*) FROM movie_download_task", Integer.class));

        // 已完成与进行中的任务都保留去重历史。
        assertEquals(true, service.downloadMagnet(URL, TITLE, magnet('4'), "1080P", "4").get("duplicate"));
        assertEquals(true, service.downloadMagnet(URL, TITLE, magnet('2'), "1080P", "2").get("duplicate"));
        verify(qb, times(statuses.size())).add(anyList(), anyString());

        // 隐藏后的任务仍能完成跟踪，状态回写不会恢复列表可见性。
        when(qb.torrent("2".repeat(40))).thenReturn(new QBittorrentService.Torrent(
                "2".repeat(40), TITLE, 1, "uploading", 0, 0, 0));
        service.refreshDownloads();
        assertEquals("completed", jdbc.queryForObject(
                "SELECT status FROM movie_download_task WHERE magnet = ?", String.class, magnet('2')));
        assertTrue(service.downloads(0).isEmpty());

        // 巡检自动重试失败任务后，已经清空的记录也不会重新出现。
        when(search.fetchMagnets(URL)).thenReturn(List.of());
        service.scan(subscription.id(), SubscriptionCheckLogService.TRIGGER_MANUAL);
        assertEquals("added", jdbc.queryForObject(
                "SELECT status FROM movie_download_task WHERE magnet = ?", String.class, magnet('5')));
        assertTrue(service.downloads(0).isEmpty());
        verify(qb, times(statuses.size() + 1)).add(anyList(), anyString());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM movie_download_task WHERE hidden_at IS NULL", Integer.class));
    }

    @Test
    void clearingDuringSubmissionKeepsTheRecordHiddenWhenSubmissionReturns() {
        subscribe("qb");
        when(qb.add(anyList(), anyString())).thenAnswer(invocation -> {
            assertEquals(1, service.clearDownloads().get("hidden"));
            return new QBittorrentService.AddResult(true, false, "已提交");
        });

        Map<String, Object> result = service.downloadMagnet(URL, TITLE, magnet('7'), "1080P", "1");

        assertEquals(true, result.get("ok"));
        assertEquals("added", jdbc.queryForObject("SELECT status FROM movie_download_task", String.class));
        assertTrue(service.downloads(0).isEmpty());
    }

    /**
     * 「消息中心 → 巡检记录」：电影港每次巡检（有无更新、是否失败）都要留一条结果快照，
     * 且记录在订阅被删除后仍然可读。
     */
    @Test
    void everyScanWritesACheckLogAndKeepsItAfterSubscriptionDeletion() {
        MovieSubscriptionService.Subscription subscription = subscribe("qb");

        // 无更新：巡检成功留一条 ok 记录
        when(search.fetchMagnets(URL)).thenReturn(List.of());
        assertEquals(0, service.scan(subscription.id(), SubscriptionCheckLogService.TRIGGER_MANUAL));
        Map<String, Object> idle = jdbc.queryForMap("SELECT * FROM subscription_check_log ORDER BY id DESC LIMIT 1");
        assertEquals("dygangs", idle.get("source"));
        assertEquals("ok", idle.get("status"));
        assertEquals("manual", idle.get("trigger_type"));
        assertEquals(TITLE, idle.get("series_title"));
        assertEquals(0, idle.get("found_count"));

        // 有新磁力：记录发现条数、提交条数与明细
        when(search.fetchMagnets(URL)).thenReturn(List.of(
                new MovieSearchService.MagnetItem(TITLE + "01.2160p.mp4", magnet('3'), "3".repeat(40), "2160P", "1"),
                new MovieSearchService.MagnetItem(TITLE + "02.1080p.mp4", magnet('4'), "4".repeat(40), "1080P", "2")));
        assertEquals(2, service.scan(subscription.id(), SubscriptionCheckLogService.TRIGGER_SCHEDULED));
        Map<String, Object> updated = jdbc.queryForMap("SELECT * FROM subscription_check_log ORDER BY id DESC LIMIT 1");
        assertEquals("updated", updated.get("status"));
        assertEquals(2, updated.get("found_count"));
        assertEquals("scheduled", updated.get("trigger_type"));
        String detail = String.valueOf(updated.get("detail"));
        assertTrue(detail.contains("第 1 集"), detail);
        assertTrue(detail.contains("2160P"), detail);

        // 抓取失败：也要留痕（failed），便于回溯站点不可达的时间点
        when(search.fetchMagnets(URL)).thenThrow(new IllegalStateException("站点不可达"));
        assertEquals(0, service.scan(subscription.id(), SubscriptionCheckLogService.TRIGGER_SCHEDULED));
        Map<String, Object> failed = jdbc.queryForMap("SELECT * FROM subscription_check_log ORDER BY id DESC LIMIT 1");
        assertEquals("failed", failed.get("status"));
        assertTrue(String.valueOf(failed.get("message")).contains("站点不可达"), String.valueOf(failed.get("message")));

        // 删除订阅：历史记录保留，只解除关联（列表页仍能读到剧名）
        long subscriptionId = subscription.id();
        service.delete(subscriptionId);
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM subscription_check_log", Integer.class));
        assertEquals(3, jdbc.queryForObject(
                "SELECT COUNT(*) FROM subscription_check_log WHERE subscription_id IS NULL", Integer.class));
        assertEquals(TITLE, jdbc.queryForObject(
                "SELECT series_title FROM subscription_check_log ORDER BY id LIMIT 1", String.class));
    }

    @Test
    void oldDatabaseMigrationIsIdempotentAndKeepsHistoricalQbTasks() {
        subscribe("qb");
        service.downloadMagnet(URL, TITLE, magnet('1'), "1080P", "1");
        jdbc.execute("ALTER TABLE movie_download_task DROP COLUMN downloader");
        jdbc.execute("ALTER TABLE movie_download_task DROP COLUMN external_task_id");
        jdbc.execute("ALTER TABLE movie_subscription DROP COLUMN downloader");
        service.saveDownloadSettings("xunlei");
        DatabaseInitializer initializer = new DatabaseInitializer(jdbc);
        initializer.run(null);
        initializer.run(null);
        assertEquals("qb", jdbc.queryForObject("SELECT downloader FROM movie_download_task", String.class));
        assertNull(jdbc.queryForObject("SELECT external_task_id FROM movie_download_task", String.class));
        assertEquals("xunlei", jdbc.queryForObject("SELECT downloader FROM movie_subscription", String.class));
        service.saveDownloadSettings("qb");
        initializer.run(null);
        assertEquals("xunlei", service.find(URL).downloader());
    }
}
