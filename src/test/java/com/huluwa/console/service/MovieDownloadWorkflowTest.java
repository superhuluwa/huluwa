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
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate jdbc;
    private MovieSubscriptionService service;

    @BeforeEach
    void setup() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        service = new MovieSubscriptionService(jdbc, search, qb, xl,
                mock(MessageService.class), mock(ChatPushService.class));
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
    void clearingDownloadsHidesFinishedMagnetRowsAndKeepsTrackingVisible() {
        MovieSubscriptionService.Subscription subscription = subscribe("qb");
        service.downloadMagnet(URL, TITLE, magnet('7'), "1080P", "1");
        service.downloadMagnet(URL, TITLE, magnet('8'), "1080P", "2");
        jdbc.update("UPDATE movie_download_task SET status = 'completed' WHERE magnet = ?", magnet('7'));

        Map<String, Object> cleared = service.clearDownloads();
        assertEquals(1, cleared.get("hidden"));
        assertEquals(1, cleared.get("kept"));
        assertEquals(List.of(magnet('8')), service.downloads(0).stream()
                .map(row -> String.valueOf(row.get("id"))).map(id -> jdbc.queryForObject(
                        "SELECT magnet FROM movie_download_task WHERE id = ?", String.class, Long.parseLong(id))).toList());

        // 清空不移除去重历史：同一磁力再次提交仍然命中「已在下载列表」
        Map<String, Object> duplicate = service.downloadMagnet(URL, TITLE, magnet('7'), "1080P", "1");
        assertEquals(true, duplicate.get("duplicate"));
        verify(qb, times(2)).add(anyList(), anyString());
        assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM movie_download_task WHERE hidden_at IS NULL AND status = 'completed'", Integer.class));
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
