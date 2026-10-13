package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.HlsProperties;
import com.huluwa.console.web.HlsRequests;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 完全使用内存数据库和模拟站点，不运行下载工具、不联网。 */
class HlsSubscriptionServiceTest {
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate db;
    private DyttSearchService source;
    private HlsDownloadService downloads;
    private HlsSubscriptionService subscriptions;

    @BeforeEach
    void setup() {
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        db = new JdbcTemplate(dataSource);
        source = mock(DyttSearchService.class);
        HlsNoticeService notices = mock(HlsNoticeService.class);
        downloads = spy(new HlsDownloadService(db, new ConfigService(db), new HlsProperties(), source, notices, new ObjectMapper()));
        doNothing().when(downloads).requireEnvironment();
        subscriptions = new HlsSubscriptionService(db, source, downloads, notices, new DataSourceTransactionManager(dataSource));
    }

    @AfterEach
    void close() { subscriptions.shutdown(); downloads.shutdown(); dataSource.destroy(); }

    private DyttSearchService.DyttItem detail(int count, String signature) {
        List<DyttSearchService.PlayItem> plays = IntStream.rangeClosed(1, count)
                .mapToObj(episode -> new DyttSearchService.PlayItem("第" + episode + "集", "https://cdn.example/" + episode + ".m3u8?token=" + signature))
                .toList();
        return new DyttSearchService.DyttItem("42", "剧名", "2026", "", "", "", "", "", "",
                List.of(new DyttSearchService.PlaySource("m3u8", count, plays)));
    }

    private HlsRequests.Subscription request(boolean existing, int fromEpisode) {
        return new HlsRequests.Subscription(null, "42", "剧名", "2026", 1, "m3u8", 60, fromEpisode,
                existing, null, null, "secret-cookie", false);
    }

    private long subscribe(boolean existing, int from) {
        Map<?, ?> result = (Map<?, ?>) subscriptions.subscribe(request(existing, from)).get("subscription");
        return ((Number) result.get("id")).longValue();
    }

    @Test
    void oneClickSubscribeQueuesExistingEpisodesWithAutomaticDefaults() {
        DyttSearchService.DyttItem original = detail(65, "old");
        when(source.detail(anyString(), anyString())).thenReturn(new DyttSearchService.DyttItem(
                original.id(), "剧名（第二季）更新至第65集", "2026", "", "", "", "https://dyttzyw.com/series/42", "", "",
                original.playSources()));
        Map<?, ?> subscription = (Map<?, ?>) subscriptions.quickSubscribe(new HlsRequests.QuickSubscription(null, "42")).get("subscription");
        assertEquals("剧名", subscription.get("title"));
        assertEquals(2L, HlsSupport.number(subscription.get("season")));
        assertEquals(60L, HlsSupport.number(subscription.get("intervalMinutes")));
        assertEquals(1L, HlsSupport.number(subscription.get("fromEpisode")));
        assertEquals("m3u8", subscription.get("sourceName"));
        assertEquals(65, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'pending'", Integer.class));
        verify(source, times(1)).detail(anyString(), anyString());
    }

    @Test
    void oneClickSubscribeRestoresExistingSettingsWithoutResettingHistory() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(2, "old"));
        long id = subscribe(false, 2);
        subscriptions.enabled(id, false);
        db.update("UPDATE hls_subscription SET interval_minutes = 180 WHERE id = ?", id);
        String created = db.queryForObject("SELECT created_at FROM hls_subscription WHERE id = ?", String.class, id);
        clearInvocations(source);
        Map<?, ?> subscription = (Map<?, ?>) subscriptions.quickSubscribe(new HlsRequests.QuickSubscription(null, "42")).get("subscription");
        assertEquals(id, HlsSupport.number(subscription.get("id")));
        assertEquals(true, subscription.get("enabled"));
        assertEquals(180L, HlsSupport.number(subscription.get("intervalMinutes")));
        assertEquals(2L, HlsSupport.number(subscription.get("fromEpisode")));
        assertEquals(created, subscription.get("createdAt"));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'skipped'", Integer.class));
        verifyNoInteractions(source);
    }

    @Test
    void oneClickSubscribeFallsBackFromAnUnusableLine() {
        DyttSearchService.DyttItem original = detail(2, "old");
        when(source.detail(anyString(), anyString())).thenReturn(new DyttSearchService.DyttItem(
                original.id(), original.title(), "未知", "", "", "", "", "", "", List.of(
                new DyttSearchService.PlaySource("first-m3u8", 1, List.of(new DyttSearchService.PlayItem("预告第01集", "https://cdn.example/trailer.m3u8"))),
                original.playSources().getFirst())));
        Map<?, ?> subscription = (Map<?, ?>) subscriptions.quickSubscribe(new HlsRequests.QuickSubscription(null, "42")).get("subscription");
        assertEquals("m3u8", subscription.get("sourceName"));
        assertEquals("", subscription.get("year"));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'pending'", Integer.class));
    }

    @Test
    void onlyFutureEpisodesAreQueuedWhenExistingDownloadsAreDisabled() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(2, "old"));
        long id = subscribe(false, 1);
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'skipped'", Integer.class));
        when(source.detail(anyString(), anyString())).thenReturn(detail(3, "new"));
        assertEquals(1, subscriptions.check(id).get("added"));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'pending'", Integer.class));
        assertEquals(0, subscriptions.check(id).get("added"));
    }

    @Test
    void completeManifestIsUsedBeyondTheSixtyEpisodeCardLimit() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(65, "old"));
        long id = subscribe(true, 3);
        assertEquals(63, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'pending'", Integer.class));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE status = 'skipped'", Integer.class));
        when(source.detail(anyString(), anyString())).thenReturn(detail(66, "new"));
        assertEquals(1, subscriptions.check(id).get("added"));
        assertEquals(66, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));
    }

    @Test
    void changingUrlsDoesNotDuplicateEpisodesOrResetCompletedTasks() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(2, "old"));
        long id = subscribe(true, 1);
        db.update("UPDATE hls_download_task SET status = 'completed', save_path = '/media/剧名 S01E01.mp4' WHERE episode = 1");
        when(source.detail(anyString(), anyString())).thenReturn(detail(2, "new"));
        subscriptions.check(id);
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));
        assertEquals("completed", db.queryForObject("SELECT status FROM hls_download_task WHERE episode = 1", String.class));
        assertTrue(db.queryForObject("SELECT url FROM hls_download_task WHERE episode = 2", String.class).endsWith("token=new"));
        Map<String, Object> view = subscriptions.list().getFirst();
        assertEquals(true, view.get("cookieConfigured"));
        assertFalse(view.toString().contains("secret-cookie"));
        assertFalse(downloads.list().toString().contains("token=new"));
    }

    @Test
    void manualDownloadOfAnExistingSubscriptionKeepsTheSavedCookie() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(2, "old"));
        subscribe(false, 1);
        downloads.enqueue(new HlsRequests.Download(null, "42", "剧名", "2026", 1, 1,
                "第1集", "https://cdn.example/1.m3u8?token=old", "m3u8", null, null, null));
        assertEquals("pending", db.queryForObject("SELECT status FROM hls_download_task WHERE episode = 1", String.class));
        assertEquals("secret-cookie", HlsSupport.headerMap(db.queryForObject(
                "SELECT headers FROM hls_download_task WHERE episode = 1", String.class)).get("Cookie"));
    }

    @Test
    void pausedSubscriptionStopsChecksAndKeepsHistory() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(2, "old"));
        long id = subscribe(true, 1);
        subscriptions.enabled(id, false);
        assertThrows(BusinessException.class, () -> subscriptions.check(id));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));
        assertEquals(1L, HlsSupport.number(downloads.list().getFirst().get("paused")));
        subscriptions.enabled(id, true);
        assertEquals(0, subscriptions.check(id).get("added"));
    }

    @Test
    void clearingDownloadHistoryHidesFinishedRowsButKeepsQueueAndDeduplication() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(3, "old"));
        long id = subscribe(true, 1);
        db.update("UPDATE hls_download_task SET status = 'completed' WHERE episode = 1");
        db.update("UPDATE hls_download_task SET status = 'failed' WHERE episode = 2");
        // episode 3 保持 pending，代表进行中的任务

        Map<String, Object> cleared = downloads.clearHistory();
        assertEquals(2, HlsSupport.number(cleared.get("hidden")));
        assertEquals(1, HlsSupport.number(cleared.get("kept")));
        assertEquals(List.of(3), downloads.list().stream().map(row -> (int) HlsSupport.number(row.get("episode"))).toList());
        assertEquals(3, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));

        // 隐藏只是列表与重试入口的变化：已完成的一集仍不会被巡检重复下载
        when(source.detail(anyString(), anyString())).thenReturn(detail(3, "new"));
        assertEquals(0, subscriptions.check(id).get("added"));

        // 重试此前失败的一集：解除隐藏并重新排队
        long failed = db.queryForObject("SELECT id FROM hls_download_task WHERE episode = 2", Long.class);
        Map<String, Object> retried = downloads.retry(failed);
        assertEquals("pending", retried.get("status"));
        assertTrue(downloads.list().stream().anyMatch(row -> HlsSupport.number(row.get("episode")) == 2));
        assertEquals(2, db.queryForObject("SELECT COUNT(*) FROM hls_download_task WHERE hidden_at IS NULL", Integer.class));
    }

    /**
     * 复现「清空下载记录后重新下载提示已有记录」：
     * 行被 hidden_at 隐藏后，手动提交必须能让它重新排队并回到列表，
     * 而不是只回一句误导性的状态文案。
     */
    @Test
    void clearingHistoryDoesNotBlockManualDownloadOfTheSameEpisode() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(1, "old"));
        long id = subscribe(true, 1);
        db.update("UPDATE hls_download_task SET status = 'completed' WHERE episode = 1");
        downloads.clearHistory();
        assertEquals(0, downloads.list().size());

        // 用户再次点击下载同一集：重新排队并回到列表，不再提示「已有下载记录」
        Map<String, Object> result = downloads.enqueue(new HlsRequests.Download(
                null, "42", "剧名", "2026", 1, 1, "第1集",
                "https://cdn.example/1.m3u8?token=new", "m3u8", null, null, null));
        assertEquals("pending", result.get("status"));
        assertEquals(1, downloads.list().size());
        assertEquals(1, db.queryForObject(
                "SELECT COUNT(*) FROM hls_download_task WHERE hidden_at IS NULL", Integer.class));
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));
    }

    /**
     * 已下载完成且归档文件仍在时：重新点击下载不应重复拉取，
     * 但列表里要能看到这一条（状态已重置为等待下载）。
     */
    @Test
    void manualDownloadOfCompletedEpisodeKeepsRecordVisible() {
        when(source.detail(anyString(), anyString())).thenReturn(detail(1, "old"));
        long id = subscribe(true, 1);
        db.update("UPDATE hls_download_task SET status = 'completed', save_path = '/media/剧名 S01E01.mp4' WHERE episode = 1");
        assertEquals(0, subscriptions.check(id).get("added"));

        Map<String, Object> result = downloads.enqueue(new HlsRequests.Download(
                null, "42", "剧名", "2026", 1, 1, "第1集",
                "https://cdn.example/1.m3u8?token=new", "m3u8", null, null, null));
        assertEquals("pending", result.get("status"));
        assertEquals(1, downloads.list().size());
        // 同一集不会因为重复提交而多出第二条记录
        assertEquals(1, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));
    }

    @Test
    void duplicateNumberingRejectsTheManifestBeforeAnyTasksAreCreated() {
        var repeated = new DyttSearchService.DyttItem("42", "剧名", "2026", "", "", "", "", "", "",
                List.of(new DyttSearchService.PlaySource("m3u8", 2, List.of(
                        new DyttSearchService.PlayItem("第01集", "https://cdn.example/1.m3u8"),
                        new DyttSearchService.PlayItem("EP01", "https://cdn.example/2.m3u8")))));
        when(source.detail(anyString(), anyString())).thenReturn(repeated);
        assertThrows(BusinessException.class, () -> subscribe(true, 1));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM hls_subscription", Integer.class));
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM hls_download_task", Integer.class));
    }
}
