package com.huluwa.console.service;

import com.huluwa.console.ws.ChatPushService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

class ChatServiceTest {

    @TempDir
    Path data;

    @Test
    void submittedMessageIdComesFromItsInsertConnection() {
        // 每次 JDBC 调用新建连接：旧的 SELECT last_insert_rowid() 会在下一连接返回 0。
        var source = new DriverManagerDataSource("jdbc:sqlite:" + data.resolve("chat.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        JdbcTemplate jdbc = new JdbcTemplate(source);
        QBittorrentService qb = mock(QBittorrentService.class);
        when(qb.settings()).thenReturn(new QBittorrentService.Settings(null, null, null, null));
        ChatService service = spy(new ChatService(jdbc, mock(DyttSearchService.class),
                mock(DongPianSearchService.class), mock(BtbtlaSearchService.class),
                mock(FanqieSearchService.class), mock(MovieCatalogService.class), qb,
                mock(MessageService.class), mock(MovieSubscriptionService.class), mock(VideoParseService.class),
                mock(XunleiService.class), mock(XunleiWinService.class),
                mock(ChatRequestService.class), mock(ChatPushService.class)));
        // 本例只验证同步 INSERT 返回值；隔离异步聊天业务，避免临时库清理时仍被线程占用。
        doNothing().when(service).processRequest(anyString(), anyString(), anyString());
        try {
            var submitted = service.submit("magnet", "request-fixture", "fixture text");
            long id = submitted.userMessage().id();
            assertTrue(id > 0);
            assertEquals("fixture text", jdbc.queryForObject("SELECT content FROM chat_message WHERE id = ?", String.class, id));
            assertEquals("magnet", jdbc.queryForObject("SELECT session_id FROM chat_message WHERE id = ?", String.class, id));
        } finally {
            service.shutdown();
        }
    }
}
