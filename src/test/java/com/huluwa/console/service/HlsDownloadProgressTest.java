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
