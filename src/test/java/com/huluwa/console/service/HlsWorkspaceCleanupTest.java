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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 工作区清理：下载器的成品在 .huluwa-work/&lt;任务&gt;/attempt-* 的 output 下产出，
 * 归档发布后必须回收（只留 download.log），否则失败重试与断电容灾会持续堆积分片与半成品。
 */
class HlsWorkspaceCleanupTest {
    @TempDir
    Path mediaRoot;

    private SingleConnectionDataSource dataSource;
    private HlsDownloadService downloads;

    @BeforeEach
    void setup() {
        HlsProperties paths = new HlsProperties();
        paths.setMediaRoot(mediaRoot.toString());
        paths.setLegacyMediaRoot(mediaRoot.resolve("legacy").toString());
        dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        JdbcTemplate db = new JdbcTemplate(dataSource);
        downloads = spy(new HlsDownloadService(db, new ConfigService(db), paths,
                mock(DyttSearchService.class), mock(HlsNoticeService.class), new ObjectMapper()));
        doNothing().when(downloads).requireEnvironment();
    }

    @AfterEach
    void close() {
        downloads.shutdown();
        dataSource.destroy();
    }

    /** 模拟一次失败的下载尝试：output 里留有半成品 / 分片，另有日志。 */
    private Path attemptOf(long taskId, String marker) throws Exception {
        Path attempt = mediaRoot.resolve(".huluwa-work").resolve(String.valueOf(taskId))
                .resolve("attempt-1-" + marker);
        Files.createDirectories(attempt.resolve("output"));
        Files.createDirectories(attempt.resolve("segments"));
        Files.writeString(attempt.resolve("output").resolve("剧名 S01E01.mp4"), "partial-media");
        Files.writeString(attempt.resolve("segments").resolve("0.ts"), "segment");
        Files.writeString(attempt.resolve("download.log"), "download log");
        return attempt;
    }

    @Test
    void startupCleanupRemovesSegmentsAndPartialMediaButKeepsLog() throws Exception {
        Path attempt = attemptOf(7, "stale");
        Path media = attempt.resolve("output").resolve("剧名 S01E01.mp4");
        assertTrue(Files.exists(media));

        downloads.run(null);

        assertFalse(Files.exists(media), "启动清扫必须回收半成品媒体");
        assertFalse(Files.exists(attempt.resolve("segments").resolve("0.ts")), "分片必须回收");
        assertTrue(Files.exists(attempt.resolve("download.log")), "日志要保留给「日志」按钮查看");
    }

    @Test
    void startupCleanupReclaimsEmptyDirectoriesButKeepsLogDirectories() throws Exception {
        Path attempt = attemptOf(9, "empty");
        Path output = attempt.resolve("output");
        Files.delete(output.resolve("剧名 S01E01.mp4"));
        assertTrue(Files.exists(output));

        downloads.run(null);

        assertFalse(Files.exists(output), "空目录应被回收");
        assertTrue(Files.exists(attempt), "含日志的目录保留");
    }

    @Test
    void startupCleanupIsIdempotentWhenWorkDirectoryMissing() {
        assertDoesNotThrow(() -> downloads.run(null));
        assertDoesNotThrow(() -> downloads.run(null));
    }
}
