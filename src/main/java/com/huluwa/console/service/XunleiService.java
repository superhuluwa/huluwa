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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
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
 * <p>配置存放 sys_config。飞牛原生模式优先用账号密码获取并续取 fnos-token；
 * 也可手动填 Cookie 值。Docker 模式仍使用可选的 Authorization 头。</p>
 */
@Service
public class XunleiService {

    private static final Logger log = LoggerFactory.getLogger(XunleiService.class);

    /** 配置键：迅雷 CGI 地址，或可由端口推断 CGI 的服务根地址 */
    public static final String CFG_URL = "xl.url";
    /** Docker 版保存路径；飞牛原生版从迅雷读取默认下载目录 */
    public static final String CFG_SAVE_PATH = "xl.savepath";
    /** 配置键：原生模式手动 fnos-token Cookie 值 / Docker 模式 Authorization 头 */
    public static final String CFG_TOKEN = "xl.token";
    /** 飞牛账号登录配置；密码与生成的登录态只在服务端保存 */
    public static final String CFG_FNOS_USERNAME = "xl.fnos.username";
    public static final String CFG_FNOS_PASSWORD = "xl.fnos.password";
    public static final String CFG_FNOS_TOKEN = "xl.fnos.token";
    public static final String CFG_FNOS_DID = "xl.fnos.did";

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
    private final FnosLoginClient fnosLogin = new FnosLoginClient();
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

    /** 迅雷可用下载目录：建任务时路径和 ID 必须来自同一条记录。 */
    private record DownloadFolder(String id, String path) {
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
        data.put("fnosUsername", trim(configService.get(CFG_FNOS_USERNAME)));
        data.put("fnosPasswordSet", trim(configService.get(CFG_FNOS_PASSWORD)) != null);
        data.put("configured", settings.configured());
        return data;
    }

    /**
     * 保存配置。
     *
     * @param url      迅雷直连地址（必填，缺协议时自动补 http://）
     * @param savePath Docker 版保存路径；飞牛原生版忽略并使用迅雷默认目录
     * @param token    原生模式填 fnos-token 值，Docker 模式填 Authorization 头；留空沿用已保存值
     */
    public Map<String, Object> save(String url, String savePath, String token,
                                    String fnosUsername, String fnosPassword, String twofaCode) {
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
        boolean nativeFnos = isNativeFnos(base);
        String path = savePath == null ? "" : savePath.trim();
        if (!nativeFnos && path.isEmpty()) {
            throw new BusinessException(400, "请填写 Docker 版迅雷保存路径");
        }
        String user = trim(fnosUsername);
        String auth = token != null && !token.isBlank() ? token.trim() : settings().token();
        String generatedToken = null;
        String password = null;
        String did = null;
        if (nativeFnos && user != null) {
            password = configuredPassword(user, fnosPassword);
            did = configuredDid(user);
            generatedToken = fnosLogin.login(base, user, password, twofaCode, did);
        } else if (nativeFnos && (auth == null || auth.isBlank()
                || invalidNativeToken(auth))) {
            throw new BusinessException(400, "请填写飞牛账号密码，或提供 fnos-token Cookie 的值");
        }

        configService.put(CFG_URL, base);
        configService.put(CFG_SAVE_PATH, nativeFnos ? "" : path);
        // 留空表示不修改已保存的认证值（前端不会拿到原值，避免明文回传）
        if (token != null && !token.isBlank()) {
            configService.put(CFG_TOKEN, token.trim());
        }
        if (nativeFnos && user != null) {
            configService.put(CFG_FNOS_USERNAME, user);
            configService.put(CFG_FNOS_PASSWORD, password);
            configService.put(CFG_FNOS_DID, did);
            configService.put(CFG_FNOS_TOKEN, generatedToken);
        } else {
            configService.put(CFG_FNOS_USERNAME, "");
            configService.put(CFG_FNOS_PASSWORD, "");
            configService.put(CFG_FNOS_TOKEN, "");
            configService.put(CFG_FNOS_DID, "");
        }
        return describe();
    }

    /** 只更新 Docker 版保存路径（「路径」指令用）：不动连接配置 */
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
        return test(url, token, null, null, null);
    }

    public Map<String, Object> test(String url, String token, String fnosUsername,
                                    String fnosPassword, String twofaCode) {
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
            XlSession session;
            String user = trim(fnosUsername);
            if (isNativeFnos(base) && user != null) {
                String generated = fnosLogin.login(base, user,
                        configuredPassword(user, fnosPassword), twofaCode, configuredDid(user));
                session = openSessionUsingToken(new Settings(base, saved.savePath(), auth), generated);
            } else {
                boolean manualOnly = fnosUsername != null;
                session = openSession(new Settings(base, saved.savePath(), auth), manualOnly);
            }
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
        return add(magnets, null);
    }

    /** 单次保存目录：电影港显式指定统一的剧名目录，原生版同样按此目录创建任务。 */
    public List<TaskResult> add(List<String> magnets, String savePath) {
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
            results.add(submitMagnet(session, magnet,
                    savePath == null || savePath.isBlank() ? settings.savePath() : savePath.trim(),
                    savePath != null && !savePath.isBlank()));
        }
        return results;
    }

    /** 单条磁力：迅雷资源解析 → 解析显式或默认目录 → 建任务。 */
    private TaskResult submitMagnet(XlSession session, String magnet, String savePath, boolean explicitPath) {
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

        // 2) 原生 CGI 的保存目录要传迅雷返回的文件夹 ID 与路径
        String folderId = "";
        String folderPath = savePath;
        if (session.nativeFnos()) {
            try {
                DownloadFolder folder = explicitPath ? ensureNativeFolder(session, savePath) : findNativeDefaultFolder(session);
                folderId = folder.id();
                folderPath = folder.path();
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
        params.put("parent_folder_path", folderPath);
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

    /** 飞牛原生迅雷自动使用其默认下载目录；未标记默认时选首个非系统目录。 */
    private DownloadFolder findNativeDefaultFolder(XlSession session) throws IOException, InterruptedException {
        Response response = send(session, "/device/download_paths", null, INFO_TIMEOUT);
        if (response.status() != 200 || responseHasError(response.body())) {
            throw new BusinessException(400, "下载目录查询返回 HTTP " + response.status() + " " + abbreviate(response.body()));
        }
        try {
            JsonNode paths = objectMapper.readTree(response.body());
            if (paths.isObject()) {
                paths = paths.path("paths");
            }
            DownloadFolder first = null;
            DownloadFolder firstNonSystem = null;
            if (paths.isArray()) {
                for (JsonNode path : paths) {
                    String id = path.path("Id").asText("");
                    String realPath = path.path("RealPath").asText("");
                    if (id.isBlank() || realPath.isBlank()) {
                        continue;
                    }
                    DownloadFolder folder = new DownloadFolder(id, realPath);
                    if (isDefaultFolder(path)) {
                        return folder;
                    }
                    if (first == null) {
                        first = folder;
                    }
                    if (firstNonSystem == null && !realPath.startsWith("/var/apps/")) {
                        firstNonSystem = folder;
                    }
                }
            }
            if (firstNonSystem != null) {
                return firstNonSystem;
            }
            if (first != null) {
                return first;
            }
        } catch (Exception e) {
            throw new BusinessException(400, "迅雷返回的下载目录列表无法解析");
        }
        throw new BusinessException(400, "迅雷没有返回可用的默认下载目录，请先在迅雷 App 中设置下载目录");
    }

    /** 在迅雷可见的磁盘目录下逐级查找 / 创建目录，始终使用接口返回的文件夹 ID。 */
    private DownloadFolder ensureNativeFolder(XlSession session, String savePath)
            throws IOException, InterruptedException {
        String requested = normalizeFolderPath(savePath);
        DownloadFolder base = null;
        Response pathsResponse = send(session, "/device/download_paths", null, INFO_TIMEOUT);
        JsonNode paths = checkedJson(pathsResponse, "查询迅雷下载目录");
        if (paths.isObject()) {
            paths = paths.path("paths");
        }
        if (paths.isArray()) {
            for (JsonNode path : paths) {
                base = closerFolder(base, new DownloadFolder(path.path("Id").asText(""),
                        normalizeFolderPath(path.path("RealPath").asText(""))), requested);
            }
        }
        for (JsonNode folder : nativeFolders(session, "")) {
            base = closerFolder(base, folderOf(folder), requested);
        }
        if (base == null) {
            throw new BusinessException(400, "迅雷目录列表中未找到保存目录 " + requested
                    + "，请在飞牛迅雷中确认该目录已授权并挂载");
        }
        if (base.path().equals(requested)) {
            return base;
        }
        String relative = requested.substring(base.path().equals("/") ? 1 : base.path().length() + 1);
        for (String name : relative.split("/")) {
            String childPath = (base.path().equals("/") ? "" : base.path()) + "/" + name;
            DownloadFolder child = findChildFolder(session, base.id(), childPath);
            if (child == null) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("parent_id", base.id());
                body.put("space", session.deviceId());
                body.put("kind", "drive#folder");
                body.put("name", name);
                Response created = send(session, "/drive/v1/files", objectMapper.writeValueAsString(body), INFO_TIMEOUT);
                if (created.status() == 200 && !responseHasError(created.body())) {
                    child = folderOf(checkedJson(created, "创建迅雷目录").path("file"));
                }
                // 并发创建同一个剧名文件夹时，重新查询已创建的目录。
                if (child == null || child.id().isBlank() || !child.path().equals(childPath)) {
                    child = findChildFolder(session, base.id(), childPath);
                }
                if (child == null) {
                    throw new BusinessException(400, "迅雷未能创建保存目录 " + childPath);
                }
            }
            base = child;
        }
        return base;
    }

    private DownloadFolder findChildFolder(XlSession session, String parentId, String realPath)
            throws IOException, InterruptedException {
        for (JsonNode node : nativeFolders(session, parentId)) {
            DownloadFolder folder = folderOf(node);
            if (!folder.id().isBlank() && folder.path().equals(realPath)) {
                return folder;
            }
        }
        return null;
    }

    private List<JsonNode> nativeFolders(XlSession session, String parentId)
            throws IOException, InterruptedException {
        List<JsonNode> folders = new ArrayList<>();
        String page = "";
        Set<String> seenPages = new java.util.HashSet<>();
        do {
            String path = "/drive/v1/files?space=" + encode(session.deviceId())
                    + "&parent_id=" + encode(parentId) + "&limit=200&page_token=" + encode(page)
                    + "&filters=" + encode("{\"kind\":{\"eq\":\"drive#folder\"},\"trashed\":{\"eq\":false}}")
                    // 迅雷按重复参数解析数组；JSON 数组会被当成一个未知类别，导致磁盘目录缺失。
                    + "&with=withCategoryDiskMountPath&with=withCategoryDownloadPath"
                    + "&with=withCategoryHistoryDownloadPath";
            JsonNode data = checkedJson(send(session, path, null, INFO_TIMEOUT), "查询迅雷文件夹");
            for (JsonNode file : data.path("files")) {
                folders.add(file);
            }
            page = data.path("next_page_token").asText("");
        } while (!page.isBlank() && seenPages.add(page));
        return folders;
    }

    private static DownloadFolder folderOf(JsonNode file) {
        return new DownloadFolder(file.path("id").asText(""),
                normalizeFolderPath(file.path("params").path("RealPath").asText("")));
    }

    private static DownloadFolder closerFolder(DownloadFolder current, DownloadFolder candidate, String target) {
        String path = candidate.path();
        boolean contains = !path.isBlank() && (path.equals(target) || path.equals("/") || target.startsWith(path + "/"));
        return !candidate.id().isBlank() && contains && (current == null || path.length() > current.path().length())
                ? candidate : current;
    }

    private static String normalizeFolderPath(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private JsonNode checkedJson(Response response, String action) throws IOException {
        if (response.status() != 200 || responseHasError(response.body())) {
            throw new BusinessException(400, action + "失败：HTTP " + response.status() + " " + abbreviate(response.body()));
        }
        return objectMapper.readTree(response.body());
    }

    /** 大小与速度以字节计；字段未返回时保留 null，区别于实际的 0。 */
    public record TaskStatus(String phase, String message, Integer progress, Long totalBytes,
                             Long receivedBytes, Long downloadSpeed, Long speedupSpeed) {
        public TaskStatus(String phase, String message) {
            this(phase, message, null, null, null, null, null);
        }
    }

    /** 按迅雷任务 ID 查询，切换默认下载工具后仍跟踪已提交的迅雷任务。 */
    public TaskStatus task(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            return null;
        }
        return tasks(List.of(taskId)).get(taskId.trim());
    }

    /** 一次建立会话，按任务 ID 批量查询；分批和翻页兼容迅雷的列表上限。 */
    public Map<String, TaskStatus> tasks(List<String> taskIds) {
        List<String> ids = taskIds == null ? List.of() : taskIds.stream()
                .filter(id -> id != null && !id.isBlank()).map(String::trim).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        try {
            XlSession session = openSession(settings());
            Map<String, TaskStatus> statuses = new LinkedHashMap<>();
            for (int offset = 0; offset < ids.size(); offset += 100) {
                List<String> batch = ids.subList(offset, Math.min(offset + 100, ids.size()));
                String filters = objectMapper.writeValueAsString(Map.of("id", Map.of("in", String.join(",", batch))));
                String pageToken = "";
                Set<String> seenPages = new LinkedHashSet<>();
                do {
                    String path = "/drive/v1/tasks?space=" + encode(session.deviceId())
                            + "&filters=" + encode(filters) + "&limit=" + batch.size();
                    if (!pageToken.isBlank()) {
                        path += "&page_token=" + encode(pageToken);
                    }
                    JsonNode data = checkedJson(send(session, path, null, INFO_TIMEOUT), "查询迅雷任务");
                    for (JsonNode task : data.path("tasks")) {
                        String id = task.path("id").asText("");
                        if (batch.contains(id)) {
                            statuses.put(id, parseTaskStatus(task));
                        }
                    }
                    pageToken = data.path("next_page_token").asText("");
                    if (!pageToken.isBlank() && !seenPages.add(pageToken)) {
                        throw new BusinessException(400, "查询迅雷任务失败：列表分页游标重复");
                    }
                } while (!pageToken.isBlank());
            }
            return statuses;
        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(400, "查询迅雷任务已中断");
        } catch (Exception e) {
            throw new BusinessException(400, "查询迅雷任务失败：" + safeMessage(e));
        }
    }

    private static TaskStatus parseTaskStatus(JsonNode task) {
        String phase = task.path("phase").asText("");
        JsonNode params = task.path("params");
        Long total = nonNegativeLong(task.path("file_size"));
        Long received = nonNegativeLong(params.path("checked_size"));
        Long downloaded = nonNegativeLong(params.path("download_size"));
        double rawProgress = task.path("progress").asDouble(Double.NaN);
        Integer progress = Double.isFinite(rawProgress) && rawProgress >= 0
                ? (int) Math.round(Math.min(100, rawProgress)) : null;
        // 与迅雷前端保持一致：下载结束后的校验阶段用 checked_size 计算进度。
        if (received != null && total != null && total > 0
                && (progress == null || (downloaded != null && downloaded.equals(total)))) {
            progress = (int) Math.round(Math.min(100, received * 100.0 / total));
        }
        if ("PHASE_TYPE_COMPLETE".equals(phase)) {
            progress = 100;
            if (received == null) {
                received = total;
            }
        }
        if (received != null && total != null && total > 0) {
            received = Math.min(received, total);
        }
        boolean running = "PHASE_TYPE_RUNNING".equals(phase);
        return new TaskStatus(phase, task.path("message").asText(""), progress, total, received,
                running ? nonNegativeLong(params.path("speed")) : Long.valueOf(0),
                running ? nonNegativeLong(params.path("speedup_speed")) : Long.valueOf(0));
    }

    /** 迅雷的大数字通常是 JSON 字符串；用 long 解析，缺失或非法值不当作 0。 */
    private static Long nonNegativeLong(JsonNode node) {
        try {
            long value = Long.parseLong(node.asText("").trim());
            return value >= 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean isDefaultFolder(JsonNode path) {
        for (String key : List.of("IsDefault", "isDefault", "Default", "default")) {
            JsonNode flag = path.path(key);
            if (flag.asBoolean(false) || "1".equals(flag.asText())) {
                return true;
            }
        }
        return false;
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

    private String configuredPassword(String username, String supplied) {
        String password = supplied == null || supplied.isBlank() ? null : supplied;
        if (password == null && username.equals(trim(configService.get(CFG_FNOS_USERNAME)))) {
            password = configService.get(CFG_FNOS_PASSWORD);
        }
        if (password == null || password.isBlank()) {
            throw new BusinessException(400, "请填写飞牛登录密码");
        }
        return password;
    }

    private String configuredDid(String username) {
        String saved = trim(configService.get(CFG_FNOS_DID));
        return username.equals(trim(configService.get(CFG_FNOS_USERNAME))) && saved != null
                ? saved : UUID.randomUUID().toString();
    }

    private static boolean invalidNativeToken(String auth) {
        return auth.startsWith("Basic ") || auth.startsWith("Bearer ")
                || auth.startsWith("fnos-token=") || auth.startsWith("Cookie:");
    }

    // ------------------------------------------------------------------ 迅雷会话

    /** 建立迅雷会话：首页提 pan_auth，再按 CGI 类型获取设备ID */
    private XlSession openSession(Settings settings) throws IOException, InterruptedException {
        return openSession(settings, false);
    }

    private XlSession openSession(Settings settings, boolean manualOnly) throws IOException, InterruptedException {
        if (isNativeFnos(settings.url()) && !manualOnly) {
            String username = trim(configService.get(CFG_FNOS_USERNAME));
            String password = configService.get(CFG_FNOS_PASSWORD);
            if (username != null && password != null && !password.isBlank()) {
                String token = trim(configService.get(CFG_FNOS_TOKEN));
                if (token == null) {
                    token = refreshFnosToken(settings.url(), username, password);
                }
                try {
                    return openSessionUsingToken(settings, token);
                } catch (BusinessException e) {
                    if (!shouldRefreshFnos(e)) {
                        throw e;
                    }
                    // CGI 登录态失效时，用已配置账号重新登录一次。
                    String refreshed = refreshFnosToken(settings.url(), username, password);
                    return openSessionUsingToken(settings, refreshed);
                }
            }
        }
        return openSessionUsingToken(settings, settings.token());
    }

    private String refreshFnosToken(String url, String username, String password) {
        String token = fnosLogin.login(url, username, password, null, configuredDid(username));
        configService.put(CFG_FNOS_TOKEN, token);
        return token;
    }

    private static boolean shouldRefreshFnos(BusinessException e) {
        String message = e.getMessage();
        return message != null && (message.contains("fnos-token 已失效")
                || message.contains("HTTP 401") || message.contains("HTTP 403"));
    }

    private XlSession openSessionUsingToken(Settings settings, String auth) throws IOException, InterruptedException {
        boolean nativeFnos = isNativeFnos(settings.url());
        String cgi = cgiUrl(settings.url());
        if (nativeFnos && (auth == null || auth.isBlank())) {
            throw new BusinessException(400, "请填写飞牛账号密码，或配置 fnos-token Cookie 的值");
        }
        if (nativeFnos && invalidNativeToken(auth)) {
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
            throw new BusinessException(400, "飞牛 fnos-token 已失效");
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
