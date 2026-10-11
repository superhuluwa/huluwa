package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 磁力下载会话的纯函数单测：磁力链接识别 / 服务地址归一化 / qB 状态与体积文案。
 * 网络与登录路径依赖真实 qB，不在此覆盖（由「测试连接」在前端联调）。
 */
class QBittorrentServiceTest {

    @Test
    void extractSingleMagnetKeepsHashAndParams() {
        String text = "magnet:?xt=urn:btih:0123456789abcdef0123456789abcdef01234567&dn=%E7%94%B5%E5%BD%B1";
        List<String> found = QBittorrentService.extractMagnets(text);
        assertEquals(1, found.size());
        assertEquals(text, found.get(0));
    }

    /** 中文说明、全角标点、右括号都不该粘进链接（否则 qB 会判定链接非法） */
    @Test
    void extractMagnetTrimsTrailingChineseText() {
        List<String> found = QBittorrentService.extractMagnets(
                "麻烦下载 magnet:?xt=urn:btih:abcdefabcdefabcdefabcdefabcdefabcdefabcd（谢谢）");
        assertEquals(1, found.size());
        assertEquals("magnet:?xt=urn:btih:abcdefabcdefabcdefabcdefabcdefabcdefabcd", found.get(0));
    }

    @Test
    void extractMultipleMagnetsKeepsOrderAndDeduplicates() {
        String a = "magnet:?xt=urn:btih:1111111111111111111111111111111111111111";
        String b = "magnet:?xt=urn:btih:2222222222222222222222222222222222222222";
        List<String> found = QBittorrentService.extractMagnets(a + "\n" + b + "\n" + a);
        assertEquals(List.of(a, b), found);
    }

    @Test
    void extractIgnoresPlainTextAndEmptyInput() {
        assertTrue(QBittorrentService.extractMagnets("随便聊聊").isEmpty());
        assertTrue(QBittorrentService.extractMagnets("").isEmpty());
        assertTrue(QBittorrentService.extractMagnets(null).isEmpty());
        // 只有前缀没有内容的伪链接不算
        assertTrue(QBittorrentService.extractMagnets("magnet:?").isEmpty());
    }

    @Test
    void normalizeBaseUrlTrimsSlashAndSpace() {
        assertEquals("http://192.168.3.202:8080", QBittorrentService.normalizeBaseUrl(" http://192.168.3.202:8080/ "));
        assertEquals("http://qb.local:8080", QBittorrentService.normalizeBaseUrl("http://qb.local:8080//"));
        assertEquals("", QBittorrentService.normalizeBaseUrl(null));
    }

    @Test
    void stateLabelMapsKnownStatesAndKeepsUnknown() {
        assertEquals("下载中", QBittorrentService.stateLabel("downloading"));
        assertEquals("做种中", QBittorrentService.stateLabel("uploading"));
        assertEquals("已暂停", QBittorrentService.stateLabel("pausedDL"));
        assertEquals("weird-state", QBittorrentService.stateLabel("weird-state"));
        assertEquals("未知状态", QBittorrentService.stateLabel(null));
    }

    @Test
    void humanSizeUsesBinaryUnits() {
        assertEquals("0 B", QBittorrentService.humanSize(0));
        assertEquals("512 B", QBittorrentService.humanSize(512));
        assertEquals("1.00 KB", QBittorrentService.humanSize(1024));
        assertEquals("1.50 MB", QBittorrentService.humanSize(1024L * 1024 * 3 / 2));
        assertEquals("2.00 GB", QBittorrentService.humanSize(2L * 1024 * 1024 * 1024));
    }

    @Test
    void humanEtaHandlesUnknownSentinel() {
        assertEquals("—", QBittorrentService.humanEta(0));
        assertEquals("—", QBittorrentService.humanEta(8640000L));
        assertEquals("2 小时 30 分", QBittorrentService.humanEta(9000L));
        assertEquals("1 分 5 秒", QBittorrentService.humanEta(65L));
        assertEquals("45 秒", QBittorrentService.humanEta(45L));
    }

    @Test
    void humanSpeedFallsBackWhenIdle() {
        assertEquals("—", QBittorrentService.humanSpeed(0));
        assertEquals("1.00 MB/s", QBittorrentService.humanSpeed(1024L * 1024));
    }

    @Test
    void magnetSessionKeyIsStable() {
        // 「磁力下载」会话键与 schema 内置 id 必须一致，前端 query 与后端路由都依赖它
        assertEquals("magnet", ChatService.MAGNET_SESSION);
        assertFalse(ChatService.MAGNET_SESSION.equals(ChatService.MOVIE_SESSION));
    }
}
