package com.huluwa.console.service;

import com.huluwa.console.config.HlsProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EpisodeLibraryServiceTest {
    private Path downloads;
    private EpisodeLibraryService service;

    @BeforeEach
    void setUp() throws IOException {
        downloads = Files.createTempDirectory("huluwa-library-");
        HlsProperties properties = new HlsProperties();
        properties.setMediaRoot(downloads.toString());
        service = new EpisodeLibraryService(properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        if (downloads != null && Files.exists(downloads)) {
            try (var paths = Files.walk(downloads)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    @Test
    void scansNestedEpisodesAndParsesSeasonAndEpisode() throws Exception {
        Path series = Files.createDirectories(downloads.resolve("庆余年"));
        Files.writeString(series.resolve("庆余年 S02E05.mp4"), "episode");
        Files.writeString(series.resolve("庆余年 第6集.mkv"), "episode");
        Files.writeString(series.resolve("readme.txt"), "not video");

        var result = service.scan();

        assertEquals(2, result.entries().size());
        assertEquals(2, result.scannedFiles());
        assertEquals(1, result.skippedFiles());
        var e5 = result.entries().stream().filter(entry -> entry.episode() == 5).findFirst().orElseThrow();
        assertEquals("庆余年", e5.seriesTitle());
        assertEquals(2, e5.season());
        assertEquals("MP4", e5.extension());
        assertTrue(e5.relativePath().endsWith("庆余年 S02E05.mp4"));
    }

    @Test
    void skipsWorkAndHiddenDirectories() throws Exception {
        Files.writeString(downloads.resolve("正片 S01E01.mp4"), "ok");
        Path work = Files.createDirectories(downloads.resolve(".huluwa-work").resolve("1"));
        Files.writeString(work.resolve("part.mp4"), "temp");
        Path hidden = Files.createDirectories(downloads.resolve(".private"));
        Files.writeString(hidden.resolve("hide S01E02.mp4"), "hidden");

        var result = service.scan();

        assertEquals(1, result.entries().size());
        assertEquals("正片 S01E01.mp4", result.entries().getFirst().fileName());
    }

    @Test
    void deleteAcceptsOnlyFreshScanTokensAndRemovesEmptySeriesDirectory() throws Exception {
        Path series = Files.createDirectories(downloads.resolve("测试剧"));
        Path file = Files.writeString(series.resolve("测试剧 S01E01.mp4"), "episode");
        var entry = service.scan().entries().getFirst();

        var result = service.delete(new EpisodeLibraryService.DeleteRequest(List.of(entry.id())));

        assertEquals(1, result.deleted());
        assertEquals(0, result.failed());
        assertFalse(Files.exists(file));
        assertFalse(Files.exists(series), "删除最后一项后应清理空剧名目录");

        var stale = service.delete(new EpisodeLibraryService.DeleteRequest(List.of(entry.id())));
        assertEquals(0, stale.deleted());
        assertEquals(1, stale.failed());
        assertTrue(stale.failures().getFirst().message().contains("过期"));
    }

    @Test
    void modifiedFileIsNotDeletedWithStaleToken() throws Exception {
        Path file = Files.writeString(downloads.resolve("剧 S01E01.mp4"), "episode");
        var entry = service.scan().entries().getFirst();
        Files.writeString(file, "changed");

        var result = service.delete(new EpisodeLibraryService.DeleteRequest(List.of(entry.id())));

        assertEquals(0, result.deleted());
        assertEquals(1, result.failed());
        assertTrue(Files.exists(file));
    }

    @Test
    void symbolsAreNotListed() throws Exception {
        Path outside = Files.createTempFile("outside-", ".mp4");
        try {
            Files.createSymbolicLink(downloads.resolve("outside.mp4"), outside);
            assertTrue(service.scan().entries().isEmpty());
        } finally {
            Files.deleteIfExists(outside);
        }
    }
}
