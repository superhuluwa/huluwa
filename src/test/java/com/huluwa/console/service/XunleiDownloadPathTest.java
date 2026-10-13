package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 本地协议桩验证原生迅雷不会把指定剧名目录替换成默认目录。 */
class XunleiDownloadPathTest {
    private static final String CGI = "/cgi/ThirdParty/xunlei/index.cgi";
    private static final String ROOT = "/vol1/1000/downloads";
    private static final String PATH = ROOT + "/剧名 A_B";
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JsonNode> submitted = new ArrayList<>();
    private HttpServer server;
    private XunleiService service;
    private boolean folderExists;
    private boolean rootAccessible = true;
    private boolean rootOnlyInDiskMounts;
    private int foldersCreated;

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(CGI, this::respond);
        server.start();
        ConfigService config = mock(ConfigService.class);
        when(config.get(XunleiService.CFG_URL)).thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + CGI);
        when(config.get(XunleiService.CFG_TOKEN)).thenReturn("test-fnos-token");
        service = new XunleiService(config);
    }

    @AfterEach
    void stopServer() { server.stop(0); }

    private void respond(HttpExchange request) throws IOException {
        String path = request.getRequestURI().getPath().substring(CGI.length());
        String query = URLDecoder.decode(String.valueOf(request.getRequestURI().getRawQuery()), StandardCharsets.UTF_8);
        Object body;
        if (path.equals("/")) {
            byte[] home = "<script>function uiauth(value){return \"abc.def.ghi\"}</script>".getBytes(StandardCharsets.UTF_8);
            request.sendResponseHeaders(200, home.length);
            request.getResponseBody().write(home);
            request.close();
            return;
        } else if (path.equals("/drive/v1/tasks")) {
            body = query.contains("type=user#runner")
                    ? Map.of("tasks", List.of(Map.of("params", Map.of("target", "test#device"))))
                    : Map.of("tasks", List.of(Map.of("id", "xl-task", "phase", "PHASE_TYPE_COMPLETE", "message", "完成")));
        } else if (path.equals("/drive/v1/resource/list")) {
            body = Map.of("list", Map.of("resources", List.of(Map.of("name", "episode.mp4", "file_size", 100))));
        } else if (path.equals("/device/download_paths")) {
            List<Map<String, Object>> paths = new ArrayList<>();
            paths.add(Map.of("Id", "wrong-default", "RealPath", "/wrong/default", "IsDefault", true));
            if (rootAccessible && !rootOnlyInDiskMounts) paths.add(Map.of("Id", "downloads-root", "RealPath", ROOT));
            body = paths;
        } else if (path.equals("/drive/v1/files") && request.getRequestMethod().equals("POST")) {
            JsonNode created = mapper.readTree(request.getRequestBody());
            assertEquals("downloads-root", created.path("parent_id").asText());
            assertEquals("剧名 A_B", created.path("name").asText());
            assertEquals("drive#folder", created.path("kind").asText());
            folderExists = true;
            foldersCreated++;
            body = Map.of("file", folder());
        } else if (path.equals("/drive/v1/files")) {
            List<String> categories = java.util.Arrays.stream(request.getRequestURI().getRawQuery().split("&"))
                    .filter(part -> part.startsWith("with="))
                    .map(part -> URLDecoder.decode(part.substring(5), StandardCharsets.UTF_8)).toList();
            if (query.contains("parent_id=downloads-root")) {
                body = Map.of("files", folderExists ? List.of(folder()) : List.of());
            } else if (rootOnlyInDiskMounts && rootAccessible && categories.contains("withCategoryDiskMountPath")) {
                body = Map.of("files", List.of(Map.of("id", "downloads-root", "name", "downloads",
                        "params", Map.of("RealPath", ROOT + "/", "category_id", "DiskMountPath", "is_write", "true"))));
            } else {
                body = Map.of("files", List.of());
            }
        } else if (path.equals("/drive/v1/task")) {
            submitted.add(mapper.readTree(request.getRequestBody()));
            body = Map.of("task", Map.of("id", "xl-task"));
        } else {
            request.sendResponseHeaders(404, -1);
            request.close();
            return;
        }
        byte[] encoded = mapper.writeValueAsBytes(body);
        request.sendResponseHeaders(200, encoded.length);
        request.getResponseBody().write(encoded);
        request.close();
    }

    private Map<String, Object> folder() {
        return Map.of("id", "series-folder", "name", "剧名 A_B", "params", Map.of("RealPath", PATH));
    }

    @Test
    void explicitNativePathCreatesAndReusesSeriesFolderAndTracksTask() {
        String magnet = "magnet:?xt=urn:btih:" + "a".repeat(40);
        assertTrue(service.add(List.of(magnet), PATH).getFirst().ok());
        assertTrue(service.add(List.of(magnet), PATH).getFirst().ok());
        assertEquals(1, foldersCreated);
        assertEquals(2, submitted.size());
        for (JsonNode task : submitted) {
            assertEquals("series-folder", task.path("params").path("parent_folder_id").asText());
            assertEquals(PATH, task.path("params").path("parent_folder_path").asText());
        }
        assertEquals("PHASE_TYPE_COMPLETE", service.task("xl-task").phase());
    }

    @Test
    void nativeDiskMountIsVisibleOnlyWithRepeatedCategoryParameters() {
        rootOnlyInDiskMounts = true;
        XunleiService.TaskResult result = service.add(List.of("magnet:?xt=urn:btih:" + "d".repeat(40)), PATH).getFirst();
        assertTrue(result.ok(), result.message());
        assertEquals(1, foldersCreated);
        assertEquals("series-folder", submitted.getFirst().path("params").path("parent_folder_id").asText());
        assertEquals(PATH, submitted.getFirst().path("params").path("parent_folder_path").asText());
    }

    @Test
    void inaccessibleExplicitPathFailsWithoutFallingBackToNativeDefault() {
        rootAccessible = false;
        XunleiService.TaskResult result = service.add(List.of("magnet:?xt=urn:btih:" + "b".repeat(40)), PATH).getFirst();
        assertFalse(result.ok());
        assertTrue(result.message().contains("目录列表中未找到保存目录"));
        assertEquals(0, submitted.size());
    }

    @Test
    void ordinaryXunleiSessionStillUsesConfiguredNativeDefault() {
        assertTrue(service.add(List.of("magnet:?xt=urn:btih:" + "c".repeat(40))).getFirst().ok());
        assertEquals("wrong-default", submitted.getFirst().path("params").path("parent_folder_id").asText());
        assertEquals("/wrong/default", submitted.getFirst().path("params").path("parent_folder_path").asText());
        assertEquals(0, foldersCreated);
    }
}
