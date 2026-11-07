package com.huluwa.console.service.starwar;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 星球大战（是男人就撑20秒 网络对战版）的全部原文常量。
 *
 * <p>所有文案逐字取自 2009 年冒泡社区 {@code starwar.mrp} 内
 * {@code starwar.ext} 的 UTF-16BE 字符串表（偏移 {@code 0x14ed4}–{@code 0x158bc}），
 * 用于保证「1:1 还原」时客户端措辞与当年完全一致。</p>
 */
public final class StarwarTexts {

    private StarwarTexts() {
    }

    // ---------------------------------------------------------------- 大厅

    /** 首页 / 上页 / 下页 / 末页 / 刷新 */
    public static final List<String> PAGER = List.of("首页", "上页", "下页", "末页", "刷新");
    public static final String ALREADY_FIRST_PAGE = "已经是第一页!";
    public static final String ALREADY_LAST_PAGE = "已经是最后一页!";

    public static final String RANK_TITLE = "游戏排行榜";
    public static final String ZONE_MULTI = "多人战专区";
    public static final String ZONE_DUO = "双人战专区";
    public static final String ZONE_TEAM = "组队战专区";
    public static final String ZONE_ACTIVITY = "活动专区";
    public static final String PLANE_SHOP = "飞机商店";
    public static final String MY_HANGAR = "我的机库";
    public static final String TRADE_MARKET = "交易市场";
    public static final String RANKING = "排行榜";

    public static final String OK = "确定";
    public static final String BACK = "返回";
    public static final String MENU = "菜单";
    public static final String INFO = "信息";
    public static final String ERROR = "错误";
    public static final String HELP = "帮助";

    // ---------------------------------------------------------------- 服务器通讯

    public static final String SERVER_ERROR = "服务器出错,请重试!";
    public static final String SERVER_TIMEOUT = "服务器超时,请重试!";
    public static final String FETCHING_DATA = "正在获取数据,请稍候…";
    public static final String FETCHING_INFO = "正在获取信息,请稍候...";
    public static final String WAITING_SERVER = "等待服务器响应,请稍候";
    public static final String REFRESH_ROOMS_FAILED = "刷新房间列表失败!";
    public static final String SERVER_NO_RESPONSE = "服务器无响应,请退出游戏稍后再试!";
    public static final String CONNECT_TIMEOUT = "连接超时,请退出重试!";
    public static final String WAITING_RANK = "游戏结束,等待服务器发送排名结果…";
    public static final String WAITING_RESULT = "游戏结束，正在等待服务器发送游戏结果…";
    public static final String COMING_SOON = "即将推出,敬请期待!";

    // ---------------------------------------------------------------- 房间

    public static final String ROOM_FULL = "房间已满,请换个房间再试!";
    public static final String ROOM_STARTED = "该房间已经开始游戏,请换个房间!";
    public static final String ROOM_GONE = "房间已经不存在!";
    public static final String ROOM_NAME = "房间名";
    public static final String ROOM_IN = "进入房间";
    public static final String ROOM_CREATE = "创建房间";
    public static final String ROOM_QUICK_START = "快速开始";
    public static final String ROOM_KICK = "踢掉对手";
    public static final String ROOM_KICK_CONFIRM = "您确认要踢掉对手?";
    public static final String ROOM_KICKED = "您已被房主踢出房间!";
    public static final String ROOM_EXIT_CONFIRM = "您确认要退出房间?";
    public static final String ROOM_EXIT_PAID_CONFIRM = "创建房间费用已支付,您确认要退出房间?";
    public static final String ROOM_PASSWORD_LABEL = "密 码";
    public static final String ROOM_PASSWORD_PROMPT = "请输入房间密码";
    public static final String ROOM_PASSWORD_WRONG = "密码错误!";
    public static final String ROOM_PASSWORD_USE = "使用密码";
    public static final String ROOM_PASSWORD_HINT = "房间密码默认为空,如非特别需要,请不要设定密码。";

    public static final String ROOM_CREATE_COST_COIN = "创建房间需要%dK币,您确定需要创建?";
    public static final String ROOM_CREATE_COST_CRYSTAL = "创建房间需要%d水晶,您确定需要创建?";

    /** 「地%s图」「选%s项」「费%s用」：%s 位置填 K 键上的选项字母 */
    public static final String OPTION_MAP = "地%s图";
    public static final String OPTION_ITEM = "选%s项";
    public static final String OPTION_COST = "费%s用";

    public static final String ONLY_OWNER_START = "只有自建房间者才能快速开始游戏";
    public static final String ONLY_OWNER_KICK = "只有自建房间者才能踢掉对手";
    public static final String NO_OPPONENT = "还没有对手加入";
    public static final String COUNTDOWN_STARTED = "游戏已经进入开始倒计时";

    // ---------------------------------------------------------------- 等待聊天室

    public static final String CHAT_WINDOW = "聊天窗口";
    public static final String CHAT_ROOM = "聊天室";
    public static final String CHAT_INPUT_PROMPT = "请输入聊天内容";
    public static final String CHAT_SEND_FAILED = "发送聊天消息失败";
    public static final String CHAT_INTERVAL = "聊天间隔10秒,请勿刷屏!";
    public static final String CHAT_NOTICE_PREFIX = "聊天室公告：";
    public static final String CHAT_RULES =
            "请爱护聊天环境,不要在10秒内重复发言或者说脏话,否则会受到48小时内禁止进入聊天室的处罚!";
    public static final String CHAT_QUICK_PHRASES = "短语";
    public static final String CHAT_PHRASE_SETTINGS = "短语设置";
    public static final String CHAT_PLAYER_LIST = "玩家列表";
    public static final String CHAT_MY_STATUS = "我的状态";

    /** 游戏内聊天提示（操作说明原文） */
    public static final String CHAT_INGAME_HINT =
            "#键：切换出聊天消息,游戏中收到聊天消息时屏幕左下方出现闪动头像";

    // ---------------------------------------------------------------- 状态字段

    public static final String LABEL_LEVEL = "等级:";
    public static final String LABEL_WINRATE = "胜率:";
    public static final String LABEL_PLANE = "飞机:";
    public static final String VS = "VS";
    public static final String OP_INFO = "对手资料";

    // ---------------------------------------------------------------- 结算

    public static final String GAME_OVER = "游戏结束!";
    public static final String CONTINUE = "继续";
    public static final String CONTINUE_GAME = "继续游戏";
    public static final String AGAIN = "再来一局!";
    public static final String RESULT_TITLE = "游戏排名结果";
    public static final String RANK_REWARD_NOTICE =
            "请注意：每局游戏结束后奖励的水晶，在本局游戏所有玩家都结束游戏并公布最终排名信息后才会增加。";

    // ---------------------------------------------------------------- 社交

    public static final String FRIEND_ADD = "加为好友";
    public static final String FRIEND_ADD_OK = "添加好友成功";
    public static final String FRIEND_ADD_FAIL = "添加好友失败";
    public static final String OP_INFO_FAIL = "查看对方资料失败";
    public static final String SEND_MSG = "发送消息";
    public static final String PLEASE_WAIT_SEND = "请稍候发送";
    public static final String FROM_STARWAR = "<来自星球大战>";

    // ---------------------------------------------------------------- 默认快捷短语（6 条）

    public static final List<String> DEFAULT_PHRASES = List.of(
            "再来一局!", "服我了吧!", "果然厉害!", "运气太差~", "快点开始~", "交个朋友吧");

    // ---------------------------------------------------------------- 操作说明

    public static final String HELP_TITLE = "操作说明：";
    public static final List<String> HELP_LINES = List.of(
            "方向上，2键：飞机/选择框向上移动",
            "方向下，8键：飞机/选择框向下移动",
            "方向左，4键：飞机/选择框向左移动",
            "方向右，6键：飞机/选择框向右移动",
            "方向中，5键：选择/确定",
            "ok或*键：使用道具",
            "左软键：调出系统菜单/确定",
            "右软键：返回/退出",
            "音量调节：超级大玩家主界面->系统设置",
            "#键：切换出聊天消息,游戏中收到聊天消息时屏幕左下方出现闪动头像");

    // ---------------------------------------------------------------- 游戏介绍

    public static final String INTRO_TITLE = "游戏介绍：";
    public static final String INTRO =
            "是男人就撑20秒游戏的网络对战版。沿袭了单机版玩法和操作简单的特点，"
                    + "并增加了多种超酷的飞机和强大的道具，绝对给您全新的体验！";
    public static final String HOW_TO_TITLE = "玩法说明：";
    public static final String HOW_TO =
            "玩家需要通过方向键控制飞机躲避四处飞来的子弹。关卡中会经常刷新各种道具，"
                    + "玩家获得后可以降低游戏的难度。每次被子弹击中飞机后会损失生命值，生命值降为0则游戏结束。";

    // ---------------------------------------------------------------- 分区与地图

    /** 四个专区的内部键与显示名 */
    public static final Map<String, String> ZONES = zones();

    private static Map<String, String> zones() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("multi", ZONE_MULTI);
        map.put("duo", ZONE_DUO);
        map.put("team", ZONE_TEAM);
        map.put("activity", ZONE_ACTIVITY);
        return java.util.Collections.unmodifiableMap(map);
    }

    /** 各专区的房间容量：多人战最多 6 人，双人战 2 人，组队战 4 人（2v2），活动区 6 人 */
    public static int capacity(String zone) {
        return switch (zone == null ? "" : zone) {
            case "duo" -> 2;
            case "team" -> 4;
            case "activity" -> 6;
            default -> 6;
        };
    }

    /** 九大星球（maps.inf 原文顺序与城市代号） */
    public static final List<Planet> PLANETS = List.of(
            new Planet(2, "地球", "dixing"),
            new Planet(3, "金星", "jinxing"),
            new Planet(4, "木星", "muxing"),
            new Planet(5, "水星", "shuixing"),
            new Planet(6, "火星", "huoxing"),
            new Planet(7, "土星", "tuxing"),
            new Planet(8, "天王星", "tianxing"),
            new Planet(9, "海王星", "haixing"),
            new Planet(10, "冥王星", "mingxing"));

    public record Planet(int id, String name, String icon) {
    }

    /** 段位链（maps.inf 原文） */
    public static final List<RankTitle> RANKS = List.of(
            new RankTitle("飞行下士", 1),
            new RankTitle("飞行少尉", 5),
            new RankTitle("飞行少校", 10),
            new RankTitle("飞行少将", 20));

    public record RankTitle(String name, int minLevel) {
    }

    public static String rankOf(int level) {
        String name = RANKS.get(0).name();
        for (RankTitle rank : RANKS) {
            if (level >= rank.minLevel()) {
                name = rank.name();
            }
        }
        return name;
    }

    /**
     * 星球解锁条件（maps.inf 原文）。
     * 未在 maps.inf 中标注条件的星球沿用「无等级要求」。
     */
    public static String unlockOf(int planetId) {
        return switch (planetId) {
            case 2 -> "飞行下士以上";
            case 3 -> "飞行少尉以上";
            case 4 -> "飞行少校以上";
            case 5 -> "飞行少将以上";
            case 6 -> "无等级要求";
            case 7 -> "必须和我同级";
            default -> "无等级要求";
        };
    }

    /** 星球解锁所需最低等级；0 表示无要求 */
    public static int unlockLevelOf(int planetId) {
        return switch (planetId) {
            case 2 -> 1;
            case 3 -> 5;
            case 4 -> 10;
            case 5 -> 20;
            default -> 0;
        };
    }

    /** 键位映射（方向键与功能键，供安卓端绘制软键盘提示） */
    public static final Map<String, String> KEYS = keys();

    private static Map<String, String> keys() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("2", "上");
        map.put("8", "下");
        map.put("4", "左");
        map.put("6", "右");
        map.put("5", "确定");
        map.put("*", "使用道具");
        map.put("#", "聊天");
        return java.util.Collections.unmodifiableMap(map);
    }
}
