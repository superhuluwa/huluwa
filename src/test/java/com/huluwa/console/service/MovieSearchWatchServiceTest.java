package com.huluwa.console.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.ws.ChatPushService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MovieSearchWatchServiceTest {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final String KEYWORD = "测试电影";
    private static final MovieSearchService.MovieItem MOVIE = new MovieSearchService.MovieItem(
            "48089", KEYWORD, "2026", "中国", "剧情", "", "https://www.dygangs.net/test/48089.htm");

    @TempDir
    Path data;
    private JdbcTemplate jdbc;
    private MovieSearchService search;
    private ChatPushService push;
    private ChatService chat;
    private MovieSearchWatchService watches;
    private DataSourceTransactionManager transactionManager;

    @BeforeEach
    void prepare() {
        var source = new DriverManagerDataSource("jdbc:sqlite:" + data.resolve("watches.db"));
        new ResourceDatabasePopulator(new ClassPathResource("schema.sql")).execute(source);
        jdbc = new JdbcTemplate(source);
        transactionManager = new DataSourceTransactionManager(source);
        search = mock(MovieSearchService.class);
        push = mock(ChatPushService.class);
        MovieSubscriptionService subscriptions = mock(MovieSubscriptionService.class);
        when(subscriptions.subscribedUrls()).thenReturn(Set.of());
        chat = new ChatService(jdbc, search, mock(DyttSearchService.class), mock(MovieHlsService.class), mock(QBittorrentService.class),
                new MessageService(jdbc), subscriptions, mock(VideoParseService.class), mock(XunleiService.class),
                mock(XunleiWinService.class), mock(ChatRequestService.class), push);
        watches = new MovieSearchWatchService(jdbc, search, chat, push, transactionManager, 30);
    }

    @AfterEach
    void close() {
        chat.shutdown();
    }

    @Test
    void startsInThirtyMinutesAndDeduplicatesNormalizedKeywords() {
        LocalDateTime before = LocalDateTime.now();
        var first = watches.start("电影港搜索 Dune");
        var duplicate = watches.start(" dune ");
        assertEquals(first.id(), duplicate.id());
        assertEquals(first.nextCheckAt(), duplicate.nextCheckAt());
        assertEquals(30, first.intervalMinutes());
        assertEquals(1, watches.list().size());
        LocalDateTime due = LocalDateTime.parse(first.nextCheckAt(), TIME_FORMAT);
        assertTrue(due.isAfter(before.plusMinutes(29)));
        assertTrue(due.isBefore(before.plusMinutes(31)));
        watches.scheduledScan();
        verifyNoInteractions(search);
    }

    @Test
    void emptySearchStaysActiveAndWaitsForNextInterval() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenReturn(List.of());
        watches.scheduledScan();
        var current = watches.list().getFirst();
        assertEquals("watching", current.status());
        assertNotNull(current.lastCheckAt());
        assertTrue(LocalDateTime.parse(current.nextCheckAt(), TIME_FORMAT).isAfter(LocalDateTime.now().plusMinutes(29)));
        assertEquals(0, count("message"));
        watches.scheduledScan();
        verify(search, times(1)).search(KEYWORD);
    }

    @Test
    void foundResourcesPersistCardsAndOneSystemNotification() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenReturn(List.of(MOVIE));
        watches.scheduledScan();
        var current = watches.list().getFirst();
        assertEquals("found", current.status());
        assertEquals(1, current.resultCount());
        assertNotNull(current.foundAt());
        assertNull(current.nextCheckAt());
        assertEquals(1, count("message"));
        assertEquals(2, count("chat_message"));
        assertEquals("电影港监听", jdbc.queryForObject("SELECT source FROM message", String.class));
        assertTrue(jdbc.queryForObject("SELECT content FROM message", String.class).contains(MOVIE.url()));
        verify(push).messages(eq(ChatService.MOVIE_SESSION), argThat(rows -> rows.size() == 2));
        verify(push).messages(eq(ChatService.SYSTEM_SESSION), argThat(rows -> rows.size() == 1
                && rows.getFirst().id() > 0 && "notify".equals(rows.getFirst().type())));
        watches.scheduledScan();
        assertEquals(1, count("message"));
        verify(search, times(1)).search(KEYWORD);
    }

    @Test
    void cancellationDuringSearchPreventsLateNotification() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenAnswer(invocation -> {
            watches.cancel(watch.id());
            return List.of(MOVIE);
        });
        watches.scheduledScan();
        assertEquals("cancelled", watches.list().getFirst().status());
        assertEquals(0, count("message"));
        assertEquals(0, count("chat_message"));
        verify(push, never()).messages(anyString(), anyList());
    }

    @Test
    void restartingDuringSearchRejectsResultsFromThePreviousRun() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenAnswer(invocation -> {
            watches.cancel(watch.id());
            watches.start(KEYWORD);
            return List.of(MOVIE);
        });
        watches.scheduledScan();
        var restarted = watches.list().getFirst();
        assertEquals("watching", restarted.status());
        assertNull(restarted.lastCheckAt());
        assertTrue(restarted.version() > watch.version());
        assertEquals(0, count("message"));
        assertEquals(0, count("chat_message"));
    }

    @Test
    void searchErrorsAreRetriedWithoutSendingFailureNotifications() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenThrow(new RuntimeException("连接超时"));
        watches.scheduledScan();
        var failed = watches.list().getFirst();
        assertEquals("watching", failed.status());
        assertEquals("连接超时", failed.checkError());
        assertNotNull(failed.nextCheckAt());
        assertEquals(0, count("message"));
        makeDue(watch.id());
        doReturn(List.of(MOVIE)).when(search).search(KEYWORD);
        watches.scheduledScan();
        assertEquals("found", watches.list().getFirst().status());
        assertNull(watches.list().getFirst().checkError());
        assertEquals(1, count("message"));
    }

    @Test
    void notificationFailureRollsBackCompletionAndCards() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenReturn(List.of(MOVIE));
        jdbc.execute("""
                CREATE TRIGGER reject_notification BEFORE INSERT ON message
                BEGIN SELECT RAISE(ABORT, '通知写入失败'); END
                """);
        watches.scheduledScan();
        assertEquals("watching", watches.list().getFirst().status());
        assertEquals(0, count("message"));
        assertEquals(0, count("chat_message"));
        verify(push, never()).messages(anyString(), anyList());
        jdbc.execute("DROP TRIGGER reject_notification");
        makeDue(watch.id());
        watches.scheduledScan();
        assertEquals("found", watches.list().getFirst().status());
        assertEquals(1, count("message"));
    }

    @Test
    void persistedWatchesResumeInAnotherServiceInstance() {
        var watch = watches.start(KEYWORD);
        makeDue(watch.id());
        when(search.search(KEYWORD)).thenReturn(List.of(MOVIE));
        var restarted = new MovieSearchWatchService(jdbc, search, chat, push, transactionManager, 30);
        assertEquals(watch.id(), restarted.list().getFirst().id());
        restarted.scheduledScan();
        assertEquals("found", restarted.list().getFirst().status());
        assertEquals(1, count("message"));
    }

    @Test
    void onlyEmptyMovieSearchRepliesCarryTheWatchAction() throws Exception {
        when(search.search(KEYWORD)).thenReturn(List.of());
        chat.processRequest("empty-fixture", "movie", "电影港 " + KEYWORD);
        var empty = chat.messages("movie", null, 50).getFirst();
        var payload = new ObjectMapper().readTree(empty.payload());
        assertEquals("movie-search-watch", payload.path("kind").asText());
        assertTrue(payload.path("noResults").asBoolean());
        assertEquals(KEYWORD, payload.path("keyword").asText());
        when(search.search(KEYWORD)).thenThrow(new RuntimeException("连接超时"));
        chat.processRequest("error-fixture", "movie", KEYWORD);
        var error = chat.messages("movie", null, 50).getLast();
        assertTrue(error.content().startsWith("搜索失败："));
        assertNull(error.payload());
    }

    private void makeDue(long id) {
        jdbc.update("UPDATE movie_search_watch SET next_check_at = '2000-01-01 00:00:00' WHERE id = ?", id);
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
