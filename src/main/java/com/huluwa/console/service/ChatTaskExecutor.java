package com.huluwa.console.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** 视频解析独立排队，避免两个慢解析占满原有聊天线程后阻塞影视搜索和下载指令。 */
final class ChatTaskExecutor implements AutoCloseable {

    private final ExecutorService chat = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("chat-worker-", 0).factory());
    private final ExecutorService video = Executors.newFixedThreadPool(2,
            Thread.ofPlatform().daemon().name("chat-video-worker-", 0).factory());

    void submit(String kind, Runnable task) {
        ("video".equals(kind) ? video : chat).submit(task);
    }

    @Override
    public void close() {
        chat.shutdownNow();
        video.shutdownNow();
    }
}
