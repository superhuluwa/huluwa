package com.huluwa.console.service.starwar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 星球大战对战房间的实时状态机（服务端权威）。
 *
 * <p>复刻原版「是男人就撑20秒」的网络对战版核心循环：
 * 玩家用方向键控制飞机躲避四处飞来的子弹，被击中损失生命值，生命值归零则本局结束，
 * 撑得最久的人获胜。服务端按固定 tick（默认 50ms，20Hz）推进世界状态，
 * 生成子弹、推进玩家位置、判定命中，并把增量快照推给房间内的客户端。</p>
 *
 * <p>线程模型：每个房间一个 {@link StarwarWorld}，所有状态变更都在该房间的
 * 单线程执行器（{@link StarwarRoomEngine} 持有的调度线程）里完成，
 * 避免加锁；HTTP/WS 线程只入队指令，不直接改状态。</p>
 */
public final class StarwarWorld {

    /** 世界尺寸：原版 240×320 竖屏，这里保持同一比例，客户端按屏宽等比缩放 */
    public static final int WIDTH = 240;
    public static final int HEIGHT = 320;

    /** 服务端 tick：50ms（20Hz），与当年低端机刷新率对齐 */
    public static final int TICK_MS = 50;

    /** 撑过 20 秒即为「撑住」；超过后继续累积分数（原版名字的由来） */
    public static final int SURVIVE_TARGET_MS = StarwarTexts.SURVIVE_TARGET_MS;

    /** 每局最长时长：20 秒目标 + 20 秒加时 = 40 秒，避免房间永远不结束 */
    public static final int MAX_MATCH_MS = 40_000;

    /** 飞机碰撞半径 */
    public static final int PLANE_RADIUS = 5;

    /** 方向键移动速度（px/tick）：60px/s，与原版低端机手感对齐 */
    public static final double MOVE_SPEED = 3.0;

    /** 子弹半径 */
    public static final int BULLET_RADIUS = 3;

    /** 初始生命值：被击中 5 次结束（原版血量条按像素格计，这里 100 / 每次 20） */
    public static final int MAX_HP = 100;
    public static final int HIT_DAMAGE = 20;

    /** 玩家一局内的状态 */
    public static final class PlayerState {
        public final String username;
        public final String nickname;
        public String plane;
        /** 位置（浮点，便于平滑移动） */
        public double x;
        public double y;
        /** 当前按住的移动方向：dx/dy ∈ {-1,0,1} */
        public int dx;
        public int dy;
        public int hp;
        public int score;
        public long survivedMs;
        public boolean alive = true;
        /** 「使用道具」触发的一次性护盾（无敌帧），单位 tick */
        public int shieldTicks;
        /** 最后一次收到客户端输入的时间，用于剔除掉线玩家 */
        public long lastInputAt;
        /** 是否已经撑满 20 秒（达标奖励只给一次） */
        public boolean reachedTarget;

        public PlayerState(String username, String nickname, String plane) {
            this.username = username;
            this.nickname = nickname;
            this.plane = plane;
            this.x = WIDTH / 2.0;
            this.y = HEIGHT - 40;
            this.hp = MAX_HP;
        }
    }

    /** 子弹：从四边飞入，速度随时间递增 */
    public static final class Bullet {
        public double x;
        public double y;
        public double vx;
        public double vy;
        /** 来源玩家（用于结算时的「谁打中了谁」，原版不区分，这里保留扩展位） */
        public String owner;

        public Bullet(double x, double y, double vx, double vy, String owner) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.owner = owner;
        }
    }

    /** 道具：玩家吃后降低难度（缩小判定、回血、加分、加护盾） */
    public static final class Prop {
        public double x;
        public double y;
        /** shield | heal | slow | score */
        public String kind;

        public Prop(double x, double y, String kind) {
            this.x = x;
            this.y = y;
            this.kind = kind;
        }
    }

    public final String matchId;
    public final String roomId;
    public final String zone;
    public final int mapId;
    public final boolean itemsEnabled;

    public final Map<String, PlayerState> players = new ConcurrentHashMap<>();
    public final List<Bullet> bullets = new ArrayList<>();
    public final List<Prop> props = new ArrayList<>();

    public long elapsedMs;
    public int ticks;
    public boolean settled;

    private final java.util.Random random = new java.util.Random();

    public StarwarWorld(String matchId, String roomId, String zone, int mapId, boolean itemsEnabled) {
        this.matchId = matchId;
        this.roomId = roomId;
        this.zone = zone;
        this.mapId = mapId;
        this.itemsEnabled = itemsEnabled;
    }

    /** 加入一名玩家（已在房间成员表里的） */
    public PlayerState join(String username, String nickname, String plane) {
        // 初始位置错开，避免重叠
        PlayerState state = new PlayerState(username, nickname, plane);
        int index = players.size();
        state.x = WIDTH / 2.0 + (index - 1) * 40;
        state.x = Math.max(PLANE_RADIUS + 2, Math.min(WIDTH - PLANE_RADIUS - 2, state.x));
        players.put(username, state);
        return state;
    }

    /** 客户端上报的输入：方向键按住状态 */
    public void input(String username, int dx, int dy) {
        PlayerState state = players.get(username);
        if (state == null) {
            return;
        }
        state.dx = Math.max(-1, Math.min(1, dx));
        state.dy = Math.max(-1, Math.min(1, dy));
        state.lastInputAt = System.currentTimeMillis();
    }

    /** 「使用道具」：消耗一次护盾（原版 ok / * 键） */
    public boolean useItem(String username) {
        PlayerState state = players.get(username);
        if (state == null || !state.alive) {
            return false;
        }
        // 每局只给一次护盾，用道具键触发
        if (state.shieldTicks > 0) {
            return false;
        }
        state.shieldTicks = 40; // 2 秒无敌
        return true;
    }

    /**
     * 推进一个 tick。返回本 tick 是否产生了「有人死亡」等需要单独推送给客户端的事件。
     */
    public TickResult tick() {
        if (settled) {
            return new TickResult(false, List.of());
        }
        elapsedMs += TICK_MS;
        ticks++;

        // 难度曲线对齐原版「是男人就撑20秒」：20 秒是分水岭，
        // 前 20 秒逐步加难（每 4 秒一档，共 5 档），20 秒后进入「撑住了」的奖励阶段，难度封顶。
        int difficulty = elapsedMs >= SURVIVE_TARGET_MS
                ? 6
                : 1 + (int) (elapsedMs / 4000);
        double speed = 1.15 + difficulty * 0.22;

        // 1) 推进玩家位置
        for (PlayerState state : players.values()) {
            if (!state.alive) {
                continue;
            }
            state.survivedMs = elapsedMs;
            if (state.shieldTicks > 0) {
                state.shieldTicks--;
            }
            double nx = state.x + state.dx * 3.0;
            double ny = state.y + state.dy * 3.0;
            state.x = Math.max(PLANE_RADIUS, Math.min(WIDTH - PLANE_RADIUS, nx));
            state.y = Math.max(PLANE_RADIUS, Math.min(HEIGHT - PLANE_RADIUS, ny));
            // 存活计分：撑到 20 秒给「达标奖励」+200，之后每秒 20 分继续累加
            if (state.survivedMs % 1000 < TICK_MS) {
                state.score += state.survivedMs > SURVIVE_TARGET_MS ? 20 : 10;
                if (state.survivedMs >= SURVIVE_TARGET_MS && !state.reachedTarget) {
                    state.reachedTarget = true;
                    state.score += 200;
                }
            }
        }

        // 2) 生成子弹：随难度提高生成概率
        int spawnCount = 1 + difficulty / 2;
        for (int i = 0; i < spawnCount; i++) {
            if (random.nextInt(100) < 30 + difficulty * 8) {
                bullets.add(spawnBullet(speed));
            }
        }

        // 3) 推进子弹并判定命中
        List<String> deaths = new ArrayList<>();
        java.util.Iterator<Bullet> iterator = bullets.iterator();
        while (iterator.hasNext()) {
            Bullet bullet = iterator.next();
            bullet.x += bullet.vx;
            bullet.y += bullet.vy;
            if (bullet.x < -10 || bullet.x > WIDTH + 10 || bullet.y < -10 || bullet.y > HEIGHT + 10) {
                iterator.remove();
                continue;
            }
            for (PlayerState state : players.values()) {
                if (!state.alive || state.shieldTicks > 0) {
                    continue;
                }
                double dx = state.x - bullet.x;
                double dy = state.y - bullet.y;
                if (dx * dx + dy * dy <= (PLANE_RADIUS + BULLET_RADIUS) * (PLANE_RADIUS + BULLET_RADIUS)) {
                    iterator.remove();
                    state.hp -= HIT_DAMAGE;
                    if (state.hp <= 0) {
                        state.hp = 0;
                        state.alive = false;
                        deaths.add(state.username);
                    }
                    break;
                }
            }
        }

        // 4) 道具（仅开启道具的房间）
        if (itemsEnabled) {
            if (props.size() < 3 && random.nextInt(100) < 2) {
                String[] kinds = {"shield", "heal", "slow", "score"};
                props.add(new Prop(20 + random.nextInt(WIDTH - 40), 40 + random.nextInt(HEIGHT - 80),
                        kinds[random.nextInt(kinds.length)]));
            }
            java.util.Iterator<Prop> propIterator = props.iterator();
            while (propIterator.hasNext()) {
                Prop prop = propIterator.next();
                for (PlayerState state : players.values()) {
                    if (!state.alive) {
                        continue;
                    }
                    double dx = state.x - prop.x;
                    double dy = state.y - prop.y;
                    if (dx * dx + dy * dy <= (PLANE_RADIUS + 7) * (PLANE_RADIUS + 7)) {
                        propIterator.remove();
                        applyProp(state, prop.kind);
                        break;
                    }
                }
            }
        }

        // 5) 结束判定：全员阵亡，或到达最长时长
        boolean allDead = players.values().stream().noneMatch(p -> p.alive);
        if (allDead || elapsedMs >= MAX_MATCH_MS) {
            settled = true;
        }
        return new TickResult(settled, deaths);
    }

    private void applyProp(PlayerState state, String kind) {
        switch (kind) {
            case "heal" -> state.hp = Math.min(MAX_HP, state.hp + HIT_DAMAGE * 2);
            case "shield" -> state.shieldTicks = 60;
            case "score" -> state.score += 200;
            // slow：本局不再加速度的简化处理，直接加分体现「降低难度」
            case "slow" -> state.score += 100;
            default -> { }
        }
    }

    private Bullet spawnBullet(double speed) {
        // 从四条边随机一侧进入，朝中心偏一点
        int side = random.nextInt(4);
        double x;
        double y;
        switch (side) {
            case 0 -> { x = random.nextDouble() * WIDTH; y = -5; }
            case 1 -> { x = WIDTH + 5; y = random.nextDouble() * HEIGHT; }
            case 2 -> { x = random.nextDouble() * WIDTH; y = HEIGHT + 5; }
            default -> { x = -5; y = random.nextDouble() * HEIGHT; }
        }
        double targetX = WIDTH / 2.0 + (random.nextDouble() - 0.5) * 120;
        double targetY = HEIGHT / 2.0 + (random.nextDouble() - 0.5) * 120;
        double dx = targetX - x;
        double dy = targetY - y;
        double len = Math.max(0.001, Math.hypot(dx, dy));
        return new Bullet(x, y, dx / len * speed, dy / len * speed, null);
    }

    /** 结算：按「撑住时长 → 分数 → 剩余血量」排序，得出名次 */
    public List<Standing> standings() {
        List<PlayerState> ordered = new ArrayList<>(players.values());
        ordered.sort((a, b) -> {
            if (a.alive != b.alive) {
                return a.alive ? -1 : 1;
            }
            int byTime = Long.compare(b.survivedMs, a.survivedMs);
            if (byTime != 0) {
                return byTime;
            }
            int byScore = Integer.compare(b.score, a.score);
            if (byScore != 0) {
                return byScore;
            }
            return Integer.compare(b.hp, a.hp);
        });
        List<Standing> out = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            PlayerState p = ordered.get(i);
            out.add(new Standing(i + 1, p.username, p.nickname, p.score, p.survivedMs, p.hp, p.survivedMs >= SURVIVE_TARGET_MS));
        }
        return out;
    }

    /** 推送给客户端的快照（增量足够小，直接全量下发，客户端按 tick 覆盖渲染） */
    public Map<String, Object> snapshot() {
        List<Map<String, Object>> playerRows = new ArrayList<>();
        for (PlayerState state : players.values()) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("user", state.username);
            row.put("nick", state.nickname);
            row.put("plane", state.plane);
            row.put("x", round(state.x));
            row.put("y", round(state.y));
            row.put("hp", state.hp);
            row.put("score", state.score);
            row.put("alive", state.alive);
            row.put("shield", state.shieldTicks > 0);
            row.put("survived", state.survivedMs);
            row.put("reachedTarget", state.reachedTarget);
            playerRows.add(row);
        }
        List<Map<String, Object>> bulletRows = new ArrayList<>(bullets.size());
        for (Bullet bullet : bullets) {
            bulletRows.add(Map.of("x", round(bullet.x), "y", round(bullet.y)));
        }
        List<Map<String, Object>> propRows = new ArrayList<>(props.size());
        for (Prop prop : props) {
            propRows.add(Map.of("x", round(prop.x), "y", round(prop.y), "kind", prop.kind));
        }
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        out.put("matchId", matchId);
        out.put("tick", ticks);
        out.put("elapsed", elapsedMs);
        out.put("width", WIDTH);
        out.put("height", HEIGHT);
        out.put("players", playerRows);
        out.put("bullets", bulletRows);
        out.put("props", propRows);
        out.put("settled", settled);
        return out;
    }

    private static double round(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    /** 一个 tick 的结果 */
    public record TickResult(boolean settled, List<String> deaths) {
    }

    /** 名次行 */
    public record Standing(int ranking, String username, String nickname, int score,
                           long survivedMs, int hp, boolean survivedTarget) {
    }
}
