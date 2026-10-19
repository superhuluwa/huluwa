package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Short-lived grants let browsers download one file without placing a login token in its URL. */
@Service
public class AndroidDownloadService {
    private static final long LIFETIME_SECONDS = 600;
    private static final int MAX_GRANTS = 512;
    private final AndroidBuildService builds;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();

    private record Grant(long taskId, boolean logFile, String filename, Instant expiresAt) {}
    public record Download(Path path, String filename, boolean logFile) {}

    public AndroidDownloadService(AndroidBuildService builds) { this.builds = builds; }

    public synchronized Map<String, Object> create(long taskId, boolean logFile) {
        Path file = builds.download(taskId, logFile);
        removeExpired();
        if (grants.size() >= MAX_GRANTS) throw new BusinessException(429, "下载请求较多，请稍后重试");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String filename = logFile ? "android-task-" + taskId + ".log" : file.getFileName().toString();
        Instant expiresAt = Instant.now().plusSeconds(LIFETIME_SECONDS);
        grants.put(ticket, new Grant(taskId, logFile, filename, expiresAt));
        // The frontend resolves this path against the actual public Huluwa origin.
        return Map.of("path", "/api/public/android-downloads/" + ticket,
                "filename", filename, "expiresAt", expiresAt.toString());
    }

    public Download resolve(String ticket) {
        Grant grant = grants.get(ticket);
        if (grant == null || !Instant.now().isBefore(grant.expiresAt())) {
            if (grant != null) grants.remove(ticket, grant);
            throw new BusinessException(404, "下载链接已失效，请返回安卓 App 页面重新点击下载");
        }
        // Grants remain reusable until expiry for browser HEAD, Range and retry requests.
        return new Download(builds.download(grant.taskId(), grant.logFile()), grant.filename(), grant.logFile());
    }

    @Scheduled(fixedDelay = 60000)
    public void removeExpired() {
        Instant now = Instant.now();
        grants.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }
}
