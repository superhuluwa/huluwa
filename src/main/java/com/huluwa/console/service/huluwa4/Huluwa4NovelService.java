package com.huluwa.console.service.huluwa4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 4hu.tv 小说目录和正文下载。
 *
 * <p>站点接口返回 AES-CBC 加密的 JSON：分类页为
 * {@code /json/category/{category}-{page}.json}，章节页为
 * {@code /json/view/{chapter}.json}。本服务只访问这两个只读接口，
 * 正文按“分类/书名/章节_章节ID.txt”落盘，已存在非空文件直接跳过。</p>
 */
@Service
public class Huluwa4NovelService {

    private static final Logger log = LoggerFactory.getLogger(Huluwa4NovelService.class);
    private static final String BASE_URL = "https://4hu.tv";
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131 Safari/537.36";
    private static final String KEY = "22946bc50fd63164b79df55070a85a92";
    private static final String IV = "kaixin1234567890";
    private static final int PAGE_SIZE = 24;
    private static final int MAX_PAGES = 20_000;
    private static final int MAX_TASKS = 32;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectMapper json = JSON;
    private final Path defaultRoot;
    private final String proxyBase;
    private volatile boolean preferProxy;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(12))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ExecutorService worker = Executors.newSingleThreadExecutor(
            Thread.ofPlatform().daemon().name("4hu-novel-download").factory());
    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final Object startLock = new Object();
    private final Set<Path> activeOutputs = ConcurrentHashMap.newKeySet();
    private final Map<Integer, Category> categoryCache = new ConcurrentHashMap<>();
    private final AtomicBoolean stopping = new AtomicBoolean();

    public Huluwa4NovelService(@Value("${huluwa.data-dir:./data}") String dataDir,
                               @Value("${huluwa.4hu.proxy-base:https://r.jina.ai}") String proxyBase) {
        this.defaultRoot = Path.of(dataDir).resolve("4hu-novels").toAbsolutePath().normalize();
        this.proxyBase = proxyBase == null ? "" : proxyBase.trim().replaceAll("/+$", "");
    }

    public record Category(int id, String name, String path) { }
    public record CategoryTotal(int categoryId, String name, int total, int perPage, int lastPage) { }

    public record Chapter(int id, int categoryId, String title, String date, long timestamp, String url,
                          String bookTitle, String chapterTitle) { }

    public record TaskStatus(String taskId, int categoryId, String categoryName, String state,
                             int totalPages, int currentPage, int currentPageProcessed,
                             int currentPageTotal, int totalChapters, int processedChapters,
                             int downloadedChapters, int skippedChapters, int failedChapters,
                             int discoveredBooks, String currentBook, String currentChapter, String outputDir,
                             String error, String startedAt, String finishedAt,
                             List<String> recentFailures) {
        public boolean running() { return "running".equals(state) || "cancelling".equals(state); }
        public int progressPercent() {
            return totalChapters <= 0 ? 0 : Math.min(100, (int) Math.round(processedChapters * 100.0 / totalChapters));
        }
    }

    private static final class Task {
        private final CategoryTotal total;
        private final String outputDir;
        private final Instant startedAt;
        private final String taskId;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile TaskStatus status;

        private Task(String taskId, CategoryTotal total, String outputDir, TaskStatus status) {
            this.taskId = taskId;
            this.total = total;
            this.outputDir = outputDir;
            this.startedAt = Instant.now();
            this.status = status;
        }
    }

    /** 4hu 顶部导航的小说分类。ID 与站点菜单一致，分类页会返回最新名称。 */
    public List<Category> categories() {
        List<Category> cached = categoryCache.values().stream()
                .sorted(Comparator.comparingInt(Category::id))
                .toList();
        if (!cached.isEmpty()) return cached;
        synchronized (categoryCache) {
            cached = categoryCache.values().stream().sorted(Comparator.comparingInt(Category::id)).toList();
            if (!cached.isEmpty()) return cached;
            Category[] known = {
                    new Category(16, "都市小说", "/category/16/novel"),
                    new Category(17, "乱伦小说", "/category/17/novel"),
                    new Category(18, "校园小说", "/category/18/novel"),
                    new Category(20, "人妻小说", "/category/20/novel")
            };
            for (Category category : known) categoryCache.put(category.id(), category);
            return List.of(known);
        }
    }

    /** 当前数据目录下的 4hu 下载根目录，供页面显示默认路径。 */
    public String outputRoot() {
        return defaultRoot.toString();
    }

    public Category category(int id) {
        return categories().stream().filter(item -> item.id() == id).findFirst()
                .orElseThrow(() -> new BusinessException(400, "未知的小说分类：" + id));
    }

    /** 读取分类第一页元数据，得到总章数、总页数和分类名。 */
    public CategoryTotal categoryTotal(int categoryId) {
        Category category = category(categoryId);
        JsonNode first = fetchCategoryPage(categoryId, 1);
        int total = first.path("total").asInt(0);
        int perPage = first.path("per_page").asInt(PAGE_SIZE);
        int lastPage = first.path("last_page").asInt(0);
        String name = first.path("category_name").asText(category.name());
        if (name.isBlank()) name = category.name();
        return new CategoryTotal(categoryId, name, total, perPage <= 0 ? PAGE_SIZE : perPage,
                lastPage <= 0 ? (int) Math.ceil(total / (double) PAGE_SIZE) : lastPage);
    }

    /** 查询章节页，供页面展示前若干条；不会触发正文下载。 */
    public List<Chapter> chapters(int categoryId, int page) {
        category(categoryId);
        if (page < 1 || page > MAX_PAGES) throw new BusinessException(400, "页码超出范围");
        JsonNode payload = fetchCategoryPage(categoryId, page);
        String categoryName = payload.path("category_name").asText(category(categoryId).name());
        List<Chapter> list = new ArrayList<>();
        for (JsonNode item : payload.path("data")) {
            int id = item.path("id").asInt();
            if (id <= 0) continue;
            String title = item.path("title").asText("");
            ParsedTitle parsed = parseTitle(title);
            list.add(new Chapter(id, categoryId, title, formatDate(item.path("newstime").asLong()),
                    item.path("newstime").asLong(), "/view/" + id, parsed.book(), parsed.chapter()));
        }
        if (!list.isEmpty() && !categoryName.isBlank()) categoryCache.put(categoryId,
                new Category(categoryId, categoryName, "/category/" + categoryId + "/novel"));
        return list;
    }

    /**
     * 启动后台下载任务。
     *
     * @param outputDir 可选分类输出目录；为空时使用数据目录/4hu-novels/分类名
     */
    public TaskStatus startDownload(int categoryId, String outputDir) {
        synchronized (startLock) {
            if (stopping.get()) throw new BusinessException(503, "服务正在停止，请稍后重试");
            CategoryTotal total = categoryTotal(categoryId);
            Path root = resolveOutputDir(categoryId, total.name(), outputDir);
            // 浏览器刷新或重复点击时，同一个保存目录直接复用运行中的任务。
            // 前端只持有展示状态；任务的真实状态始终以后端内存任务为准。
            for (Task running : tasks.values()) {
                TaskStatus status = running.status;
                if (status.running() && status.categoryId() == categoryId
                        && root.equals(Path.of(status.outputDir()).toAbsolutePath().normalize())) {
                    return status;
                }
            }
            if (!activeOutputs.add(root)) {
                throw new BusinessException(409, "该保存目录已有下载任务，请等待完成：" + root);
            }
            String taskId = categoryId + "-" + System.currentTimeMillis();
            try {
                Files.createDirectories(root);
            } catch (IOException error) {
                activeOutputs.remove(root);
                throw new BusinessException(500, "无法创建下载目录：" + root, error);
            }
            TaskStatus status = new TaskStatus(taskId, categoryId, total.name(), "running",
                    total.lastPage(), 0, 0, 0, total.total(), 0, 0, 0, 0, 0,
                    null, null, root.toString(), null, now(), null, List.of());
            Task task = new Task(taskId, total, root.toString(), status);
            tasks.put(taskId, task);
            try {
                worker.submit(() -> run(task));
            } catch (RuntimeException error) {
                activeOutputs.remove(root);
                tasks.remove(taskId);
                throw new BusinessException(503, "下载任务暂时无法启动，请重试", error);
            }
            return status;
        }
    }

    public TaskStatus status(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) throw new BusinessException(404, "下载任务不存在或已过期");
        return task.status;
    }

    public TaskStatus cancel(String taskId) {
        Task task = tasks.get(taskId);
        if (task == null) throw new BusinessException(404, "下载任务不存在或已过期");
        if (!task.status.running()) return task.status;
        task.cancelled.set(true);
        TaskStatus current = task.status;
        TaskStatus cancelling = new TaskStatus(current.taskId(), current.categoryId(), current.categoryName(), "cancelling",
                current.totalPages(), current.currentPage(), current.currentPageProcessed(),
                current.currentPageTotal(), current.totalChapters(), current.processedChapters(),
                current.downloadedChapters(), current.skippedChapters(), current.failedChapters(),
                current.discoveredBooks(), current.currentBook(), current.currentChapter(), current.outputDir(),
                current.error(), current.startedAt(), null, current.recentFailures());
        task.status = cancelling;
        return cancelling;
    }

    public List<TaskStatus> activeTasks() {
        trimTasks();
        return tasks.values().stream().map(task -> task.status)
                .sorted((a, b) -> {
                    String left = a.startedAt() == null ? "" : a.startedAt();
                    String right = b.startedAt() == null ? "" : b.startedAt();
                    return right.compareTo(left);
                }).toList();
    }

    private Path resolveOutputDir(int categoryId, String categoryName, String requested) {
        if (requested == null || requested.isBlank()) return defaultRoot.resolve(safeName(categoryName, "分类-" + categoryId));
        Path path = Path.of(requested.trim()).toAbsolutePath().normalize();
        Path dataRoot = defaultRoot.getParent();
        if (!path.startsWith(dataRoot)) {
            throw new BusinessException(400, "保存目录必须位于数据目录下：" + dataRoot);
        }
        return path;
    }

    private void run(Task task) {
        int processed = 0;
        int downloaded = 0;
        int skipped = 0;
        int failed = 0;
        int currentPage = 0;
        int currentPageProcessed = 0;
        int currentPageTotal = 0;
        List<String> failures = new ArrayList<>();
        java.util.LinkedHashSet<String> books = new java.util.LinkedHashSet<>();
        String currentBook = null;
        String currentChapter = null;
        try {
            CategoryTotal total = task.total;
            Path root = Path.of(task.outputDir);
            for (int page = 1; page <= total.lastPage(); page++) {
                if (task.cancelled.get()) {
                    update(task, state(task.status, "cancelled", null, page - 1, currentPageProcessed,
                            currentPageTotal, processed, downloaded, skipped, failed, books.size(),
                            currentBook, currentChapter, failures));
                    return;
                }
                JsonNode payload = fetchCategoryPage(task.total.categoryId(), page);
                currentPage = page;
                currentPageProcessed = 0;
                currentPageTotal = payload.path("data").size();
                update(task, state(task.status, "running", null, currentPage, currentPageProcessed,
                        currentPageTotal, processed, downloaded, skipped, failed, books.size(),
                        currentBook, currentChapter, failures));
                for (JsonNode item : payload.path("data")) {
                    if (task.cancelled.get()) {
                        update(task, state(task.status, "cancelled", null, currentPage, currentPageProcessed,
                                currentPageTotal, processed, downloaded, skipped, failed, books.size(),
                                currentBook, currentChapter, failures));
                        return;
                    }
                    int chapterId = item.path("id").asInt();
                    if (chapterId <= 0) continue;
                    String rawTitle = item.path("title").asText("");
                    ParsedTitle parsed = parseTitle(rawTitle);
                    currentBook = parsed.book();
                    currentChapter = parsed.chapter();
                    books.add(parsed.book());
                    Path target = root.resolve(safeName(parsed.book(), "未分类"))
                            .resolve(safeName(parsed.chapter(), "正文") + "_" + chapterId + ".txt");
                    processed++;
                    currentPageProcessed++;
                    if (Files.isRegularFile(target) && Files.size(target) > 0) {
                        skipped++;
                    } else {
                        try {
                            JsonNode detail = fetchJson("/json/view/" + chapterId + ".json");
                            if (detail.path("classid").asInt() != total.categoryId()) {
                                throw new IOException("章节分类不一致：" + detail.path("classid").asInt());
                            }
                            writeChapter(target, rawTitle, total.name(), chapterId,
                                    formatDate(item.path("newstime").asLong()), detail.path("content").asText(""));
                            downloaded++;
                        } catch (Exception error) {
                            failed++;
                            if (failures.size() < 8) failures.add("#" + chapterId + " " + shortError(error));
                            log.warn("4hu 小说章节 {} 下载失败：{}", chapterId, error.getMessage());
                        }
                    }
                    update(task, state(task.status, "running", null, currentPage, currentPageProcessed,
                            currentPageTotal, processed, downloaded, skipped, failed, books.size(),
                            currentBook, currentChapter, failures));
                }
                sleepQuietly(800);
            }
            update(task, state(task.status, "done", null, currentPage, currentPageProcessed,
                    currentPageTotal, processed, downloaded, skipped, failed, books.size(),
                    currentBook, currentChapter, failures));
        } catch (BusinessException error) {
            String nextState = task.cancelled.get() ? "cancelled" : "failed";
            update(task, state(task.status, nextState, task.cancelled.get() ? null : error.getMessage(),
                    currentPage, currentPageProcessed, currentPageTotal, processed, downloaded, skipped,
                    failed, books.size(), currentBook, currentChapter, failures));
        } catch (Exception error) {
            log.warn("4hu 小说下载任务 {} 失败：{}", task.taskId, error.getMessage());
            update(task, state(task.status, "failed", "下载失败：" + shortError(error),
                    currentPage, currentPageProcessed, currentPageTotal, processed, downloaded, skipped,
                    failed, books.size(), currentBook, currentChapter, failures));
        } finally {
            activeOutputs.remove(Path.of(task.outputDir));
        }
    }

    private static TaskStatus state(TaskStatus base, String state, String error, int currentPage,
                                    int currentPageProcessed, int currentPageTotal, int processed,
                                    int downloaded, int skipped, int failed, int discoveredBooks,
                                    String currentBook, String currentChapter, List<String> failures) {
        return new TaskStatus(base.taskId(), base.categoryId(), base.categoryName(), state,
                base.totalPages(), currentPage, currentPageProcessed, currentPageTotal,
                base.totalChapters(), processed, downloaded, skipped, failed, discoveredBooks,
                currentBook, currentChapter, base.outputDir(), error, base.startedAt(),
                ("running".equals(state) || "cancelling".equals(state)) ? null : now(),
                List.copyOf(failures));
    }

    private void update(Task task, TaskStatus status) {
        task.status = status;
    }

    private void writeChapter(Path target, String title, String categoryName, int chapterId,
                              String date, String html) throws IOException {
        Files.createDirectories(target.getParent());
        String text = title + "\n\n分类：" + categoryName + "\n日期：" + date +
                "\n来源：" + BASE_URL + "/view/" + chapterId + "\n\n" + htmlToText(html) + "\n";
        Path temp = target.resolveSibling(target.getFileName() + ".part");
        Files.writeString(temp, text, StandardCharsets.UTF_8);
        try {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private JsonNode fetchCategoryPage(int categoryId, int page) {
        return fetchJson("/json/category/" + categoryId + "-" + page + ".json");
    }

    private JsonNode fetchJson(String path) {
        // Cloudflare 会按网络环境拦截直连；一旦失败，本进程后续请求直接走代理。
        if (preferProxy) return viaProxy(path, null);
        try {
            return fetchJsonFrom(BASE_URL + path);
        } catch (BusinessException directError) {
            preferProxy = true;
            log.info("4hu 直连失败，后续请求改用代理 {}：{}", proxyBase, directError.getMessage());
            return viaProxy(path, directError);
        }
    }

    private JsonNode viaProxy(String path, BusinessException directError) {
        if (proxyBase.isBlank()) {
            throw new BusinessException(502, "4hu 直连失败且未配置代理：" +
                    (directError == null ? "Cloudflare 拦截" : directError.getMessage()));
        }
        try {
            return fetchJsonFrom(proxyBase + "/" + BASE_URL + path);
        } catch (BusinessException proxyError) {
            throw new BusinessException(502, "4hu 接口暂时不可用：" + proxyError.getMessage(), proxyError);
        }
    }

    private JsonNode fetchJsonFrom(String url) {
        IOException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                if (attempt > 0) sleepQuietly(400L * attempt);
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(25))
                        .header("user-agent", USER_AGENT)
                        .header("accept", "application/json,text/plain,*/*")
                        .header("accept-language", "zh-CN,zh;q=0.9")
                        .GET().build();
                HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status < 200 || status >= 300) {
                    if (status != 429 && status < 500) {
                        throw new BusinessException(502, "HTTP " + status);
                    }
                    throw new IOException("HTTP " + status);
                }
                String payload = extractPayload(response.body());
                return json.readTree(decrypt(payload));
            } catch (BusinessException error) {
                throw error;
            } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                throw new BusinessException(500, "请求已中断", error);
            } catch (IOException | RuntimeException error) {
                last = error instanceof IOException io ? io : new IOException(error.getMessage(), error);
            }
        }
        throw new BusinessException(502, last == null ? "未知错误" : shortError(last));
    }

    /** 兼容 4hu 原始 JSON 与代理返回的 Markdown 包装正文。 */
    private static String extractPayload(String body) throws IOException {
        String text = body == null ? "" : body;
        try {
            JsonNode envelope = JSON.readTree(text);
            String payload = envelope.path("data").asText("");
            if (!payload.isBlank()) return payload;
        } catch (IOException ignored) {
            // 代理会把 JSON 放进 Markdown 正文，继续按文本提取。
        }
        int status = text.indexOf("\"status\"");
        int data = text.indexOf("\"data\"", Math.max(0, status));
        if (status >= 0 && data > status) {
            int start = text.indexOf('"', text.indexOf(':', data) + 1);
            int end = start < 0 ? -1 : text.indexOf('"', start + 1);
            if (start >= 0 && end > start) {
                return text.substring(start + 1, end);
            }
        }
        throw new IOException("响应缺少加密数据或被 Cloudflare 拦截");
    }

    private static String decrypt(String payload) {
        try {
            String normalized = payload.replace('-', '+').replace('_', '/');
            int padding = normalized.length() % 4;
            if (padding != 0) normalized += "=".repeat(4 - padding);
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE,
                    new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "AES"),
                    new IvParameterSpec(IV.getBytes(StandardCharsets.UTF_8)));
            return new String(cipher.doFinal(Base64.getDecoder().decode(normalized)), StandardCharsets.UTF_8);
        } catch (Exception error) {
            throw new BusinessException(502, "无法解密 4hu 数据：" + error.getMessage(), error);
        }
    }

    private static String htmlToText(String html) {
        return html == null ? "" : html
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</(p|div|h[1-6]|li)>", "\n")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
                .replaceAll("[ \\t]+\\n", "\n").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static ParsedTitle parseTitle(String raw) {
        String title = raw == null ? "" : raw.trim();
        var match = java.util.regex.Pattern.compile("^\\s*[《【](.+?)[》】]\\s*(?:[-—–:：]\\s*)?(.*)$", java.util.regex.Pattern.DOTALL)
                .matcher(title);
        if (match.matches()) return new ParsedTitle(match.group(1).trim(), blank(match.group(2), title));
        match = java.util.regex.Pattern.compile("^\\s*(.+?)\\s+[-—–]\\s+(.+)$", java.util.regex.Pattern.DOTALL).matcher(title);
        if (match.matches()) return new ParsedTitle(match.group(1).trim(), blank(match.group(2), title));
        return new ParsedTitle("未分类", title.isBlank() ? "正文" : title);
    }

    private static String safeName(String value, String fallback) {
        String name = value == null ? "" : value
                .replaceAll("[\\u0000-\\u001f\\u007f]", "")
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ").replaceAll("[. ]+$", "").trim();
        if (name.isEmpty()) name = fallback;
        StringBuilder limited = new StringBuilder();
        int bytes = 0;
        for (int point : name.codePoints().toArray()) {
            String character = new String(Character.toChars(point));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 180) break;
            limited.append(character);
            bytes += size;
        }
        name = limited.toString().replaceAll("[. ]+$", "");
        return name.isEmpty() ? fallback : name;
    }

    private static String formatDate(long seconds) {
        if (seconds <= 0) return "";
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(seconds), ZoneId.of("Asia/Shanghai")).format(STAMP);
    }

    private static String now() {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(STAMP);
    }

    private static String shortError(Throwable error) {
        String text = error == null ? "未知错误" : String.valueOf(error.getMessage());
        return text.length() > 160 ? text.substring(0, 160) : text;
    }

    private static String blank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static void sleepQuietly(long millis) {
        try { Thread.sleep(millis); } catch (InterruptedException error) { Thread.currentThread().interrupt(); }
    }

    private void trimTasks() {
        if (tasks.size() < MAX_TASKS) return;
        tasks.entrySet().stream()
                .filter(entry -> !entry.getValue().status.running())
                .sorted(Comparator.comparing(entry -> entry.getValue().startedAt))
                .limit(Math.max(0, tasks.size() - MAX_TASKS + 1))
                .map(Map.Entry::getKey).toList().forEach(tasks::remove);
    }

    @PreDestroy
    public void shutdown() {
        stopping.set(true);
        tasks.values().forEach(task -> task.cancelled.set(true));
        worker.shutdownNow();
    }

    private record ParsedTitle(String book, String chapter) { }
}
