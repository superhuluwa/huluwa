package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.starwar.StarwarRoomEngine;
import com.huluwa.console.service.starwar.StarwarTexts;
import com.huluwa.console.service.starwar.StarwarWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 星球大战（是男人就撑20秒 网络对战版）服务端。
 *
 * <p>完整复刻 2009 年冒泡社区 {@code starwar.mrp} 的服务端行为：
 * 大厅四专区房间列表、创建 / 进入 / 踢人 / 快速开始、等待聊天室（10 秒发言间隔、48 小时禁言）、
 * 创建房间计费（K币 / 水晶）、实时对战、结算排名与水晶延迟发放、好友、资料、排行榜。</p>
 *
 * <p>数据落 SQLite（schema v12 的 {@code starwar_*} 表）；实时对局状态在
 * {@link StarwarRoomEngine} 的内存世界对象里，服务重启即中断。</p>
 */
@Service
public class StarwarService {

    private static final Logger log = LoggerFactory.getLogger(StarwarService.class);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 聊天间隔：原文「聊天间隔10秒,请勿刷屏!」 */
    private static final long CHAT_INTERVAL_MS = 10_000L;
    /** 禁言时长：原文「48 小时内禁止进入聊天室」 */
    private static final long SILENCE_MS = 48L * 60 * 60 * 1000;
    /** 开局倒计时：3 秒 */
    private static final long COUNTDOWN_MS = 3_000L;
    /** 房间空闲自动关闭：30 分钟无人 */
    private static final long ROOM_IDLE_MS = 30L * 60 * 1000;

    /** 创建房间默认费用（按专区） */
    private static final Map<String, Integer> DEFAULT_COIN_COST = Map.of(
            "multi", 10, "duo", 5, "team", 15, "activity", 20);
    private static final Map<String, Integer> DEFAULT_CRYSTAL_COST = Map.of(
            "multi", 1, "duo", 1, "team", 2, "activity", 3);

    private static final int MAX_ROOMS_PER_ZONE = 50;
    private static final int MAX_NICKNAME = 12;
    private static final int MAX_CHAT_LENGTH = 120;
    private static final int MAX_ROOM_NAME = 16;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final StarwarRoomEngine engine;
    private final SecureRandom random = new SecureRandom();

    public StarwarService(JdbcTemplate jdbc, ObjectMapper json, StarwarRoomEngine engine) {
        this.jdbc = jdbc;
        this.json = json;
        this.engine = engine;
        this.engine.addSettleListener(this::settle);
    }

    // ================================================================== 玩家档案

    /** 取玩家档案；不存在则按「新兵」初始化（初始机库、默认短语、排行榜行） */
    public Map<String, Object> profile(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM starwar_player WHERE username = ?", username);
        if (rows.isEmpty()) {
            createPlayer(username);
            rows = jdbc.queryForList("SELECT * FROM starwar_player WHERE username = ?", username);
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> out = new LinkedHashMap<>(row);
        int level = intOf(row.get("level"));
        int wins = intOf(row.get("wins"));
        int losses = intOf(row.get("losses"));
        int total = wins + losses;
        out.put("rank", StarwarTexts.rankOf(level));
        out.put("winRate", total == 0 ? 0 : Math.round(wins * 1000.0 / total) / 10.0);
        out.put("matches", total);
        out.put("silencedFor", Math.max(0, intOf(row.get("silent_until")) * 1000L - System.currentTimeMillis()));
        out.put("phrases", phrases(username));
        out.put("hangar", hangar(username));
        return out;
    }

    private void createPlayer(String username) {
        String now = LocalDateTime.now().format(TIME_FORMAT);
        String nickname = username;
        List<Map<String, Object>> users = jdbc.queryForList(
                "SELECT nickname FROM sys_user WHERE username = ?", username);
        if (!users.isEmpty() && users.get(0).get("nickname") != null) {
            nickname = String.valueOf(users.get(0).get("nickname"));
        }
        try {
            jdbc.update("""
                    INSERT OR IGNORE INTO starwar_player
                    (username, nickname, level, exp, wins, losses, gold, crystal, plane, equip_engine,
                     silent_until, banned_until, created_at, updated_at)
                    VALUES (?, ?, 1, 0, 0, 0, 100, 10, 'plane', 'engine', 0, 0, ?, ?)
                    """, username, nickname, now, now);
        } catch (Exception error) {
            log.debug("初始化星球大战玩家 {} 失败：{}", username, error.getMessage());
            return;
        }
        jdbc.update("""
                INSERT OR IGNORE INTO starwar_hangar (username, item_type, item_id, source, created_at)
                VALUES (?, 'plane', 'plane', 'initial', ?)
                """, username, now);
        jdbc.update("""
                INSERT OR IGNORE INTO starwar_hangar (username, item_type, item_id, source, created_at)
                VALUES (?, 'engine', 'engine', 'initial', ?)
                """, username, now);
        for (int slot = 1; slot <= StarwarTexts.DEFAULT_PHRASES.size(); slot++) {
            jdbc.update("INSERT OR IGNORE INTO starwar_phrase (username, slot, content) VALUES (?, ?, ?)",
                    username, slot, StarwarTexts.DEFAULT_PHRASES.get(slot - 1));
        }
        jdbc.update("""
                INSERT OR IGNORE INTO starwar_rank
                (username, nickname, total_score, best_score, matches, wins, updated_at)
                VALUES (?, ?, 0, 0, 0, 0, ?)
                """, username, nickname, now);
    }

    public Map<String, Object> updateNickname(String username, String nickname) {
        String value = nickname == null ? "" : nickname.trim();
        if (value.isEmpty() || value.length() > MAX_NICKNAME) {
            throw new BusinessException(400, "昵称需 1-" + MAX_NICKNAME + " 个字符");
        }
        profile(username);
        jdbc.update("UPDATE starwar_player SET nickname = ?, updated_at = ? WHERE username = ?",
                value, LocalDateTime.now().format(TIME_FORMAT), username);
        jdbc.update("UPDATE starwar_rank SET nickname = ? WHERE username = ?", value, username);
        return profile(username);
    }

    /** 我的状态：等级 / 胜率 / 飞机（原文三行标签） */
    public Map<String, Object> myStatus(String username) {
        Map<String, Object> profile = profile(username);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("level", profile.get("level"));
        out.put("rank", profile.get("rank"));
        out.put("winRate", profile.get("winRate"));
        out.put("plane", profile.get("plane"));
        out.put("gold", profile.get("gold"));
        out.put("crystal", profile.get("crystal"));
        out.put("wins", profile.get("wins"));
        out.put("losses", profile.get("losses"));
        out.put("labels", Map.of(
                "level", StarwarTexts.LABEL_LEVEL,
                "winRate", StarwarTexts.LABEL_WINRATE,
                "plane", StarwarTexts.LABEL_PLANE));
        return out;
    }

    // ================================================================== 大厅

    /** 大厅首页数据：四专区、星球、段位、帮助文案 */
    public Map<String, Object> lobby(String username) {
        Map<String, Object> profile = profile(username);
        int level = intOf(profile.get("level"));
        List<Map<String, Object>> zones = new ArrayList<>();
        StarwarTexts.ZONES.forEach((key, title) -> zones.add(Map.of(
                "key", key,
                "title", title,
                "capacity", StarwarTexts.capacity(key),
                "costCoin", DEFAULT_COIN_COST.getOrDefault(key, 10),
                "costCrystal", DEFAULT_CRYSTAL_COST.getOrDefault(key, 1))));

        List<Map<String, Object>> planets = new ArrayList<>();
        for (StarwarTexts.Planet planet : StarwarTexts.PLANETS) {
            int need = StarwarTexts.unlockLevelOf(planet.id());
            planets.add(Map.of(
                    "id", planet.id(),
                    "name", planet.name(),
                    "icon", planet.icon(),
                    "unlockText", StarwarTexts.unlockOf(planet.id()),
                    "unlockLevel", need,
                    "locked", need > 0 && level < need));
        }

        List<Map<String, Object>> ranks = new ArrayList<>();
        StarwarTexts.RANKS.forEach(rank -> ranks.add(Map.of("name", rank.name(), "minLevel", rank.minLevel())));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("profile", profile);
        out.put("zones", zones);
        out.put("planets", planets);
        out.put("ranks", ranks);
        out.put("texts", texts());
        out.put("help", help());
        out.put("chatNotice", StarwarTexts.CHAT_NOTICE_PREFIX + StarwarTexts.CHAT_RULES);
        out.put("activityNotice", StarwarTexts.activitySchedule());
        out.put("activityFree", StarwarTexts.activityFreeNow(java.time.LocalDateTime.now()));
        out.put("online", onlineCount());
        return out;
    }

    /** 客户端需要的全部原文文案（1:1 还原用） */
    public Map<String, Object> texts() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("pager", StarwarTexts.PAGER);
        out.put("rankTitle", StarwarTexts.RANK_TITLE);
        out.put("zones", StarwarTexts.ZONES);
        out.put("menu", Map.of(
                "planeShop", StarwarTexts.PLANE_SHOP,
                "hangar", StarwarTexts.MY_HANGAR,
                "trade", StarwarTexts.TRADE_MARKET,
                "ranking", StarwarTexts.RANKING));
        out.put("buttons", Map.ofEntries(
                Map.entry("ok", StarwarTexts.OK), Map.entry("back", StarwarTexts.BACK),
                Map.entry("menu", StarwarTexts.MENU), Map.entry("info", StarwarTexts.INFO),
                Map.entry("error", StarwarTexts.ERROR), Map.entry("help", StarwarTexts.HELP),
                Map.entry("enter", StarwarTexts.ENTER), Map.entry("exit", StarwarTexts.EXIT),
                Map.entry("continue", StarwarTexts.CONTINUE),
                Map.entry("createRoom", StarwarTexts.ROOM_CREATE),
                Map.entry("enterRoom", StarwarTexts.ROOM_IN),
                Map.entry("quickStart", StarwarTexts.ROOM_QUICK_START),
                Map.entry("kick", StarwarTexts.ROOM_KICK)));
        out.put("server", Map.of(
                "error", StarwarTexts.SERVER_ERROR,
                "timeout", StarwarTexts.SERVER_TIMEOUT,
                "fetching", StarwarTexts.FETCHING_DATA,
                "fetchingInfo", StarwarTexts.FETCHING_INFO,
                "waiting", StarwarTexts.WAITING_SERVER,
                "refreshFailed", StarwarTexts.REFRESH_ROOMS_FAILED,
                "noResponse", StarwarTexts.SERVER_NO_RESPONSE,
                "connectTimeout", StarwarTexts.CONNECT_TIMEOUT,
                "waitingRank", StarwarTexts.WAITING_RANK,
                "waitingResult", StarwarTexts.WAITING_RESULT,
                "comingSoon", StarwarTexts.COMING_SOON));
        out.put("room", Map.of(
                "full", StarwarTexts.ROOM_FULL, "started", StarwarTexts.ROOM_STARTED,
                "gone", StarwarTexts.ROOM_GONE, "name", StarwarTexts.ROOM_NAME,
                "kickConfirm", StarwarTexts.ROOM_KICK_CONFIRM, "kicked", StarwarTexts.ROOM_KICKED,
                "exitConfirm", StarwarTexts.ROOM_EXIT_CONFIRM,
                "exitPaidConfirm", StarwarTexts.ROOM_EXIT_PAID_CONFIRM,
                "passwordLabel", StarwarTexts.ROOM_PASSWORD_LABEL,
                "passwordPrompt", StarwarTexts.ROOM_PASSWORD_PROMPT,
                "passwordWrong", StarwarTexts.ROOM_PASSWORD_WRONG,
                "passwordUse", StarwarTexts.ROOM_PASSWORD_USE,
                "passwordHint", StarwarTexts.ROOM_PASSWORD_HINT,
                "createCostCoin", StarwarTexts.ROOM_CREATE_COST_COIN,
                "createCostCrystal", StarwarTexts.ROOM_CREATE_COST_CRYSTAL,
                "onlyOwnerStart", StarwarTexts.ONLY_OWNER_START,
                "onlyOwnerKick", StarwarTexts.ONLY_OWNER_KICK,
                "noOpponent", StarwarTexts.NO_OPPONENT,
                "countdownStarted", StarwarTexts.COUNTDOWN_STARTED,
                "optionMap", StarwarTexts.OPTION_MAP,
                "optionItem", StarwarTexts.OPTION_ITEM,
                "optionCost", StarwarTexts.OPTION_COST));
        out.put("chat", Map.of(
                "window", StarwarTexts.CHAT_WINDOW, "room", StarwarTexts.CHAT_ROOM,
                "inputPrompt", StarwarTexts.CHAT_INPUT_PROMPT,
                "sendFailed", StarwarTexts.CHAT_SEND_FAILED,
                "interval", StarwarTexts.CHAT_INTERVAL,
                "noticePrefix", StarwarTexts.CHAT_NOTICE_PREFIX,
                "rules", StarwarTexts.CHAT_RULES,
                "quickPhrases", StarwarTexts.CHAT_QUICK_PHRASES,
                "phraseSettings", StarwarTexts.CHAT_PHRASE_SETTINGS,
                "playerList", StarwarTexts.CHAT_PLAYER_LIST,
                "myStatus", StarwarTexts.CHAT_MY_STATUS,
                "ingameHint", StarwarTexts.CHAT_INGAME_HINT));
        out.put("status", Map.of(
                "level", StarwarTexts.LABEL_LEVEL, "winRate", StarwarTexts.LABEL_WINRATE,
                "plane", StarwarTexts.LABEL_PLANE, "vs", StarwarTexts.VS,
                "opInfo", StarwarTexts.OP_INFO));
        out.put("result", Map.of(
                "gameOver", StarwarTexts.GAME_OVER, "title", StarwarTexts.RESULT_TITLE,
                "continue", StarwarTexts.CONTINUE, "continueGame", StarwarTexts.CONTINUE_GAME,
                "again", StarwarTexts.AGAIN, "rewardNotice", StarwarTexts.RANK_REWARD_NOTICE));
        out.put("social", Map.of(
                "addFriend", StarwarTexts.FRIEND_ADD, "addOk", StarwarTexts.FRIEND_ADD_OK,
                "addFail", StarwarTexts.FRIEND_ADD_FAIL, "opInfoFail", StarwarTexts.OP_INFO_FAIL,
                "sendMsg", StarwarTexts.SEND_MSG, "pleaseWait", StarwarTexts.PLEASE_WAIT_SEND,
                "from", StarwarTexts.FROM_STARWAR));
        out.put("phrases", StarwarTexts.DEFAULT_PHRASES);
        out.put("keys", StarwarTexts.KEYS);
        out.put("surviveTargetMs", StarwarTexts.SURVIVE_TARGET_MS);
        out.put("helpEntry", StarwarTexts.HELP_ENTRY);
        out.put("exitInGameConfirm", StarwarTexts.EXIT_IN_GAME_CONFIRM);
        // 原文里这几个短语是分段存储的，一并下发便于客户端按屏宽自由拼接
        out.put("segments", Map.of(
                "gameOver", StarwarTexts.GAMEOVER_SEGMENT,
                "waitingRank", StarwarTexts.WAITING_RANK_SEGMENT,
                "waitingResult", StarwarTexts.WAITING_RESULT_SEGMENT,
                "exitInGame", StarwarTexts.EXIT_IN_GAME_SEGMENT,
                "exitConfirm", StarwarTexts.EXIT_CONFIRM_SEGMENT));
        return out;
    }

    public Map<String, Object> help() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("helpTitle", StarwarTexts.HELP_TITLE);
        out.put("helpLines", StarwarTexts.HELP_LINES);
        out.put("introTitle", StarwarTexts.INTRO_TITLE);
        out.put("intro", StarwarTexts.INTRO);
        out.put("howToTitle", StarwarTexts.HOW_TO_TITLE);
        out.put("howTo", StarwarTexts.HOW_TO);
        return out;
    }

    // ================================================================== 房间列表

    /** 某专区的房间列表（分页，每页 10 个） */
    public Map<String, Object> rooms(String zone, int page) {
        if (!StarwarTexts.ZONES.containsKey(zone)) {
            throw new BusinessException(400, "专区不存在");
        }
        int size = 10;
        int offset = Math.max(0, page) * size;
        Integer total = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room WHERE zone = ? AND status != 'closed'",
                Integer.class, zone);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT r.*, (SELECT COUNT(1) FROM starwar_room_member m WHERE m.room_id = r.id) AS members
                FROM starwar_room r
                WHERE r.zone = ? AND r.status != 'closed'
                ORDER BY r.created_at DESC, r.id DESC
                LIMIT ? OFFSET ?
                """, zone, size, offset);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(roomView(row, null));
        }
        int count = total == null ? 0 : total;
        int pages = Math.max(1, (count + size - 1) / size);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("zone", zone);
        out.put("zoneTitle", StarwarTexts.ZONES.get(zone));
        out.put("page", page);
        out.put("pages", pages);
        out.put("total", count);
        out.put("capacity", StarwarTexts.capacity(zone));
        out.put("rooms", list);
        out.put("pager", StarwarTexts.PAGER);
        if (page <= 0 && count > 0) {
            out.put("notice", StarwarTexts.ALREADY_FIRST_PAGE);
        }
        if (page >= pages - 1 && count > 0) {
            out.put("notice", StarwarTexts.ALREADY_LAST_PAGE);
        }
        return out;
    }

    private Map<String, Object> roomView(Map<String, Object> row, String viewer) {
        Map<String, Object> out = new LinkedHashMap<>();
        String id = String.valueOf(row.get("id"));
        out.put("id", id);
        out.put("zone", row.get("zone"));
        out.put("name", row.get("name"));
        out.put("owner", row.get("owner"));
        out.put("mapId", row.get("map_id"));
        out.put("mapName", planetName(intOf(row.get("map_id"))));
        out.put("itemMode", intOf(row.get("item_mode")));
        out.put("costMode", intOf(row.get("cost_mode")));
        out.put("costAmount", intOf(row.get("cost_amount")));
        out.put("hasPassword", !String.valueOf(row.get("password")).isEmpty());
        out.put("status", row.get("status"));
        out.put("members", intOf(row.get("members")));
        out.put("capacity", StarwarTexts.capacity(String.valueOf(row.get("zone"))));
        out.put("round", intOf(row.get("round_no")));
        out.put("labels", Map.of(
                "name", StarwarTexts.ROOM_NAME,
                "map", String.format(StarwarTexts.OPTION_MAP, "1"),
                "item", String.format(StarwarTexts.OPTION_ITEM, "2"),
                "cost", String.format(StarwarTexts.OPTION_COST, "3")));
        if (viewer != null) {
            out.put("isOwner", viewer.equals(String.valueOf(row.get("owner"))));
        }
        return out;
    }

    // ================================================================== 创建 / 进入 / 退出

    /** 创建房间。K币或水晶按 costMode 扣费；密码可选。 */
    public Map<String, Object> createRoom(String username, String zone, String name, int mapId,
                                          boolean itemsEnabled, int costMode, String password) {
        if (!StarwarTexts.ZONES.containsKey(zone)) {
            throw new BusinessException(400, "专区不存在");
        }
        Map<String, Object> profile = profile(username);
        int level = intOf(profile.get("level"));
        int need = StarwarTexts.unlockLevelOf(mapId);
        if (need > 0 && level < need) {
            throw new BusinessException(400, "等级不足：" + StarwarTexts.unlockOf(mapId));
        }
        Integer zoneRooms = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room WHERE zone = ? AND status != 'closed'",
                Integer.class, zone);
        if (zoneRooms != null && zoneRooms >= MAX_ROOMS_PER_ZONE) {
            throw new BusinessException(400, StarwarTexts.REFRESH_ROOMS_FAILED);
        }
        Integer mine = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room WHERE owner = ? AND status != 'closed'",
                Integer.class, username);
        if (mine != null && mine > 0) {
            throw new BusinessException(400, "你已经在房间里了，请先退出");
        }

        String roomName = (name == null ? "" : name.trim());
        if (roomName.isEmpty()) {
            roomName = profile.get("nickname") + "的房间";
        }
        if (roomName.length() > MAX_ROOM_NAME) {
            throw new BusinessException(400, "房间名最多 " + MAX_ROOM_NAME + " 个字");
        }

        int coin;
        int crystal;
        switch (costMode) {
            case 1 -> { coin = DEFAULT_COIN_COST.getOrDefault(zone, 10); crystal = 0; }
            case 2 -> { coin = 0; crystal = DEFAULT_CRYSTAL_COST.getOrDefault(zone, 1); }
            default -> { coin = 0; crystal = 0; }
        }
        // 活动专区在活动时段（每日 20:00-22:00）免创建费
        if ("activity".equals(zone) && StarwarTexts.activityFreeNow(java.time.LocalDateTime.now())) {
            coin = 0;
            crystal = 0;
        }
        if (coin > intOf(profile.get("gold"))) {
            throw new BusinessException(400, "K币不足");
        }
        if (crystal > intOf(profile.get("crystal"))) {
            throw new BusinessException(400, "水晶不足");
        }

        String id = newRoomId();
        String now = LocalDateTime.now().format(TIME_FORMAT);
        String pwd = password == null ? "" : password.trim();
        jdbc.update("""
                INSERT INTO starwar_room
                (id, zone, name, owner, map_id, item_mode, cost_mode, cost_amount, password,
                 status, countdown_end, round_no, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'waiting', 0, 1, ?, ?)
                """, id, zone, roomName, username, mapId, itemsEnabled ? 1 : 0,
                costMode, costMode == 1 ? coin : crystal, pwd, now, now);
        if (coin > 0 || crystal > 0) {
            jdbc.update("UPDATE starwar_player SET gold = gold - ?, crystal = crystal - ?, updated_at = ? "
                    + "WHERE username = ?", coin, crystal, now, username);
        }
        jdbc.update("""
                INSERT OR REPLACE INTO starwar_room_member
                (room_id, username, seat, team, ready, alive, hp, score, survive_ms, plane, joined_at)
                VALUES (?, ?, 0, 0, 1, 1, 100, 0, 0, ?, ?)
                """, id, username, String.valueOf(profile.get("plane")), now);
        systemChat(id, "房间已创建，" + StarwarTexts.CHAT_NOTICE_PREFIX + StarwarTexts.CHAT_RULES);
        return roomDetail(id, username);
    }

    private String newRoomId() {
        for (int attempt = 0; attempt < 50; attempt++) {
            int value = 10000000 + random.nextInt(90000000);
            String id = String.valueOf(value);
            Integer exists = jdbc.queryForObject(
                    "SELECT COUNT(1) FROM starwar_room WHERE id = ?", Integer.class, id);
            if (exists == null || exists == 0) {
                return id;
            }
        }
        return UUID.randomUUID().toString().substring(0, 8);
    }

    /** 进入房间：校验密码、容量、状态；已开始在局中的房间不可进 */
    public Map<String, Object> enterRoom(String username, String roomId, String password) {
        Map<String, Object> room = roomRow(roomId);
        String status = String.valueOf(room.get("status"));
        if ("closed".equals(status)) {
            throw new BusinessException(404, StarwarTexts.ROOM_GONE);
        }
        if (!"waiting".equals(status)) {
            throw new BusinessException(400, StarwarTexts.ROOM_STARTED);
        }
        String stored = String.valueOf(room.get("password"));
        if (!stored.isEmpty() && !stored.equals(password == null ? "" : password.trim())) {
            throw new BusinessException(403, StarwarTexts.ROOM_PASSWORD_WRONG);
        }
        int capacity = StarwarTexts.capacity(String.valueOf(room.get("zone")));
        Integer members = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room_member WHERE room_id = ?", Integer.class, roomId);
        Integer alreadyCount = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room_member WHERE room_id = ? AND username = ?",
                Integer.class, roomId, username);
        boolean already = alreadyCount != null && alreadyCount > 0;
        if (!already && members != null && members >= capacity) {
            throw new BusinessException(400, StarwarTexts.ROOM_FULL);
        }
        if (already) {
            return roomDetail(roomId, username);
        }
        Map<String, Object> profile = profile(username);
        int seat = members == null ? 0 : members;
        int team = "team".equals(room.get("zone")) ? seat % 2 : 0;
        jdbc.update("""
                INSERT INTO starwar_room_member
                (room_id, username, seat, team, ready, alive, hp, score, survive_ms, plane, joined_at)
                VALUES (?, ?, ?, ?, 0, 1, 100, 0, 0, ?, ?)
                """, roomId, username, seat, team, String.valueOf(profile.get("plane")),
                LocalDateTime.now().format(TIME_FORMAT));
        jdbc.update("UPDATE starwar_room SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().format(TIME_FORMAT), roomId);
        systemChat(roomId, profile.get("nickname") + " 加入了房间");
        return roomDetail(roomId, username);
    }

    /** 退出房间：房主退出则解散；付费房间按原文提示「创建房间费用已支付」 */
    public Map<String, Object> leaveRoom(String username, String roomId) {
        Map<String, Object> room = roomRow(roomId);
        boolean owner = username.equals(String.valueOf(room.get("owner")));
        jdbc.update("DELETE FROM starwar_room_member WHERE room_id = ? AND username = ?", roomId, username);
        String now = LocalDateTime.now().format(TIME_FORMAT);
        if (owner) {
            jdbc.update("UPDATE starwar_room SET status = 'closed', updated_at = ? WHERE id = ?", now, roomId);
            engine.stop(roomId);
            jdbc.update("DELETE FROM starwar_room_member WHERE room_id = ?", roomId);
        } else {
            jdbc.update("UPDATE starwar_room SET updated_at = ? WHERE id = ?", now, roomId);
            Map<String, Object> profile = profile(username);
            systemChat(roomId, profile.get("nickname") + " 离开了房间");
        }
        return Map.of("ok", true, "dissolved", owner);
    }

    /**
     * 房主修改房间设置：地图 / 道具开关 / 费用模式 / 密码。
     *
     * <p>对应原版房间界面里「地%s图」「选%s项」「费%s用」「密%s码」四个可切换项，
     * 只有房主能改，且只能在 waiting 状态改（已开局不可改）。</p>
     */
    public Map<String, Object> updateSettings(String username, String roomId, Integer mapId,
                                              Boolean items, Integer costMode, String password) {
        Map<String, Object> room = roomRow(roomId);
        if (!username.equals(String.valueOf(room.get("owner")))) {
            throw new BusinessException(403, "只有房主可以修改房间设置");
        }
        if (!"waiting".equals(room.get("status"))) {
            throw new BusinessException(400, StarwarTexts.ROOM_STARTED);
        }
        Map<String, Object> profile = profile(username);
        String now = LocalDateTime.now().format(TIME_FORMAT);
        if (mapId != null) {
            int need = StarwarTexts.unlockLevelOf(mapId);
            if (need > 0 && intOf(profile.get("level")) < need) {
                throw new BusinessException(400, "等级不足：" + StarwarTexts.unlockOf(mapId));
            }
            jdbc.update("UPDATE starwar_room SET map_id = ?, updated_at = ? WHERE id = ?", mapId, now, roomId);
        }
        if (items != null) {
            jdbc.update("UPDATE starwar_room SET item_mode = ?, updated_at = ? WHERE id = ?",
                    items ? 1 : 0, now, roomId);
        }
        if (costMode != null) {
            int coin = costMode == 1 ? DEFAULT_COIN_COST.getOrDefault(String.valueOf(room.get("zone")), 10) : 0;
            int crystal = costMode == 2 ? DEFAULT_CRYSTAL_COST.getOrDefault(String.valueOf(room.get("zone")), 1) : 0;
            jdbc.update("UPDATE starwar_room SET cost_mode = ?, cost_amount = ?, updated_at = ? WHERE id = ?",
                    costMode, costMode == 1 ? coin : crystal, now, roomId);
        }
        if (password != null) {
            jdbc.update("UPDATE starwar_room SET password = ?, updated_at = ? WHERE id = ?",
                    password.trim(), now, roomId);
        }
        return roomDetail(roomId, username);
    }

    /** 踢人：只有房主可踢 */
    public Map<String, Object> kick(String username, String roomId, String target) {
        Map<String, Object> room = roomRow(roomId);
        if (!username.equals(String.valueOf(room.get("owner")))) {
            throw new BusinessException(403, StarwarTexts.ONLY_OWNER_KICK);
        }
        if (target == null || target.isBlank() || target.equals(username)) {
            throw new BusinessException(400, "踢人对象无效");
        }
        jdbc.update("DELETE FROM starwar_room_member WHERE room_id = ? AND username = ?", roomId, target);
        systemChat(roomId, StarwarTexts.ROOM_KICKED);
        return roomDetail(roomId, username);
    }

    /** 准备 / 取消准备 */
    public Map<String, Object> ready(String username, String roomId, boolean ready) {
        roomRow(roomId);
        jdbc.update("UPDATE starwar_room_member SET ready = ? WHERE room_id = ? AND username = ?",
                ready ? 1 : 0, roomId, username);
        return roomDetail(roomId, username);
    }

    /** 快速开始：只有房主可发起；倒计时 3 秒后自动开局 */
    public Map<String, Object> quickStart(String username, String roomId) {
        Map<String, Object> room = roomRow(roomId);
        if (!username.equals(String.valueOf(room.get("owner")))) {
            throw new BusinessException(403, StarwarTexts.ONLY_OWNER_START);
        }
        if (!"waiting".equals(room.get("status"))) {
            throw new BusinessException(400, StarwarTexts.COUNTDOWN_STARTED);
        }
        Integer members = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room_member WHERE room_id = ?", Integer.class, roomId);
        if (members == null || members < 2) {
            throw new BusinessException(400, StarwarTexts.NO_OPPONENT);
        }
        long end = System.currentTimeMillis() + COUNTDOWN_MS;
        jdbc.update("UPDATE starwar_room SET status = 'countdown', countdown_end = ?, updated_at = ? WHERE id = ?",
                end, LocalDateTime.now().format(TIME_FORMAT), roomId);
        systemChat(roomId, StarwarTexts.COUNTDOWN_STARTED);
        return roomDetail(roomId, username);
    }

    // ================================================================== 房间详情

    public Map<String, Object> roomDetail(String roomId, String viewer) {
        Map<String, Object> row = roomRow(roomId);
        Map<String, Object> out = roomView(row, viewer);
        List<Map<String, Object>> members = jdbc.queryForList("""
                SELECT m.*, p.nickname, p.level, p.wins, p.losses, p.plane AS player_plane
                FROM starwar_room_member m
                LEFT JOIN starwar_player p ON p.username = m.username
                WHERE m.room_id = ?
                ORDER BY m.seat
                """, roomId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> member : members) {
            int wins = intOf(member.get("wins"));
            int losses = intOf(member.get("losses"));
            int total = wins + losses;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("user", member.get("username"));
            item.put("nick", member.get("nickname") == null ? member.get("username") : member.get("nickname"));
            item.put("seat", member.get("seat"));
            item.put("team", member.get("team"));
            item.put("ready", intOf(member.get("ready")) == 1);
            item.put("alive", intOf(member.get("alive")) == 1);
            item.put("hp", member.get("hp"));
            item.put("score", member.get("score"));
            item.put("plane", member.get("plane"));
            item.put("level", member.get("level"));
            item.put("rank", StarwarTexts.rankOf(intOf(member.get("level"))));
            item.put("winRate", total == 0 ? 0 : Math.round(wins * 1000.0 / total) / 10.0);
            item.put("isOwner", String.valueOf(member.get("username")).equals(String.valueOf(row.get("owner"))));
            list.add(item);
        }
        out.put("memberList", list);
        out.put("members", list.size());
        out.put("owner", row.get("owner"));
        out.put("countdownEnd", row.get("countdown_end"));
        out.put("passwordHint", StarwarTexts.ROOM_PASSWORD_HINT);
        out.put("capacity", StarwarTexts.capacity(String.valueOf(row.get("zone"))));
        StarwarWorld world = engine.world(roomId);
        if (world != null) {
            out.put("world", world.snapshot());
        }
        return out;
    }

    private Map<String, Object> roomRow(String roomId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM starwar_room WHERE id = ?", roomId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, StarwarTexts.ROOM_GONE);
        }
        return rows.get(0);
    }

    // ================================================================== 等待聊天室

    /** 房间聊天历史（默认最近 50 条） */
    public Map<String, Object> chatHistory(String username, String roomId, long afterId) {
        roomRow(roomId);
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT * FROM starwar_chat WHERE room_id = ? AND id > ?
                ORDER BY id ASC LIMIT 200
                """, roomId, afterId);
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            list.add(chatView(row, username));
        }
        Map<String, Object> profile = profile(username);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roomId", roomId);
        out.put("messages", list);
        out.put("phrases", phrases(username));
        out.put("notice", StarwarTexts.CHAT_NOTICE_PREFIX + StarwarTexts.CHAT_RULES);
        out.put("intervalMs", CHAT_INTERVAL_MS);
        out.put("silencedFor", Math.max(0, intOf(profile.get("silent_until")) * 1000L - System.currentTimeMillis()));
        out.put("nextAllowedAt", nextChatAt(username));
        return out;
    }

    private Map<String, Object> chatView(Map<String, Object> row, String viewer) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", row.get("id"));
        out.put("user", row.get("username"));
        out.put("nick", row.get("nickname"));
        out.put("content", row.get("content"));
        out.put("kind", row.get("kind"));
        out.put("at", row.get("created_at"));
        out.put("mine", viewer != null && viewer.equals(String.valueOf(row.get("username"))));
        return out;
    }

    /** 发言：10 秒间隔 + 禁言校验（原文「聊天间隔10秒,请勿刷屏!」/ 48 小时处罚） */
    public Map<String, Object> say(String username, String roomId, String content, String kind) {
        roomRow(roomId);
        Map<String, Object> profile = profile(username);
        long now = System.currentTimeMillis();
        if (intOf(profile.get("silent_until")) * 1000L > now) {
            throw new BusinessException(403, "您已被禁言，" + StarwarTexts.CHAT_RULES);
        }
        long nextAllowed = nextChatAt(username);
        if (now < nextAllowed) {
            throw new BusinessException(429, StarwarTexts.CHAT_INTERVAL);
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, StarwarTexts.CHAT_INPUT_PROMPT);
        }
        if (text.length() > MAX_CHAT_LENGTH) {
            throw new BusinessException(400, "消息最多 " + MAX_CHAT_LENGTH + " 个字");
        }
        String nickname = String.valueOf(profile.get("nickname"));
        jdbc.update("""
                INSERT INTO starwar_chat (room_id, username, nickname, content, kind, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, roomId, username, nickname, text,
                kind == null || kind.isBlank() ? "text" : kind, now);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM starwar_chat WHERE room_id = ?", Long.class, roomId);
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM starwar_chat WHERE id = ?", id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("message", rows.isEmpty() ? null : chatView(rows.get(0), username));
        out.put("nextAllowedAt", now + CHAT_INTERVAL_MS);
        return out;
    }

    /** 系统消息（公告 / 进出房间提示），不受间隔限制 */
    public void systemChat(String roomId, String content) {
        jdbc.update("""
                INSERT INTO starwar_chat (room_id, username, nickname, content, kind, created_at)
                VALUES (?, 'system', '系统', ?, 'system', ?)
                """, roomId, content, System.currentTimeMillis());
    }

    private long nextChatAt(String username) {
        Long last = jdbc.queryForObject(
                "SELECT MAX(created_at) FROM starwar_chat WHERE username = ? AND kind != 'system'",
                Long.class, username);
        return last == null ? 0 : last + CHAT_INTERVAL_MS;
    }

    // ================================================================== 短语与好友

    public List<String> phrases(String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT content FROM starwar_phrase WHERE username = ? ORDER BY slot", username);
        List<String> out = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            out.add(String.valueOf(row.get("content")));
        }
        if (out.isEmpty()) {
            return StarwarTexts.DEFAULT_PHRASES;
        }
        return out;
    }

    public List<String> savePhrases(String username, List<String> contents) {
        if (contents == null || contents.size() != StarwarTexts.DEFAULT_PHRASES.size()) {
            throw new BusinessException(400, "短语需要 " + StarwarTexts.DEFAULT_PHRASES.size() + " 条");
        }
        profile(username);
        for (int slot = 1; slot <= contents.size(); slot++) {
            String text = contents.get(slot - 1) == null ? "" : contents.get(slot - 1).trim();
            if (text.isEmpty() || text.length() > 30) {
                throw new BusinessException(400, "短语需 1-30 个字符");
            }
            jdbc.update("INSERT OR REPLACE INTO starwar_phrase (username, slot, content) VALUES (?, ?, ?)",
                    username, slot, text);
        }
        return phrases(username);
    }

    public Map<String, Object> addFriend(String username, String target) {
        if (target == null || target.isBlank() || target.equals(username)) {
            throw new BusinessException(400, StarwarTexts.FRIEND_ADD_FAIL);
        }
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_player WHERE username = ?", Integer.class, target);
        if (exists == null || exists == 0) {
            throw new BusinessException(404, StarwarTexts.FRIEND_ADD_FAIL);
        }
        jdbc.update("INSERT OR IGNORE INTO starwar_friend (username, friend, created_at) VALUES (?, ?, ?)",
                username, target, LocalDateTime.now().format(TIME_FORMAT));
        return Map.of("ok", true, "text", StarwarTexts.FRIEND_ADD_OK);
    }

    public List<Map<String, Object>> friends(String username) {
        return jdbc.queryForList("""
                SELECT f.friend AS user, p.nickname AS nick, p.level, p.plane
                FROM starwar_friend f LEFT JOIN starwar_player p ON p.username = f.friend
                WHERE f.username = ? ORDER BY f.created_at DESC
                """, username);
    }

    /** 对手资料（「对手资料」/「查看对方资料失败」） */
    public Map<String, Object> opponent(String username, String target) {
        profile(username);
        if (target == null || target.isBlank()) {
            throw new BusinessException(400, StarwarTexts.OP_INFO_FAIL);
        }
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM starwar_player WHERE username = ?", target);
        if (rows.isEmpty()) {
            throw new BusinessException(404, StarwarTexts.OP_INFO_FAIL);
        }
        Map<String, Object> row = rows.get(0);
        int wins = intOf(row.get("wins"));
        int losses = intOf(row.get("losses"));
        int total = wins + losses;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("user", row.get("username"));
        out.put("nick", row.get("nickname"));
        out.put("level", row.get("level"));
        out.put("rank", StarwarTexts.rankOf(intOf(row.get("level"))));
        out.put("wins", wins);
        out.put("losses", losses);
        out.put("winRate", total == 0 ? 0 : Math.round(wins * 1000.0 / total) / 10.0);
        out.put("plane", row.get("plane"));
        Integer friendCount = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_friend WHERE username = ? AND friend = ?",
                Integer.class, username, target);
        out.put("isFriend", friendCount != null && friendCount > 0);
        return out;
    }

    // ================================================================== 商店 / 机库 / 市场

    public List<Map<String, Object>> hangar(String username) {
        return jdbc.queryForList(
                "SELECT item_type AS type, item_id AS id, source FROM starwar_hangar WHERE username = ? "
                        + "ORDER BY item_type, item_id", username);
    }

    /** 飞机商店：固定货架（复刻原版飞机 / 引擎两栏），用 K币或水晶购买 */
    public Map<String, Object> shop(String username) {
        Map<String, Object> profile = profile(username);
        List<Map<String, Object>> items = new ArrayList<>(SHOP_ITEMS);
        List<String> owned = new ArrayList<>();
        for (Map<String, Object> item : hangar(username)) {
            owned.add(item.get("type") + ":" + item.get("id"));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> row = new LinkedHashMap<>(item);
            row.put("owned", owned.contains(item.get("type") + ":" + item.get("id")));
            rows.add(row);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", StarwarTexts.PLANE_SHOP);
        out.put("items", rows);
        out.put("gold", profile.get("gold"));
        out.put("crystal", profile.get("crystal"));
        return out;
    }

    private static final List<Map<String, Object>> SHOP_ITEMS = List.of(
            Map.of("type", "plane", "id", "plane", "name", "初教机", "gold", 0, "crystal", 0, "desc", "新手自带的飞机"),
            Map.of("type", "plane", "id", "oppos", "name", "猛禽", "gold", 500, "crystal", 0, "desc", "速度更快"),
            Map.of("type", "plane", "id", "selfs", "name", "疾风", "gold", 1200, "crystal", 0, "desc", "转向灵活"),
            Map.of("type", "plane", "id", "team", "name", "编队", "gold", 0, "crystal", 5, "desc", "组队战专用"),
            Map.of("type", "plane", "id", "sun", "name", "恒星", "gold", 0, "crystal", 12, "desc", "顶级机型"),
            Map.of("type", "engine", "id", "engine", "name", "标准引擎", "gold", 0, "crystal", 0, "desc", "默认引擎"),
            Map.of("type", "engine", "id", "engine2", "name", "涡轮引擎", "gold", 800, "crystal", 0, "desc", "推力更强"),
            Map.of("type", "engine", "id", "engine3", "name", "离子引擎", "gold", 0, "crystal", 8, "desc", "极限加速"));

    public Map<String, Object> buy(String username, String type, String itemId) {
        Map<String, Object> profile = profile(username);
        Map<String, Object> item = SHOP_ITEMS.stream()
                .filter(row -> Objects.equals(row.get("type"), type) && Objects.equals(row.get("id"), itemId))
                .findFirst()
                .orElseThrow(() -> new BusinessException(404, "商品不存在"));
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_hangar WHERE username = ? AND item_type = ? AND item_id = ?",
                Integer.class, username, type, itemId);
        if (owned != null && owned > 0) {
            throw new BusinessException(400, "已经拥有该装备");
        }
        int gold = intOf(item.get("gold"));
        int crystal = intOf(item.get("crystal"));
        if (gold > intOf(profile.get("gold")) || crystal > intOf(profile.get("crystal"))) {
            throw new BusinessException(400, "K币或水晶不足");
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbc.update("UPDATE starwar_player SET gold = gold - ?, crystal = crystal - ?, updated_at = ? WHERE username = ?",
                gold, crystal, now, username);
        jdbc.update("INSERT INTO starwar_hangar (username, item_type, item_id, source, created_at) VALUES (?, ?, ?, 'shop', ?)",
                username, type, itemId, now);
        return shop(username);
    }

    /** 装备（换出战飞机 / 引擎） */
    public Map<String, Object> equip(String username, String type, String itemId) {
        profile(username);
        Integer owned = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_hangar WHERE username = ? AND item_type = ? AND item_id = ?",
                Integer.class, username, type, itemId);
        if (owned == null || owned == 0) {
            throw new BusinessException(400, "机库里没有该装备");
        }
        String column = "plane".equals(type) ? "plane" : "equip_engine";
        jdbc.update("UPDATE starwar_player SET " + column + " = ?, updated_at = ? WHERE username = ?",
                itemId, LocalDateTime.now().format(TIME_FORMAT), username);
        return profile(username);
    }

    /** 交易市场：原版在冒泡平台里是跨用户挂单，本地版先做只读行情 + 即将推出提示 */
    public Map<String, Object> market(String username) {
        profile(username);
        return Map.of(
                "title", StarwarTexts.TRADE_MARKET,
                "notice", StarwarTexts.COMING_SOON,
                "items", List.of());
    }

    // ================================================================== 排行榜

    public Map<String, Object> ranking(String scope, int page) {
        int size = 20;
        int offset = Math.max(0, page) * size;
        String order = "wins".equals(scope) ? "wins DESC, total_score DESC" : "total_score DESC, best_score DESC";
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM starwar_rank ORDER BY " + order + " LIMIT ? OFFSET ?", size, offset);
        List<Map<String, Object>> list = new ArrayList<>();
        int rank = offset + 1;
        for (Map<String, Object> row : rows) {
            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("ranking", rank++);
            list.add(item);
        }
        Integer total = jdbc.queryForObject("SELECT COUNT(1) FROM starwar_rank", Integer.class);
        int count = total == null ? 0 : total;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("title", StarwarTexts.RANK_TITLE);
        out.put("scope", scope == null ? "score" : scope);
        out.put("page", page);
        out.put("pages", Math.max(1, (count + size - 1) / size));
        out.put("total", count);
        out.put("items", list);
        out.put("pager", StarwarTexts.PAGER);
        return out;
    }

    // ================================================================== 对战

    /** 开局：倒计时结束后由客户端或定时任务触发；幂等（同一房间只开一次） */
    public StarwarWorld startMatch(String roomId) {
        Map<String, Object> room = roomRow(roomId);
        StarwarWorld running = engine.world(roomId);
        if (running != null) {
            return running;
        }
        if (!"countdown".equals(room.get("status")) && !"playing".equals(room.get("status"))) {
            throw new BusinessException(400, StarwarTexts.COUNTDOWN_STARTED);
        }
        List<Map<String, Object>> members = jdbc.queryForList(
                "SELECT * FROM starwar_room_member WHERE room_id = ? ORDER BY seat", roomId);
        if (members.size() < 2) {
            throw new BusinessException(400, StarwarTexts.NO_OPPONENT);
        }
        String matchId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String now = LocalDateTime.now().format(TIME_FORMAT);
        jdbc.update("""
                INSERT INTO starwar_match (id, room_id, zone, map_id, status, started_at, settled_at, result_json)
                VALUES (?, ?, ?, ?, 'playing', ?, 0, NULL)
                """, matchId, roomId, room.get("zone"), room.get("map_id"), System.currentTimeMillis());
        jdbc.update("UPDATE starwar_room SET status = 'playing', updated_at = ? WHERE id = ?", now, roomId);
        for (Map<String, Object> member : members) {
            jdbc.update("""
                    UPDATE starwar_room_member SET alive = 1, hp = 100, score = 0, survive_ms = 0
                    WHERE room_id = ? AND username = ?
                    """, roomId, member.get("username"));
        }

        StarwarWorld world = new StarwarWorld(matchId, roomId, String.valueOf(room.get("zone")),
                intOf(room.get("map_id")), intOf(room.get("item_mode")) == 1);
        for (Map<String, Object> member : members) {
            Map<String, Object> profile = profile(String.valueOf(member.get("username")));
            world.join(String.valueOf(member.get("username")),
                    String.valueOf(profile.get("nickname")),
                    String.valueOf(member.get("plane")));
        }
        engine.start(world);
        systemChat(roomId, StarwarTexts.GAME_OVER + " " + StarwarTexts.WAITING_RESULT);
        return world;
    }

    /**
     * 结算：写成绩、发奖、更新段位与排行榜。
     *
     * <p>原文规则：奖励水晶要等「本局游戏所有玩家都结束游戏并公布最终排名信息后才会增加」，
     * 因此这里在全部玩家都有结果时才统一发奖。</p>
     */
    public void settle(StarwarWorld world) {
        List<StarwarWorld.Standing> standings = world.standings();
        String now = LocalDateTime.now().format(TIME_FORMAT);
        long settledAt = System.currentTimeMillis();
        int winnerScore = standings.isEmpty() ? 0 : standings.get(0).score();

        for (StarwarWorld.Standing standing : standings) {
            // 活动专区为官方活动场，奖励翻倍
            double multiplier = StarwarTexts.rewardMultiplier(world.zone);
            int rewardCoin = (int) Math.round(rankRewardCoin(standing.ranking(), standings.size()) * multiplier);
            int rewardCrystal = (int) Math.round(rankRewardCrystal(standing.ranking(), standings.size()) * multiplier);
            jdbc.update("""
                    INSERT OR REPLACE INTO starwar_match_result
                    (match_id, username, ranking, score, survive_ms, hp_left, reward_coin, reward_crystal)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """, world.matchId, standing.username(), standing.ranking(), standing.score(),
                    standing.survivedMs(), standing.hp(), rewardCoin, rewardCrystal);

            boolean won = standing.ranking() == 1;
            int expGain = 20 + standing.score() / 50;
            // 先累加经验，再按经验推导等级：SQLite 同一条 UPDATE 里读到的 exp 是旧值，必须分两步
            jdbc.update("UPDATE starwar_player SET exp = exp + ?, updated_at = ? WHERE username = ?",
                    expGain, now, standing.username());
            jdbc.update("""
                    UPDATE starwar_player SET
                        wins = wins + ?, losses = losses + ?, gold = gold + ?, crystal = crystal + ?,
                        level = 1 + exp / 100,
                        updated_at = ?
                    WHERE username = ?
                    """, won ? 1 : 0, won ? 0 : 1, rewardCoin, rewardCrystal, now, standing.username());

            Map<String, Object> profile = profile(standing.username());
            jdbc.update("""
                    INSERT INTO starwar_rank (username, nickname, total_score, best_score, matches, wins, updated_at)
                    VALUES (?, ?, ?, ?, 1, ?, ?)
                    ON CONFLICT(username) DO UPDATE SET
                        nickname = excluded.nickname,
                        total_score = total_score + excluded.total_score,
                        best_score = MAX(best_score, excluded.best_score),
                        matches = matches + 1,
                        wins = wins + excluded.wins,
                        updated_at = excluded.updated_at
                    """, standing.username(), profile.get("nickname"), standing.score(), standing.score(),
                    won ? 1 : 0, now);
        }

        // 组队战：按队伍总分再判一次胜负（个人名次保留）
        String teamWinner = null;
        if ("team".equals(world.zone)) {
            teamWinner = teamWinner(world);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("matchId", world.matchId);
        result.put("roomId", world.roomId);
        result.put("zone", world.zone);
        result.put("mapId", world.mapId);
        result.put("winnerScore", winnerScore);
        result.put("teamWinner", teamWinner);
        result.put("rankings", standings);
        result.put("title", StarwarTexts.RESULT_TITLE);
        result.put("gameOver", StarwarTexts.GAME_OVER);
        result.put("rewardNotice", StarwarTexts.RANK_REWARD_NOTICE);
        try {
            jdbc.update("UPDATE starwar_match SET status = 'settled', settled_at = ?, result_json = ? WHERE id = ?",
                    settledAt, json.writeValueAsString(result), world.matchId);
            jdbc.update("UPDATE starwar_room SET status = 'settled', updated_at = ? WHERE id = ?",
                    now, world.roomId);
        } catch (Exception error) {
            log.warn("星球大战对局 {} 结算落库失败：{}", world.matchId, error.getMessage());
        }
        systemChat(world.roomId, StarwarTexts.RESULT_TITLE + "：" + describeStandings(standings));
    }

    private String describeStandings(List<StarwarWorld.Standing> standings) {
        StringBuilder sb = new StringBuilder();
        for (StarwarWorld.Standing standing : standings) {
            if (sb.length() > 0) {
                sb.append("，");
            }
            sb.append(standing.ranking()).append(". ").append(standing.nickname())
              .append(" ").append(standing.score()).append("分");
        }
        return sb.toString();
    }

    private String teamWinner(StarwarWorld world) {
        Map<Integer, Integer> teamScore = new LinkedHashMap<>();
        for (StarwarWorld.PlayerState player : world.players.values()) {
            SeatInfo seat = seatOf(world.roomId, player.username);
            teamScore.merge(seat.team(), player.score, Integer::sum);
        }
        return teamScore.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(entry -> entry.getKey() == 0 ? "蓝队" : "红队")
                .orElse(null);
    }

    private record SeatInfo(int seat, int team) {
    }

    private SeatInfo seatOf(String roomId, String username) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT seat, team FROM starwar_room_member WHERE room_id = ? AND username = ?",
                roomId, username);
        if (rows.isEmpty()) {
            return new SeatInfo(0, 0);
        }
        return new SeatInfo(intOf(rows.get(0).get("seat")), intOf(rows.get(0).get("team")));
    }

    private static int rankRewardCoin(int ranking, int total) {
        if (total <= 1) {
            return 20;
        }
        return switch (ranking) {
            case 1 -> 60;
            case 2 -> 40;
            case 3 -> 30;
            default -> 20;
        };
    }

    private static int rankRewardCrystal(int ranking, int total) {
        if (total <= 1) {
            return 1;
        }
        return ranking == 1 ? 3 : 1;
    }

    /** 对局结果（结算后查询） */
    public Map<String, Object> matchResult(String username, String matchId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM starwar_match WHERE id = ?", matchId);
        if (rows.isEmpty()) {
            throw new BusinessException(404, "对局不存在");
        }
        Map<String, Object> match = rows.get(0);
        List<Map<String, Object>> results = jdbc.queryForList("""
                SELECT r.*, p.nickname FROM starwar_match_result r
                LEFT JOIN starwar_player p ON p.username = r.username
                WHERE r.match_id = ? ORDER BY r.ranking
                """, matchId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("matchId", matchId);
        out.put("status", match.get("status"));
        out.put("settled", "settled".equals(match.get("status")));
        out.put("result", results);
        out.put("title", StarwarTexts.RESULT_TITLE);
        if (!"settled".equals(match.get("status"))) {
            out.put("message", StarwarTexts.WAITING_RANK);
        }
        return out;
    }

    /** 客户端输入下行：方向键 / 使用道具 */
    public Map<String, Object> input(String username, String roomId, int dx, int dy, boolean useItem) {
        StarwarWorld world = engine.world(roomId);
        if (world == null) {
            throw new BusinessException(409, StarwarTexts.GAME_OVER);
        }
        world.input(username, dx, dy);
        if (useItem) {
            world.useItem(username);
        }
        return Map.of("ok", true);
    }

    /** 世界快照（客户端轮询兜底；正常走 WebSocket 推送） */
    public Map<String, Object> snapshot(String roomId) {
        StarwarWorld world = engine.world(roomId);
        if (world == null) {
            throw new BusinessException(404, "该房间没有进行中的对局");
        }
        return world.snapshot();
    }

    // ================================================================== 工具

    public int onlineCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(1) FROM starwar_room_member", Integer.class);
        return count == null ? 0 : count;
    }

    private static String planetName(int planetId) {
        for (StarwarTexts.Planet planet : StarwarTexts.PLANETS) {
            if (planet.id() == planetId) {
                return planet.name();
            }
        }
        return "地球";
    }

    private static int intOf(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException error) {
            return 0;
        }
    }

    /** 清理空闲房间（老库 / 异常退出残留） */
    public int sweepIdleRooms() {
        long cutoff = System.currentTimeMillis() - ROOM_IDLE_MS;
        List<String> idle = jdbc.queryForList(
                "SELECT id FROM starwar_room WHERE status IN ('waiting','countdown') "
                        + "AND CAST(strftime('%s', updated_at) AS INTEGER) * 1000 < ?",
                String.class, cutoff);
        for (String roomId : idle) {
            jdbc.update("UPDATE starwar_room SET status = 'closed' WHERE id = ?", roomId);
            engine.stop(roomId);
        }
        return idle.size();
    }
}
