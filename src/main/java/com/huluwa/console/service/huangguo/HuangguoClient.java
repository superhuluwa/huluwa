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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /** 黄果官网的一级分类；original 使用独立原创筛选，其余 route 对应分类接口。 */
    public record Category(String name, String route) { }

    /** 官网分类页的二级标签；value 为空表示该频道全量。 */
    public record Filter(String name, String value) { }

    /** 黄果官网题材页的分组；具体题材通过 /tag/{slug}/ 页面读取。 */
    public record TagGroup(String code, String name, List<Tag> tags) { }

    public record Tag(String name, String slug) { }

    /** 分类列表的下一页状态；黄果分类接口返回分页对象。 */
    public record Page(List<Catalog> items, int page, int pages, int total) { }

    /** 博客卡片；博客列表与短剧列表字段完全不同，单独建模。 */
    public record Post(String id, String title, String cover, String description, String author,
                       String publishedAt, long hot, List<String> tags) { }

    public record PostPage(List<Post> items, int page, int pages, int total) { }

    /** 文章正文块：正文 HTML 由服务端拆成原生可渲染的段落、标题、图片和视频。 */
    public record PostBlock(String type, String text, int index) { }

    public record PostDetail(Post post, String authorAvatar, int reads,
                             List<PostBlock> blocks, List<String> tagLinks) { }

    public record PostVideo(String id, int index, String url, String authKey, String authValue, String app) { }

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

    /** 一级分类与题材分组来自官网公开配置接口，避免客户端自行维护菜单。 */
    public List<Category> categories() {
        // 官网导航新增了“黄果原创”，它不是 /api/site/categories/video 中的普通频道。
        // 仍以配置接口为准，再按官网导航顺序合并原创与可用频道。
        JsonNode root = getJson("/api/site/categories/video");
        List<Category> configured = new ArrayList<>();
        for (JsonNode item : root.path("data").path("items")) {
            addCategory(configured, item.path("name").asText(""), item.path("route").asText(""));
        }
        List<Category> list = new ArrayList<>();
        addCategory(list, "黄果原创", "original");
        for (String route : List.of("ai-duanju", "ai-manju")) {
            configured.stream().filter(item -> item.route().equals(route)).findFirst()
                    .ifPresent(item -> addCategory(list, item.name(), item.route()));
        }
        for (Category item : configured) {
            if (!item.route().equals("ai-mogai") && !item.route().equals("ai-huanlian")) {
                addCategory(list, item.name(), item.route());
            }
        }
        return list;
    }

    /** 官网原创页的三个入口；cate_id 为官网原创短剧/漫剧的实际分类 ID。 */
    public List<Filter> originalFilters() {
        return List.of(new Filter("全部原创", ""), new Filter("原创漫剧", "25"), new Filter("原创短剧", "24"));
    }

    /** 普通频道当前可稳定分页的二级菜单；官网页面的“精选推荐”不返回可翻页数据。 */
    public List<Filter> channelFilters() {
        return List.of(new Filter("最近更新", "latest"), new Filter("当前热播", "hot_window"));
    }

    public List<TagGroup> tagGroups() {
        JsonNode root = getJson("/api/tags");
        List<TagGroup> list = new ArrayList<>();
        for (JsonNode group : root.path("data").path("categories")) {
            List<Tag> tags = new ArrayList<>();
            for (JsonNode item : group.path("tags")) {
                String name = item.path("name").asText("");
                String slug = item.path("slug").asText("");
                if (!name.isBlank() && !slug.isBlank()) tags.add(new Tag(name, slug));
            }
            if (!tags.isEmpty()) list.add(new TagGroup(group.path("code").asText(""),
                    group.path("name").asText(""), tags));
        }
        return list;
    }

    /** 分类列表；sort=hot 是官网分类页使用的排序参数。 */
    public Page category(String route, int page, int size) {
        JsonNode root = getJson("/api/videos/category/" + encode(route) + "?page=" + page + "&size=" + size + "&sort=hot");
        return page(root.path("data"), page);
    }

    /** 分类页的最近更新、当前热播；均使用官网分类页实际请求的排序参数。 */
    public Page category(String route, String sort, int page, int size) {
        JsonNode root = getJson("/api/videos/category/" + encode(route) + "?page=" + page + "&size=" + size + "&sort=" + encode(sort));
        return page(root.path("data"), page);
    }

    /** 原创频道由 /api/videos 的 is_original 与 cate_id 过滤，顺序与官网原创页一致。 */
    public Page original(String cateId, int page, int size) {
        String query = "/api/videos?is_original=1&page=" + page + "&size=" + size + "&sort=new";
        if (cateId != null && !cateId.isBlank()) query += "&cate_id=" + encode(cateId);
        JsonNode root = getJson(query);
        return page(root.path("data"), page);
    }

    /** 博客频道：blog=全量，gossip=黑料吃瓜，reviews=短剧剧评。 */
    public PostPage posts(String channel, int page, int size) {
        String category = switch (channel == null ? "" : channel) {
            case "gossip" -> "9002";
            case "reviews" -> "9001";
            default -> "";
        };
        String query = "/api/posts?page=" + page + "&size=" + size + "&sort=new";
        if (!category.isBlank()) query += "&cate_id=" + category;
        JsonNode root = getJson(query);
        JsonNode data = root.path("data");
        List<Post> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) items.add(post(item));
        JsonNode pagination = data.path("pagination");
        return new PostPage(items, pagination.path("page").asInt(page),
                Math.max(pagination.path("pages").asInt(1), 1), pagination.path("total").asInt(items.size()));
    }

    /** 文章详情；正文中的站内相对图片需要转成源站绝对地址。 */
    public PostDetail postDetail(String id) {
        JsonNode root = getJson("/api/posts/" + encode(id));
        JsonNode data = root.path("data");
        if (data.isMissingNode() || data.isNull()) throw new HuangguoException("黄果源站没有返回该文章");
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : data.path("tags")) tags.add(tag.asText(""));
        Post post = new Post(data.path("id").asText(""), data.path("title").asText(""),
                absoluteUrl(data.path("cover").asText("")), data.path("desc").asText(data.path("description").asText("")),
                data.path("author").path("name").asText("黄果短剧"),
                data.path("publish_time").asText(data.path("created_at").asText("")),
                data.path("counts").path("hot").asLong(0), tags);
        List<PostBlock> blocks = parsePostBlocks(data.path("content").asText(""));
        List<String> tagLinks = new ArrayList<>();
        for (JsonNode tag : data.path("tag_links")) tagLinks.add(tag.path("name").asText(""));
        return new PostDetail(post, absoluteUrl(data.path("author").path("avatar").asText("")),
                data.path("counts").path("hot").asInt(0), blocks, tagLinks);
    }

    /** 文章视频地址同样有短时签名，不能缓存。 */
    public List<PostVideo> postVideos(String id) {
        JsonNode root = postJson("/api/posts/" + encode(id) + "/playback", "{}");
        if (root.path("status").asInt(0) != 1) throw new HuangguoException("黄果文章视频接口返回失败");
        JsonNode cdn = root.path("data").path("cdn");
        String authKey = cdn.path("authKey").asText("");
        String authValue = cdn.path("authValue").asText("");
        String app = cdn.path("app").asText("");
        List<PostVideo> videos = new ArrayList<>();
        for (JsonNode item : root.path("data").path("videos")) {
            videos.add(new PostVideo(id, item.path("index").asInt(videos.size()), item.path("url").asText(""),
                    authKey, authValue, app));
        }
        return videos;
    }

    private Post post(JsonNode item) {
        List<String> tags = new ArrayList<>();
        for (JsonNode tag : item.path("tags")) tags.add(tag.asText(""));
        return new Post(item.path("id").asText(""), item.path("title").asText(""),
                absoluteUrl(item.path("cover").asText("")), item.path("desc").asText(""),
                "", item.path("publish_time").asText(item.path("created_at").asText("")),
                item.path("hot").asLong(0), tags);
    }

    private static String absoluteUrl(String value) {
        if (value == null || value.isBlank()) return "";
        if (value.startsWith("//")) return "https:" + value;
        if (value.startsWith("/")) return SITE + value;
        return value;
    }

    private static List<PostBlock> parsePostBlocks(String html) {
        List<PostBlock> blocks = new ArrayList<>();
        if (html == null || html.isBlank()) return blocks;
        Pattern tag = Pattern.compile("<\\s*(h[1-6]|p|img|div)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
        Matcher matcher = tag.matcher(html);
        while (matcher.find()) {
            String name = matcher.group(1).toLowerCase();
            String attrs = matcher.group(2);
            if ("img".equals(name)) {
                String src = attr(attrs, "data-src");
                if (src.isBlank()) src = attr(attrs, "src");
                if (!src.isBlank() && !src.contains("cover-placeholder")) blocks.add(new PostBlock("image", absoluteUrl(src), 0));
                continue;
            }
            if ("div".equals(name)) {
                if (attrs.contains("post-video-player")) {
                    int videoIndex = 0;
                    Matcher indexMatcher = Pattern.compile("data-video-index=\"([0-9]+)\"").matcher(attrs);
                    if (indexMatcher.find()) videoIndex = Integer.parseInt(indexMatcher.group(1));
                    blocks.add(new PostBlock("video", "", videoIndex));
                }
                continue;
            }
            int end = html.toLowerCase().indexOf("</" + name + ">", matcher.end());
            if (end < 0) continue;
            String text = html.substring(matcher.end(), end)
                    .replaceAll("<[^>]+>", "")
                    .replace("&nbsp;", " ").replace("&amp;", "&")
                    .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                    .trim();
            if (!text.isBlank()) blocks.add(new PostBlock(name.startsWith("h") ? "heading" : "paragraph", text, 0));
        }
        return blocks;
    }

    private static String attr(String attrs, String name) {
        Matcher matcher = Pattern.compile(name + "\\s*=\\s*[\"']([^\"']*)", Pattern.CASE_INSENSITIVE).matcher(attrs);
        return matcher.find() ? matcher.group(1) : "";
    }

    /** 文章详情和播放接口是 POST 公共接口，不经过视频加密握手。 */
    private JsonNode postJson(String path, String body) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SITE + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", SITE + "/blog/")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new HuangguoException("黄果文章接口 HTTP " + response.statusCode());
            JsonNode root = mapper.readTree(response.body());
            if (root.path("status").asInt(0) != 1) throw new HuangguoException("黄果文章接口返回：" + root.path("msg").asText("请求失败"));
            return root;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new HuangguoException("黄果文章请求被中断", error);
        } catch (HuangguoException error) {
            throw error;
        } catch (Exception error) {
            throw new HuangguoException("黄果文章请求失败：" + error.getMessage(), error);
        }
    }

    /** 题材页按官网服务端渲染页面读取，兼容官网当前没有公开的题材 JSON 列表接口。 */
    public Page tag(String slug, int page, int size) {
        String path = "/tag/" + encode(slug) + "/" + (page <= 1 ? "" : "page/" + page + "/");
        String html = getText(path, SITE + "/tag/" + slug + "/");
        List<Catalog> items = new ArrayList<>();
        // 页面顶部还有「猜你喜欢」等卡片；只解析正文列表，避免跨区域匹配到推荐内容。
        int gridStart = html.indexOf("hg-card-grid");
        if (gridStart >= 0) html = html.substring(gridStart);
        Pattern card = Pattern.compile(
                "<div class=\"hg-drama-card\"[^>]*data-track-item-id=\"([0-9]+)\"[\\s\\S]*?<img[^>]*data-src=\"([^\"]+)\"[^>]*alt=\"([^\"]*)\"[\\s\\S]*?class=\"hg-drama-card__score\">([^<]*)</span>[\\s\\S]*?class=\"hg-drama-card__episode\">([^<]*)</span>");
        Matcher matcher = card.matcher(html);
        int limit = Math.min(Math.max(size, 1), 50);
        while (matcher.find() && items.size() < limit) {
            String id = matcher.group(1);
            String cover = matcher.group(2).replace("&amp;", "&");
            String title = matcher.group(3).replace("&amp;", "&").replace("&quot;", "\"");
            double score = parseScore(matcher.group(4));
            int episodes = parseEpisodes(matcher.group(5));
            items.add(new Catalog(id, title, cover, "", score, episodes, List.of()));
        }
        int pages = 1;
        Matcher pager = Pattern.compile("data-pages=\"([0-9]+)\"").matcher(html);
        if (pager.find()) pages = Integer.parseInt(pager.group(1));
        return new Page(items, page, pages, items.size());
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

    private Page page(JsonNode data, int page) {
        List<Catalog> items = new ArrayList<>();
        for (JsonNode item : data.path("items")) items.add(catalog(item));
        JsonNode pagination = data.path("pagination");
        return new Page(items, pagination.path("page").asInt(page), Math.max(pagination.path("pages").asInt(1), 1),
                pagination.path("total").asInt(items.size()));
    }

    private static void addCategory(List<Category> list, String name, String route) {
        if (name.isBlank() || route.isBlank() || list.stream().anyMatch(item -> item.route().equals(route))) return;
        list.add(new Category(name, route));
    }

    private static double parseScore(String text) {
        Matcher matcher = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)").matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group(1)) : 0;
    }

    private static int parseEpisodes(String text) {
        Matcher matcher = Pattern.compile("([0-9]+)").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group(1)) : 0;
    }

    private String getText(String path, String referer) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(SITE + path))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", referer)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) throw new HuangguoException("黄果题材页 HTTP " + response.statusCode());
            return response.body();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new HuangguoException("黄果题材页请求被中断", error);
        } catch (HuangguoException error) {
            throw error;
        } catch (Exception error) {
            throw new HuangguoException("黄果题材页请求失败：" + error.getMessage(), error);
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
                hello.path("sid").asText(), offset, requestKey, responseKey, new AtomicInteger());
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
                           long offset, byte[] requestKey, byte[] responseKey, AtomicInteger sequence) {
        int nextSeq() {
            return sequence.incrementAndGet();
        }

        byte[] ad(int seq, long timestamp, String direction) {
            return (info + "\n" + direction + "\n" + seq + "\n" + timestamp).getBytes(StandardCharsets.UTF_8);
        }
    }
}
