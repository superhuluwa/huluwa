package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.config.AndroidBuildProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AndroidBuildServiceTest {
    @TempDir Path directory;
    AndroidBuildService service;
    JdbcTemplate jdbc;
    String node;
    String token;
    final Map<String, Object> heartbeat = Map.of("platform", "windows", "signingReady", true,
            "branch", "master", "repository", "https://example.com/repo.git", "source",
            Map.of("commit", "a".repeat(40), "commitSubject", "fixture", "versionCode", 1));

    @BeforeEach void setup() throws Exception {
        var dataSource = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("test.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        service = new AndroidBuildService(jdbc, new TransactionTemplate(new DataSourceTransactionManager(dataSource)),
                new ObjectMapper(), new AndroidBuildProperties(true, directory.resolve("mailbox"), 60, 10));
        var enrollment = service.createServer("Windows 电脑");
        node = (String) enrollment.get("id"); token = (String) enrollment.get("token");
        service.pollAgent(node, token, heartbeat);
    }

    @Test void routesToSelectedNodeAndNeverExposesCredentials() {
        assertEquals(true, service.status(node).get("workerOnline"));
        assertEquals(false, service.status().get("workerOnline"));
        assertFalse(service.servers().toString().contains(token));
        assertThrows(BusinessException.class, () -> service.submit("UPDATE", "admin", "local"));
        var task = service.submit("BUILD", "admin", node);
        assertEquals(node, task.get("serverId"));
        assertEquals(1010, task.get("versionCode"));
        assertEquals("1.0.10", task.get("versionName"));
        assertThrows(BusinessException.class, () -> service.submit("BUILD", "admin", node));
        assertThrows(BusinessException.class, () -> service.deleteServer(node));
    }

    @Test void rejectsInvalidCredentialsAndCrossNodeTasks() throws Exception {
        assertThrows(BusinessException.class, () -> service.pollAgent(node, "wrong", heartbeat));
        var task = service.submit("UPDATE", "admin", node);
        var other = service.createServer("Other");
        assertThrows(BusinessException.class, () -> service.pollAgent((String) other.get("id"), (String) other.get("token"),
                Map.of("taskId", task.get("id"))));
        assertThrows(BusinessException.class, () -> service.uploadAgent(node, token, ((Number)task.get("id")).longValue(), "../oops.apk", new ByteArrayInputStream(new byte[0])));
        service.cancel(((Number)task.get("id")).longValue());
        var pending = (List<?>) service.pollAgent(node, token, heartbeat).get("tasks");
        assertEquals(true, ((Map<?, ?>)pending.getFirst()).get("cancel"));
    }

    @Test void syncIsIdempotentAndLogsPreserveChinese() throws Exception {
        long id = ((Number)service.submit("UPDATE", "admin", node).get("id")).longValue();
        byte[] text = "中文日志\n".getBytes(StandardCharsets.UTF_8);
        var payload = new LinkedHashMap<>(heartbeat);
        payload.putAll(Map.of("taskId", id, "logOffset", 0, "log", Base64.getEncoder().encodeToString(text),
                "state", Map.of("status", "RUNNING", "stage", "FETCHING")));
        service.pollAgent(node, token, payload);
        service.pollAgent(node, token, payload);
        assertEquals("中文日志\n", service.logs(id, 0).get("text"));
        payload.put("result", Map.of("status", "SUCCESS", "stage", "DONE", "completedAt", java.time.Instant.now().toString()));
        assertTrue(((List<?>) service.pollAgent(node, token, payload).get("tasks")).isEmpty());
        assertEquals("SUCCESS", service.task(id).get("status"));
        assertTrue(((List<?>) service.pollAgent(node, token, payload).get("tasks")).isEmpty());
    }

    @Test void publishesOnlyCompleteAndVerifiedArtifactsThenDownloadsWithoutWorker() throws Exception {
        var task = service.submit("BUILD", "admin", node);
        long id = ((Number)task.get("id")).longValue();
        byte[] apk = "apk-fixture".getBytes(StandardCharsets.UTF_8);
        var result = new LinkedHashMap<String, Object>(Map.of("status", "SUCCESS", "stage", "DONE", "versionCode", task.get("versionCode"),
                "versionName", task.get("versionName"), "artifactName", "huluwa-test.apk", "apkSize", apk.length,
                "sha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(apk)),
                "signatureSha256", "b".repeat(64), "completedAt", java.time.Instant.now().toString()));
        var payload = new LinkedHashMap<>(heartbeat);
        payload.putAll(Map.of("taskId", id, "result", result));
        assertThrows(BusinessException.class, () -> service.pollAgent(node, token, payload));
        assertEquals(false, service.latestRelease().get("available"));
        service.uploadAgent(node, token, id, "huluwa-test.apk", new ByteArrayInputStream(apk));
        result.put("sha256", "0".repeat(64));
        assertThrows(BusinessException.class, () -> service.pollAgent(node, token, payload));
        result.put("sha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(apk)));
        service.pollAgent(node, token, payload);
        service.deleteServer(node);
        assertEquals(true, service.latestRelease().get("available"));
        assertArrayEquals(apk, Files.readAllBytes(service.download(id, false)));
    }

    @Test void versionSequenceSurvivesNodeSwitchAndFailedBuild() throws Exception {
        long id = ((Number)service.submit("BUILD", "admin", node).get("id")).longValue();
        service.pollAgent(node, token, Map.of("taskId", id, "result", Map.of("status", "FAILED", "stage", "DONE")));
        var other = service.createServer("Linux remote");
        String second = (String)other.get("id");
        service.pollAgent(second, (String)other.get("token"), heartbeat);
        assertEquals(1011, service.submit("BUILD", "admin", second).get("versionCode"));
    }

    @Test void windowsAndExistingLinuxShareProductionVersionCounter() throws Exception {
        jdbc.update("INSERT INTO sys_config(cfg_key,cfg_value,updated_at) VALUES('android.build.version-code','1043','fixture')");
        var windows = service.submit("BUILD", "admin", node);
        assertEquals(1044, windows.get("versionCode"));
        assertEquals("1.0.44", windows.get("versionName"));
        long id = ((Number)windows.get("id")).longValue();
        service.pollAgent(node, token, Map.of("taskId", id, "result", Map.of("status", "FAILED", "stage", "DONE")));
        Path mailbox = directory.resolve("mailbox");
        var mapper = new ObjectMapper();
        mapper.writeValue(mailbox.resolve("worker.json").toFile(), Map.of("heartbeat", java.time.Instant.now().getEpochSecond(), "signingReady", true));
        mapper.writeValue(mailbox.resolve("source.json").toFile(), heartbeat.get("source"));
        var linux = service.submit("BUILD", "admin", "local");
        assertEquals(1045, linux.get("versionCode"));
        assertEquals("1.0.45", linux.get("versionName"));
        assertEquals("local", linux.get("serverId"));
        assertTrue(Files.exists(mailbox.resolve("tasks").resolve(linux.get("id").toString()).resolve("request.json")));
    }

    @Test void migratesExistingDatabaseWithoutLosingHistory() {
        long id = ((Number)service.submit("UPDATE", "admin", node).get("id")).longValue();
        jdbc.execute("ALTER TABLE android_build_task DROP COLUMN server_id");
        new com.huluwa.console.config.DatabaseInitializer(jdbc).run(null);
        assertEquals("local", service.task(id).get("serverId"));
        assertEquals("UPDATE", service.task(id).get("type"));
    }
}
