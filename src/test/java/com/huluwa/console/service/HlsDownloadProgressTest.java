package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HlsDownloadProgressTest {
    @Test
    void readsRedirectedConsoleProgressAndChangingBars() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        assertNull(progress.snapshot().percent());
        progress.accept("\u001b[32mVid 1920x1080 ━━━╸──── 25/100 25.00% 12.00MB/48.00MB 2.00MBps 00:18\u001b[0m");
        assertEquals(25d, progress.snapshot().percent());
        assertEquals("2.0 MB/s", progress.snapshot().speed());
        progress.accept("Vid 1920x1080 ━━━━━━╸── 50/100 50.00% 24.00MB/48.00MB 512.00KBps 00:48");
        assertEquals(50d, progress.snapshot().percent());
        assertEquals("512.0 KB/s", progress.snapshot().speed());
    }

    @Test
    void readsBackToBackUpdatesWithoutNewlinesAndUsesLatestValue() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        progress.accept("Vid 1920x1080 | 800 Kbps ━━━━━ 10/814 1.23% 10.00MB/703.50MB 2.00MBps 00:30"
                + "Vid 1920x1080 | 800 Kbps ━━━━━ 20/814 2.46% 20.00MB/703.50MB 3.00MBps 00:29");
        assertEquals(2.4d, progress.snapshot().percent());
        assertEquals("3.0 MB/s", progress.snapshot().speed());
    }

    @Test
    void derivesPercentFromByteCountersWhenSamplerOmitsPercent() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        progress.accept("Vid 1920x1080 | 800 Kbps 351.75MB/703.50MB 2.50MBps 00:20");
        assertEquals(50d, progress.snapshot().percent());
        assertEquals("2.5 MB/s", progress.snapshot().speed());
    }

    @Test
    void publishesProgressWithoutNewlinesAndPreservesSplitConsoleOutput() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        progress.accept("\u001b[3");
        progress.accept("2mVi");
        assertNull(progress.snapshot().percent());
        progress.accept("d 1920x1080 ━━━ 12/100 12.");
        assertNull(progress.snapshot().percent());
        progress.accept("00% 6.00MiB/50.00MiB 1.50MiB/s\u001b[0m");
        assertEquals(12d, progress.snapshot().percent());
        assertEquals("1.5 MB/s", progress.snapshot().speed());
        progress.accept("Vid 1920x1080 ━━━━━ 25/100 25.00% 12.50MiB/50.00MiB 2.00MiB/s");
        assertEquals(25d, progress.snapshot().percent());
        assertEquals("2.0 MB/s", progress.snapshot().speed());
    }

    @Test
    void separatesTracksWithMixedProgressFormatsAndDifferentByteUnits() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        progress.accept("Vid 1920x1080 ━━━ 50/100 50.00% 500MB/1GB 4.00MBps"
                + "Aud 128 Kbps 256.00MiB/1.00GiB 512.00KiB/s");
        assertEquals(37.5d, progress.snapshot().percent());
        assertEquals("4.5 MB/s", progress.snapshot().speed());
        progress.accept("\r\n下载地址 https://cdn.example/a.m3u8?token=secret\n");
        assertEquals(37.5d, progress.snapshot().percent());
        assertFalse(progress.snapshot().toString().contains("secret"));
    }

    @Test
    void averagesTracksAndDoesNotReportArchiveCompletionFromSegments() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        progress.accept("Vid 1920x1080 ━━━━━━━ 100/100 100.00% 48.00MB - 00:00");
        progress.accept("Aud 128 Kbps ─────── 0/100 0.00% - - 00:00");
        assertEquals(50d, progress.snapshot().percent());
        assertEquals("downloading", progress.snapshot().phase());
        progress.accept("Aud 128 Kbps ━━━━━━━ 100/100 100.00% 2.00MB - 00:00");
        assertEquals(99d, progress.snapshot().percent());
        assertEquals("merging", progress.snapshot().phase());
    }

    @Test
    void ignoresLogsAndInvalidProgressAndStartsEachAttemptFresh() {
        HlsDownloadProgress progress = new HlsDownloadProgress();
        progress.accept("下载地址 https://cdn.example/a.m3u8?token=secret 50%");
        progress.accept("Vid ━━━ 0/0 0.00% - -");
        progress.accept("Vid ━━━ 120/100 120.00% - -");
        assertNull(progress.snapshot().percent());
        progress.accept("Vid ━━━ 70/100 70.00% - 1.00MBps");
        assertEquals(70d, progress.snapshot().percent());
        assertNull(new HlsDownloadProgress().snapshot().percent());
        assertFalse(progress.snapshot().toString().contains("secret"));
    }
}
