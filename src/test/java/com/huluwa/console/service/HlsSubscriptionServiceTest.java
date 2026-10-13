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
