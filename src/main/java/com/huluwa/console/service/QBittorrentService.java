package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * qBittorrent 下载器对接（Web API v2），对齐 quantum 参考工程的 qBittorrentAdd.cs：
 *
 * <ul>
 *   <li>登录：POST /api/v2/auth/login（multipart 账号密码）→ 取 Set-Cookie 会话；
 *       会话缓存 12 小时内免登录复用，超时或校验失效自动重登并回写（对齐参考脚本的 qbSID 缓存）；</li>
 *   <li>添加：POST /api/v2/torrents/add，字段与参考脚本一致
 *       （urls / autoTMM=false / paused=false / contentLayout=Original，可选 savepath）；</li>
 *   <li>成败按 HTTP 状态码判定：409 视为「已在下载列表」（qB 5.x 重复添加），
 *       403 提示账号密码或 IP 白名单问题（与参考脚本的提示口径一致）。</li>
 * </ul>
 *
 * <p>配置存放 sys_config（qb.url / qb.username / qb.password / qb.savepath），
 * 由「磁力下载」会话首次使用时引导填写；密码只写不读，接口只回传是否已设置。</p>
 */
@Service
public class QBittorrentService {

    private static final Logger log = LoggerFactory.getLogger(QBittorrentService.class);

    /** 配置键：服务地址 */
    public static final String CFG_URL = "qb.url";
    /** 配置键：登录用户名 */
    public static final String CFG_USERNAME = "qb.username";
    /** 配置键：登录密码（只写不读） */
    public static final String CFG_PASSWORD = "qb.password";
    /** 配置键：默认保存路径（可选） */
    public static final String CFG_SAVE_PATH = "qb.savepath";
    /** 配置键：登录会话缓存（Cookie 串） */
    private static final String CFG_SESSION = "qb.session";
    /** 配置键：会话缓存元信息（epochSeconds|url|username） */
    private static final String CFG_SESSION_META = "qb.session.meta";

    /** 会话缓存有效期：与参考脚本 qbSID 一致（12 小时） */
    private static final Duration SESSION_TTL = Duration.ofHours(12);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration ADD_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(20);

    /** 磁力链接匹配；命中后再按合法字符集裁掉尾随的中文标点或说明文字 */
    private static final Pattern MAGNET_PATTERN = Pattern.compile("(?i)magnet:\\?[^\\s\"'<>]+");
    /** 磁力链接允许出现的字符（RFC 3986 保留字 / 非保留字 + 百分号） */
    private static final Pattern MAGNET_ALLOWED = Pattern.compile("[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+");

    /** qB 任务状态 → 中文文案（覆盖 4.x / 5.x 常见状态） */
    private static final Map<String, String> STATE_LABELS = Map.ofEntries(
            Map.entry("error", "错误"),
            Map.entry("missingFiles", "文件丢失"),
            Map.entry("uploading", "做种中"),
            Map.entry("pausedUP", "已暂停（做种）"),
            Map.entry("stoppedUP", "已停止（做种）"),
            Map.entry("queuedUP", "排队做种"),
            Map.entry("stalledUP", "做种中（无连接）"),
            Map.entry("checkingUP", "校验中"),
            Map.entry("forcedUP", "强制做种"),
            Map.entry("allocating", "分配空间"),
            Map.entry("downloading", "下载中"),
            Map.entry("metaDL", "获取元数据"),
            Map.entry("forcedDL", "下载中（强制）"),
            Map.entry("stalledDL", "下载中（等待连接）"),
            Map.entry("pausedDL", "已暂停"),
            Map.entry("stoppedDL", "已停止"),
            Map.entry("queuedDL", "排队下载"),
            Map.entry("checkingDL", "校验中"),
            Map.entry("checkingResumeData", "校验恢复数据"),
            Map.entry("moving", "移动文件中"),
            Map.entry("unknown", "未知状态"));

    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public QBittorrentService(ConfigService configService) {
        this.configService = configService;
    }

    /** 当前生效的连接配置（不含密码明文读取，密码用 hasPassword 表达） */
    public record Settings(String url, String username, String password, String savePath) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }

        public boolean useAuth() {
            return configured() && username != null && !username.isBlank();
        }
    }

    /** 添加结果（失败时 message 为可直接展示给用户的中文说明） */
    public record AddResult(boolean ok, boolean duplicate, String message) {
    }

    /** 下载任务概览（给「状态」指令与订阅下载跟踪使用） */
    public record Torrent(String hash, String name, double progress, String state, long size,
                          long downloadSpeed, long eta) {
    }

    /** 任务里的单个文件（订阅下载完成后按剧名补齐文件名用） */
    public record TorrentFile(String name, long size, double progress, int priority) {
    }

    // ------------------------------------------------------------------ 配置读写

    public Settings settings() {
        return new Settings(
                trim(configService.get(CFG_URL)),
                trim(configService.get(CFG_USERNAME)),
                trim(configService.get(CFG_PASSWORD)),
                trim(configService.get(CFG_SAVE_PATH)));
    }

    /** 下发前端的配置视图：绝不回传密码明文，只回传是否已设置 */
    public Map<String, Object> describe() {
        Settings settings = settings();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", settings.url());
        data.put("username", settings.username());
        data.put("savePath", settings.savePath());
        data.put("useAuth", settings.useAuth());
        data.put("passwordSet", settings.password() != null && !settings.password().isBlank());
        data.put("configured", settings.configured());
        return data;
    }

    /**
     * 保存配置。
     *
     * @param url      服务地址（必填，缺协议时自动补 http://）
     * @param username 登录用户名
     * @param password 登录密码；为 null 或空串表示「保持已保存的密码不变」
     * @param savePath 默认保存路径（可选，直接透传给 qB 的 savepath）
     * @param useAuth  是否启用账号密码登录；false 表示清空账号密码、依赖 qB 白名单放行
     */
    public Map<String, Object> save(String url, String username, String password, String savePath, boolean useAuth) {
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            throw new BusinessException(400, "请填写 qBittorrent 服务地址，例如 http://192.168.3.202:8080");
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        try {
            URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "qBittorrent 服务地址格式不正确：" + url);
        }

        String user = trim(username);
        if (useAuth) {
            if (user == null || user.isBlank()) {
                throw new BusinessException(400, "请填写 qBittorrent 登录用户名");
            }
            if ((password == null || password.isBlank()) && (settings().password() == null || settings().password().isBlank())) {
                throw new BusinessException(400, "请填写 qBittorrent 登录密码，或关闭「使用账号密码登录」");
            }
        }

        configService.put(CFG_URL, base);
        configService.put(CFG_SAVE_PATH, savePath == null ? "" : savePath.trim());
        if (useAuth) {
            configService.put(CFG_USERNAME, user);
            // 留空表示不修改已保存的密码（前端不会拿到原密码，避免明文回传）
            if (password != null && !password.isBlank()) {
                configService.put(CFG_PASSWORD, password);
            }
        } else {
            configService.put(CFG_USERNAME, "");
            configService.put(CFG_PASSWORD, "");
        }
        clearSession();
        return describe();
    }

    /** 只更新默认保存路径（「路径」指令用）：不动连接配置与会话缓存 */
    public void saveSavePath(String savePath) {
        configService.put(CFG_SAVE_PATH, savePath == null ? "" : savePath.trim());
    }

    // ------------------------------------------------------------------ 连接测试

    /** 测试连接：用表单里的最新值（密码留空则复用已保存的），返回 success/message/version */
    public Map<String, Object> test(String url, String username, String password, boolean useAuth) {
        Map<String, Object> result = new LinkedHashMap<>();
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            result.put("success", false);
            result.put("message", "请先填写 qBittorrent 服务地址");
            return result;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        String user = trim(username);
        String pass = password != null && !password.isBlank() ? password : settings().password();
        try {
            String cookie = useAuth ? login(base, user, pass).cookie() : "";
            String version = fetchVersion(base, cookie);
            result.put("success", true);
            result.put("version", version);
            result.put("message", "连接成功：qBittorrent " + version);
        } catch (BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败：" + safeMessage(e));
        }
        return result;
    }

    // ------------------------------------------------------------------ 添加磁力

    /**
     * 添加磁力任务到 qB。
     *
     * @param magnets  磁力链接（多条按换行合并提交，与参考脚本一致）
     * @param savePath 单次指定的保存路径，留空用配置里的默认值
     */
    public AddResult add(List<String> magnets, String savePath) {
        Settings settings = settings();
        if (!settings.configured()) {
            throw new BusinessException(400, "尚未配置 qBittorrent 服务地址");
        }
        if (magnets == null || magnets.isEmpty()) {
            throw new BusinessException(400, "请提供磁力链接");
        }
        String base = settings.url();
        String cookie;
        try {
            cookie = currentCookie(settings);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "连接 qBittorrent 失败：" + safeMessage(e));
        }

        List<Map.Entry<String, String>> fields = new ArrayList<>();
        fields.add(field("urls", String.join("\n", magnets)));
        fields.add(field("autoTMM", "false"));
        fields.add(field("paused", "false"));
        fields.add(field("contentLayout", "Original"));
        String path = savePath == null || savePath.isBlank() ? settings.savePath() : savePath.trim();
        if (path != null && !path.isBlank()) {
            fields.add(field("savepath", path));
        }

        HttpResponse<String> response;
        try {
            response = post(base + "/api/v2/torrents/add", cookie, fields, ADD_TIMEOUT);
        } catch (Exception e) {
            throw new BusinessException(400, "提交 qBittorrent 失败：" + safeMessage(e));
        }

        String body = response.body() == null ? "" : response.body().trim();
        if (response.statusCode() == 409) {
            return new AddResult(true, true, "磁力已在 qBittorrent 下载列表中，未重复添加");
        }
        if (response.statusCode() == 415) {
            return new AddResult(false, false, "qBittorrent 拒绝了该链接（磁力格式无效或暂不支持）");
        }
        if (response.statusCode() == 403) {
            // 会话可能已失效：清缓存，让下次调用重新登录
            clearSession();
            return new AddResult(false, false,
                    "qBittorrent 拒绝访问（403）：请检查账号密码，或在 qB 的「Web UI」设置中放行本机 IP");
        }
        if (response.statusCode() / 100 != 2) {
            return new AddResult(false, false, "qBittorrent 返回 HTTP " + response.statusCode()
                    + (body.isEmpty() ? "" : "：" + body));
        }
        // qB 5.x 起响应体为空，旧版为 "Ok."
        if (body.toLowerCase().contains("fail")) {
            return new AddResult(false, false, "qBittorrent 未能添加该磁力：" + body);
        }
        return new AddResult(true, false, "已提交 qBittorrent 下载");
    }

    /** 任务列表（按添加顺序倒序），用于「状态」指令 */
    public List<Torrent> torrents(int limit) {
        Settings settings = settings();
        if (!settings.configured()) {
            throw new BusinessException(400, "尚未配置 qBittorrent 服务地址");
        }
        String base = settings.url();
        String cookie;
        try {
            cookie = currentCookie(settings);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "连接 qBittorrent 失败：" + safeMessage(e));
        }

        HttpResponse<String> response;
        try {
            response = get(base + "/api/v2/torrents/info", cookie, QUERY_TIMEOUT);
        } catch (Exception e) {
            throw new BusinessException(400, "读取 qBittorrent 任务失败：" + safeMessage(e));
        }
        if (response.statusCode() == 403) {
            clearSession();
            throw new BusinessException(400, "qBittorrent 拒绝访问（403）：请检查账号密码或 IP 白名单设置");
        }
        if (response.statusCode() / 100 != 2) {
            throw new BusinessException(400, "qBittorrent 返回 HTTP " + response.statusCode());
        }

        List<Torrent> list = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                return list;
            }
            for (JsonNode node : root) {
                if (list.size() >= Math.max(1, limit)) {
                    break;
                }
                String hash = node.path("hash").asText("").toLowerCase(java.util.Locale.ROOT);
                String name = node.path("name").asText("");
                double progress = node.path("progress").asDouble(0);
                String state = node.path("state").asText("");
                long size = node.path("size").asLong(node.path("total_size").asLong(0));
                long speed = node.path("dlspeed").asLong(0);
                long eta = node.path("eta").asLong(0);
                list.add(new Torrent(hash, name.isBlank() ? "（未命名任务）" : name, progress, state, size, speed, eta));
            }
        } catch (Exception e) {
            throw new BusinessException(400, "解析 qBittorrent 任务列表失败：" + safeMessage(e));
        }
        return list;
    }

    /**
     * 按 info hash 查单个任务（订阅下载跟踪用）。
     *
     * @return 找到返回任务，未找到返回 null
     */
    public Torrent torrent(String hash) {
        String target = hash == null ? "" : hash.trim().toLowerCase(java.util.Locale.ROOT);
        if (target.isEmpty()) {
            return null;
        }
        for (Torrent torrent : torrents(500)) {
            if (target.equals(torrent.hash())) {
                return torrent;
            }
        }
        return null;
    }

    /**
     * 读取某个任务的文件列表（GET /api/v2/torrents/files）。
     * 用于下载完成后检查种子里的文件名是否带上了剧名。
     */
    public List<TorrentFile> files(String hash) {
        Settings settings = settings();
        if (!settings.configured()) {
            throw new BusinessException(400, "尚未配置 qBittorrent 服务地址");
        }
        String target = hash == null ? "" : hash.trim();
        if (target.isEmpty()) {
            return List.of();
        }
        String cookie;
        try {
            cookie = currentCookie(settings);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "连接 qBittorrent 失败：" + safeMessage(e));
        }

        HttpResponse<String> response;
        try {
            response = get(settings.url() + "/api/v2/torrents/files?hash=" + urlEncode(target), cookie, QUERY_TIMEOUT);
        } catch (Exception e) {
            throw new BusinessException(400, "读取 qBittorrent 文件列表失败：" + safeMessage(e));
        }
        if (response.statusCode() == 403) {
            clearSession();
            throw new BusinessException(400, "qBittorrent 拒绝访问（403）：请检查账号密码或 IP 白名单设置");
        }
        if (response.statusCode() / 100 != 2) {
            throw new BusinessException(400, "qBittorrent 返回 HTTP " + response.statusCode());
        }

        List<TorrentFile> files = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (!root.isArray()) {
                return files;
            }
            for (JsonNode node : root) {
                files.add(new TorrentFile(
                        node.path("name").asText(""),
                        node.path("size").asLong(0),
                        node.path("progress").asDouble(0),
                        node.path("priority").asInt(0)));
            }
        } catch (Exception e) {
            throw new BusinessException(400, "解析 qBittorrent 文件列表失败：" + safeMessage(e));
        }
        return files;
    }

    /**
     * 重命名任务内的文件（POST /api/v2/torrents/renameFile）。
     * 订阅下载在完成后用它把「没有剧名」的文件名补上剧名（含子目录内的相对路径）。
     *
     * @param oldPath 任务内的原始相对路径（qB 文件列表里的 name）
     * @param newPath 目标相对路径（与 oldPath 同层级，仅文件名变化）
     * @return 成功返回 null，失败返回可展示的中文原因
     */
    public String renameFile(String hash, String oldPath, String newPath) {
        Settings settings = settings();
        if (!settings.configured()) {
            return "尚未配置 qBittorrent 服务地址";
        }
        String cookie;
        try {
            cookie = currentCookie(settings);
        } catch (BusinessException e) {
            return e.getMessage();
        } catch (Exception e) {
            return "连接 qBittorrent 失败：" + safeMessage(e);
        }

        List<Map.Entry<String, String>> fields = List.of(
                field("hash", hash == null ? "" : hash),
                field("oldPath", oldPath == null ? "" : oldPath),
                field("newPath", newPath == null ? "" : newPath));
        HttpResponse<String> response;
        try {
            response = post(settings.url() + "/api/v2/torrents/renameFile", cookie, fields, ADD_TIMEOUT);
        } catch (Exception e) {
            return "调用 qBittorrent 重命名失败：" + safeMessage(e);
        }
        String body = response.body() == null ? "" : response.body().trim();
        if (response.statusCode() == 403) {
            clearSession();
            return "qBittorrent 拒绝访问（403）";
        }
        if (response.statusCode() == 409) {
            return "目标文件名已存在：" + newPath;
        }
        if (response.statusCode() / 100 != 2) {
            return "qBittorrent 返回 HTTP " + response.statusCode() + (body.isEmpty() ? "" : "：" + body);
        }
        return null;
    }

    /** 单点 URL 编码（路径参数 / 查询参数用） */
    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return value;
        }
    }

    // ------------------------------------------------------------------ 登录 / 会话缓存

    /** 取当前可用会话：优先 12 小时内的缓存 Cookie（先用 app/version 校验），失效再重新登录 */
    private String currentCookie(Settings settings) throws IOException, InterruptedException {
        if (!settings.useAuth()) {
            // 未配置账号密码：按参考脚本维持旧行为，依赖 qB 的 IP 白名单放行
            return "";
        }
        String cached = trim(configService.get(CFG_SESSION));
        String meta = trim(configService.get(CFG_SESSION_META));
        if (cached != null && !cached.isBlank() && validMeta(meta, settings)) {
            if (verifySession(settings.url(), cached)) {
                return cached;
            }
            log.info("qBittorrent 会话缓存已失效，重新登录");
        }
        LoginSession session = login(settings.url(), settings.username(), settings.password());
        cacheSession(session, settings);
        return session.cookie();
    }

    private boolean validMeta(String meta, Settings settings) {
        if (meta == null || meta.isBlank()) {
            return false;
        }
        String[] parts = meta.split("\\|", 3);
        if (parts.length < 3) {
            return false;
        }
        try {
            long epochSeconds = Long.parseLong(parts[0].trim());
            if (Instant.now().getEpochSecond() - epochSeconds > SESSION_TTL.toSeconds()) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        return parts[1].equals(settings.url()) && parts[2].equals(settings.username());
    }

    private boolean verifySession(String base, String cookie) {
        try {
            return get(base + "/api/v2/app/version", cookie, QUERY_TIMEOUT).statusCode() == 200;
        } catch (Exception e) {
            log.debug("qBittorrent 会话校验异常（按失效处理）：{}", e.getMessage());
            return false;
        }
    }

    private void cacheSession(LoginSession session, Settings settings) {
        if (session.cookie() == null || session.cookie().isBlank()) {
            return;
        }
        try {
            configService.put(CFG_SESSION, session.cookie());
            configService.put(CFG_SESSION_META,
                    Instant.now().getEpochSecond() + "|" + settings.url() + "|" + settings.username());
        } catch (Exception e) {
            log.warn("qBittorrent 会话缓存写入失败（不影响本次执行）：{}", e.getMessage());
        }
    }

    /** 配置变化 / 会话失效时清空缓存，下次调用重新登录 */
    private void clearSession() {
        configService.put(CFG_SESSION, "");
        configService.put(CFG_SESSION_META, "");
    }

    private record LoginSession(String cookie) {
    }

    /** 登录 qB：multipart 提交账号密码，从 Set-Cookie 收集会话 Cookie（兼容各版本 Cookie 名） */
    private LoginSession login(String base, String user, String password) throws IOException, InterruptedException {
        if (user == null || user.isBlank()) {
            return new LoginSession("");
        }
        List<Map.Entry<String, String>> fields = List.of(
                field("username", user),
                field("password", password == null ? "" : password));
        HttpResponse<String> response = post(base + "/api/v2/auth/login", "", fields, LOGIN_TIMEOUT);
        String body = response.body() == null ? "" : response.body().trim();
        String cookie = collectCookies(response);
        log.info("qBittorrent 登录：HTTP {} {}，会话 {}",
                response.statusCode(), body, cookie.isBlank() ? "未取到" : "已取到");
        if (cookie.isBlank()) {
            if (response.statusCode() == 403 || body.toLowerCase().contains("fail")) {
                throw new BusinessException(400, "qBittorrent 登录失败：账号或密码错误（可在设置中重新填写）");
            }
            if (response.statusCode() / 100 != 2) {
                throw new BusinessException(400, "qBittorrent 登录失败：HTTP " + response.statusCode());
            }
            // 少数版本在免验证场景不回 Set-Cookie，此时按无会话继续（依赖白名单）
            return new LoginSession("");
        }
        return new LoginSession(cookie);
    }

    /** 把 Set-Cookie 里的 name=value 拼成请求用的 Cookie 头（兼容 SID / QBT_SID 等命名） */
    private static String collectCookies(HttpResponse<?> response) {
        StringBuilder sb = new StringBuilder();
        for (String header : response.headers().allValues("Set-Cookie")) {
            int end = header.indexOf(';');
            String pair = (end < 0 ? header : header.substring(0, end)).trim();
            if (pair.isEmpty() || pair.indexOf('=') <= 0) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append("; ");
            }
            sb.append(pair);
        }
        return sb.toString();
    }

    private String fetchVersion(String base, String cookie) throws IOException, InterruptedException {
        HttpResponse<String> response = get(base + "/api/v2/app/version", cookie, QUERY_TIMEOUT);
        if (response.statusCode() == 403) {
            throw new BusinessException(400,
                    "qBittorrent 拒绝访问（403）：请填写账号密码，或在 qB 的「Web UI」设置中放行本机 IP");
        }
        if (response.statusCode() / 100 != 2) {
            throw new BusinessException(400, "qBittorrent 返回 HTTP " + response.statusCode());
        }
        String body = response.body() == null ? "" : response.body().trim();
        return body.isEmpty() ? "（版本未知）" : body;
    }

    // ------------------------------------------------------------------ HTTP 工具

    private HttpResponse<String> get(String url, String cookie, Duration timeout) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .GET();
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private HttpResponse<String> post(String url, String cookie, List<Map.Entry<String, String>> fields, Duration timeout)
            throws IOException, InterruptedException {
        String boundary = "----huluwa" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(timeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                // qB 5.x 默认开启 CSRF / Host 校验，带上同源 Referer 与 Origin 才不会被拒
                .header("Referer", originOf(url))
                .header("Origin", originOf(url))
                .header("User-Agent", "huluwa-console/1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, fields)));
        if (cookie != null && !cookie.isBlank()) {
            builder.header("Cookie", cookie);
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /** 取 URL 的协议 + 主机 + 端口（Referer / Origin 用）；解析失败返回空串 */
    private static String originOf(String url) {
        String scheme = null;
        String host = null;
        int port = -1;
        try {
            URI uri = URI.create(url);
            scheme = uri.getScheme();
            host = uri.getHost();
            port = uri.getPort();
        } catch (Exception e) {
            return "";
        }
        if (scheme == null || host == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(scheme).append("://").append(host);
        if (port > 0) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }

    private static byte[] multipartBody(String boundary, List<Map.Entry<String, String>> fields) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> field : fields) {
            sb.append("--").append(boundary).append("\r\n")
                    .append("Content-Disposition: form-data; name=\"").append(field.getKey()).append("\"\r\n\r\n")
                    .append(sanitizeFieldValue(field.getValue())).append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** multipart 文本值不允许出现换行（防注入额外表单字段） */
    private static String sanitizeFieldValue(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\r", "").replace("\n", " ");
    }

    private static Map.Entry<String, String> field(String name, String value) {
        return Map.entry(name, value == null ? "" : value);
    }

    // ------------------------------------------------------------------ 纯函数工具

    /**
     * 从用户输入里提取磁力链接：支持一条消息贴多个链接（空格 / 换行分隔），
     * 容忍中英文混排（如「下载 magnet:?xt=urn:btih:xxx，谢谢」），并按出现顺序去重。
     */
    public static List<String> extractMagnets(String text) {
        List<String> found = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return found;
        }
        Matcher matcher = MAGNET_PATTERN.matcher(text);
        while (matcher.find()) {
            String link = truncateToAllowedChars(matcher.group());
            if (link.length() > "magnet:?".length() && !found.contains(link)) {
                found.add(link);
            }
        }
        return found;
    }

    /** 截断到磁力链接合法字符集（丢掉尾随的中文标点、说明文字、右括号等） */
    private static String truncateToAllowedChars(String raw) {
        Matcher matcher = MAGNET_ALLOWED.matcher(raw);
        return matcher.lookingAt() ? matcher.group() : raw;
    }

    /** 服务地址归一化：去空白、去尾部斜杠 */
    public static String normalizeBaseUrl(String url) {
        String value = trim(url);
        if (value == null) {
            return "";
        }
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value.trim();
    }

    /** qB 状态码 → 中文文案（未知状态原样返回） */
    public static String stateLabel(String state) {
        if (state == null || state.isBlank()) {
            return "未知状态";
        }
        return STATE_LABELS.getOrDefault(state, state);
    }

    /** 字节数 → 可读体积（B / KB / MB / GB / TB） */
    public static String humanSize(long bytes) {
        if (bytes <= 0) {
            return "0 B";
        }
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        double value = bytes;
        int unit = 0;
        while (value >= 1024 && unit < units.length - 1) {
            value /= 1024;
            unit++;
        }
        return unit == 0 ? (long) value + " B"
                : String.format(java.util.Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    /** 速度 → 可读文案 */
    public static String humanSpeed(long bytesPerSecond) {
        return bytesPerSecond <= 0 ? "—" : humanSize(bytesPerSecond) + "/s";
    }

    /** 剩余时间 → 可读文案（qB 用 8640000 表示未知） */
    public static String humanEta(long seconds) {
        if (seconds <= 0 || seconds >= 8640000L) {
            return "—";
        }
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + " 小时 " + minutes + " 分";
        }
        if (minutes > 0) {
            return minutes + " 分 " + (seconds % 60) + " 秒";
        }
        return seconds + " 秒";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
