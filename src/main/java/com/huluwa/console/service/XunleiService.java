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
 * 迅雷远程下载对接（对齐 quantum 参考工程的 qBittorrentXunleiTransfer.cs）：
 * 飞牛应用中心的迅雷 = 迅雷 NAS 版容器，接口为社区逆向口径，迅雷升级可能变动。
 *
 * <ul>
 *   <li>会话：GET {直连地址}/webman/3rdparty/pan-xunlei-com/index.cgi/ 首页提 pan_auth
 *       （uiauth 函数，兜底 JWT 形态）→ POST device/info/watch 取设备ID。
 *       每次提交前重建会话、不做缓存（与参考脚本「每轮一次」的口径一致，
 *       避免拿失效 pan_auth 反复失败）；</li>
 *   <li>提交：POST drive/v1/resource/list 服务端解析磁力文件清单
 *       → POST drive/v1/task 建任务（sub_file_index=--1 下载全部文件，
 *       parent_folder_path=配置的保存路径，与参考脚本的载荷字段一致）；</li>
 *   <li>成败按 HTTP 状态码判定：建任务 HTTP 200 即视为成功，其余把响应体带给用户。</li>
 * </ul>
 *
 * <p>配置存放 sys_config（xl.url / xl.savepath / xl.token），由「迅雷下载」会话首次使用时引导填写；
 * Authorization 认证头（迅雷前端加了 Basic 认证时用，飞牛直连端口一般不需要）只写不读。</p>
 */
@Service
public class XunleiService {

    private static final Logger log = LoggerFactory.getLogger(XunleiService.class);

    /** 配置键：迅雷直连地址（飞牛「应用中心 → 迅雷」详情页的访问端口） */
    public static final String CFG_URL = "xl.url";
    /** 配置键：保存路径（迅雷 App 新建任务页里选择的目录，如 /downloads/） */
    public static final String CFG_SAVE_PATH = "xl.savepath";
    /** 配置键：Authorization 认证头（可选，只写不读；如 Basic xxx） */
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
            Pattern.compile("function uiauth\\(value\\)\\{ return \"([^\"]+)\" \\}");
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

    /** 迅雷的连接配置（Authorization 头只写不读，tokenSet 表达是否已设置） */
    public record Settings(String url, String savePath, String token) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }

    /** 单条磁力的提交结果（失败时 message 为可直接展示给用户的中文说明） */
    public record TaskResult(boolean ok, String message, String name, long size, int fileCount, String taskId) {
    }

    /** 迅雷会话：cgi 基址 + pan_auth + 设备ID + 可选 Authorization 认证头 */
    private record XlSession(String cgi, String panToken, String deviceId, String auth) {
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

    /** 下发前端的配置视图：绝不回传 Authorization 头明文，只回传是否已设置 */
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
     * @param token    Authorization 认证头；为 null 或空串表示「保持已保存的值不变」
     */
    public Map<String, Object> save(String url, String savePath, String token) {
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            throw new BusinessException(400, "请填写迅雷直连地址，例如 http://192.168.3.202:2345");
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

        configService.put(CFG_URL, base);
        configService.put(CFG_SAVE_PATH, path);
        // 留空表示不修改已保存的认证头（前端不会拿到原值，避免明文回传）
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
        if (list.status() != 200) {
            return new TaskResult(false, "迅雷解析磁力失败：HTTP " + list.status() + " " + abbreviate(list.body()),
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

        // 2) 建下载任务（全部文件；字段与参考脚本一致）
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("target", "device_id#" + session.deviceId());
        params.put("url", magnet);
        params.put("total_file_count", String.valueOf(leaves.size()));
        params.put("parent_folder_id", "");
        params.put("parent_folder_path", savePath);
        params.put("sub_file_index", "--1");
        params.put("mime_type", firstMime);
        params.put("file_id", firstId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "user#download-url");
        payload.put("name", firstName);
        payload.put("file_name", firstName);
        payload.put("file_size", String.valueOf(totalSize));
        payload.put("space", "device_id#" + session.deviceId());
        payload.put("params", params);

        Response task;
        String taskBody;
        try {
            taskBody = objectMapper.writeValueAsString(payload);
            task = send(session, "/drive/v1/task", taskBody, TASK_TIMEOUT);
        } catch (Exception e) {
            return new TaskResult(false, "迅雷建任务失败：" + safeMessage(e), firstName, totalSize, leaves.size(), null);
        }
        if (task.status() != 200) {
            return new TaskResult(false, "迅雷建任务失败：HTTP " + task.status() + " " + abbreviate(task.body()),
                    firstName, totalSize, leaves.size(), null);
        }
        String taskId = readTaskId(task.body());
        log.info("迅雷任务已创建（磁力 {}…，{} 个文件，任务ID {}）",
                magnet.substring(0, Math.min(24, magnet.length())), leaves.size(),
                taskId == null ? "未返回" : taskId);
        return new TaskResult(true, "已创建迅雷任务", firstName, totalSize, leaves.size(), taskId);
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

    // ------------------------------------------------------------------ 迅雷会话

    /** 建立迅雷会话：首页提 pan_auth（uiauth 函数，兜底 JWT 形态）→ device/info/watch 取设备ID */
    private XlSession openSession(Settings settings) throws IOException, InterruptedException {
        String cgi = settings.url() + "/webman/3rdparty/pan-xunlei-com/index.cgi";
        String auth = settings.token();

        HttpRequest.Builder homeBuilder = HttpRequest.newBuilder(URI.create(cgi + "/"))
                .timeout(HOME_TIMEOUT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("User-Agent", USER_AGENT)
                .GET();
        if (auth != null && !auth.isBlank()) {
            homeBuilder.header("Authorization", auth);
        }
        HttpResponse<String> home = http.send(homeBuilder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String panToken = extractPanAuth(home.body());
        if (panToken == null) {
            throw new BusinessException(400, "迅雷首页未解析到 pan_auth（HTTP " + home.statusCode()
                    + "）：请核对直连地址是否正确、迅雷是否已完成账号绑定");
        }

        Response info = send(new XlSession(cgi, panToken, "", auth), "/device/info/watch", "{}", INFO_TIMEOUT);
        if (info.status() != 200) {
            throw new BusinessException(400, "迅雷取设备ID失败：HTTP " + info.status() + " " + abbreviate(info.body()));
        }
        String target = "";
        try {
            target = objectMapper.readTree(info.body()).path("target").asText("");
        } catch (Exception e) {
            // 按 target 缺失处理
        }
        int idx = target.indexOf('#');
        if (idx < 0 || idx + 1 >= target.length()) {
            throw new BusinessException(400, "迅雷设备信息响应缺少设备ID：" + abbreviate(info.body()));
        }
        String deviceId = target.substring(idx + 1);
        log.info("迅雷会话就绪（pan_auth 已取，设备ID {}…）", deviceId.substring(0, Math.min(8, deviceId.length())));
        return new XlSession(cgi, panToken, deviceId, auth);
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

    /** 迅雷接口统一发送：pan_auth 走查询参数与请求头双通道（与官方前端一致） */
    private Response send(XlSession session, String path, String json, Duration timeout)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(
                        session.cgi() + path + "?pan_auth=" + encode(session.panToken()) + "&device_space="))
                .timeout(timeout)
                .header("Accept", "application/json, text/plain, */*")
                .header("pan-auth", session.panToken())
                .header("device-space", "")
                .header("User-Agent", USER_AGENT);
        if (session.auth() != null && !session.auth().isBlank()) {
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
