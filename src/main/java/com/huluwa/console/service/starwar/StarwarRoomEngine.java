package com.huluwa.console.service.starwar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

/**
 * 对战世界调度器：每个进行中的房间一个 tick 循环，20Hz 推进 {@link StarwarWorld}。
 *
 * <p>结算回调由 {@link com.huluwa.console.service.StarwarService} 注入，
 * 避免引擎直接依赖数据库层。房间结束或无人后循环自动停止。</p>
 */
@Component
public class StarwarRoomEngine {

    private static final Logger log = LoggerFactory.getLogger(StarwarRoomEngine.class);

    /** 房间号 → 运行中的世界 */
    private final Map<String, StarwarWorld> worlds = new ConcurrentHashMap<>();
    /** 房间号 → tick 任务 */
    private final Map<String, ScheduledFuture<?>> tasks = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4,
            Thread.ofPlatform().daemon().name("starwar-tick-", 0).factory());

    /**
     * tick 监听（可以有多个订阅者：推送层推快照、服务层做落库）。
     * 早期实现是单个 volatile 字段，后注册的会覆盖先注册的——结算回调因此会丢，
     * 现在改为 CopyOnWriteArrayList，注册顺序不影响结果。
     */
    private final java.util.List<BiConsumer<StarwarWorld, java.util.List<StarwarWorld.Standing>>> tickListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();
    private final java.util.List<java.util.function.Consumer<StarwarWorld>> settleListeners =
            new java.util.concurrent.CopyOnWriteArrayList<>();

    public void addTickListener(BiConsumer<StarwarWorld, java.util.List<StarwarWorld.Standing>> listener) {
        if (listener != null) {
            tickListeners.add(listener);
        }
    }

    public void addSettleListener(java.util.function.Consumer<StarwarWorld> listener) {
        if (listener != null) {
            settleListeners.add(listener);
        }
    }

    /** 兼容旧签名：等价于 {@link #addSettleListener} */
    public void setSettleListener(java.util.function.Consumer<StarwarWorld> listener) {
        addSettleListener(listener);
    }

    /** 兼容旧签名：等价于 {@link #addTickListener} */
    public void setTickListener(BiConsumer<StarwarWorld, java.util.List<StarwarWorld.Standing>> listener) {
        addTickListener(listener);
    }

    public StarwarWorld world(String roomId) {
        return worlds.get(roomId);
    }

    public boolean running(String roomId) {
        return tasks.containsKey(roomId);
    }

    /** 开一局：登记世界并启动 tick 循环 */
    public StarwarWorld start(StarwarWorld world) {
        stop(world.roomId);
        worlds.put(world.roomId, world);
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(() -> tickOnce(world),
                StarwarWorld.TICK_MS, StarwarWorld.TICK_MS, TimeUnit.MILLISECONDS);
        tasks.put(world.roomId, task);
        return world;
    }

    /** 停掉房间的 tick 循环（房间关闭 / 服务重启） */
    public void stop(String roomId) {
        ScheduledFuture<?> task = tasks.remove(roomId);
        if (task != null) {
            task.cancel(false);
        }
        worlds.remove(roomId);
    }

    private void tickOnce(StarwarWorld world) {
        try {
            StarwarWorld.TickResult result = world.tick();
            java.util.List<StarwarWorld.Standing> standings = world.standings();
            for (BiConsumer<StarwarWorld, java.util.List<StarwarWorld.Standing>> listener : tickListeners) {
                try {
                    listener.accept(world, standings);
                } catch (Exception error) {
                    log.debug("星球大战 tick 监听异常：{}", error.getMessage());
                }
            }
            if (result.settled()) {
                stop(world.roomId);
                for (java.util.function.Consumer<StarwarWorld> listener : settleListeners) {
                    try {
                        listener.accept(world);
                    } catch (Exception error) {
                        log.warn("星球大战房间 {} 结算失败：{}", world.roomId, error.getMessage());
                    }
                }
            }
        } catch (Exception error) {
            log.warn("星球大战房间 {} tick 异常，停止循环：{}", world.roomId, error.getMessage());
            stop(world.roomId);
        }
    }

    /** 服务重启后清空内存里的世界（数据库里残留的对局由服务层标记为中断） */
    public void clear() {
        for (String roomId : java.util.List.copyOf(tasks.keySet())) {
            stop(roomId);
        }
    }

    @PreDestroy
    public void shutdown() {
        clear();
        scheduler.shutdownNow();
    }
}
