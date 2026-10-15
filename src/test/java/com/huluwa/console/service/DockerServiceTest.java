package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectContainerCmd;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.transport.DockerHttpClient;
import com.huluwa.console.config.AppProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DockerServiceTest {

    private static final String CONTAINER_ID = "test-container";
    private final ObjectMapper applicationMapper = new ObjectMapper();
    private final DockerClient dockerClient = mock(DockerClient.class);
    private final InspectContainerCmd inspectCommand = mock(InspectContainerCmd.class);
    private final DockerHttpClient httpClient = mock(DockerHttpClient.class);
    private DockerService service;

    @BeforeEach
    void setup() {
        service = new DockerService(mock(JdbcTemplate.class), new AppProperties(), applicationMapper);
        ReflectionTestUtils.setField(service, "client", dockerClient);
        ReflectionTestUtils.setField(service, "httpClient", httpClient);
        ReflectionTestUtils.setField(service, "resolvedApiVersion", "1.44");
        when(dockerClient.inspectContainerCmd(CONTAINER_ID)).thenReturn(inspectCommand);
    }

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void inspectPreservesEmptyPortAndVolumeObjectsWithoutChangingApplicationMapper() throws IOException {
        when(inspectCommand.exec()).thenReturn(inspectResponse(false));

        Map<String, Object> details = service.inspectContainer(CONTAINER_ID);
        Map<?, ?> config = assertInstanceOf(Map.class, details.get("Config"));
        assertEquals(CONTAINER_ID, details.get("Id"));
        assertEquals("example:latest", config.get("Image"));
        assertEquals(Map.of("8000/tcp", Map.of()), config.get("ExposedPorts"));
        assertEquals(Map.of("/data", Map.of()), config.get("Volumes"));
        assertTrue(applicationMapper.isEnabled(SerializationFeature.FAIL_ON_EMPTY_BEANS));
        assertThrows(IOException.class, () -> applicationMapper.writeValueAsString(new Object()));
    }

    @Test
    void readsTtyLogsFromContainerWithExposedPort() throws IOException {
        assertLogs(true);
    }

    @Test
    void demultiplexesNonTtyLogsFromContainerWithExposedPort() throws IOException {
        assertLogs(false);
    }

    private void assertLogs(boolean tty) throws IOException {
        when(inspectCommand.exec()).thenReturn(inspectResponse(tty));
        String expected = "容器日志：启动成功\n";
        byte[] payload = expected.getBytes(StandardCharsets.UTF_8);
        byte[] body = tty ? payload : ByteBuffer.allocate(8 + payload.length)
                .put((byte) 1).put(new byte[3]).putInt(payload.length).put(payload).array();
        DockerHttpClient.Response response = mock(DockerHttpClient.Response.class);
        when(httpClient.execute(any(DockerHttpClient.Request.class))).thenReturn(response);
        when(response.getStatusCode()).thenReturn(200);
        when(response.getBody()).thenReturn(new ByteArrayInputStream(body));

        assertEquals(expected, service.containerLogs(CONTAINER_ID, false, true, true, "100"));
        ArgumentCaptor<DockerHttpClient.Request> request = ArgumentCaptor.forClass(DockerHttpClient.Request.class);
        verify(httpClient).execute(request.capture());
        assertEquals("/v1.44/containers/" + CONTAINER_ID + "/logs?stdout=1&stderr=1&follow=0&tail=100",
                request.getValue().path());
    }

    private InspectContainerResponse inspectResponse(boolean tty) throws IOException {
        return applicationMapper.readValue("""
                {"Id":"test-container","Config":{"Image":"example:latest","Tty":%s,
                "ExposedPorts":{"8000/tcp":{}},"Volumes":{"/data":{}}}}
                """.formatted(tty), InspectContainerResponse.class);
    }
}
