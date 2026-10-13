package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatTaskExecutorTest {

    @Test
    void twoBlockedVideoTasksDoNotDelayMovieSearch() throws Exception {
        CountDownLatch videosStarted = new CountDownLatch(2);
        CountDownLatch releaseVideos = new CountDownLatch(1);
        CountDownLatch movieFinished = new CountDownLatch(1);
        try (ChatTaskExecutor executor = new ChatTaskExecutor()) {
            for (int i = 0; i < 2; i++) executor.submit("video", () -> {
                videosStarted.countDown();
                try {
                    releaseVideos.await();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
            assertTrue(videosStarted.await(2, TimeUnit.SECONDS));
            executor.submit("movie", movieFinished::countDown);
            assertTrue(movieFinished.await(2, TimeUnit.SECONDS), "搜索不应等待两个视频任务结束");
        } finally {
            releaseVideos.countDown();
        }
    }
}
