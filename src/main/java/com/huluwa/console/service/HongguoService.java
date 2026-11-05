package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/** 红果只在服务端取得元数据；CDN 下载、密钥解包与 CENC 解密由安卓执行。 */
@Service
public class HongguoService {
    private static final String ORIGIN = "https://api5-normal-sinfonlineb.fqnovel.com";
    private static final String DEFAULT_SIGN_URL = "http://192.168.3.83:8901";
    private static final String PREFIX = "/reading/bookapi/";
    private static final Set<String> PROFILE_KEYS = Set.of(
            "device_id", "iid", "cdid", "device_type", "device_brand", "channel", "aid", "app_name",
            "version_code", "version_name", "manifest_version_code", "update_version_code", "os", "ssmix",
            "language", "os_api", "os_version", "resolution", "dpi", "ac", "host_abi", "device_platform",
            "dragon_device_type", "user_is_login");
    private static final Set<String> SIGN_HEADERS = Set.of(
            "x-gorgon", "x-khronos", "x-argus", "x-ladon", "x-medusa", "x-helios");
    private final ConfigService config;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public HongguoService(ConfigService config, ObjectMapper json) {
        this.config = config;
        this.json = json;
    }

    public Map<String, Object> settings() {
        ObjectNode profile = profile(false);
        JsonNode params = profile.path("params");
        return Map.of("signUrl", signUrl(), "configured", !params.path("device_id").asText().isBlank(),
                "deviceModel", params.path("device_type").asText(),
                "profileAvailable", !params.path("iid").asText().isBlank());
    }

    public Map<String, Object> saveSettings(String url, JsonNode deviceProfile) {
        String normalized = validateBaseUrl(url);
        ObjectNode imported = null;
        if (deviceProfile != null && !deviceProfile.isNull()) imported = normalizeProfile(deviceProfile);
        if (!normalized.equals(signUrl()) && imported == null) {
            throw new BusinessException(400, "更换签名服务时请同时导入对应 App 的设备资料");
        }
        config.put("hongguo.sign-url", normalized);
        if (imported != null) config.put("hongguo.device-profile", imported.toString());
        return settings();
    }

    /** 兼容以后在 /info 中提供设备资料的签名服务，不改变 /sign 协议。 */
    public Map<String, Object> syncProfile() {
        JsonNode info = readJson(send(HttpRequest.newBuilder(URI.create(signUrl() + "/info"))
                .timeout(Duration.ofSeconds(30)).GET().build()), "签名服务");
        JsonNode candidate = info.has("deviceProfile") ? info.get("deviceProfile") : info;
        if (!candidate.has("params") && candidate.has("device")) candidate = candidate.get("device");
        ObjectNode profile;
        try { profile = normalizeProfile(candidate); }
        catch (BusinessException error) {
            throw new BusinessException(400, "当前签名服务未提供设备资料，请导入 hg 自检脚本导出的 JSON 文件");
        }
        config.put("hongguo.device-profile", profile.toString());
        return settings();
    }

    public Map<String, Object> search(String query, int offset) {
        String keyword = query == null ? "" : query.trim();
        if (keyword.isBlank() || keyword.length() > 100) throw new BusinessException(400, "请输入 1–100 字的剧名");
        if (offset < 0 || offset > 2000) throw new BusinessException(400, "搜索页码超出范围");
        JsonNode response = request("search/tab/v", Map.ofEntries(
                Map.entry("query", keyword), Map.entry("offset", String.valueOf(offset)), Map.entry("count", "0"),
                Map.entry("bookshelf_search_plan", "4"), Map.entry("bookstore_tab", "-1"),
                Map.entry("search_source", "1"), Map.entry("use_correct", "true"),
                Map.entry("is_first_enter_search", String.valueOf(offset == 0))));
        Map<String, Map<String, Object>> series = new LinkedHashMap<>();
        for (JsonNode tab : response.path("search_tabs")) for (JsonNode item : tab.path("data")) {
            for (JsonNode video : item.path("video_data")) {
                String sid = text(video, "series_id", "series_id_str");
                String vid = text(video, "vid", "video_id");
                if (!sid.matches("[0-9]+") || !vid.matches("[0-9]+")) continue;
                JsonNode detail = video.path("video_detail");
                String title = text(video, "series_title", "title");
                if (title.isBlank()) title = text(detail, "seriesTitle", "series_title", "title");
                if (title.isBlank()) title = text(item, "title", "book_name", "bookName");
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", sid); row.put("firstVid", vid); row.put("title", title.isBlank() ? keyword : title);
                row.put("cover", text(video, "cover", "series_cover"));
                row.put("intro", text(video, "series_intro", "intro"));
                row.put("episodeCount", video.path("episode_cnt").asInt(detail.path("episodeCnt").asInt()));
                series.putIfAbsent(sid, row);
            }
        }
        boolean more = response.path("has_more").asBoolean(!series.isEmpty());
        return Map.of("items", new ArrayList<>(series.values()), "nextOffset", offset + 20, "hasMore", more);
    }

    public Map<String, Object> detail(String seriesId) {
        id(seriesId);
        JsonNode data = request("video_tab/video_detail/v", Map.of("video_series_id", seriesId,
                "source", "0", "screen_width_px", "720", "is_horizontal_screen", "false")).path("data");
        JsonNode video = data.path("video_data");
        List<Map<String, Object>> episodes = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (JsonNode item : data.path("dir_data").path("item_list")) {
            String vid = text(item, "video_id", "itemId", "vid");
            if (!vid.matches("[0-9]+") || !seen.add(vid)) continue;
            int order = episodes.size() + 1;
            String title = text(item, "title");
            episodes.add(Map.of("id", vid, "number", order, "title", title.isBlank() ? "第" + order + "集" : title,
                    "locked", item.path("need_unlock").asBoolean()));
        }
        if (episodes.isEmpty()) throw new BusinessException(502, "该剧暂未返回可用分集，请稍后重试");
        return Map.of("id", seriesId, "title", text(video, "series_title", "seriesTitle"),
                "cover", text(video, "series_cover", "cover"), "intro", text(video, "series_intro", "intro"),
                "episodeCount", episodes.size(), "episodes", episodes);
    }

    public Map<String, Object> playback(String videoId) {
        id(videoId);
        JsonNode data = request("video_tab/video_model/v", Map.of("video_id", videoId, "source", "0",
                "need_all_video_definition", "true")).path("data");
        JsonNode model = data.path("video_model");
        if (model.isTextual()) {
            try { model = json.readTree(model.asText()); }
            catch (IOException error) { throw new BusinessException(502, "红果播放模型格式不正确"); }
        }
        List<Map<String, Object>> streams = new ArrayList<>();
        if (model != null) for (JsonNode stream : model.path("video_list")) {
            String url = text(stream, "main_url");
            if (!mediaUrl(url)) continue;
            JsonNode meta = stream.path("video_meta"), encryption = stream.path("encrypt_info");
            boolean encrypted = encryption.path("encrypt").asBoolean();
            String method = text(encryption, "encryption_method");
            if (encrypted && !"cenc-aes-ctr".equals(method)) continue;
            Map<String, Object> variant = new LinkedHashMap<>();
            variant.put("url", url); variant.put("backupUrl", text(stream, "backup_url"));
            variant.put("definition", text(meta, "definition")); variant.put("codec", text(meta, "codec_type"));
            variant.put("width", meta.path("vwidth").asInt()); variant.put("height", meta.path("vheight").asInt());
            variant.put("size", meta.path("size").asLong()); variant.put("encrypted", encrypted);
            variant.put("encryptionMethod", method); variant.put("spadeA", text(encryption, "spade_a"));
            variant.put("kid", text(encryption, "kid"));
            if (!encrypted || !text(encryption, "spade_a").isBlank()) streams.add(variant);
        }
        if (streams.isEmpty()) {
            String direct = text(data, "mainUrl", "main_url");
            if (mediaUrl(direct) && (model == null || model.isMissingNode() || model.isNull())) {
                streams.add(Map.of("url", direct, "encrypted", false, "definition", "默认"));
            }
        }
        if (streams.isEmpty()) throw new BusinessException(502, "该集暂未返回支持的播放地址");
        return Map.of("videoId", videoId, "streams", streams,
                "expireTime", data.path("expire_time").asLong(model == null ? 0 : model.path("url_expire").asLong()),
                "duration", model == null ? 0 : model.path("video_duration").asDouble(),
                "userAgent", profile(true).path("user_agent").asText(defaultUserAgent()));
    }

    private JsonNode request(String path, Map<String, String> business) {
        ObjectNode profile = profile(true);
        Map<String, String> params = new LinkedHashMap<>(Map.ofEntries(
                Map.entry("device_platform", "android"), Map.entry("aid", "8662"), Map.entry("app_name", "novelread"),
                Map.entry("version_code", "73932"), Map.entry("version_name", "7.3.9.32"), Map.entry("ssmix", "a"),
                Map.entry("os", "android"), Map.entry("user_is_login", "0"),
                Map.entry("manifest_version_code", "73932"), Map.entry("update_version_code", "73932"),
                Map.entry("language", "zh"), Map.entry("os_api", "34"), Map.entry("os_version", "14"),
                Map.entry("resolution", "720*1280"), Map.entry("dpi", "320"), Map.entry("ac", "wifi"),
                Map.entry("host_abi", "arm64-v8a"), Map.entry("dragon_device_type", "phone"),
                Map.entry("channel", "vivo_8662_64"), Map.entry("device_type", "redroid14_x86_64_only"),
                Map.entry("device_brand", "redroid")));
        profile.path("params").fields().forEachRemaining(e -> params.put(e.getKey(), e.getValue().asText()));
        params.putAll(business); params.put("_rticket", String.valueOf(System.currentTimeMillis()));
        String query = params.entrySet().stream().map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
        String url = ORIGIN + PREFIX + path + "?" + query;
        String body;
        try { body = json.writeValueAsString(Map.of("url", url)); }
        catch (IOException error) { throw new BusinessException(500, "签名请求构造失败"); }
        JsonNode signature = readJson(send(HttpRequest.newBuilder(URI.create(signUrl() + "/sign"))
                .timeout(Duration.ofSeconds(45)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build()), "签名服务");
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(40))
                .header("User-Agent", profile.path("user_agent").asText(defaultUserAgent()))
                .header("Accept-Encoding", "gzip").GET();
        Set<String> found = new java.util.HashSet<>();
        signature.fields().forEachRemaining(e -> {
            String name = e.getKey().toLowerCase(java.util.Locale.ROOT);
            if (SIGN_HEADERS.contains(name) && e.getValue().isTextual() && !e.getValue().asText().isBlank()) {
                request.header(e.getKey(), e.getValue().asText()); found.add(name);
            }
        });
        if (!found.containsAll(SIGN_HEADERS)) throw new BusinessException(502, "签名服务未返回完整签名，请检查 App 状态");
        JsonNode result = readJson(send(request.build()), "红果接口");
        int code = result.path("code").asInt(-1);
        if (code == 110) throw new BusinessException(502, "红果设备身份不可用，请重新导入当前签名 App 的设备资料");
        if (code != 0) throw new BusinessException(502, "红果请求失败（" + code + "）：" + text(result, "message"));
        return result;
    }

    private HttpResponse<byte[]> send(HttpRequest request) {
        try { return http.send(request, HttpResponse.BodyHandlers.ofByteArray()); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt(); throw new BusinessException(503, "红果请求已中断");
        } catch (IOException error) { throw new BusinessException(502, "无法连接红果或签名服务，请检查连接设置"); }
    }

    private JsonNode readJson(HttpResponse<byte[]> response, String source) {
        if (response.statusCode() != 200) throw new BusinessException(502, source + "返回 HTTP " + response.statusCode());
        byte[] bytes = response.body();
        if (bytes.length == 0) throw new BusinessException(502, "红果返回空内容，请重新导入当前签名 App 的设备资料");
        try {
            if (bytes.length > 1 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139) {
                try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(bytes))) { bytes = gzip.readAllBytes(); }
            }
            JsonNode result = json.readTree(bytes);
            if (result == null || !result.isObject()) throw new IOException("not an object");
            return result;
        } catch (IOException error) { throw new BusinessException(502, source + "返回的数据格式不正确"); }
    }

    private ObjectNode profile(boolean required) {
        String saved = config.get("hongguo.device-profile", "");
        try {
            if (!saved.isBlank()) return normalizeProfile(json.readTree(saved));
            // 可将 hg 自检脚本导出的资料放进持久化数据目录，无须写入源码。
            Path file = Path.of(System.getProperty("huluwa.data-dir", "./data"), "hongguo-device-profile.json");
            if (Files.isRegularFile(file)) return normalizeProfile(json.readTree(Files.readString(file)));
        } catch (IOException error) { throw new BusinessException(500, "红果设备资料无法读取，请重新导入"); }
        if (required) throw new BusinessException(400, "请先在右上角连接设置中导入红果设备资料");
        return json.createObjectNode();
    }

    private ObjectNode normalizeProfile(JsonNode input) {
        JsonNode params = input.path("params").isObject() ? input.get("params") : input;
        ObjectNode filtered = json.createObjectNode();
        for (String key : PROFILE_KEYS) if (params.hasNonNull(key) && params.get(key).isValueNode()) {
            String value = params.get(key).asText();
            if (value.length() > 300 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0) throw new BusinessException(400, "设备资料字段无效");
            filtered.put(key, value);
        }
        for (String key : List.of("device_id", "iid", "cdid")) {
            if (filtered.path(key).asText().isBlank()) throw new BusinessException(400, "设备资料缺少 " + key);
        }
        String ua = input.path("user_agent").asText(defaultUserAgent());
        if (ua.isBlank() || ua.length() > 600 || ua.contains("\n") || ua.contains("\r")) throw new BusinessException(400, "设备资料 User-Agent 无效");
        ObjectNode profile = json.createObjectNode(); profile.set("params", filtered); profile.put("user_agent", ua);
        return profile;
    }

    private String signUrl() { return validateBaseUrl(config.get("hongguo.sign-url", DEFAULT_SIGN_URL)); }
    private static String validateBaseUrl(String value) {
        try {
            URI uri = URI.create(value == null ? "" : value.trim());
            if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null ||
                    uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) throw new IllegalArgumentException();
            return uri.toString().replaceAll("/+$", "");
        } catch (IllegalArgumentException error) { throw new BusinessException(400, "请输入有效的签名服务 HTTP(S) 地址"); }
    }
    private static String encode(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    private static boolean mediaUrl(String value) { return value.startsWith("https://") || value.startsWith("http://"); }
    private static void id(String value) {
        if (value == null || !value.matches("[0-9]{1,24}")) throw new BusinessException(400, "红果剧集或分集编号无效");
    }
    private static String text(JsonNode node, String... keys) {
        for (String key : keys) {
            String value = node.path(key).asText(""); if (!value.isBlank()) return value;
        }
        return "";
    }
    private static String defaultUserAgent() {
        return "com.phoenix.read/73932 (Linux; U; Android 14; zh_CN; redroid14_x86_64_only; Build/UD2A.240505.001.W1;tt-ok/3.12.13.20)";
    }
}
