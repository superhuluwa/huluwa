package com.huluwa.console.service;

import com.huluwa.console.service.starwar.StarwarRoomEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 星球大战后台调度：
 * <ul>
 *   <li>倒计时结束自动开局（房主点了「快速开始」后 3 秒）</li>
 *   <li>清理空闲房间（30 分钟无人）</li>
 *   <li>启动时把残留的 playing 对局标记为中断（服务重启会清空内存世界）</li>
 * </ul>
 */
@Component
public class StarwarScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StarwarScheduler.class);

    private final JdbcTemplate jdbc;
    private final StarwarService service;
    private final StarwarRoomEngine engine;

    public StarwarScheduler(JdbcTemplate jdbc, StarwarService service, StarwarRoomEngine engine) {
        this.jdbc = jdbc;
        this.service = service;
        this.engine = engine;
    }

    /** 每 500ms 检查一次到点的倒计时房间 */
    @Scheduled(fixedDelay = 500L, initialDelay = 2000L)
    public void tickCountdowns() {
        long now = System.currentTimeMillis();
        List<String> ready = jdbc.queryForList(
                "SELECT id FROM starwar_room WHERE status = 'countdown' AND countdown_end > 0 AND countdown_end <= ?",
                String.class, now);
        for (String roomId : ready) {
            try {
                service.startMatch(roomId);
                log.info("星球大战房间 {} 倒计时结束，已开局", roomId);
            } catch (Exception error) {
                log.warn("星球大战房间 {} 自动开局失败：{}", roomId, error.getMessage());
                jdbc.update("UPDATE starwar_room SET status = 'waiting', countdown_end = 0 WHERE id = ?", roomId);
            }
        }
    }

    /** 每 5 分钟清理空闲房间 */
    @Scheduled(fixedDelay = 300_000L, initialDelay = 60_000L)
    public void sweepRooms() {
        int closed = service.sweepIdleRooms();
        if (closed > 0) {
            log.info("星球大战清理空闲房间 {} 个", closed);
        }
    }

    /** 启动恢复：内存世界已清空，残留 playing 对局与房间统一标记中断 */
    @Override
    public void run(ApplicationArguments args) {
        engine.clear();
        int matches = jdbc.update(
                "UPDATE starwar_match SET status = 'settled', result_json = ? WHERE status = 'playing'",
                "{\"interrupted\":true,\"message\":\"服务重启导致对局中断\"}");
        int rooms = jdbc.update(
                "UPDATE starwar_room SET status = 'waiting', countdown_end = 0 WHERE status IN ('playing','countdown')");
        if (matches > 0 || rooms > 0) {
            log.info("星球大战启动恢复：中断对局 {} 场，重置房间 {} 个", matches, rooms);
        }
    }
}
