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
 * 视频解析服务对接（对齐 quantum 参考工程的 scripts/quantum-video-parse.cs）：
 * 把抖音、快手等 App 的分享口令解析成标题 / 作者 / 视频直链 / 封面 / 图集，供「视频解析」会话回复媒体气泡。
 *
 * <ul>
 *   <li>登录：POST {服务地址}/api/login（JSON：username / password / remember=true），
 *       从 Set-Cookie 收集会话 Cookie；与参考脚本一致，每次解析前都先登录一次（不做会话缓存，
 *       避免服务端重启后拿着失效 Cookie 反复失败）；账号留空时跳过登录、按匿名请求
 *       （服务端可能用 IP 白名单放行）；</li>
 *   <li>解析：GET {服务地址}/video/share/url/parse?url={分享链接}（带登录 Cookie）；
 *       响应 {code, msg, data}，data 内含 title / cover_url / video_url / music_url / author / images；</li>
 *   <li>请求头与参考脚本一致：Accept / Accept-Language / Origin / Referer / User-Agent；</li>
 *   <li>超时：解析是站点侧再抓一次作品页，耗时较长，与参考脚本的 100s 保持一致。</li>
 * </ul>
 *
 * <p>配置存放 sys_config（video.url / video.username / video.password），由「视频解析」会话首次使用时引导填写；
 * 密码只写不读，接口只回传是否已设置。</p>
 */
@Service
public class VideoParseService {

    private static final Logger log = LoggerFactory.getLogger(VideoParseService.class);

    /** 配置键：视频解析服务地址 */
    public static final String CFG_URL = "video.url";
    /** 配置键：登录用户名（可留空，表示匿名访问） */
    public static final String CFG_USERNAME = "video.username";
    /** 配置键：登录密码（只写不读；用户名为空时忽略） */
    public static final String CFG_PASSWORD = "video.password";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration LOGIN_TIMEOUT = Duration.ofSeconds(20);
    /** 解析超时：与参考脚本 QuantumTaskContext 的 100s 请求超时一致 */
    private static final Duration PARSE_TIMEOUT = Duration.ofSeconds(100);

    /** 与参考脚本同一串 UA（解析服务侧按 UA 放行桌面请求） */
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/146.0.0.0 Safari/537.36 Edg/146.0.0.0";

    /**
     * 分享口令里的链接。抖音/快手口令形如
     * 「7.68 复制打开抖音，看看【xxx】的作品 https://v.douyin.com/xxxx/ 复制此链接，打开抖音搜索」，
     * 说明文字可能紧贴链接两端；这里按 RFC 3986 合法字符集匹配，中文自然在链接处截断。
     */
    private static final Pattern SHARE_URL = Pattern.compile(
            "https?://[A-Za-z0-9\\-._~:/?#\\[\\]@!$&'()*+,;=%]+", Pattern.CASE_INSENSITIVE);

    /** 链接尾部常见的收尾标点（中英文句读），提取后需要裁掉 */
    private static final String TRAILING_PUNCTUATION = ".,;:!?、，。；：！？）】》”’";

    private final ConfigService configService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public VideoParseService(ConfigService configService) {
        this.configService = configService;
    }

    /** 解析服务的连接配置（不含密码明文读取，密码用 hasPassword 表达） */
    public record Settings(String url, String username, String password) {

        public boolean configured() {
            return url != null && !url.isBlank();
        }
    }

    /** 解析结果里的图集条目：live_photo_url 非空表示实况照片（按视频气泡推送） */
    public record ImageItem(String url, String livePhotoUrl) {
    }

    /** 一次解析的作品信息（字段对应解析服务响应里的下划线命名） */
    public record VideoItem(String title, String author, String coverUrl, String videoUrl,
                            String musicUrl, String proxyUrl, List<ImageItem> images) {
    }

    // ------------------------------------------------------------------ 配置读写

    public Settings settings() {
        return new Settings(
                trim(configService.get(CFG_URL)),
                trim(configService.get(CFG_USERNAME)),
                trim(configService.get(CFG_PASSWORD)));
    }

    /** 下发前端的配置视图：绝不回传密码明文，只回传是否已设置 */
    public Map<String, Object> describe() {
        Settings settings = settings();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("url", settings.url());
        data.put("username", settings.username());
        data.put("passwordSet", settings.password() != null && !settings.password().isBlank());
        data.put("configured", settings.configured());
        return data;
    }

    /**
     * 保存配置。
     *
     * @param url      服务地址（必填，缺协议时自动补 http://）
     * @param username 登录用户名（可留空，表示匿名访问）
     * @param password 登录密码；为 null 或空串表示「保持已保存的密码不变」
     */
    public Map<String, Object> save(String url, String username, String password) {
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            throw new BusinessException(400, "请填写视频解析服务地址，例如 http://192.168.3.202:8080");
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        try {
            URI.create(base);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(400, "视频解析服务地址格式不正确：" + url);
        }

        configService.put(CFG_URL, base);
        configService.put(CFG_USERNAME, trim(username) == null ? "" : trim(username));
        // 留空表示不修改已保存的密码（前端不会拿到原密码，避免明文回传）
        if (password != null && !password.isBlank()) {
            configService.put(CFG_PASSWORD, password);
        }
        if (trim(username) == null) {
            // 改为匿名访问：旧密码已无意义，直接清掉
            configService.put(CFG_PASSWORD, "");
        }
        return describe();
    }

    // ------------------------------------------------------------------ 解析

    /**
     * 解析一条分享口令 / 分享链接。
     *
     * @param text 用户输入（可直接是口令原文，内部自动提取其中的链接）
     * @return 作品信息（含视频直链、封面与图集）
     * @throws BusinessException 未配置服务、口令里没有链接、服务返回失败或网络异常
     */
    public VideoItem parse(String text) {
        Settings settings = settings();
        if (!settings.configured()) {
            throw new BusinessException(400, "尚未配置视频解析服务地址");
        }
        String shareUrl = extractShareUrl(text);
        if (shareUrl == null) {
            throw new BusinessException(400, "没有识别到分享链接，请把带链接的口令整段发给我");
        }

        String cookie;
        try {
            cookie = login(settings);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "连接视频解析服务失败：" + safeMessage(e));
        }

        String body;
        try {
            body = requestJson(settings.url() + "/video/share/url/parse?url=" + encode(shareUrl),
                    settings.url() + "/", cookie, PARSE_TIMEOUT);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "调用视频解析服务失败：" + safeMessage(e));
        }
        log.info("视频解析响应：{}", abbreviate(body));
        return readResult(body);
    }

    /** 测试连接：用表单里的最新值登录一次（字段留空则回落到已保存的配置，密码留空复用已保存的密码） */
    public Map<String, Object> test(String url, String username, String password) {
        Map<String, Object> result = new LinkedHashMap<>();
        Settings saved = settings();
        String base = normalizeBaseUrl(url);
        if (base.isBlank()) {
            base = saved.url() == null ? "" : saved.url();
        }
        if (base.isBlank()) {
            result.put("success", false);
            result.put("message", "请先填写视频解析服务地址");
            return result;
        }
        if (!base.startsWith("http://") && !base.startsWith("https://")) {
            base = "http://" + base;
        }
        String user = trim(username);
        if (user == null) {
            user = saved.username();
        }
        if (user == null) {
            result.put("success", true);
            result.put("message", "地址已填写；未配置账号，将按匿名方式调用解析接口");
            return result;
        }
        String pass = password != null && !password.isBlank() ? password : saved.password();
        try {
            String cookie = login(new Settings(base, user, pass));
            result.put("success", true);
            result.put("message", cookie.isBlank() ? "服务可达（未返回会话 Cookie）" : "连接成功：已登录解析服务");
        } catch (BusinessException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败：" + safeMessage(e));
        }
        return result;
    }

    /** 登录解析服务并返回 Cookie 头；未配置账号时返回空串（匿名访问） */
    private String login(Settings settings) throws IOException, InterruptedException {
        if (settings.username() == null || settings.username().isBlank()) {
            return "";
        }
        String payload = objectMapper.writeValueAsString(Map.of(
                "username", settings.username(),
                "password", settings.password() == null ? "" : settings.password(),
                "remember", true));
        HttpRequest request = baseBuilder(settings.url() + "/api/login", settings.url() + "/login")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                .timeout(LOGIN_TIMEOUT)
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        String cookie = collectCookies(response);
        log.info("视频解析服务登录：HTTP {}，会话 {}", response.statusCode(), cookie.isBlank() ? "未取到" : "已取到");
        if (response.statusCode() / 100 != 2) {
            throw new BusinessException(400, "视频解析服务登录失败：HTTP " + response.statusCode()
                    + "（请检查账号密码）");
        }
        return cookie;
    }

    /** 请求 JSON 并返回响应体；HTTP 非 2xx 直接报错 */
    private String requestJson(String url, String referer, String cookie, Duration timeout)
            throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(
                baseBuilder(url, referer).GET().timeout(timeout).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() / 100 != 2) {
            throw new BusinessException(400, "视频解析服务返回 HTTP " + response.statusCode());
        }
        return response.body() == null ? "" : response.body();
    }

    /** 与参考脚本一致的一组请求头（Accept / Accept-Language / Origin / Referer / UA + 可选 Cookie） */
    private HttpRequest.Builder baseBuilder(String url, String referer) {
        URI uri = URI.create(url);
        String origin = uri.getScheme() + "://" + uri.getAuthority();
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh;q=0.9")
                .header("Origin", origin)
                .header("Referer", referer)
                .header("User-Agent", USER_AGENT);
        return builder;
    }

    /** 把 Set-Cookie 里的 name=value 拼成请求用的 Cookie 头（每条只取第一段，与参考脚本一致） */
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

    /** 解析响应体：{code, msg, data:{title, cover_url, video_url, music_url, author, images, proxy_url}} */
    private VideoItem readResult(String body) {
        if (body == null || body.isBlank()) {
            throw new BusinessException(400, "视频解析服务没有返回内容");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new BusinessException(400, "视频解析服务返回的内容无法识别");
        }
        if (root == null || !root.isObject()) {
            throw new BusinessException(400, "视频解析服务返回的内容无法识别");
        }
        int code = root.path("code").asInt(0);
        if (code != 200) {
            String msg = root.path("msg").asText("");
            throw new BusinessException(400, msg.isBlank() ? "视频解析失败（code=" + code + "）" : msg);
        }
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull() || !data.isObject()) {
            throw new BusinessException(400, "视频解析服务没有返回解析结果");
        }

        List<ImageItem> images = new ArrayList<>();
        JsonNode imageNodes = data.path("images");
        if (imageNodes.isArray()) {
            for (JsonNode node : imageNodes) {
                String imageUrl = text(node, "url");
                if (!imageUrl.isBlank()) {
                    images.add(new ImageItem(imageUrl, text(node, "live_photo_url")));
                }
            }
        }

        return new VideoItem(
                text(data, "title"),
                text(data.path("author"), "name"),
                text(data, "cover_url"),
                text(data, "video_url"),
                text(data, "music_url"),
                text(data, "proxy_url"),
                images);
    }

    // ------------------------------------------------------------------ 纯函数工具

    /**
     * 从分享口令里提取链接：取第一个 http(s) 链接并裁掉尾随的中英文标点。
     *
     * @return 找到返回链接，未找到返回 null
     */
    public static String extractShareUrl(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        Matcher matcher = SHARE_URL.matcher(text);
        while (matcher.find()) {
            String url = trimTrailingPunctuation(matcher.group());
            if (url.length() > "https://".length() + 3) {
                return url;
            }
        }
        return null;
    }

    /** 裁掉链接尾部的中英文句读（口令常常是「链接 + 中文说明」紧贴的形态） */
    private static String trimTrailingPunctuation(String url) {
        int end = url.length();
        while (end > 0 && TRAILING_PUNCTUATION.indexOf(url.charAt(end - 1)) >= 0) {
            end--;
        }
        return url.substring(0, end);
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

    /** 查询参数编码（分享链接本身带 ? & 等字符，必须整体编码后再拼进 url=） */
    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** 日志里截断超长响应体，避免刷屏 */
    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        String oneLine = text.replaceAll("\\s+", " ").trim();
        return oneLine.length() > 500 ? oneLine.substring(0, 500) + "…" : oneLine;
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return "";
        }
        return node.path(field).asText("").trim();
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
