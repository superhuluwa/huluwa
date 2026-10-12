package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 迅雷远程下载对接：支持飞牛原生 CGI 与 Docker 版 CGI，接口为社区逆向口径。
 *
 * <ul>
 *   <li>会话：GET CGI 首页提 pan_auth（uiauth 函数，兜底 JWT 形态）；
 *       飞牛原生通过 user#runner 任务获取设备ID，Docker 版通过 device/info/watch 获取。
 *       每次提交前重建会话、不做缓存（与参考脚本「每轮一次」的口径一致，
 *       避免拿失效 pan_auth 反复失败）；</li>
 *   <li>提交：POST drive/v1/resource/list 服务端解析磁力文件清单
 *       → POST drive/v1/task 建任务（sub_file_index=--1 下载全部文件）；</li>
 *   <li>建任务同时检查 HTTP 状态码与响应体错误字段。</li>
 * </ul>
 *
 * <p>配置存放 sys_config（xl.url / xl.savepath / xl.token）。原生模式的 xl.token 是
 * fnos-token Cookie 值，Docker 模式是可选的 Authorization 头；只写不读。</p>
 */
@Service
public class XunleiService {

    private static final Logger log = LoggerFactory.getLogger(XunleiService.class);

    /** 配置键：迅雷 CGI 地址，或可由端口推断 CGI 的服务根地址 */
    public static final String CFG_URL = "xl.url";
    /** 配置键：保存路径（迅雷 App 新建任务页里选择的完整目录路径） */
    public static final String CFG_SAVE_PATH = "xl.savepath";
    /** 配置键：原生模式 fnos-token Cookie 值 / Docker 模式 Authorization 头 */
    public static final String CFG_TOKEN = "xl.token";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(8);
    private static final Duration HOME_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration INFO_TIMEOUT = Duration.ofSeconds(20);
    /** 磁力解析超时：迅雷服务端要回源拿元数据，死种会一直挂到超时，给足窗口 */
    private static final Duration LIST_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration TASK_TIMEOUT = Duration.ofSeconds(30);

    /** 与参考脚本同一串 UA（首页按桌面浏览器响应） */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/126.0.0.0 Safari/537.36";

    /** 首页 pan_auth 的首选取法：uiauth 函数返回值（与官方前端一致） */
    private static final Pattern PAN_AUTH_UIAUTH =
            Pattern.compile("function\\s+uiauth\\(value\\)\\s*\\{\\s*return\\s*\"([^\"]+)\"");
    /** 兜底取法：页面里的 JWT 形态串（三段 base64url，中划线下划线允许） */
    private static final Pattern PAN_AUTH_JWT =
            Pattern.compile("([A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+)");

    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public XunleiService(ConfigService configService) {
        this.configService = configService;
    }

    /** 迅雷的连接配置（认证值只写不读，tokenSet 表达是否已设置） */
    public record Settings(String url, String savePath, String token) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }

    /** 单条磁力的提交结果（失败时 message 为可直接展示给用户的中文说明） */
    public record TaskResult(boolean ok, String message, String name, long size, int fileCount, String taskId) {
    }

    /** 迅雷会话：CGI 基址 + pan_auth + 设备ID + 对应模式的认证值 */
    private record XlSession(String cgi, String panToken, String deviceId, String auth, boolean nativeFnos) {
    }

    /** 迅雷接口响应（状态码 + 响应体） */
    private record Response(int status, String body) {
    }

    // ------------------------------------------------------------------ 配置读写

    public Settings settings() {
        return new Settings(
                trim(configService.get(CFG_URL)),
                trim(configService.get(CFG_SAVE_PATH)),
                trim(configService.get(CFG_TOKEN)));
    }

    /** 下发前端的配置视图：不回传认证值明文，只回传是否已设置 */
    public Map<String, Object> describe() {
        Settings settings = settings();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", settings.url());
        data.put("savePath", settings.savePath());
        data.put("tokenSet", settings.token() != null && !settings.token().isBlank());
        data.put("configured", settings.configured());
        return data;
    }

    /**
     * 保存配置。
     *
     * @param url      迅雷直连地址（必填，缺协议时自动补 http://）
     * @param savePath 保存路径（必填，建任务时作为 parent_folder_path 透传给迅雷）
     * @param token    原生模式填 fnos-token 值，Docker 模式填 Authorization 头；留空沿用已保存值
     */
    public Map<String, Object> save(String url, String savePath, String token) {
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            throw new BusinessException(400, "请填写迅雷直连地址，例如 http://192.168.3.202:5666/cgi/ThirdParty/xunlei/index.cgi");
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        try {
            URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "迅雷直连地址格式不正确：" + url);
        }
        String path = savePath == null ? "" : savePath.trim();
        if (path.isEmpty()) {
            throw new BusinessException(400, "请填写迅雷保存路径（迅雷 App 新建任务页里选择的目录，如 /downloads/）");
        }
        String auth = token != null && !token.isBlank() ? token.trim() : settings().token();
        if (isNativeFnos(base) && (auth == null || auth.isBlank()
                || auth.startsWith("Basic ") || auth.startsWith("Bearer ")
                || auth.startsWith("fnos-token=") || auth.startsWith("Cookie:"))) {
            throw new BusinessException(400, "飞牛原生迅雷请填写 fnos-token Cookie 的值（不含 fnos-token= 前缀）");
        }

        configService.put(CFG_URL, base);
        configService.put(CFG_SAVE_PATH, path);
        // 留空表示不修改已保存的认证值（前端不会拿到原值，避免明文回传）
        if (token != null && !token.isBlank()) {
            configService.put(CFG_TOKEN, token.trim());
        }
        return describe();
    }

    /** 只更新保存路径（「路径」指令用）：不动连接配置 */
    public void saveSavePath(String savePath) {
        String path = savePath == null ? "" : savePath.trim();
        if (path.isEmpty()) {
            throw new BusinessException(400, "迅雷必须指定保存路径，不能置空");
        }
        configService.put(CFG_SAVE_PATH, path);
    }

    // ------------------------------------------------------------------ 连接测试

    /** 测试连接：首页提 pan_auth + 取设备ID，成功即认为迅雷在线（token 留空复用已保存的值） */
    public Map<String, Object> test(String url, String token) {
        Map<String, Object> result = new LinkedHashMap<>();
        Settings saved = settings();
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            base = saved.url() == null ? "" : saved.url();
        }
        if (base.isBlank()) {
            result.put("success", false);
            result.put("message", "请先填写迅雷直连地址");
            return result;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        String auth = token != null && !token.isBlank() ? token.trim() : saved.token();
        try {
            XlSession session = openSession(new Settings(base, saved.savePath(), auth));
            String deviceId = session.deviceId();
            result.put("success", true);
            result.put("message", "连接成功：迅雷在线（设备ID "
                    + deviceId.substring(0, Math.min(8, deviceId.length())) + "…）");
        } catch (BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败：" + safeMessage(e));
        }
        return result;
    }

    // ------------------------------------------------------------------ 提交磁力

    /**
     * 提交磁力任务到迅雷：逐条「服务端解析文件清单 → 建任务（HTTP 200 算成功）」。
     *
     * @param magnets 磁力链接（每条独立提交、独立成败）
     * @return 与入参同序的逐条结果
     * @throws BusinessException 未配置 / 无法建立迅雷会话
     */
    public List<TaskResult> add(List<String> magnets) {
        Settings settings = settings();
        if (!settings.configured()) {
            throw new BusinessException(400, "尚未配置迅雷直连地址");
        }
        if (magnets == null || magnets.isEmpty()) {
            throw new BusinessException(400, "请提供磁力链接");
        }
        XlSession session;
        try {
            session = openSession(settings);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "连接迅雷失败：" + safeMessage(e));
        }

        List<TaskResult> results = new ArrayList<>();
        for (String magnet : magnets) {
            results.add(submitMagnet(session, magnet, settings.savePath()));
        }
        return results;
    }

    /** 单条磁力：迅雷资源解析 → 建任务（全部文件，保存到配置的路径） */
    private TaskResult submitMagnet(XlSession session, String magnet, String savePath) {
        // 1) 服务端解析磁力文件清单（取文件名 / 大小 / 数量供建任务）
        Response list;
        try {
            list = send(session, "/drive/v1/resource/list",
                    "{\"page_size\":2000,\"urls\":" + objectMapper.writeValueAsString(magnet) + "}", LIST_TIMEOUT);
        } catch (Exception e) {
            return new TaskResult(false, "迅雷解析磁力失败：" + safeMessage(e), null, 0, 0, null);
        }
        if (list.status() != 200 || responseHasError(list.body())) {
            String detail = list.body().contains("unauthenticated")
                    ? "迅雷账号登录态已失效，请在飞牛迅雷 App 重新登录或绑定账号后重试；若测试连接也失败，请更新 fnos-token。"
                    : "HTTP " + list.status() + " " + abbreviate(list.body());
            return new TaskResult(false, "迅雷解析磁力失败：" + detail,
                    null, 0, 0, null);
        }
        List<JsonNode> leaves = extractLeaves(list.body());
        if (leaves.isEmpty()) {
            return new TaskResult(false, "迅雷未能解析出该磁力的文件清单（资源可能已失效）", null, 0, 0, null);
        }
        long totalSize = 0;
        for (JsonNode leaf : leaves) {
            totalSize += leaf.path("file_size").asLong(0);
        }
        JsonNode first = leaves.get(0);
        String firstName = first.path("name").asText("");
        String firstMime = first.path("meta").path("mime_type").asText("");
        String firstId = first.path("id").asText("");
        String taskName = firstName;
        if (session.nativeFnos()) {
            try {
                String rootName = objectMapper.readTree(list.body())
                        .path("list").path("resources").path(0).path("name").asText("");
                if (!rootName.isBlank()) {
                    taskName = rootName;
                }
            } catch (Exception e) {
                // 保留首个文件名作为兜底
            }
        }

        // 2) 原生 CGI 的保存目录要传文件夹 ID，不能仅传路径
        String folderId = "";
        if (session.nativeFnos()) {
            try {
                folderId = findNativeFolderId(session, savePath);
            } catch (Exception e) {
                return new TaskResult(false, "获取迅雷保存目录失败：" + safeMessage(e),
                        taskName, totalSize, leaves.size(), null);
            }
        }

        // 3) 建下载任务（全部文件）
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("target", session.deviceId());
        params.put("url", magnet);
        params.put("total_file_count", String.valueOf(leaves.size()));
        params.put("parent_folder_id", folderId);
        params.put("parent_folder_path", savePath);
        params.put("sub_file_index", session.nativeFnos() ? "--1," : "--1");
        params.put("mime_type", session.nativeFnos() ? "" : firstMime);
        params.put("file_id", session.nativeFnos() ? "" : firstId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "user#download-url");
        payload.put("name", taskName);
        payload.put("file_name", taskName);
        payload.put("file_size", String.valueOf(totalSize));
        payload.put("space", session.deviceId());
        payload.put("params", params);

        Response task;
        String taskBody;
        try {
            taskBody = objectMapper.writeValueAsString(payload);
            task = send(session, "/drive/v1/task", taskBody, TASK_TIMEOUT);
        } catch (Exception e) {
            return new TaskResult(false, "迅雷建任务失败：" + safeMessage(e), taskName, totalSize, leaves.size(), null);
        }
        if (task.status() != 200 || responseHasError(task.body())) {
            return new TaskResult(false, "迅雷建任务失败：HTTP " + task.status() + " " + abbreviate(task.body()),
                    taskName, totalSize, leaves.size(), null);
        }
        String taskId = readTaskId(task.body());
        log.info("迅雷任务已创建（磁力 {}…，{} 个文件，任务ID {}）",
                magnet.substring(0, Math.min(24, magnet.length())), leaves.size(),
                taskId == null ? "未返回" : taskId);
        return new TaskResult(true, "已创建迅雷任务", taskName, totalSize, leaves.size(), taskId);
    }

    /** 建任务响应里的任务ID（task.id 或顶层 id，取不到返回 null，不影响提交结果） */
    private String readTaskId(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode idNode = root.path("task").path("id");
            if (!idNode.isValueNode() || idNode.isNull()) {
                idNode = root.path("id");
            }
            String value = idNode.isValueNode() && !idNode.isNull() ? idNode.asText("") : "";
            return value.isBlank() ? null : value;
        } catch (Exception e) {
            log.debug("迅雷建任务响应解析任务ID异常（不影响提交结果）：{}", e.getMessage());
            return null;
        }
    }

    /** 飞牛原生迅雷用下载目录 ID 创建任务；路径取自迅雷自身可选的下载目录。 */
    private String findNativeFolderId(XlSession session, String savePath) throws IOException, InterruptedException {
        Response response = send(session, "/device/download_paths", null, INFO_TIMEOUT);
        if (response.status() != 200) {
            throw new BusinessException(400, "下载目录查询返回 HTTP " + response.status() + " " + abbreviate(response.body()));
        }
        try {
            JsonNode paths = objectMapper.readTree(response.body());
            if (paths.isObject()) {
                paths = paths.path("paths");
            }
            if (paths.isArray()) {
                for (JsonNode path : paths) {
                    if (sameFolder(savePath, path.path("RealPath").asText(""))) {
                        String id = path.path("Id").asText("");
                        if (!id.isBlank()) {
                            return id;
                        }
                    }
                }
            }
        } catch (Exception e) {
            throw new BusinessException(400, "迅雷返回的下载目录列表无法解析");
        }
        throw new BusinessException(400, "保存路径不在迅雷可选下载目录中：" + savePath
                + "。请从迅雷新建任务页复制完整目录路径");
    }

    private static boolean sameFolder(String left, String right) {
        return stripTrailingSlash(left).equals(stripTrailingSlash(right));
    }

    private static String stripTrailingSlash(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private boolean responseHasError(String body) {
        try {
            JsonNode root = objectMapper.readTree(body);
            return (root.hasNonNull("error") && !root.path("error").asText("").isBlank())
                    || (root.has("HttpStatus") && root.path("HttpStatus").asInt(0) != 0);
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 迅雷会话

    /** 建立迅雷会话：首页提 pan_auth，再按 CGI 类型获取设备ID */
    private XlSession openSession(Settings settings) throws IOException, InterruptedException {
        boolean nativeFnos = isNativeFnos(settings.url());
        String cgi = cgiUrl(settings.url());
        String auth = settings.token();
        if (nativeFnos && (auth == null || auth.isBlank())) {
            throw new BusinessException(400, "飞牛原生迅雷需要 fnos-token：在已登录飞牛的浏览器 Cookie 中复制 fnos-token 的值，填入迅雷设置的认证栏");
        }
        if (nativeFnos && (auth.startsWith("Basic ") || auth.startsWith("Bearer ")
                || auth.startsWith("fnos-token=") || auth.startsWith("Cookie:"))) {
            throw new BusinessException(400, "飞牛原生迅雷的认证栏应填写 fnos-token Cookie 的值，不是 Authorization 头");
        }

        HttpRequest.Builder homeBuilder = HttpRequest.newBuilder(URI.create(cgi + "/"))
                .timeout(HOME_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", USER_AGENT)
                .GET();
        if (nativeFnos) {
            homeBuilder.header("Cookie", "fnos-token=" + auth);
        } else if (auth != null && !auth.isBlank()) {
            homeBuilder.header("Authorization", auth);
        }
        HttpResponse<String> home = http.send(homeBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (nativeFnos && home.body().contains("invalid token")) {
            throw new BusinessException(400, "飞牛 fnos-token 已失效，请重新登录飞牛并更新迅雷设置中的 Cookie 值");
        }
        String panToken = extractPanAuth(home.body());
        if (panToken == null) {
            throw new BusinessException(400, "迅雷首页未解析到 pan_auth（HTTP " + home.statusCode()
                    + "）：请核对直连地址是否正确、迅雷是否已完成账号绑定");
        }

        XlSession initial = new XlSession(cgi, panToken, "", auth, nativeFnos);
        Response info = nativeFnos
                ? send(initial, "/drive/v1/tasks?type=user%23runner", null, INFO_TIMEOUT)
                : send(initial, "/device/info/watch", "{}", INFO_TIMEOUT);
        if (info.status() != 200 || responseHasError(info.body())) {
            throw new BusinessException(400, "迅雷取设备ID失败：HTTP " + info.status() + " " + abbreviate(info.body()));
        }
        String target = "";
        try {
            JsonNode root = objectMapper.readTree(info.body());
            target = nativeFnos
                    ? root.path("tasks").path(0).path("params").path("target").asText("")
                    : root.path("target").asText("");
        } catch (Exception e) {
            // 按 target 缺失处理
        }
        int idx = target.indexOf('#');
        if (idx < 0 || idx + 1 >= target.length()) {
            throw new BusinessException(400, "迅雷设备信息响应缺少设备ID：" + abbreviate(info.body()));
        }
        String deviceId = target;
        log.info("迅雷会话就绪（pan_auth 已取，设备ID {}…）", deviceId.substring(0, Math.min(8, deviceId.length())));
        return new XlSession(cgi, panToken, deviceId, auth, nativeFnos);
    }

    /** 首页提 pan_auth：先按 uiauth 函数取，取不到再兜底 JWT 形态（与参考脚本同口径） */
    static String extractPanAuth(String html) {
        if (html == null || html.isEmpty()) {
            return null;
        }
        Matcher matcher = PAN_AUTH_UIAUTH.matcher(html);
        if (matcher.find()) {
            return matcher.group(1);
        }
        matcher = PAN_AUTH_JWT.matcher(html);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 迅雷接口统一发送：原生 CGI 使用 fnos-token Cookie，pan_auth 走查询参数与请求头。 */
    private Response send(XlSession session, String path, String json, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                        session.cgi() + path + (path.contains("?") ? "&" : "?")
                                + "pan_auth=" + encode(session.panToken()) + "&device_space="))
                .timeout(timeout)
                .header("Accept", "application/json, text/plain, */*")
                .header("pan-auth", session.panToken())
                .header("device-space", "")
                .header("Referer", session.cgi() + "/")
                .header("User-Agent", USER_AGENT);
        if (session.nativeFnos()) {
            builder.header("Cookie", "fnos-token=" + session.auth());
        } else if (session.auth() != null && !session.auth().isBlank()) {
            builder.header("Authorization", session.auth());
        }
        if (json != null) {
            builder.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        } else {
            builder.GET();
        }
        HttpResponse<String> response = http.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body() == null ? "" : response.body());
    }

    /** 递归抽取迅雷 resource/list 响应里的文件叶子（is_dir 的走 dir.resources 下钻） */
    List<JsonNode> extractLeaves(String body) {
        List<JsonNode> leaves = new ArrayList<>();
        try {
            JsonNode resources = objectMapper.readTree(body).path("list").path("resources");
            walk(resources, leaves);
        } catch (Exception e) {
            // 解析异常按无清单处理，由调用方记失败
        }
        return leaves;
    }

    private static void walk(JsonNode token, List<JsonNode> leaves) {
        if (token == null || !token.isArray()) {
            return;
        }
        for (JsonNode item : token) {
            JsonNode children = item.path("is_dir").asBoolean(false)
                    ? item.path("dir").path("resources")
                    : null;
            if (children != null && children.isArray()) {
                walk(children, leaves);
            } else {
                leaves.add(item);
            }
        }
    }

    // ------------------------------------------------------------------ 纯函数工具

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

    /** 完整 CGI 地址优先；服务根地址按飞牛 5666 / Docker 端口分别补全。 */
    static String cgiUrl(String url) {
        String base = normalizeBaseUrl(url);
        if (base.endsWith("/cgi/ThirdParty/xunlei/index.cgi")
                || base.endsWith("/webman/3rdparty/pan-xunlei-com/index.cgi")) {
            return base;
        }
        return base + (isNativeFnos(base)
                ? "/cgi/ThirdParty/xunlei/index.cgi"
                : "/webman/3rdparty/pan-xunlei-com/index.cgi");
    }

    static boolean isNativeFnos(String url) {
        String base = normalizeBaseUrl(url);
        if (base.contains("/cgi/ThirdParty/xunlei/index.cgi")) {
            return true;
        }
        if (base.contains("/webman/3rdparty/pan-xunlei-com/index.cgi")) {
            return false;
        }
        try {
            URI uri = URI.create(base);
            return uri.getPort() == 5666;
        } catch (Exception e) {
            return false;
        }
    }

    /** 查询参数编码（pan_auth 里的 JWT 串含点与下划线，整体编码后拼进 URL） */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 日志与错误提示里截断超长响应体，避免刷屏 */
    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 200 ? oneLine.substring(0, 200) + "…" : oneLine;
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
