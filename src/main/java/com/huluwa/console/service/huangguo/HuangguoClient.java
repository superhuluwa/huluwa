package com.huluwa.console.service.huangguo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黄果短剧源站客户端。
 *
 * <p>公共列表和详情可直接请求；播放地址由站点放在 dx150-edge-v1 加密通道内：
 * ECDH P-256 握手，HKDF-SHA256 派生双向密钥，AES-GCM 传输请求与响应。
 * 播放地址带短时签名，本类每次调用都重新协商，不做持久缓存。</p>
 */
@Component
public class HuangguoClient {

    public static final String SITE = "https://huangguoai.com";
    private static final String PROTOCOL = "dx150-edge-v1";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36";
    private static final String P256_SPKI_PREFIX = "3059301306072a8648ce3d020106082a8648ce3d030107034200";

    private final ObjectMapper mapper;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public HuangguoClient(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public record Catalog(String id, String title, String cover, String description,
                          double score, int episodeCount, List<String> tags) { }

    public record Episode(int ep, String title, int duration) { }

    public record Detail(Catalog catalog, List<Episode> episodes) { }

    public record Playback(String id, int ep, String url, String hevc, String preview,
                           Map<String, Object> cdn) { }

    /** 搜索；参数名必须是 q，keyword 会被源站忽略。 */
    public List<Catalog> search(String keyword, int page, int size) {
        String query = encode(keyword);
        JsonNode root = getJson("/api/search?q=" + query + "&page=" + page + "&size=" + size);
        List<Catalog> list = new ArrayList<>();
        for (JsonNode item : root.path("data").path("items")) {
            list.add(catalog(item));
        }
        return list;
    }

    /** 首页推荐：源站精选列表，按热度排序。 */
    public List<Catalog> recommend(int size) {
        List<Catalog> list = new ArrayList<>();
        for (String route : List.of("recommend", "ai-duanju", "ai-manju")) {
            try {
                JsonNode root = getJson("/api/videos/category/" + route + "?page=1&size=" + size + "&sort=hot");
                for (JsonNode item : root.path("data").path("items")) {
                    String id = item.path("id").asText("");
                    if (!id.isBlank() && list.stream().noneMatch(it -> it.id().equals(id))) {
                        list.add(catalog(item));
                    }
                }
            } catch (RuntimeException ignored) {
                // 推荐栏目并非每台网络都可用，逐项降级，最后仍有搜索页面。
            }
            if (list.size() >= size) {
                break;
            }
        }
        return list.size() > size ? list.subList(0, size) : list;
    }

    /** 详情同时给出规范集数；源站 total_episodes 经常为 0，以 episodes 实际列表为准。 */
    public Detail detail(String id) {
        JsonNode root = getJson("/api/videos/" + encode(id));
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) {
            throw new HuangguoException("黄果源站没有返回该剧");
        }
        List<Episode> episodes = new ArrayList<>();
        for (JsonNode item : data.path("episodes")) {
            int ep = item.path("ep_num").asInt(episodes.size() + 1);
            String title = item.path("title").asText("第" + ep + "集");
            episodes.add(new Episode(ep, title, item.path("duration").asInt(0)));
        }
        int count = Math.max(data.path("episode_count").asInt(0), episodes.size());
        if (episodes.isEmpty() && count > 0) {
            for (int ep = 1; ep <= count; ep++) {
                episodes.add(new Episode(ep, "第" + ep + "集", 0));
            }
        }
        Catalog catalog = catalog(data);
        return new Detail(new Catalog(catalog.id(), catalog.title(), catalog.cover(), catalog.description(),
                catalog.score(), count, catalog.tags()), episodes);
    }

    /** 获取单集 m3u8；每次调用重新握手，返回值包含短时地址。 */
    public Playback playback(String id, int ep) {
        String referer = SITE + "/video/" + id + "/" + (ep <= 1 ? "" : "ep-" + ep + "/");
        try {
            Session session = openSession("playback", referer);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("method", "POST");
            request.put("path", "/api/videos/" + id + "/episodes/" + ep + "/playback");
            request.put("body", Base64.getEncoder().encodeToString("{}".getBytes(StandardCharsets.UTF_8)));
            request.put("content_type", "application/json");
            request.put("visitor_id", "");
            String plain = mapper.writeValueAsString(Map.of("request", request));
            JsonNode inner = call(session, plain);
            if (inner.path("status").asInt(0) != 200) {
                throw new HuangguoException("黄果播放接口返回失败");
            }
            String body = new String(Base64.getDecoder().decode(inner.path("body").asText("")), StandardCharsets.UTF_8);
            JsonNode result = mapper.readTree(body);
            JsonNode data = result.path("data");
            JsonNode streams = data.path("streams");
            Map<String, Object> cdn = mapper.convertValue(data.path("cdn"), Map.class);
            return new Playback(data.path("id").asText(id), data.path("ep").asInt(ep),
                    streams.path("main").asText(""), streams.path("hevc").asText(""),
                    streams.path("preview").asText(""), cdn);
        } catch (Exception error) {
            throw new HuangguoException("黄果播放地址解析失败：" + error.getMessage(), error);
        }
    }

    private Catalog catalog(JsonNode item) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : item.path("tags")) {
            tags.add(tag.asText(""));
        }
        return new Catalog(item.path("id").asText(""), item.path("title").asText(""),
                item.path("cover").asText(""), item.path("description").asText(""),
                item.path("score").asDouble(0), item.path("episode_count").asInt(0), tags);
    }

    private JsonNode getJson(String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SITE + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", SITE + "/")
                    .header("Accept", "application/json, text/plain, */*")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                throw new HuangguoException("黄果源站 HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            if (root.path("status").asInt(0) != 1) {
                throw new HuangguoException("黄果源站返回：" + root.path("msg").asText("请求失败"));
            }
            return root;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new HuangguoException("黄果请求被中断", error);
        } catch (Exception error) {
            throw new HuangguoException("黄果请求失败：" + error.getMessage(), error);
        }
    }

    private Session openSession(String scope, String referer) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(256);
        KeyPair pair = generator.generateKeyPair();
        String clientPublic = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(rawPublicKey(pair.getPublic()));

        JsonNode hello = postEnvelope(scope, referer, mapper.writeValueAsString(Map.of(
                "v", 1, "type", "hello", "client_pub", clientPublic)));
        if (!"hello".equals(hello.path("type").asText())) {
            throw new IllegalStateException("加密握手响应不合法");
        }
        byte[] serverPublicRaw = Base64.getUrlDecoder().decode(hello.path("server_pub").asText());
        PublicKey serverPublic = KeyFactory.getInstance("EC").generatePublic(
                new X509EncodedKeySpec(concat(hex(P256_SPKI_PREFIX), serverPublicRaw)));
        KeyAgreement agreement = KeyAgreement.getInstance("ECDH");
        agreement.init(pair.getPrivate());
        agreement.doPhase(serverPublic, true);
        byte[] shared = agreement.generateSecret();
        byte[] salt = Base64.getUrlDecoder().decode(hello.path("salt").asText());
        String info = String.join("\n", PROTOCOL, scope, hello.path("site").asText(),
                hello.path("kid").asText(), hello.path("sid").asText());
        byte[] requestKey = hkdf(shared, salt, info + "\nreq", 32);
        byte[] responseKey = hkdf(shared, salt, info + "\nres", 32);
        long offset = hello.path("server_time").asLong() - System.currentTimeMillis() / 1000;
        return new Session(scope, referer, info, hello.path("kid").asText(),
                hello.path("sid").asText(), offset, requestKey, responseKey);
    }

    private JsonNode call(Session session, String plain) throws Exception {
        int seq = session.nextSeq();
        long timestamp = System.currentTimeMillis() / 1000 + session.offset;
        byte[] iv = nonce(seq);
        byte[] cipher = aesGcm(Cipher.ENCRYPT_MODE, session.requestKey, iv,
                session.ad(seq, timestamp, "req"), plain.getBytes(StandardCharsets.UTF_8));
        String body = mapper.writeValueAsString(Map.of(
                "v", 1, "type", "call", "kid", session.kid, "sid", session.sid,
                "seq", seq, "ts", timestamp,
                "ciphertext", Base64.getUrlEncoder().withoutPadding().encodeToString(cipher)));
        JsonNode envelope = postEnvelope(session.scope, session.referer, body);
        byte[] plainBytes = aesGcm(Cipher.DECRYPT_MODE, session.responseKey, iv,
                session.ad(seq, timestamp, "res"),
                Base64.getUrlDecoder().decode(envelope.path("ciphertext").asText()));
        return mapper.readTree(plainBytes);
    }

    private JsonNode postEnvelope(String scope, String referer, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(SITE + "/api/" + scope + "/request"))
                .timeout(Duration.ofSeconds(25))
                .header("User-Agent", USER_AGENT)
                .header("Origin", SITE)
                .header("Referer", referer)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IllegalStateException("加密通道 HTTP " + response.statusCode());
        }
        return mapper.readTree(response.body());
    }

    private static byte[] rawPublicKey(PublicKey key) {
        byte[] encoded = key.getEncoded();
        byte[] prefix = hex(P256_SPKI_PREFIX);
        byte[] raw = new byte[encoded.length - prefix.length];
        System.arraycopy(encoded, prefix.length, raw, 0, raw.length);
        return raw;
    }

    private static byte[] hkdf(byte[] ikm, byte[] salt, String info, int length) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        byte[] prk = mac.doFinal(ikm);
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        mac.update(info.getBytes(StandardCharsets.UTF_8));
        mac.update((byte) 1);
        byte[] okm = mac.doFinal();
        return length == okm.length ? okm : Arrays.copyOf(okm, length);
    }

    private static byte[] aesGcm(int mode, byte[] key, byte[] iv, byte[] aad, byte[] input) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(aad);
        return cipher.doFinal(input);
    }

    private static byte[] nonce(int seq) {
        byte[] iv = new byte[12];
        iv[8] = (byte) (seq >>> 24);
        iv[9] = (byte) (seq >>> 16);
        iv[10] = (byte) (seq >>> 8);
        iv[11] = (byte) seq;
        return iv;
    }

    private static byte[] concat(byte[] left, byte[] right) {
        byte[] value = new byte[left.length + right.length];
        System.arraycopy(left, 0, value, 0, left.length);
        System.arraycopy(right, 0, value, left.length, right.length);
        return value;
    }

    private static byte[] hex(String value) {
        return HexFormat.of().parseHex(value);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private record Session(String scope, String referer, String info, String kid, String sid,
                           long offset, byte[] requestKey, byte[] responseKey) {
        int seq;

        synchronized int nextSeq() {
            return ++seq;
        }

        byte[] ad(int seq, long timestamp, String direction) {
            return (info + "\n" + direction + "\n" + seq + "\n" + timestamp).getBytes(StandardCharsets.UTF_8);
        }
    }
}
