package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.config.HlsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.lang.reflect.InvocationTargetException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 私有工作区维护、旧日志迁移，以及跨文件系统复制后的原子发布。 */
class HlsWorkspaceCleanupTest {
    @TempDir
    Path temporary;

    private Path mediaRoot;
    private Path workRoot;
    private SingleConnectionDataSource dataSource;
    private JdbcTemplate db;
    private HlsDownloadService downloads;

    @BeforeEach
    void setup() throws Exception {
        mediaRoot = Files.createDirectories(temporary.resolve("media"));
        workRoot = temporary.resolve("private-work");
        HlsProperties paths = new HlsProperties();
        paths.setMediaRoot(mediaRoot.toString());
        paths.setWorkRoot(workRoot.toString());
        paths.setLegacyMediaRoot(temporary.resolve("legacy").toString());
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        db = new JdbcTemplate(dataSource);
        downloads = spy(new HlsDownloadService(db, new ConfigService(db), paths,
                mock(DyttSearchService.class), mock(HlsNoticeService.class), new ObjectMapper()));
        doNothing().when(downloads).requireEnvironment();
        doNothing().when(downloads).kick();
    }

    @AfterEach
    void close() {
        downloads.shutdown();
        dataSource.destroy();
    }

    private long completedTask() {
        long id = downloads.discover(null, UUID.randomUUID().toString(), "", "", "剧名", "", 1, 1,
                "第1集", "m3u8", "https://cdn.example/1.m3u8", "{}", true);
        db.update("UPDATE hls_download_task SET status = 'completed' WHERE id = ?", id);
        return id;
    }

    private Path attemptOf(long taskId, String marker) throws Exception {
        return attemptOf(workRoot, taskId, marker);
    }

    /** 模拟一次失败尝试：output 里有 MP4 半成品，另有分片和日志。 */
    private Path attemptOf(Path root, long taskId, String marker) throws Exception {
        Path attempt = root.resolve(String.valueOf(taskId)).resolve("attempt-1-" + marker);
        Files.createDirectories(attempt.resolve("output"));
        Files.createDirectories(attempt.resolve("segments"));
        Files.writeString(attempt.resolve("output").resolve("剧名 S01E01.mp4"), "partial-media");
        Files.writeString(attempt.resolve("segments").resolve("0.ts"), "segment");
        Files.writeString(attempt.resolve("download.log"), "download log");
        return attempt;
    }

    @Test
    void startupCleanupRemovesSegmentsAndPartialMediaButKeepsLog() throws Exception {
        Path attempt = attemptOf(completedTask(), "stale");

        downloads.run(null);

        assertFalse(Files.exists(attempt.resolve("output").resolve("剧名 S01E01.mp4")));
        assertFalse(Files.exists(attempt.resolve("segments").resolve("0.ts")));
        assertTrue(Files.exists(attempt.resolve("download.log")));
    }

    @Test
    void startupCleanupReclaimsEmptyDirectoriesButKeepsLogDirectories() throws Exception {
        Path attempt = attemptOf(completedTask(), "empty");
        Files.delete(attempt.resolve("output").resolve("剧名 S01E01.mp4"));

        downloads.run(null);

        assertFalse(Files.exists(attempt.resolve("output")));
        assertTrue(Files.exists(attempt));
    }

    @Test
    void startupCleanupIsIdempotentWhenWorkDirectoryMissing() {
        assertDoesNotThrow(() -> downloads.run(null));
        assertDoesNotThrow(() -> downloads.run(null));
    }

    @Test
    void maintenanceReclaimsOrphanTasksIncludingFreshLogs() throws Exception {
        Path attempt = attemptOf(999, "orphan");

        downloads.cleanupStaleWorkspaces();

        assertFalse(Files.exists(attempt.getParent()));
    }

    @Test
    void maintenanceExpiresOldLogsButKeepsRecentLogs() throws Exception {
        long id = completedTask();
        Path expired = attemptOf(id, "expired");
        Path recent = attemptOf(id, "recent");
        Files.setLastModifiedTime(expired.resolve("download.log"),
                FileTime.fromMillis(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(8)));

        downloads.cleanupStaleWorkspaces();

        assertFalse(Files.exists(expired));
        assertTrue(Files.exists(recent.resolve("download.log")));
        assertFalse(Files.exists(recent.resolve("output")));
    }

    @Test
    void maintenanceSkipsActiveTask() throws Exception {
        long id = completedTask();
        Path attempt = attemptOf(id, "active");
        var active = HlsDownloadService.class.getDeclaredField("activeTaskId");
        active.setAccessible(true);
        active.setLong(downloads, id);

        downloads.cleanupStaleWorkspaces();

        assertTrue(Files.exists(attempt.resolve("output").resolve("剧名 S01E01.mp4")));
        assertTrue(Files.exists(attempt.resolve("segments").resolve("0.ts")));
    }

    @Test
    void startupMigratesValidLegacyLogsAndUpdatesTaskPath() throws Exception {
        long id = completedTask();
        Path shared = mediaRoot.resolve(".huluwa-work");
        Path attempt = attemptOf(shared, id, "legacy");
        db.update("UPDATE hls_download_task SET log_path = ? WHERE id = ?",
                attempt.resolve("download.log").toString(), id);

        downloads.run(null);

        Path migrated = workRoot.resolve(String.valueOf(id)).resolve("attempt-1-legacy").resolve("download.log");
        assertEquals("download log", Files.readString(migrated));
        assertEquals(migrated.toString(), db.queryForObject(
                "SELECT log_path FROM hls_download_task WHERE id = ?", String.class, id));
        assertEquals("download log", downloads.log(id).get("content"));
        assertFalse(Files.exists(shared));
    }

    @Test
    void maintenanceRemovesCopyRemnantsButPreservesArchivedVideo() throws Exception {
        Path archive = Files.createDirectories(mediaRoot.resolve("剧名"));
        Path video = Files.writeString(archive.resolve("剧名 S01E01.mp4"), "archived-video");
        Path part = Files.writeString(archive.resolve(".huluwa-publish-1-" + UUID.randomUUID() + ".part"), "partial-copy");

        downloads.cleanupStaleWorkspaces();

        assertFalse(Files.exists(part));
        assertEquals("archived-video", Files.readString(video));
    }

    private void publish(long id, Path source, Path target) throws Exception {
        var method = HlsDownloadService.class.getDeclaredMethod("publish", long.class, Path.class, Path.class);
        method.setAccessible(true);
        method.invoke(downloads, id, source, target);
    }

    @Test
    void publicationPublishesVideoAndRemovesPrivateCopyAndStagingFile() throws Exception {
        long id = completedTask();
        Path source = attemptOf(id, "publish").resolve("output").resolve("剧名 S01E01.mp4");
        Path archive = Files.createDirectories(mediaRoot.resolve("剧名"));
        Path target = archive.resolve(source.getFileName());

        publish(id, source, target);

        assertEquals("partial-media", Files.readString(target));
        assertFalse(Files.exists(source));
        try (var files = Files.list(archive)) { assertEquals(1, files.count()); }
    }

    @Test
    void publicationNeverOverwritesExistingVideo() throws Exception {
        long id = completedTask();
        Path source = attemptOf(id, "existing").resolve("output").resolve("剧名 S01E01.mp4");
        Path archive = Files.createDirectories(mediaRoot.resolve("剧名"));
        Path target = Files.writeString(archive.resolve(source.getFileName()), "original-video");

        InvocationTargetException failure = assertThrows(InvocationTargetException.class, () -> publish(id, source, target));

        assertInstanceOf(FileAlreadyExistsException.class, failure.getCause());
        assertEquals("original-video", Files.readString(target));
        assertTrue(Files.exists(source));
        try (var files = Files.list(archive)) { assertEquals(1, files.count()); }
    }
}
