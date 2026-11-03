package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.service.fanqie.FanqieClient;
import com.huluwa.console.service.fanqie.FanqieShareLinks;
import com.huluwa.console.ws.ChatPushService;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 消息中心会话服务：会话列表 / 消息流 / 发送（电影港搜索、磁力下载、视频解析、迅雷下载【飞牛】、迅雷下载【Win】）/ 已读 / 清空 / 检索。
 *
 * 数据来源两条线：
 *  - 「电影港」等聊天会话：chat_session + chat_message（用户发送与机器人回复都落库）；
 *  - 「系统通知」虚拟会话：直接读既有 message 表（Frp / Docker / 推送等系统事件），不复制数据。
 * 发送是「同步受理 + 异步处理」两段：submit() 毫秒级落用户消息并返回（chat_request 幂等），
 * 站点搜索 / qB 提交 / 视频解析在 chat-worker 线程执行，产出的机器人气泡经 WebSocket 推送。
 * 电影港结果按参考脚本的形态逐条落成机器人气泡：
 * 摘要一条 + 每条结果一个「封面图 + 配文」气泡（配文含标题/年代/产地/类型与详情页链接）。
 * 每条结果的结构化载荷里额外带 subscribed 标记与订阅所需字段，前端据此渲染「订阅 / 已订阅」按钮，
 * 点击后由 /api/chat/subscriptions 落库并把该剧详情页的全部磁力存为对比基线。
 *
 * 发送「视频解析」消息即在后台线程调用内网解析服务（对齐 quantum 的 quantum-video-parse.cs，约 5-30 秒），
 * 把抖音等分享口令解析出的作品按气泡形态逐条落库：
 *  - 有视频直链 → 一条 video 气泡（封面作预览图，配文=标题/作者，点按端内播放）；
 *  - 无视频直链（图文作品）→ 多图合并为一条可翻页的图集气泡，单张实况照片按 video 气泡推；
 *  - 音乐直链不单独推气泡，只落在日志与加载回复里。
 */
@Service
public class ChatService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 系统通知虚拟会话键（不落 chat_session，读 message 表） */
    public static final String SYSTEM_SESSION = "system";
    /** 电影港会话键（schema 内置） */
    public static final String MOVIE_SESSION = "movie";
    /** 旧电影港在线会话键，兼容旧客户端并统一映射到 movie。 */
    public static final String MOVIE_HLS_SESSION = "movie-hls";
    /** 电影天堂会话键（schema 内置） */
    public static final String DYTT_SESSION = "dytt";
    /** 懂片帝会话键（schema 内置） */
    public static final String DPD_SESSION = "dpd";
    /** BT影视磁力搜索会话键。 */
    public static final String BTBTLA_SESSION = "btbtla";
    /** 番茄小说搜索会话键。 */
    public static final String FANQIE_SESSION = "fanqie";
    /** 磁力下载会话键（schema 内置） */
    public static final String MAGNET_SESSION = "magnet";
    /** 视频解析会话键（schema 内置） */
    public static final String VIDEO_SESSION = "video";
    /** 迅雷下载【飞牛】会话键（schema 内置；历史键名保持 xunlei 兼容既有数据与路由） */
    public static final String XUNLEI_SESSION = "xunlei";
    /** 迅雷下载【Win】会话键（schema 内置） */
    public static final String XUNLEI_WIN_SESSION = "xunlei-win";
    /** 磁力下载会话内的配置引导指令 */
    private static final String MAGNET_HELP_TASK = "帮助";
    /** 磁力下载会话内的任务状态指令 */
    private static final String MAGNET_STATUS_TASK = "状态";
    /** 磁力下载会话内的保存路径指令前缀（如「路径 D:/Downloads/电影」） */
    private static final String MAGNET_PATH_PREFIX = "路径";
    /** 视频解析会话内的配置引导指令 */
    private static final String VIDEO_HELP_TASK = "帮助";
    /** 视频解析会话内的连接状态指令 */
    private static final String VIDEO_STATUS_TASK = "状态";
    /** 迅雷下载会话内的配置引导指令 */
    private static final String XUNLEI_HELP_TASK = "帮助";
    /** 迅雷下载会话内的任务状态指令 */
    private static final String XUNLEI_STATUS_TASK = "状态";
    /** Docker 版迅雷会话内的保存路径指令前缀 */
    private static final String XUNLEI_PATH_PREFIX = "路径";
    /** 迅雷【Win】会话内的配置引导指令 */
    private static final String XUNLEI_WIN_HELP_TASK = "帮助";
    /** 迅雷【Win】会话内的任务状态指令 */
    private static final String XUNLEI_WIN_STATUS_TASK = "状态";
    /** 迅雷【Win】会话内的保存目录指令前缀（Windows 路径，如「路径 D:\\迅雷下载」） */
    private static final String XUNLEI_WIN_PATH_PREFIX = "路径";
    /** 单条搜索最多展示的结果条数（与参考脚本 scripts_dygangs_max 缺省一致） */
    private static final int MOVIE_SHOW_MAX = MovieSearchService.DEFAULT_MAX;
    /** 电影天堂单条搜索最多展示的结果条数 */
    private static final int DYTT_SHOW_MAX = DyttSearchService.DEFAULT_MAX;
    /** 懂片帝单条搜索最多展示的结果条数 */
    private static final int DPD_SHOW_MAX = DongPianSearchService.DEFAULT_MAX;
    /** 番茄小说单条搜索最多展示的书目数 */
    private static final int FANQIE_SHOW_MAX = FanqieSearchService.DEFAULT_MAX;
    /** 兼容手动输入书号的阅读指令；安卓搜索卡直接打开原生阅读页。 */
    private static final String FANQIE_READ_PREFIX = "阅读 ";
    /** 番茄小说会话内的继续阅读指令：不带书号，取最近一本。 */
    private static final String FANQIE_CONTINUE = "继续阅读";

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    /**
     * 聊天处理线程池：影视搜索 / 下载指令保留 2 线程，视频解析独立使用 2 线程，
     * 网络等待彼此隔离；SQLite 写入仍保持短操作。
     * 已知代价：同一会话连发多条消息时机器人回复可能乱序穿插（消息 id 仍单调递增）。
     */
    private final ChatTaskExecutor chatWorker = new ChatTaskExecutor();

    private final JdbcTemplate jdbcTemplate;
    private final DyttSearchService dyttSearchService;
    private final DongPianSearchService dongPianSearchService;
    private final BtbtlaSearchService btbtlaSearchService;
    private final FanqieSearchService fanqieSearchService;
    private final FanqieLibraryService fanqieLibraryService;
    private final MovieCatalogService movieCatalogService;
    private final QBittorrentService qBittorrentService;
    private final MessageService messageService;
    private final MovieSubscriptionService movieSubscriptionService;
    private final VideoParseService videoParseService;
    private final XunleiService xunleiService;
    private final XunleiWinService xunleiWinService;
    private final ChatRequestService chatRequestService;
    private final ChatPushService chatPushService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatService(JdbcTemplate jdbcTemplate, DyttSearchService dyttSearchService,
                       DongPianSearchService dongPianSearchService,
                       BtbtlaSearchService btbtlaSearchService,
                       FanqieSearchService fanqieSearchService,
                       FanqieLibraryService fanqieLibraryService,
                       MovieCatalogService movieCatalogService,
                       QBittorrentService qBittorrentService, MessageService messageService,
                       MovieSubscriptionService movieSubscriptionService,
                       VideoParseService videoParseService, XunleiService xunleiService,
                       XunleiWinService xunleiWinService,
                       ChatRequestService chatRequestService,
                       ChatPushService chatPushService) {
        this.jdbcTemplate = jdbcTemplate;
        this.dyttSearchService = dyttSearchService;
        this.dongPianSearchService = dongPianSearchService;
        this.btbtlaSearchService = btbtlaSearchService;
        this.fanqieSearchService = fanqieSearchService;
        this.fanqieLibraryService = fanqieLibraryService;
        this.movieCatalogService = movieCatalogService;
        this.qBittorrentService = qBittorrentService;
        this.messageService = messageService;
        this.movieSubscriptionService = movieSubscriptionService;
        this.videoParseService = videoParseService;
        this.xunleiService = xunleiService;
        this.xunleiWinService = xunleiWinService;
        this.chatRequestService = chatRequestService;
        this.chatPushService = chatPushService;
    }

    @PreDestroy
    public void shutdown() {
        chatWorker.close();
    }

    /** 会话消息（前后端统一形状，camelCase 下发） */
    public record ChatMessage(long id, String sessionId, String role, String type, String content,
                              String contentText, String payload, boolean read, String createdAt) {
    }

    // ------------------------------------------------------------------ 会话列表

    /** 会话列表（含未读与最后一条预览），按最近一条消息时间倒序 */
    public List<Map<String, Object>> sessions() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> row : jdbcTemplate.queryForList(
                "SELECT id, title, kind, updated_at FROM chat_session")) {
            String id = String.valueOf(row.get("id"));
            // 电影天堂暂时从功能列表屏蔽，保留会话及历史数据，方便后续恢复。
            if (DYTT_SESSION.equals(id)) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", id);
            item.put("title", row.get("title"));
            item.put("kind", row.get("kind"));
            item.put("unread", unread(id));
            Map<String, Object> last = lastMessage(id);
            item.put("last", last);
            item.put("updatedAt", last == null ? row.get("updated_at") : last.get("createdAt"));
            list.add(item);
        }

        // 系统通知是虚拟会话；它和普通会话使用同一套最近消息排序，不再固定置底。
        Map<String, Object> system = new LinkedHashMap<>();
        system.put("id", SYSTEM_SESSION);
        system.put("title", "系统通知");
        system.put("kind", "system");
        system.put("unread", countSystemUnread());
        Map<String, Object> lastSystem = lastSystemMessage();
        system.put("last", lastSystem);
        system.put("updatedAt", lastSystem == null ? null : lastSystem.get("createdAt"));
        list.add(system);

        list.sort((left, right) -> {
            int byTime = String.valueOf(right.get("updatedAt")).compareTo(String.valueOf(left.get("updatedAt")));
            return byTime != 0 ? byTime : String.valueOf(left.get("id")).compareTo(String.valueOf(right.get("id")));
        });
        return list;
    }

    public static String canonicalSessionId(String sessionId) {
        return MOVIE_HLS_SESSION.equals(sessionId) ? MOVIE_SESSION : sessionId;
    }

    /** 会话存在性校验（系统通知为虚拟会话，不查表） */
    public void requireSession(String sessionId) {
        sessionId = canonicalSessionId(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            return;
        }
        Long found = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_session WHERE id = ?", Long.class, sessionId);
        if (found == null || found == 0L) {
            throw new BusinessException(404, "会话不存在");
        }
    }

    public String sessionTitle(String sessionId) {
        sessionId = canonicalSessionId(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            return "系统通知";
        }
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT title FROM chat_session WHERE id = ?", sessionId);
        return rows.isEmpty() ? sessionId : String.valueOf(rows.get(0).get("title"));
    }

    private long unread(String sessionId) {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM chat_message WHERE session_id = ? AND role = 'bot' AND read_flag = 0",
                Long.class, sessionId);
        return value == null ? 0L : value;
    }

    private long countSystemUnread() {
        Long value = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM message WHERE read_flag = 0", Long.class);
        return value == null ? 0L : value;
    }

    private Map<String, Object> lastMessage(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, role, type, content, content_text, created_at FROM chat_message "
                        + "WHERE session_id = ? ORDER BY id DESC LIMIT 1", sessionId);
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> last = new LinkedHashMap<>();
        last.put("role", row.get("role"));
        last.put("type", row.get("type"));
        last.put("content", row.get("content"));
        last.put("contentText", row.get("content_text"));
        last.put("createdAt", row.get("created_at"));
        return last;
    }

    private Map<String, Object> lastSystemMessage() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT id, title, content, level, created_at FROM message ORDER BY id DESC LIMIT 1");
        if (rows.isEmpty()) {
            return null;
        }
        Map<String, Object> row = rows.get(0);
        Map<String, Object> last = new LinkedHashMap<>();
        last.put("role", "bot");
        last.put("type", "notify");
        // 与 messages() 的系统通知映射保持同一形状：正文放 content，标题放 contentText，
        // 列表预览按标题展示（sessionPreview 的 notify 分支取 contentText）。
        last.put("content", row.get("content"));
        last.put("contentText", row.get("title"));
        last.put("createdAt", row.get("created_at"));
        return last;
    }

    // ------------------------------------------------------------------ 消息流

    /**
     * 会话消息（按 id 升序返回，前端从末尾开始渲染）。
     *
     * @param beforeId 向上翻页游标：仅返回 id 小于该值的更早消息（可为 null）
     * @param limit    本页条数（1-200）
     */
    public List<ChatMessage> messages(String sessionId, Long beforeId, int limit) {
        sessionId = canonicalSessionId(sessionId);
        requireSession(sessionId);
        int size = Math.max(1, Math.min(limit, 200));
        if (SYSTEM_SESSION.equals(sessionId)) {
            return systemMessages(beforeId, size);
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, session_id, role, type, content, content_text, payload, read_flag, created_at "
                        + "FROM chat_message WHERE session_id = ?");
        List<Object> params = new ArrayList<>();
        params.add(sessionId);
        if (beforeId != null) {
            sql.append(" AND id < ?");
            params.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<ChatMessage> list = new ArrayList<>();
        for (int i = rows.size() - 1; i >= 0; i--) {
            list.add(toChatMessage(rows.get(i)));
        }
        return list;
    }

    /** 写入系统通知并返回其准确消息 ID；调用方在事务提交后负责实时推送。 */
    public ChatMessage appendSystemNotification(String title, String content, String level, String source) {
        MessageService.PublishedMessage notification = messageService.publishAndGet(title, content, level, source);
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("level", notification.level());
        meta.put("source", notification.source());
        meta.put("title", notification.title());
        return new ChatMessage(notification.id(), SYSTEM_SESSION, "bot", "notify", notification.content(),
                notification.title(), toJson(meta), false, notification.createdAt());
    }

    /** 系统通知以 message 表映射为 notify 类型气泡（保留级别与来源） */
    private List<ChatMessage> systemMessages(Long beforeId, int size) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, title, content, level, source, read_flag, created_at FROM message WHERE 1 = 1");
        List<Object> params = new ArrayList<>();
        if (beforeId != null) {
            sql.append(" AND id < ?");
            params.add(beforeId);
        }
        sql.append(" ORDER BY id DESC LIMIT ?");
        params.add(size);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        List<ChatMessage> list = new ArrayList<>();
        Map<String, Object> meta;
        for (int i = rows.size() - 1; i >= 0; i--) {
            Map<String, Object> row = rows.get(i);
            meta = new LinkedHashMap<>();
            meta.put("level", row.get("level"));
            meta.put("source", row.get("source"));
            meta.put("title", row.get("title"));
            list.add(new ChatMessage(number(row.get("id")), SYSTEM_SESSION, "bot", "notify",
                    string(row.get("content")), string(row.get("title")), toJson(meta),
                    number(row.get("read_flag")) == 1, string(row.get("created_at"))));
        }
        return list;
    }

    private ChatMessage toChatMessage(Map<String, Object> row) {
        return new ChatMessage(number(row.get("id")), string(row.get("session_id")), string(row.get("role")),
                string(row.get("type")), string(row.get("content")), string(row.get("content_text")),
                string(row.get("payload")), number(row.get("read_flag")) == 1, string(row.get("created_at")));
    }

    // ------------------------------------------------------------------ 发送（搜索）

    /**
     * 一次发送的受理结果：requestId 归一化后的幂等键；status 取自 chat_request 表
     * （received = 本次新受理 / processing = 重放撞上在途请求 / done|failed = 重放历史请求）；
     * userMessage 为落库后的真实用户消息行（含数字 id），前端据此收编 pending 气泡。
     */
    public record SubmitResult(String requestId, String status, ChatMessage userMessage) {
    }

    /**
     * 发送的同步接收段（毫秒级返回）：幂等落用户消息 + 登记 chat_request + 广播用户行 + 异步入队。
     * 电影港搜索 / qB 提交 / 视频解析等耗时处理在 {@link #processRequest}（chat-worker 线程）执行，
     * 结果经 WebSocket 推送（或前端轮询）到达，HTTP/WS 两个入口共用本方法，天然幂等。
     */
    public synchronized SubmitResult submit(String requestedSessionId, String requestId, String content) {
        String sessionId = canonicalSessionId(requestedSessionId);
        requireSession(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            throw new BusinessException(400, "系统通知为只读会话，不支持发送消息");
        }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, "请输入搜索内容");
        }
        String key = requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId.trim();

        // 幂等：outbox 重放同一 requestId（弱网重连 / 页面刷新）只落一条用户消息
        ChatRequestService.Existing existing = chatRequestService.find(key);
        if (existing != null) {
            return new SubmitResult(key, existing.status(), chatRequestService.userMessage(key));
        }

        ChatMessage userMessage = append(sessionId, "user", "text", text, null, null);
        chatRequestService.insert(key, sessionId, userMessage.id());
        touch(sessionId);
        // 广播用户行：多标签页实时同步（发送方还另有一帧定向 ack 收编 pending 气泡，重复行由前端按 id 去重）
        chatPushService.messages(sessionId, List.of(userMessage));
        chatWorker.submit(sessionKind(sessionId), () -> processRequest(key, sessionId, text));
        return new SubmitResult(key, "received", userMessage);
    }

    /**
     * 发送的异步处理段：只在 chat-worker 线程执行（外部站点搜索 / qB / 视频解析最长 2 分钟）。
     * 产出与终止（chat.messages + chat.done）都通过 WebSocket 广播；无论成败最后推一份会话列表快照。
     */
    void processRequest(String requestId, String sessionId, String text) {
        try {
            chatRequestService.markProcessing(requestId);
            List<ChatMessage> replies = new ArrayList<>();
            String kind = sessionKind(sessionId);
            if ("movie".equals(kind)) {
                // 站点关键字用归一化后的文本（用户气泡仍保留原始输入）
                replies.addAll(runMovieSearch(sessionId, normalizeKeyword(text)));
            } else if ("dytt".equals(kind)) {
                // 电影天堂（dyttzyw）搜索：只需要去掉可选的指令前缀，关键字保持原样
                replies.addAll(runDyttSearch(sessionId, normalizeDyttKeyword(text)));
            } else if ("dpd".equals(kind)) {
                // 懂片帝搜索：与电影天堂同一套指令前缀归一化规则
                replies.addAll(runDpdSearch(sessionId, normalizeDpdKeyword(text)));
            } else if ("btbtla".equals(kind)) {
                replies.addAll(runBtbtlaSearch(sessionId, text));
            } else if ("fanqie".equals(kind)) {
                // 番茄小说会话：搜索书目，也支持分享链接、手动阅读和继续阅读指令
                String readBook = fanqieReadBookId(text);
                if (readBook != null) {
                    replies.addAll(runFanqieRead(sessionId, readBook));
                } else if (FANQIE_CONTINUE.equals(text)) {
                    // 「继续阅读」取最近读过的书，未加入书架的临时阅读记录也算在内。
                    List<FanqieLibraryService.BookSummary> books = fanqieLibraryService.recent();
                    if (books.isEmpty()) {
                        replies.add(append(sessionId, "bot", "text", "还没有阅读记录，发送书名或作者搜索后点击书目即可阅读。", null, null));
                    } else {
                        replies.add(fanqieReaderMessage(sessionId, books.getFirst()));
                    }
                } else {
                    // 分享口令（「推荐一部好书《书名》https://…」）先按书号直达，定位不到再退回书名搜索
                    String sharedBook = fanqieShareBookId(text);
                    String shareTitle = FanqieShareLinks.sharedTitle(text);
                    boolean hasLink = FanqieShareLinks.hasLink(text);
                    if (sharedBook != null) {
                        try {
                            // 命中书号时只回一条阅读记录；不再追加搜索结果卡片。
                            replies.addAll(runFanqieRead(sessionId, sharedBook));
                        } catch (Exception error) {
                            // 书号能解析但打不开（已下架 / 上游限流）：仍只回一条提示，
                            // 回退到书名搜索会刷出整页卡片，反而更难定位。
                            log.warn("番茄分享书号 {} 打开失败：{}", sharedBook, error.getMessage());
                            String name = shareTitle == null ? "这本书" : "《" + shareTitle + "》";
                            replies.add(append(sessionId, "bot", "error",
                                    name + "暂时打不开：" + safeMessage(error) + "，请稍后重试。", null, null));
                        }
                    } else if (hasLink && shareTitle != null) {
                        // 番茄链接解析不到书号、但分享文案带书名：按书名搜一次，
                        // 书名能对上时只留那一条，否则保留搜索结果。
                        replies.addAll(runFanqieShareSearch(sessionId, shareTitle));
                    } else if (hasLink) {
                        replies.add(append(sessionId, "bot", "text",
                                "这条番茄分享链接没能定位到书籍，请把带书名的分享文案整段发给我，或直接发送书名、作者。", null, null));
                    } else {
                        replies.addAll(runFanqieSearch(sessionId, fanqieSearchKeyword(text)));
                    }
                }
            } else if ("magnet".equals(kind)) {
                // 磁力会话：submit = 提交磁力给 qB（未配置时引导配置），状态 / 帮助为内置指令
                replies.addAll(runMagnetTask(sessionId, text));
            } else if ("video".equals(kind)) {
                // 视频解析会话：把分享口令交给解析服务，回复视频 / 图集气泡
                replies.addAll(runVideoParseTask(sessionId, text));
            } else if ("xunlei".equals(kind)) {
                // 迅雷【飞牛】会话：submit = 提交磁力给飞牛迅雷建下载任务（未配置时引导配置），状态 / 帮助为内置指令
                replies.addAll(runXunleiTask(sessionId, text));
            } else if ("xunlei-win".equals(kind)) {
                // 迅雷【Win】会话：submit = 提交磁力给 Windows 迅雷建下载任务，状态 / 帮助为内置指令
                replies.addAll(runXunleiWinTask(sessionId, text));
            }
            chatRequestService.markDone(requestId);
            chatPushService.messages(sessionId, replies);
            chatPushService.done(requestId, sessionId, "done", null);
            touch(sessionId);
        } catch (Exception e) {
            log.warn("聊天请求 {} 处理失败：{}", requestId, e.getMessage());
            String reason = safeMessage(e);
            try {
                ChatMessage errorReply = append(sessionId, "bot", "error", "处理失败：" + reason, null, null);
                chatRequestService.markFailed(requestId, reason);
                chatPushService.messages(sessionId, List.of(errorReply));
            } catch (Exception suppressed) {
                // 落库失败时仍要发终止帧，避免前端 typing 指示挂死
                log.warn("聊天请求 {} 失败回复落库异常：{}", requestId, suppressed.getMessage());
            }
            chatPushService.done(requestId, sessionId, "failed", reason);
        } finally {
            chatPushService.sessions(sessions());
        }
    }

    /**
     * 关键字归一化（对齐参考脚本 NormalizeKeyword 的用法）：
     * 兼容用户手打指令前缀「电影港搜索 凡人」/「电影港 凡人」→「凡人」，
     * 纯片名（含数字片名，如「2046」）保持原样。
     */
    static String normalizeKeyword(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        int space = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\u3000' || ch == '\t' || ch == '\n' || ch == '\r') {
                space = i;
                break;
            }
        }
        if (space <= 0) {
            return text;
        }
        String head = text.substring(0, space);
        if ("电影港搜索".equals(head) || "电影港".equals(head) || "dyg".equalsIgnoreCase(head)) {
            String rest = text.substring(space + 1).trim();
            return rest.isEmpty() ? text : rest;
        }
        return text;
    }

    /**
     * 电影天堂关键字归一化：兼容手打指令前缀「电影天堂搜索 凡人」/「电影天堂 凡人」/「dyttzy 凡人」→「凡人」，
     * 纯片名（含数字片名）保持原样（与电影港的 normalizeKeyword 同规则，只是前缀不同）。
     */
    static String normalizeDyttKeyword(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        int space = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '\u3000' || ch == '\t' || ch == '\n' || ch == '\r') {
                space = i;
                break;
            }
        }
        if (space <= 0) {
            return text;
        }
        String head = text.substring(0, space);
        if ("电影天堂搜索".equals(head) || "电影天堂".equals(head)
                || "dytt".equalsIgnoreCase(head) || "dyttzy".equalsIgnoreCase(head)) {
            String rest = text.substring(space + 1).trim();
            return rest.isEmpty() ? text : rest;
        }
        return text;
    }

    /**
     * 懂片帝关键字归一化：兼容「懂片帝搜索 凡人」/「懂片帝 凡人」/「dpd 凡人」→「凡人」，
     * 规则与电影天堂一致；纯片名（含数字片名）保持原样。
     */
    static String normalizeDpdKeyword(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        int space = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '　' || ch == '\t' || ch == '\n' || ch == '\r') {
                space = i;
                break;
            }
        }
        if (space <= 0) {
            return text;
        }
        String head = text.substring(0, space);
        if ("懂片帝搜索".equals(head) || "懂片帝".equals(head)
                || "dpd".equalsIgnoreCase(head) || "dongpian".equalsIgnoreCase(head)) {
            String rest = text.substring(space + 1).trim();
            return rest.isEmpty() ? text : rest;
        }
        return text;
    }

    /**
     * 懂片帝搜索：回复形态与电影天堂一致（摘要一条 + 每条结果一条气泡）。
     *
     * <p>与电影天堂的差别：懂片帝的线路清单要等打开「资源列表」时再解析（每集一次请求，
     * 全量解析很慢），所以卡片载荷里不带线路，安卓端进弹层后走 /api/dpd/detail。
     * 也不参与订阅追更（站点按集下发 token，没有稳定的分集地址可对比）。
     */
    private List<ChatMessage> runDpdSearch(String sessionId, String keyword) {
        List<ChatMessage> replies = new ArrayList<>();
        List<DongPianSearchService.DpdItem> found;
        try {
            found = dongPianSearchService.search(keyword);
        } catch (Exception e) {
            replies.add(append(sessionId, "bot", "error", "搜索失败：" + safeMessage(e), null, null));
            return replies;
        }

        if (found.isEmpty()) {
            replies.add(append(sessionId, "bot", "error",
                    "未搜索到「" + keyword + "」相关资源，换个片名试试（懂片帝按片名匹配）。", null, null));
            return replies;
        }

        int shown = Math.min(found.size(), DPD_SHOW_MAX);
        replies.add(append(sessionId, "bot", "text",
                "关键字「" + keyword + "」命中 " + found.size() + " 条，展示前 " + shown + " 条：\n"
                        + "点「资源列表」查看该作品的各路播放源（m3u8 直链，可播放 / 复制 / 离线下载）。", null, null));
        for (int i = 0; i < shown; i++) {
            DongPianSearchService.DpdItem item = found.get(i);
            String caption = dpdCaption(item);
            String payload = toJson(dongPianSearchService.toPayload(item));
            if (item.cover() == null || item.cover().isBlank()) {
                replies.add(append(sessionId, "bot", "text", caption, null, payload));
            } else {
                replies.add(append(sessionId, "bot", "image", item.cover(), caption, payload));
            }
        }
        return replies;
    }

    /** BT影视聊天仅搜索作品；资源接口负责目录和全部版本磁力的数据库缓存。 */
    private List<ChatMessage> runBtbtlaSearch(String sessionId, String text) {
        String keyword = text.replaceFirst("(?i)^(?:BT影视搜索|BT影视|btbtla搜索|btbtla)\\s+", "").trim();
        List<BtbtlaSearchService.SearchItem> found = btbtlaSearchService.search(keyword);
        List<ChatMessage> replies = new ArrayList<>();
        if (found.isEmpty()) {
            replies.add(append(sessionId, "bot", "text", "未搜索到「" + keyword + "」相关资源，换个片名试试。", null, null));
            return replies;
        }
        int shown = Math.min(found.size(), BtbtlaSearchService.DEFAULT_MAX);
        replies.add(append(sessionId, "bot", "text", "关键字「" + keyword + "」找到 " + found.size()
                + " 部作品，展示前 " + shown + " 部。点「资源列表」按清晰度、集数查看磁力，最高清晰度优先。", null, null));
        for (var item : found.subList(0, shown)) {
            String caption = item.title() + "（" + String.join(" / ", List.of(item.year(), item.genre(), item.origin())
                    .stream().filter(value -> !value.isBlank()).toList()) + "）\n详情：" + item.pageUrl();
            String payload = toJson(btbtlaSearchService.payload(item));
            replies.add(item.cover().isBlank()
                    ? append(sessionId, "bot", "text", caption, null, payload)
                    : append(sessionId, "bot", "image", item.cover(), caption, payload));
        }
        return replies;
    }

    /** 兼容手动输入「阅读 <bookId>」，纯数字书号才作为指令解析。 */
    static String fanqieReadBookId(String text) {
        String value = text == null ? "" : text.trim();
        if (!value.startsWith(FANQIE_READ_PREFIX)) return null;
        String bookId = value.substring(FANQIE_READ_PREFIX.length()).trim();
        return bookId.matches("[0-9]{6,24}") ? bookId : null;
    }

    /**
     * 从分享口令里定位书号。
     *
     * <p>番茄 App 的分享文本形如「推荐一部好书《书名》https://changdunovel.com/t/xxxx/」，
     * 落地页才带 book_id，因此短链需要跟随一次重定向。解析不到返回 null，
     * 由调用方退回按书名搜索。</p>
     */
    private String fanqieShareBookId(String text) {
        String url = FanqieShareLinks.firstUrl(text);
        if (url == null) return null;
        try {
            return fanqieSearchService.resolveShareUrl(url);
        } catch (Exception error) {
            log.warn("番茄分享链接解析失败：{}", error.getMessage());
            return null;
        }
    }

    /**
     * 番茄搜索关键字：剥掉指令前缀后，若带分享链接则优先用书名号里的书名，
     * 否则去掉链接再搜索，避免整段口令（含 URL）超过关键字长度上限。
     */
    static String fanqieSearchKeyword(String raw) {
        String value = normalizeFanqieKeyword(raw);
        if (!FanqieShareLinks.hasLink(value)) return value;
        String title = FanqieShareLinks.sharedTitle(value);
        if (title != null && !title.isBlank()) return title;
        String stripped = FanqieShareLinks.stripLinks(value);
        return stripped.isEmpty() ? value : stripped;
    }

    private List<ChatMessage> runFanqieRead(String sessionId, String bookId) {
        // 分享链接直达时最近搜索结果里没有这本书，先取详情补全书名、作者和封面
        FanqieClient.Book known = fanqieSearchService.findCached(bookId);
        if (known == null) known = fanqieSearchService.describe(bookId);
        // 会话只负责进入阅读页：书不自动加入书架，由阅读页右上角按钮决定。
        FanqieLibraryService.BookSummary book = fanqieLibraryService.open(bookId,
                known == null ? "" : known.bookName(), known == null ? "" : known.author(),
                known == null ? "" : known.thumbUrl(), false);
        return List.of(fanqieReaderMessage(sessionId, book));
    }

    private ChatMessage fanqieReaderMessage(String sessionId, FanqieLibraryService.BookSummary book) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "fanqie-reader");
        meta.put("bookId", book.bookId());
        meta.put("title", book.title());
        meta.put("author", book.author());
        meta.put("cover", book.cover());
        meta.put("progressChapter", book.progressChapter());
        String caption = "《" + book.title() + "》" + (book.author().isBlank() ? "" : "  " + book.author())
                + "\n点击即可在 App 内阅读，共 " + book.chapterCount() + " 章。"
                + (book.progressChapter() > 0 ? "\n上次读到第 " + book.progressChapter() + " 章。" : "");
        return book.cover().isBlank()
                ? append(sessionId, "bot", "text", caption, null, toJson(meta))
                : append(sessionId, "bot", "image", book.cover(), caption, toJson(meta));
    }

    /**
     * 分享文案带书名但没解析到书号时：按书名搜一次，书名完全对上的只保留一条。
     *
     * <p>番茄分享的《书名》就是全书唯一标题，上游模糊搜索却会带出大量同前缀的书
     * （《有钱后，我每天都很快乐》能搜出 19 本），所以命中精确书名后直接收敛成一条。</p>
     */
    private List<ChatMessage> runFanqieShareSearch(String sessionId, String title) {
        List<FanqieClient.Book> found;
        try {
            found = fanqieSearchService.search(title);
        } catch (Exception e) {
            return List.of(append(sessionId, "bot", "error", "搜索失败：" + safeMessage(e), null, null));
        }
        String target = title.trim();
        for (FanqieClient.Book book : found) {
            if (book.bookName() != null && book.bookName().trim().equals(target)) {
                String payload = toJson(fanqieSearchService.payload(book));
                return List.of(book.thumbUrl().isBlank()
                        ? append(sessionId, "bot", "text", FanqieSearchService.caption(book), null, payload)
                        : append(sessionId, "bot", "image", book.thumbUrl(),
                                FanqieSearchService.caption(book), payload));
            }
        }
        // 没有精确同名结果时保留完整搜索结果，用户仍可从列表里挑。
        if (found.isEmpty()) {
            return List.of(append(sessionId, "bot", "text",
                    "未搜索到《" + title + "》，换个书名或作者名试试。", null, null));
        }
        List<ChatMessage> replies = new ArrayList<>();
        int shown = Math.min(found.size(), FANQIE_SHOW_MAX);
        replies.add(append(sessionId, "bot", "text",
                "书名「" + title + "」没有精确匹配，找到 " + found.size() + " 本相关书籍，展示前 " + shown + " 本。", null, null));
        for (FanqieClient.Book book : found.subList(0, shown)) {
            String payload = toJson(fanqieSearchService.payload(book));
            replies.add(book.thumbUrl().isBlank()
                    ? append(sessionId, "bot", "text", FanqieSearchService.caption(book), null, payload)
                    : append(sessionId, "bot", "image", book.thumbUrl(),
                            FanqieSearchService.caption(book), payload));
        }
        return replies;
    }

    /**
     * 番茄小说搜索：回复形态与 BT影视一致（摘要一条 + 每条书目一张卡片）。
     *
     * <p>番茄小说不参与订阅追更，卡片只带书目信息与封面；搜不到时给出换词提示。</p>
     */
    private List<ChatMessage> runFanqieSearch(String sessionId, String keyword) {
        List<ChatMessage> replies = new ArrayList<>();
        List<FanqieClient.Book> found;
        try {
            found = fanqieSearchService.search(keyword);
        } catch (Exception e) {
            replies.add(append(sessionId, "bot", "error", "搜索失败：" + safeMessage(e), null, null));
            return replies;
        }
        if (found.isEmpty()) {
            replies.add(append(sessionId, "bot", "text",
                    "未搜索到「" + keyword + "」相关书籍，换个书名或作者名试试。", null, null));
            return replies;
        }
        int shown = Math.min(found.size(), FANQIE_SHOW_MAX);
        replies.add(append(sessionId, "bot", "text",
                "关键字「" + keyword + "」找到 " + found.size() + " 本书，展示前 " + shown + " 本。", null, null));
        for (FanqieClient.Book book : found.subList(0, shown)) {
            String caption = FanqieSearchService.caption(book);
            String payload = toJson(fanqieSearchService.payload(book));
            replies.add(book.thumbUrl().isBlank()
                    ? append(sessionId, "bot", "text", caption, null, payload)
                    : append(sessionId, "bot", "image", book.thumbUrl(), caption, payload));
        }
        return replies;
    }

    /**
     * 番茄小说关键字归一化：兼容「番茄小说搜索 斗罗」/「番茄 斗罗」/「fanqie 斗罗」→「斗罗」，
     * 纯书名保持原样。
     */
    static String normalizeFanqieKeyword(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return text;
        }
        int space = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ' || ch == '　' || ch == '\t' || ch == '\n' || ch == '\r') {
                space = i;
                break;
            }
        }
        if (space <= 0) {
            return text;
        }
        String head = text.substring(0, space);
        if ("番茄小说搜索".equals(head) || "番茄小说".equals(head) || "番茄".equals(head)
                || "fanqie".equalsIgnoreCase(head)) {
            String rest = text.substring(space + 1).trim();
            return rest.isEmpty() ? text : rest;
        }
        return text;
    }

    /** 懂片帝卡片配文：标题（年代 / 产地 / 类型 / 评分）+ 更新备注 + 详情页链接 */
    private static String dpdCaption(DongPianSearchService.DpdItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.title());
        List<String> meta = new ArrayList<>();
        if (notBlank(item.year())) {
            meta.add(item.year());
        }
        if (notBlank(item.origin())) {
            meta.add(item.origin());
        }
        if (notBlank(item.genre())) {
            meta.add(item.genre());
        }
        if (notBlank(item.score())) {
            meta.add("评分 " + item.score());
        }
        if (!meta.isEmpty()) {
            sb.append('（').append(String.join(" / ", meta)).append('）');
        }
        if (notBlank(item.remarks())) {
            sb.append('\n').append("状态：").append(item.remarks());
        }
        if (notBlank(item.url())) {
            sb.append('\n').append("详情：").append(item.url());
        }
        return sb.toString();
    }

    /**
     * 电影天堂搜索：回复形态与电影港一致（摘要一条 + 每条结果一条气泡），
     * 卡片额外带豆瓣评分、更新备注与在线播放线路（点击即复制资源地址）。
     */
    private List<ChatMessage> runDyttSearch(String sessionId, String keyword) {
        List<ChatMessage> replies = new ArrayList<>();
        List<DyttSearchService.DyttItem> found;
        try {
            found = dyttSearchService.search(keyword);
        } catch (Exception e) {
            replies.add(append(sessionId, "bot", "error", "搜索失败：" + safeMessage(e), null, null));
            return replies;
        }

        if (found.isEmpty()) {
            replies.add(append(sessionId, "bot", "error",
                    "未搜索到「" + keyword + "」相关资源，换个片名试试（电影天堂按片名匹配，不支持演员）。", null, null));
            return replies;
        }

        int shown = Math.min(found.size(), DYTT_SHOW_MAX);
        replies.add(append(sessionId, "bot", "text",
                "关键字「" + keyword + "」命中 " + found.size() + " 条，展示前 " + shown + " 条：\n"
                        + "点卡片右上角「订阅」即可自动下载并追更，也可查看资源列表。", null, null));
        List<String> subscribed = jdbcTemplate.queryForList(
                "SELECT res_id FROM hls_subscription WHERE api_url = ? AND enabled = 1", String.class, HlsSupport.providerUrl(null));
        for (int i = 0; i < shown; i++) {
            DyttSearchService.DyttItem item = found.get(i);
            String caption = dyttCaption(item);
            Map<String, Object> payloadMap = dyttSearchService.toPayload(item);
            payloadMap.put("subscribed", subscribed.contains(item.id()));
            String payload = toJson(payloadMap);
            if (item.cover() == null || item.cover().isBlank()) {
                replies.add(append(sessionId, "bot", "text", caption, null, payload));
            } else {
                replies.add(append(sessionId, "bot", "image", item.cover(), caption, payload));
            }
        }
        return replies;
    }

    /** 电影天堂卡片配文：标题（年代 / 产地 / 类型 / 评分）+ 更新备注 + 详情页链接 */
    private static String dyttCaption(DyttSearchService.DyttItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(item.id()).append(']').append(item.title());
        List<String> meta = new ArrayList<>();
        if (notBlank(item.year())) {
            meta.add(item.year());
        }
        if (notBlank(item.origin())) {
            meta.add(item.origin());
        }
        if (notBlank(item.genre())) {
            meta.add(item.genre());
        }
        if (notBlank(item.score())) {
            meta.add("豆瓣 " + item.score());
        }
        if (!meta.isEmpty()) {
            sb.append('（').append(String.join(" / ", meta)).append('）');
        }
        if (notBlank(item.remarks())) {
            sb.append('\n').append("状态：").append(item.remarks());
        }
        if (notBlank(item.url())) {
            sb.append('\n').append("详情：").append(item.url());
        }
        return sb.toString();
    }

    /** 统一搜索两个站点，磁力优先，同名作品合并为一张资源卡片。 */
    private List<ChatMessage> runMovieSearch(String sessionId, String keyword) {
        MovieCatalogService.SearchResult result = movieCatalogService.search(keyword);
        if (result.films().isEmpty()) {
            if (!result.errors().isEmpty()) {
                return List.of(append(sessionId, "bot", "error", String.join("\n", result.errors()), null, null));
            }
            Map<String, Object> watchPayload = new LinkedHashMap<>();
            watchPayload.put("kind", "movie-search-watch");
            watchPayload.put("keyword", keyword);
            watchPayload.put("noResults", true);
            return List.of(append(sessionId, "bot", "error",
                    "未搜索到「" + keyword + "」相关资源。可点击下方「监听」，后台会定期重新搜索，找到后发送系统通知。",
                    null, toJson(watchPayload)));
        }
        int shown = Math.min(result.films().size(), MOVIE_SHOW_MAX);
        List<ChatMessage> replies = new ArrayList<>();
        String summary = "关键字「" + keyword + "」命中 " + result.films().size() + " 条，展示前 " + shown
                + " 条：\n磁力资源优先，点击「资源列表」可切换磁力 / 在线资源。";
        if (!result.errors().isEmpty()) summary += "\n" + String.join("\n", result.errors());
        replies.add(append(sessionId, "bot", "text", summary, null, null));
        Set<String> subscribed = movieSubscriptionService.subscribedUrls();
        for (var film : result.films().subList(0, shown)) {
            Map<String, Object> payload = new LinkedHashMap<>(film);
            payload.put("subscribed", subscribed.contains(string(film.get("url"))));
            var item = new MovieSearchService.MovieItem(string(film.get("id")), string(film.get("title")),
                    string(film.get("year")), string(film.get("origin")), string(film.get("genre")),
                    string(film.get("cover")), string(film.get("url")));
            replies.add(notBlank(item.cover())
                    ? append(sessionId, "bot", "image", item.cover(), caption(item), toJson(payload))
                    : append(sessionId, "bot", "text", caption(item), null, toJson(payload)));
        }
        return replies;
    }

    /** 监听命中后的卡片复用普通搜索格式；调用方提交事务后再推送。 */
    public List<ChatMessage> appendWatchedMovieResults(String keyword, List<MovieSearchService.MovieItem> found) {
        List<ChatMessage> replies = movieSearchResults(MOVIE_SESSION, keyword, found, true);
        touch(MOVIE_SESSION);
        return replies;
    }

    private List<ChatMessage> movieSearchResults(String sessionId, String keyword,
                                                List<MovieSearchService.MovieItem> found, boolean watched) {
        List<ChatMessage> replies = new ArrayList<>();
        int shown = Math.min(found.size(), MOVIE_SHOW_MAX);
        replies.add(append(sessionId, "bot", "text",
                (watched ? "监听关键字「" : "关键字「") + keyword + "」命中 " + found.size() + " 条，展示前 " + shown + " 条：\n"
                        + "点卡片上的「订阅」即可监听该剧更新（默认每小时巡检一次）。", null, null));
        Set<String> subscribed = movieSubscriptionService.subscribedUrls();
        for (int i = 0; i < shown; i++) {
            MovieSearchService.MovieItem item = found.get(i);
            String caption = caption(item);
            Map<String, Object> payloadMap = MovieSearchService.toPayload(item);
            payloadMap.put("subscribed", subscribed.contains(item.url()));
            String payload = toJson(payloadMap);
            if (item.cover() == null || item.cover().isBlank()) {
                replies.add(append(sessionId, "bot", "text", caption, null, payload));
            } else {
                replies.add(append(sessionId, "bot", "image", item.cover(), caption, payload));
            }
        }
        return replies;
    }

    // ------------------------------------------------------------------ 磁力下载

    /**
     * 磁力下载会话的一次发送：识别内置指令（帮助 / 状态 / 路径），否则按磁力提交给 qB。
     *
     * <p>首次使用（未配置服务地址）时返回「配置引导」气泡，前端据此弹出配置表单；
     * 之后每条消息都会带一份连接状态快照，前端在页脚常驻「已连接 / 未配置」入口。</p>
     */
    private List<ChatMessage> runMagnetTask(String sessionId, String text) {
        List<ChatMessage> replies = new ArrayList<>();
        String task = text == null ? "" : text.trim();
        boolean configured = qBittorrentService.settings().configured();

        if (task.isEmpty() || MAGNET_HELP_TASK.equals(task)) {
            replies.add(magnetText(sessionId, magnetHelpText(configured), magnetMeta(configured)));
            return replies;
        }
        if (task.equalsIgnoreCase(MAGNET_STATUS_TASK)) {
            replies.add(magnetStatus(sessionId));
            return replies;
        }
        if (task.startsWith(MAGNET_PATH_PREFIX + " ") || task.equals(MAGNET_PATH_PREFIX)) {
            replies.add(magnetSavePath(sessionId, task.length() > MAGNET_PATH_PREFIX.length()
                    ? task.substring(MAGNET_PATH_PREFIX.length()).trim() : ""));
            return replies;
        }

        List<String> magnets = QBittorrentService.extractMagnets(task);
        if (magnets.isEmpty()) {
            return replies;
        }
        if (!configured) {
            replies.add(magnetText(sessionId,
                    "已收到 " + magnets.size() + " 条磁力链接，但还没有配置 qBittorrent。"
                            + "\n请填写服务地址与账号密码，配置完成后本条磁力可直接重发。",
                    magnetMeta("config", magnets)));
            return replies;
        }

        QBittorrentService.AddResult result;
        try {
            result = qBittorrentService.add(magnets, null);
        } catch (Exception e) {
            replies.add(magnetText(sessionId, "提交失败：" + safeMessage(e), magnetMeta(true)));
            return replies;
        }

        String summary = result.duplicate() ? "已在列表" : (result.ok() ? "添加成功" : "添加失败");
        String headline = (result.ok() ? "✅ " : "⚠️ ") + summary + "：" + result.message();
        replies.add(magnetText(sessionId, headline, magnetMeta(true)));
        // 磁力链接很长，逐条单独成气泡（可整体复制）
        for (String magnet : magnets) {
            replies.add(append(sessionId, "bot", "text", magnet, null, magnetMeta(true)));
        }
        return replies;
    }

    /** 「状态」指令：汇总 qB 的任务列表 */
    private ChatMessage magnetStatus(String sessionId) {
        if (!qBittorrentService.settings().configured()) {
            return magnetText(sessionId, "尚未配置 qBittorrent，请先填写服务地址与账号密码。", magnetMeta(false));
        }
        List<QBittorrentService.Torrent> torrents;
        try {
            torrents = qBittorrentService.torrents(10);
        } catch (Exception e) {
            return magnetText(sessionId, "读取任务失败：" + safeMessage(e), magnetMeta(true));
        }
        if (torrents.isEmpty()) {
            return magnetText(sessionId, "qBittorrent 当前没有任务。把磁力链接发给我即可开始下载。", magnetMeta(true));
        }
        StringBuilder sb = new StringBuilder("qBittorrent 最近 ").append(torrents.size()).append(" 个任务：");
        for (QBittorrentService.Torrent torrent : torrents) {
            sb.append('\n').append('•').append(' ').append(torrent.name())
                    .append('\n').append("    ")
                    .append(Math.round(torrent.progress() * 100)).append("%  ")
                    .append(QBittorrentService.stateLabel(torrent.state())).append("  ")
                    .append(QBittorrentService.humanSize(torrent.size()));
            if (torrent.downloadSpeed() > 0) {
                sb.append("  ↓").append(QBittorrentService.humanSpeed(torrent.downloadSpeed()));
            }
            if (torrent.eta() > 0 && torrent.eta() < 8640000L) {
                sb.append("  剩余 ").append(QBittorrentService.humanEta(torrent.eta()));
            }
        }
        return magnetText(sessionId, sb.toString(), magnetMeta(true));
    }

    /** 「路径 xxx」指令：保存默认下载路径（留空 = 清空，用 qB 自身的默认目录） */
    private ChatMessage magnetSavePath(String sessionId, String path) {
        if (!qBittorrentService.settings().configured()) {
            return magnetText(sessionId, "请先完成 qBittorrent 连接配置，再设置保存路径。", magnetMeta(false));
        }
        // 只改路径：不重新校验账号密码，也不清除已缓存的登录会话
        qBittorrentService.saveSavePath(path);
        return magnetText(sessionId, path.isEmpty()
                        ? "已清空默认保存路径：新任务使用 qBittorrent 自身的默认下载目录。"
                        : "已设置默认保存路径：" + path + "\n（新任务会把保存路径透传给 qB，目录必须对 qB 可见）",
                magnetMeta(true));
    }

    /** 配置引导文案：未配置时是「首次使用向导」，已配置时是帮助卡片 */
    private static String magnetHelpText(boolean configured) {
        if (!configured) {
            return """
                    还没有配置 qBittorrent，无法提交磁力任务。请按下面两步完成配置：
                    1. 在 qB 中开启「Web UI」并记住端口（默认 8080），地址形如 http://192.168.3.202:8080；
                    2. 在本会话里填写服务地址与账号密码（账号密码可留空，此时依赖 qB 的 IP 白名单放行）。
                    配置会保存在本机，密码只在保存时提交，之后不会回传到页面。""";
        }
        return """
                qBittorrent 下载已就绪，把磁力链接直接发给我即可：
                1. 一条消息可放多个磁力链接（空格或换行分隔），会一起提交给 qBittorrent；
                2. 输入「状态」查看最近的下载任务；
                3. 输入「路径 D:/Downloads」设置默认保存目录，「路径」单独发送则清空；
                4. 首次使用需在 qB 的「Web UI」中放行本机 IP，或填写账号密码登录。""";
    }

    /** 磁力会话消息的结构化载荷（前端据此渲染状态徽标与配置入口） */
    private String magnetMeta(boolean configured) {
        return magnetMeta(configured ? "ready" : "config");
    }

    private String magnetMeta(String status) {
        return magnetMeta(status, null);
    }

    private String magnetMeta(String status, List<String> magnets) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "magnet");
        meta.put("status", status);
        meta.put("settings", qBittorrentService.describe());
        if (magnets != null && !magnets.isEmpty()) {
            meta.put("magnets", magnets);
        }
        return toJson(meta);
    }

    private ChatMessage magnetText(String sessionId, String content, String payload) {
        return append(sessionId, "bot", "text", content, null, payload);
    }

    // ------------------------------------------------------------------ 迅雷下载

    /**
     * 迅雷下载会话的一次发送：识别内置指令（帮助 / 状态 / 路径），否则按磁力提交给迅雷。
     *
     * <p>首次使用（未配置直连地址）时返回「配置引导」气泡（payload.kind=xunlei），前端据此弹出配置表单；
     * 提交口径对齐参考脚本 qBittorrentXunleiTransfer.cs：服务端解析磁力文件清单 → 建任务（全部文件）。</p>
     */
    private List<ChatMessage> runXunleiTask(String sessionId, String text) {
        List<ChatMessage> replies = new ArrayList<>();
        String task = text == null ? "" : text.trim();
        boolean configured = xunleiService.settings().configured();

        if (task.isEmpty() || XUNLEI_HELP_TASK.equals(task)) {
            replies.add(xunleiText(sessionId, xunleiHelpText(configured), xunleiMeta(configured)));
            return replies;
        }
        if (XUNLEI_STATUS_TASK.equals(task)) {
            replies.add(xunleiStatus(sessionId));
            return replies;
        }
        if (task.startsWith(XUNLEI_PATH_PREFIX + " ") || task.equals(XUNLEI_PATH_PREFIX)) {
            String path = task.length() > XUNLEI_PATH_PREFIX.length()
                    ? task.substring(XUNLEI_PATH_PREFIX.length()).trim() : "";
            replies.add(xunleiSavePath(sessionId, path));
            return replies;
        }

        List<String> magnets = QBittorrentService.extractMagnets(task);
        if (magnets.isEmpty()) {
            replies.add(xunleiText(sessionId,
                    "没有识别到磁力链接，把 magnet:?xt=urn:btih:… 整段发给我即可创建迅雷下载任务。",
                    xunleiMeta(configured)));
            return replies;
        }
        if (!configured) {
            replies.add(xunleiText(sessionId,
                    "已收到 " + magnets.size() + " 条磁力链接，但还没有配置迅雷。"
                            + "\n请填写飞牛直连地址和登录信息，配置完成后本条磁力可直接重发。",
                    xunleiMeta("config", magnets)));
            return replies;
        }

        List<XunleiService.TaskResult> results;
        try {
            results = xunleiService.add(magnets);
        } catch (Exception e) {
            replies.add(xunleiText(sessionId, "提交失败：" + safeMessage(e), xunleiMeta(true)));
            return replies;
        }

        for (XunleiService.TaskResult result : results) {
            if (result.ok()) {
                String headline = "✅ 已创建迅雷任务：《" + result.name() + "》\n"
                        + "大小：" + QBittorrentService.humanSize(result.size()) + " / " + result.fileCount() + " 个文件"
                        + (string(result.taskId()) == null ? "" : "\n迅雷任务ID：" + result.taskId());
                replies.add(xunleiText(sessionId, headline, xunleiMeta(true)));
            } else {
                replies.add(xunleiText(sessionId, "⚠️ 提交失败：" + result.message(), xunleiMeta(true)));
            }
        }
        // 磁力链接很长，逐条单独成气泡（可整体复制）
        for (String magnet : magnets) {
            replies.add(append(sessionId, "bot", "text", magnet, null, xunleiMeta(true)));
        }
        return replies;
    }

    /** 「状态」指令：展示当前配置并实时探测迅雷在线状态 */
    private ChatMessage xunleiStatus(String sessionId) {
        XunleiService.Settings settings = xunleiService.settings();
        if (!settings.configured()) {
            return xunleiText(sessionId, "尚未配置迅雷直连地址，请先完成配置。", xunleiMeta(false));
        }
        StringBuilder sb = new StringBuilder("迅雷配置：\n");
        sb.append("• 直连地址：").append(settings.url()).append('\n');
        sb.append("• 保存路径：")
                .append(XunleiService.isNativeFnos(settings.url()) ? "迅雷默认目录（自动）" : settings.savePath())
                .append('\n');
        Map<String, Object> probe = xunleiService.test(settings.url(), settings.token());
        boolean online = Boolean.TRUE.equals(probe.get("success"));
        sb.append("• 连接状态：").append(online ? "" : "不").append("在线")
                .append("（").append(probe.get("message")).append('）').append('\n');
        sb.append("• 用法：把磁力链接直接发给我即可创建下载任务。");
        return xunleiText(sessionId, sb.toString(), xunleiMeta(true));
    }

    /** Docker 版可用「路径 xxx」修改保存路径；飞牛原生版固定使用迅雷默认目录。 */
    private ChatMessage xunleiSavePath(String sessionId, String path) {
        if (!xunleiService.settings().configured()) {
            return xunleiText(sessionId, "请先完成迅雷连接配置，再设置保存路径。", xunleiMeta(false));
        }
        if (XunleiService.isNativeFnos(xunleiService.settings().url())) {
            return xunleiText(sessionId, "飞牛原生迅雷自动使用迅雷 App 的默认下载目录，无需设置路径。", xunleiMeta(true));
        }
        if (path.isEmpty()) {
            return xunleiText(sessionId,
                    "迅雷必须指定保存路径，不能清空。当前路径：" + xunleiService.settings().savePath(), xunleiMeta(true));
        }
        xunleiService.saveSavePath(path);
        return xunleiText(sessionId, "已设置迅雷保存路径：" + path
                + "\n（路径需与迅雷 App 新建任务页里可选的目录一致）", xunleiMeta(true));
    }

    /** 配置引导文案：未配置时是「首次使用向导」，已配置时是帮助卡片 */
    private static String xunleiHelpText(boolean configured) {
        if (!configured) {
            return """
                    还没有配置迅雷，无法提交下载任务。请完成配置：
                    1. 飞牛原生迅雷直连地址形如 http://192.168.3.202:5666/cgi/ThirdParty/xunlei/index.cgi；
                    2. 填写飞牛 NAS 登录账号和密码，程序会获取并续取 fnos-token（pan-auth 也会自动获取）。
                    飞牛原生迅雷自动使用迅雷 App 的默认下载目录，无需填写保存路径。
                    启用两步验证时，首次登录需填写当前验证码；也可改用手动 fnos-token。
                    Docker 版可填写其 2345 端口地址及保存路径；配置只保存在本机。""";
        }
        return """
                迅雷远程下载已就绪，把磁力链接直接发给我即可：
                1. 一条消息可放多个磁力链接（空格或换行分隔），逐条在迅雷创建下载任务（含全部文件）；
                2. 输入「状态」查看当前配置与迅雷在线状态；
                3. 飞牛原生迅雷自动使用迅雷默认目录；Docker 版可用「路径」指令修改保存目录；
                4. NAS 版迅雷每日任务数有限额，超额提交会被迅雷拒绝。""";
    }

    /** 迅雷会话消息的结构化载荷（前端据此渲染闪电头像与配置入口） */
    private String xunleiMeta(boolean configured) {
        return xunleiMeta(configured ? "ready" : "config");
    }

    private String xunleiMeta(String status) {
        return xunleiMeta(status, null);
    }

    private String xunleiMeta(String status, List<String> magnets) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "xunlei");
        meta.put("status", status);
        meta.put("settings", xunleiService.describe());
        if (magnets != null && !magnets.isEmpty()) {
            meta.put("magnets", magnets);
        }
        return toJson(meta);
    }

    private ChatMessage xunleiText(String sessionId, String content, String payload) {
        return append(sessionId, "bot", "text", content, null, payload);
    }

    // ------------------------------------------------------------------ 迅雷下载【Win】

    /**
     * 迅雷【Win】会话的一次发送：识别内置指令（帮助 / 状态 / 路径），否则按磁力提交给 Windows 迅雷。
     *
     * <p>对接 Windows 机器上的 xunlei-win-api 服务：提交返回流水号，进度由「我的下载」跟踪；
     * 未配置服务地址时返回「配置引导」气泡（payload.kind=xunlei-win），前端据此弹出配置表单。</p>
     */
    private List<ChatMessage> runXunleiWinTask(String sessionId, String text) {
        List<ChatMessage> replies = new ArrayList<>();
        String task = text == null ? "" : text.trim();
        boolean configured = xunleiWinService.settings().configured();

        if (task.isEmpty() || XUNLEI_WIN_HELP_TASK.equals(task)) {
            replies.add(xunleiWinText(sessionId, xunleiWinHelpText(configured), xunleiWinMeta(configured)));
            return replies;
        }
        if (XUNLEI_WIN_STATUS_TASK.equals(task)) {
            replies.add(xunleiWinStatus(sessionId));
            return replies;
        }
        if (task.startsWith(XUNLEI_WIN_PATH_PREFIX + " ") || task.equals(XUNLEI_WIN_PATH_PREFIX)) {
            String path = task.length() > XUNLEI_WIN_PATH_PREFIX.length()
                    ? task.substring(XUNLEI_WIN_PATH_PREFIX.length()).trim() : "";
            replies.add(xunleiWinSavePath(sessionId, path));
            return replies;
        }

        List<String> magnets = QBittorrentService.extractMagnets(task);
        if (magnets.isEmpty()) {
            replies.add(xunleiWinText(sessionId,
                    "没有识别到磁力链接，把 magnet:?xt=urn:btih:… 整段发给我即可创建迅雷【Win】下载任务。",
                    xunleiWinMeta(configured)));
            return replies;
        }
        if (!configured) {
            replies.add(xunleiWinText(sessionId,
                    "已收到 " + magnets.size() + " 条磁力链接，但还没有配置迅雷下载【Win】。"
                            + "\n请填写 Windows 机器上 xunlei-win-api 的服务地址，配置完成后本条磁力可直接重发。",
                    xunleiWinMeta("config", magnets)));
            return replies;
        }

        List<XunleiWinService.TaskResult> results;
        try {
            results = xunleiWinService.add(magnets, null);
        } catch (Exception e) {
            replies.add(xunleiWinText(sessionId, "提交失败：" + safeMessage(e), xunleiWinMeta(true)));
            return replies;
        }

        for (XunleiWinService.TaskResult result : results) {
            if (result.ok()) {
                String headline = "✅ 已提交迅雷【Win】下载任务"
                        + (string(result.taskId()) == null ? "" : "（流水号 " + result.taskId() + "）")
                        + "\n下载进度在「我的下载 → 迅雷【Win】」查看。";
                replies.add(xunleiWinText(sessionId, headline, xunleiWinMeta(true)));
            } else {
                replies.add(xunleiWinText(sessionId, "⚠️ 提交失败：" + result.message(), xunleiWinMeta(true)));
            }
        }
        // 磁力链接很长，逐条单独成气泡（可整体复制）
        for (String magnet : magnets) {
            replies.add(append(sessionId, "bot", "text", magnet, null, xunleiWinMeta(true)));
        }
        return replies;
    }

    /** 「状态」指令：展示当前配置并实时探测 xunlei-win-api 在线状态 */
    private ChatMessage xunleiWinStatus(String sessionId) {
        XunleiWinService.Settings settings = xunleiWinService.settings();
        if (!settings.configured()) {
            return xunleiWinText(sessionId, "尚未配置迅雷下载【Win】服务地址，请先完成配置。", xunleiWinMeta(false));
        }
        StringBuilder sb = new StringBuilder("迅雷下载【Win】配置：\n");
        sb.append("• 服务地址：").append(settings.url()).append('\n');
        sb.append("• 保存目录：").append(settings.savePath() == null || settings.savePath().isBlank()
                ? "迅雷客户端默认目录" : settings.savePath()).append('\n');
        Map<String, Object> probe = xunleiWinService.test(settings.url());
        sb.append("• 连接状态：").append(Boolean.TRUE.equals(probe.get("success")) ? "" : "不").append("在线")
                .append("（").append(probe.get("message")).append('）').append('\n');
        sb.append("• 用法：把磁力链接直接发给我即可创建下载任务。");
        return xunleiWinText(sessionId, sb.toString(), xunleiWinMeta(true));
    }

    /** 「路径 xxx」修改 Windows 保存根目录；「路径」单独发送则清空，回到迅雷客户端默认目录。 */
    private ChatMessage xunleiWinSavePath(String sessionId, String path) {
        if (!xunleiWinService.settings().configured()) {
            return xunleiWinText(sessionId, "请先完成迅雷下载【Win】连接配置，再设置保存目录。", xunleiWinMeta(false));
        }
        xunleiWinService.saveSavePath(path);
        return xunleiWinText(sessionId, path.isEmpty()
                ? "已清空保存根目录：之后的新任务由迅雷客户端使用默认下载目录。"
                : "已设置迅雷【Win】保存根目录：" + path + "\n（Windows 本机路径，如 D:\\迅雷下载）",
                xunleiWinMeta(true));
    }

    /** 配置引导文案：未配置时是「首次使用向导」，已配置时是帮助卡片 */
    private static String xunleiWinHelpText(boolean configured) {
        if (!configured) {
            return """
                    还没有配置迅雷下载【Win】，无法提交下载任务。请完成配置：
                    1. 在 Windows 机器上安装迅雷客户端并保持运行，部署 xunlei-win-api 服务；
                    2. 填写服务地址（如 http://192.168.3.100:8899），「测试连接」确认迅雷正在运行；
                    3. 保存根目录可留空，留空时新任务使用迅雷客户端的默认下载目录。""";
        }
        return """
                迅雷下载【Win】已就绪，把磁力链接直接发给我即可：
                1. 一条消息可放多个磁力链接（空格或换行分隔），逐条在 Windows 迅雷创建下载任务；
                2. 输入「状态」查看当前配置与 xunlei-win-api 在线状态；
                3. 输入「路径 D:\\迅雷下载」设置保存根目录，「路径」单独发送则清空（用迅雷默认目录）；
                4. 电影港订阅 / 资源列表下载时也可选择「迅雷【Win】」工具，进度在「我的下载 → 迅雷【Win】」查看。""";
    }

    /** 迅雷【Win】会话消息的结构化载荷（前端据此渲染闪电头像与配置入口） */
    private String xunleiWinMeta(boolean configured) {
        return xunleiWinMeta(configured ? "ready" : "config");
    }

    private String xunleiWinMeta(String status) {
        return xunleiWinMeta(status, null);
    }

    private String xunleiWinMeta(String status, List<String> magnets) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "xunlei-win");
        meta.put("status", status);
        meta.put("settings", xunleiWinService.describe());
        if (magnets != null && !magnets.isEmpty()) {
            meta.put("magnets", magnets);
        }
        return toJson(meta);
    }

    private ChatMessage xunleiWinText(String sessionId, String content, String payload) {
        return append(sessionId, "bot", "text", content, null, payload);
    }

    /** 配文：标题（年代 / 产地 / 类型）+ 详情页链接（前端自动识别为可点链接） */
    private static String caption(MovieSearchService.MovieItem item) {
        StringBuilder sb = new StringBuilder();
        sb.append('[').append(item.id()).append(']').append(item.title());
        List<String> meta = new ArrayList<>();
        if (notBlank(item.year())) {
            meta.add(item.year());
        }
        if (notBlank(item.origin())) {
            meta.add(item.origin());
        }
        if (notBlank(item.genre())) {
            meta.add(item.genre());
        }
        if (!meta.isEmpty()) {
            sb.append('（').append(String.join(" / ", meta)).append('）');
        }
        sb.append('\n').append("详情：").append(item.url());
        return sb.toString();
    }

    private ChatMessage append(String sessionId, String role, String type, String content,
                               String contentText, String payload) {
        String now = LocalDateTime.now().format(TIME_FORMAT);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                INSERT INTO chat_message (session_id, role, type, content, content_text, payload, read_flag, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, sessionId);
            statement.setString(2, role);
            statement.setString(3, type);
            statement.setString(4, content);
            statement.setString(5, contentText);
            statement.setString(6, payload);
            statement.setInt(7, "user".equals(role) ? 1 : 0);
            statement.setString(8, now);
            return statement;
        }, key);
        Number id = key.getKey();
        if (id == null) throw new IllegalStateException("消息写入未返回主键");
        return new ChatMessage(id.longValue(), sessionId, role, type, content, contentText, payload,
                "user".equals(role), now);
    }

    private void touch(String sessionId) {
        jdbcTemplate.update("UPDATE chat_session SET updated_at = ? WHERE id = ?",
                LocalDateTime.now().format(TIME_FORMAT), sessionId);
    }

    private String sessionKind(String sessionId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT kind FROM chat_session WHERE id = ?", sessionId);
        return rows.isEmpty() ? "chat" : string(rows.get(0).get("kind"));
    }

    // ------------------------------------------------------------------ 视频解析

    /**
     * 视频解析会话的一次发送：识别内置指令（帮助 / 状态），否则整条输入交给解析服务。
     *
     * <p>解析服务未配置时返回「配置引导」气泡（带 payload.status=config），前端据此弹出配置表单；
     * 解析成功后按作品类型回复视频 / 图集气泡（形态对齐 quantum 的 quantum-video-parse.cs）。</p>
     */
    private List<ChatMessage> runVideoParseTask(String sessionId, String text) {
        List<ChatMessage> replies = new ArrayList<>();
        String task = text == null ? "" : text.trim();
        boolean configured = videoParseService.settings().configured();

        if (task.isEmpty() || VIDEO_HELP_TASK.equals(task)) {
            replies.add(videoText(sessionId, videoHelpText(configured),
                    videoMeta(configured ? "ready" : "config")));
            return replies;
        }
        if (VIDEO_STATUS_TASK.equals(task)) {
            replies.add(videoStatus(sessionId));
            return replies;
        }
        if (!configured) {
            replies.add(videoText(sessionId, videoHelpText(false), videoMeta("config")));
            return replies;
        }

        VideoParseService.VideoItem item;
        try {
            item = videoParseService.parse(task);
        } catch (Exception e) {
            replies.add(append(sessionId, "bot", "error", "解析失败：" + safeMessage(e), null,
                    videoMeta("error")));
            return replies;
        }

        String title = notBlank(item.title()) ? item.title() : "未知标题";
        String author = notBlank(item.author()) ? item.author() : "未知作者";
        String caption = "标题：" + title + "\n作者：" + author;
        String payload = videoMeta("ready", item);

        if (notBlank(item.videoUrl())) {
            // 视频作品：单条视频气泡（封面作播放预览）
            replies.add(append(sessionId, "bot", "video", item.videoUrl(), caption, payload));
            return replies;
        }

        if (!item.images().isEmpty()) {
            VideoParseService.ImageItem firstImage = item.images().getFirst();
            if (item.images().size() > 1) {
                // 图集合为一条消息，客户端用 payload.images 左右翻页；content 保留首图供旧客户端展示。
                replies.add(append(sessionId, "bot", "image", firstImage.url(), caption, payload));
            } else if (notBlank(firstImage.livePhotoUrl())) {
                replies.add(append(sessionId, "bot", "video", firstImage.livePhotoUrl(),
                        caption, videoMeta("ready", item, firstImage.url())));
            } else {
                replies.add(append(sessionId, "bot", "image", firstImage.url(), caption, payload));
            }
            return replies;
        }

        if (notBlank(item.coverUrl())) {
            replies.add(append(sessionId, "bot", "image", item.coverUrl(), caption, payload));
            return replies;
        }

        replies.add(append(sessionId, "bot", "error",
                "解析成功但没有拿到可播放的视频或图片直链，可能是作品已删除或服务端解析能力有限。",
                null, videoMeta("error")));
        return replies;
    }

    /** 「状态」指令：展示解析服务的连接配置与启停摘要 */
    private ChatMessage videoStatus(String sessionId) {
        boolean configured = videoParseService.settings().configured();
        if (!configured) {
            return videoText(sessionId, "尚未配置视频解析服务地址，请先完成配置。", videoMeta("config"));
        }
        StringBuilder sb = new StringBuilder("视频解析服务已就绪：\n");
        sb.append("• 服务地址：").append(videoParseService.settings().url()).append('\n');
        String username = videoParseService.settings().username();
        sb.append("• 登录账号：").append(notBlank(username) ? username : "（匿名访问）").append('\n');
        sb.append("• 用法：把抖音等 App 的分享口令整段发进来即可。");
        return videoText(sessionId, sb.toString(), videoMeta("ready"));
    }

    /** 配置引导文案：未配置时是「首次使用向导」，已配置时是帮助卡片 */
    private static String videoHelpText(boolean configured) {
        if (!configured) {
            return """
                    还没有配置视频解析服务，无法解析分享口令。请按下面两步完成配置：
                    1. 在本会话里填写视频解析服务地址（自建服务形如 http://192.168.3.202:8080）；
                    2. 按需填写该服务的登录账号密码（服务端若用 IP 白名单放行，账号可以留空）。
                    配置只保存在本机，密码不回传到页面。""";
        }
        return """
                视频解析已就绪，把分享口令整段发给我即可：
                1. 支持抖音、快手等 App 的分享口令（形如「7.68 复制打开抖音…https://v.douyin.com/xxxx/」）；
                2. 视频作品回复一条可播放的视频气泡，多图作品可左右切换查看，实况照片支持播放；
                3. 输入「状态」查看当前解析服务配置，「帮助」随时回到本说明。""";
    }

    /** 视频解析会话消息的结构化载荷（前端据此渲染头像、媒体气泡与配置入口） */
    private String videoMeta(String status) {
        return videoMeta(status, null, null);
    }

    private String videoMeta(String status, VideoParseService.VideoItem item) {
        return videoMeta(status, item, null);
    }

    private String videoMeta(String status, VideoParseService.VideoItem item, String posterOverride) {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("kind", "video");
        meta.put("status", status);
        meta.put("settings", videoParseService.describe());
        if (item != null) {
            meta.put("title", item.title());
            meta.put("author", item.author());
            // poster 供视频气泡的封面位使用（视频作品取作品封面，实况照片取对应的图片）
            meta.put("poster", notBlank(posterOverride) ? posterOverride : item.coverUrl());
            meta.put("cover", notBlank(posterOverride) ? posterOverride : item.coverUrl());
            meta.put("video", item.videoUrl());
            meta.put("music", item.musicUrl());
            meta.put("imageCount", item.images().size());
            if (!notBlank(item.videoUrl()) && item.images().size() > 1) {
                meta.put("images", item.images());
            }
        }
        return toJson(meta);
    }

    private ChatMessage videoText(String sessionId, String content, String payload) {
        return append(sessionId, "bot", "text", content, null, payload);
    }

    // ------------------------------------------------------------------ 已读 / 清空 / 检索

    /** 会话标记已读（系统通知同步推进既有 message 表，与「全部已读」同语义）；末尾推送列表快照同步其它标签页 */
    public void markRead(String sessionId) {
        sessionId = canonicalSessionId(sessionId);
        requireSession(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            messageService.markAllRead();
            chatPushService.sessions(sessions());
            return;
        }
        // 标记已读不改动会话 updated_at：会话排序只随消息产生变化，避免已读操作把会话顶到列表最前
        int updated = jdbcTemplate.update(
                "UPDATE chat_message SET read_flag = 1 WHERE session_id = ? AND role = 'bot' AND read_flag = 0",
                sessionId);
        if (updated > 0) {
            chatPushService.sessions(sessions());
        }
    }

    /** 清空会话消息（仅聊天会话；系统通知由消息中心原有删除/已读入口管理）；推送列表快照同步其它标签页 */
    public void clear(String sessionId) {
        sessionId = canonicalSessionId(sessionId);
        requireSession(sessionId);
        if (SYSTEM_SESSION.equals(sessionId)) {
            throw new BusinessException(400, "系统通知不支持清空，请使用消息中心的消息管理入口");
        }
        jdbcTemplate.update("DELETE FROM chat_message WHERE session_id = ?", sessionId);
        touch(sessionId);
        chatPushService.sessions(sessions());
    }

    /**
     * 跨会话消息检索（顶栏搜索）：按正文/配文/标题匹配，
     * 命中电影港等聊天会话的 chat_message 与系统通知的 message 表，按时间倒序返回。
     */
    public List<Map<String, Object>> search(String keyword, int limit) {
        String trimmed = keyword == null ? "" : keyword.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, Math.min(limit, 50));
        String like = "%" + trimmed + "%";
        List<Map<String, Object>> list = new ArrayList<>();

        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT m.id, m.session_id, s.title, m.role, m.type, m.content, m.content_text, m.created_at
                  FROM chat_message m
                  JOIN chat_session s ON s.id = m.session_id
                 WHERE m.content LIKE ? OR IFNULL(m.content_text, '') LIKE ?
                 ORDER BY m.id DESC LIMIT ?
                """, like, like, size)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", row.get("session_id"));
            item.put("sessionTitle", row.get("title"));
            item.put("messageId", row.get("id"));
            item.put("role", row.get("role"));
            item.put("type", row.get("type"));
            item.put("content", row.get("content"));
            item.put("contentText", row.get("content_text"));
            item.put("createdAt", row.get("created_at"));
            list.add(item);
        }

        for (Map<String, Object> row : jdbcTemplate.queryForList("""
                SELECT id, title, content, created_at
                  FROM message
                 WHERE title LIKE ? OR IFNULL(content, '') LIKE ?
                 ORDER BY id DESC LIMIT ?
                """, like, like, size)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("sessionId", SYSTEM_SESSION);
            item.put("sessionTitle", "系统通知");
            item.put("messageId", row.get("id"));
            item.put("role", "bot");
            item.put("type", "notify");
            item.put("content", row.get("content"));
            item.put("contentText", row.get("title"));
            item.put("createdAt", row.get("created_at"));
            list.add(item);
        }

        list.sort((a, b) -> String.valueOf(b.get("createdAt")).compareTo(String.valueOf(a.get("createdAt"))));
        return list.size() > size ? new ArrayList<>(list.subList(0, size)) : list;
    }

    // ------------------------------------------------------------------ 工具

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? "网络异常，请稍后重试" : message;
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static long number(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }
}
