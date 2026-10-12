package com.huluwa.console.service;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 电影港订阅解析单测：用真实详情页快照（src/test/resources/dygangs-detail-48089.html，
 * 剧集「斗破苍穹 第5季」，抓于 2026-10-11）锁定：
 *  - 每集磁力解析（含 &amp; 实体还原，否则 tracker 参数会失效）；
 *  - 清晰度识别与「同集取最高清晰度」；
 *  - 文件名补剧名 / 保存目录归类。
 */
class MovieSubscriptionServiceTest {

    private static String detailFixture() throws Exception {
        try (InputStream in = MovieSubscriptionServiceTest.class
                .getResourceAsStream("/dygangs-detail-48089.html")) {
            assertNotNull(in, "缺少测试用例资源 dygangs-detail-48089.html");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void parseMagnetsExtractsEveryEpisodeAndDecodesEntities() throws Exception {
        List<MovieSearchService.MagnetItem> magnets = MovieSearchService.parseMagnets(detailFixture());

        assertFalse(magnets.isEmpty(), "应解析出磁力");
        assertTrue(magnets.size() > 100, "该剧详情页有大量分集磁力，实际 " + magnets.size());

        MovieSearchService.MagnetItem first = magnets.get(0);
        assertEquals("f58c739d1720a85612ecb96d14598018278e37ce", first.hash());
        assertTrue(first.magnet().contains("&dn="), "HTML 实体必须还原成 &，否则 qB 拿不到 tracker 参数");
        assertFalse(first.magnet().contains("&amp;"), "不应残留 &amp;");
        assertEquals("2160P", first.quality());
        assertEquals("208", first.episode());
        assertEquals("斗破苍穹 第5季208-209.2160p.HD国语中字无水印.mp4", first.title());

        // 同一 info hash 只保留首次出现（站点会重复挂同一集）
        long distinct = magnets.stream().map(MovieSearchService.MagnetItem::hash).distinct().count();
        assertEquals(magnets.size(), distinct, "磁力列表不应有重复 info hash");
    }

    /**
     * 「查看磁力」要求按集数倒序：接口层排序后最新一集在最前，且不能改坏订阅巡检依赖的原始解析顺序。
     */
    @Test
    void compareEpisodeDescSortsLatestEpisodeFirst() throws Exception {
        List<MovieSearchService.MagnetItem> magnets = MovieSearchService.parseMagnets(detailFixture());
        List<MovieSearchService.MagnetItem> sorted = new ArrayList<>(magnets);
        sorted.sort(MovieSubscriptionService::compareEpisodeDesc);

        int latest = sorted.stream()
                .map(MovieSearchService.MagnetItem::episode)
                .filter(episode -> episode != null && !episode.isBlank())
                .mapToInt(Integer::parseInt)
                .max()
                .orElseThrow();
        assertEquals("214", String.valueOf(latest), "快照里的最新集数应稳定为 214");
        assertEquals("214", sorted.get(0).episode(), "最新一集必须排在第一行");

        int previous = Integer.MAX_VALUE;
        for (MovieSearchService.MagnetItem item : sorted) {
            int current = Integer.parseInt(item.episode());
            assertTrue(current <= previous, "集数必须按倒序排列，异常位置：" + item.episode());
            previous = current;
        }

        // 详情页解析顺序保持不变，订阅巡检仍按页面顺序处理基线
        assertEquals("208", magnets.get(0).episode(), "解析层不应被接口层的倒序排序影响");
    }

    /** 无法识别集号的条目放在末尾，不会插到最新一集前面 */
    @Test
    void compareEpisodeDescKeepsUnknownEpisodesLast() {
        List<MovieSearchService.MagnetItem> items = new ArrayList<>(List.of(
                new MovieSearchService.MagnetItem("特别篇.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "a".repeat(40), "a".repeat(40), "1080P", null),
                new MovieSearchService.MagnetItem("剧 第1季02.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "b".repeat(40), "b".repeat(40), "1080P", "2"),
                new MovieSearchService.MagnetItem("剧 第1季10.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "c".repeat(40), "c".repeat(40), "1080P", "10")));
        items.sort(MovieSubscriptionService::compareEpisodeDesc);

        assertEquals("10", items.get(0).episode(), "两位数集号必须排在个位数之前");
        assertEquals("2", items.get(1).episode());
        assertEquals(null, items.get(2).episode(), "识别不出集号的条目排在最后");
    }

    @Test
    void parseTitleStripsSiteSuffix() throws Exception {
        assertEquals("斗破苍穹 第5季", MovieSearchService.parseTitle(detailFixture()));
    }

    @Test
    void qualityRankPrefersHigherResolution() {
        assertTrue(MovieSearchService.qualityRank("2160P") > MovieSearchService.qualityRank("1080P"));
        assertTrue(MovieSearchService.qualityRank("1080P") > MovieSearchService.qualityRank("720P"));
        assertTrue(MovieSearchService.qualityRank("720P") > MovieSearchService.qualityRank("480P"));
        assertTrue(MovieSearchService.qualityRank("480P") > MovieSearchService.qualityRank("360P"));
        assertEquals(-1, MovieSearchService.qualityRank("OTHER"));
        assertEquals("2160P", MovieSearchService.qualityOf("某剧 第1季01.4K.HDR.mp4"));
        assertEquals("1080P", MovieSearchService.qualityOf("某剧 第1季02.1080p.mp4"));
        assertEquals("OTHER", MovieSearchService.qualityOf("某剧 第1季03.mp4"));
        assertEquals("OTHER", MovieSearchService.qualityOf(null));
    }

    @Test
    void episodeOfHandlesSeasonAndExplicitFormats() {
        assertEquals("212", MovieSearchService.episodeOf("斗破苍穹 第5季212.2160p.mp4"));
        assertEquals("208", MovieSearchService.episodeOf("斗破苍穹 第5季208-209.2160p.mp4"));
        assertEquals("12", MovieSearchService.episodeOf("Some.Show.S05E12.1080p.mkv"));
        assertEquals("2", MovieSearchService.episodeOf("某剧 第02集 1080P"));
        assertEquals("2", MovieSearchService.episodeOf("剧名 第5季02.1080p.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("全集打包 1080P"));
    }

    /** 同一集同时给出 1080P / 2160P 时，只提交最高的那条 */
    @Test
    void pickByEpisodeKeepsHighestQualityPerEpisode() {
        List<MovieSearchService.MagnetItem> fresh = List.of(
                new MovieSearchService.MagnetItem("剧 第1季01.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "a".repeat(40), "a".repeat(40), "1080P", "01"),
                new MovieSearchService.MagnetItem("剧 第1季01.2160p.mp4",
                        "magnet:?xt=urn:btih:" + "b".repeat(40), "b".repeat(40), "2160P", "01"),
                new MovieSearchService.MagnetItem("剧 第1季02.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "c".repeat(40), "c".repeat(40), "1080P", "02"));

        List<MovieSearchService.MagnetItem> picks = MovieSubscriptionService.pickByEpisode(fresh);

        assertEquals(2, picks.size(), "01 集只保留 2160P，02 集保留 1080P");
        assertTrue(picks.stream().anyMatch(i -> "2160P".equals(i.quality())));
        assertTrue(picks.stream().noneMatch(i -> "1080P".equals(i.quality()) && "01".equals(i.episode())));
        assertTrue(picks.stream().anyMatch(i -> "1080P".equals(i.quality()) && "02".equals(i.episode())));
    }

    /** 识别不出集号的条目各自独立保留，避免整季打包条目被吞掉 */
    @Test
    void pickByEpisodeKeepsUnnumberedItemsSeparately() {
        List<MovieSearchService.MagnetItem> fresh = List.of(
                new MovieSearchService.MagnetItem("第一季全集打包.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "1".repeat(40), "1".repeat(40), "1080P", null),
                new MovieSearchService.MagnetItem("第二季全集打包.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "2".repeat(40), "2".repeat(40), "1080P", null));
        assertEquals(2, MovieSubscriptionService.pickByEpisode(fresh).size());
    }

    @Test
    void savePathGroupsEverySeriesIntoOwnFolder() {
        // 默认根目录 + 剧名子目录：同一部剧永远落在自己的文件夹里，不会串到别的剧
        assertEquals("/downloads/斗破苍穹 第5季", MovieSubscriptionService.savePathUnder(null, "斗破苍穹 第5季"));
        assertEquals("/downloads/斗破苍穹 第5季", MovieSubscriptionService.savePathUnder("/downloads/", "斗破苍穹 第5季"));
        // 目录名里的非法字符会被替换，不会串到别的目录层级
        assertEquals("/downloads/剧名 A_B", MovieSubscriptionService.savePathUnder(null, "剧名 A/B"));
        assertEquals("/downloads/剧名_第1季", MovieSubscriptionService.savePathUnder(null, "剧名:第1季"));
        // 已配置根目录（如 qB 的默认保存路径）时用它作为根
        assertEquals("/volume1/video/剧名", MovieSubscriptionService.savePathUnder("/volume1/video", "剧名"));
        // 空片名兜底成「未命名」，不会退化成根目录导致所有剧混在一起
        assertEquals("/downloads/未命名", MovieSubscriptionService.savePathUnder(null, "  "));
        assertEquals("未命名", MovieSubscriptionService.sanitizeFolder(null));
    }

    @Test
    void withSeriesPrefixAddsTitleWhenMissing() {
        assertEquals("斗破苍穹 第5季 212.2160p.mp4",
                MovieSubscriptionService.withSeriesPrefix("212.2160p.mp4", "斗破苍穹 第5季"));
        // 已有剧名不再重复前置
        assertEquals("斗破苍穹 第5季212.2160p.mp4",
                MovieSubscriptionService.withSeriesPrefix("斗破苍穹 第5季212.2160p.mp4", "斗破苍穹 第5季"));
        assertEquals("片子 01.mp4", MovieSubscriptionService.withSeriesPrefix("01.mp4", "片子"));
    }

    /** 站点在正片之后补挂低清晰度版本时，同一集不该重复下载（由 handledEpisodes 过滤保证） */
    @Test
    void laterLowerQualityOfSameEpisodeIsNotADifferentEpisode() {
        List<MovieSearchService.MagnetItem> fresh = List.of(
                new MovieSearchService.MagnetItem("剧 第1季05.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "d".repeat(40), "d".repeat(40), "1080P", "5"));
        // 「已处理集」过滤发生在 pickByEpisode 之后：这里验证集号本身稳定，5 与 05 会归并
        assertEquals("5", fresh.get(0).episode());
        assertEquals("5", MovieSearchService.episodeOf("剧 第1季05.2160p.mp4"));
    }

    /** 手动下载的条目描述：集号 + 清晰度 + 剧名；缺字段时也要能给出可读文案 */
    @Test
    void describePartsCoversManualDownloadNotice() {
        assertEquals("第 12 集 [2160P] 斗破苍穹 第5季",
                MovieSubscriptionService.describeParts("12", "2160P", "斗破苍穹 第5季"));
        assertEquals("[1080P] 某剧", MovieSubscriptionService.describeParts(null, "1080P", "某剧"));
        assertEquals("[未知清晰度] 某剧", MovieSubscriptionService.describeParts("", null, "某剧"));
        assertEquals("[1080P] 未命名剧集", MovieSubscriptionService.describeParts(null, "1080P", null));
    }

    /** 手动下载同样落到「根目录 / 剧名」，且与订阅巡检使用同一套目录规则 */
    @Test
    void manualDownloadUsesSameFolderRuleAsSubscription() {
        String folder = MovieSubscriptionService.savePathUnder("/downloads", "斗破苍穹 第5季");
        assertEquals("/downloads/斗破苍穹 第5季", folder);
        // 剧名里的斜杠不会造成多级目录穿透
        assertEquals("/downloads/剧名 A_B", MovieSubscriptionService.savePathUnder("/downloads", "剧名 A/B"));
        assertEquals(folder, MovieSubscriptionService.savePathUnder(MovieSubscriptionService.DEFAULT_SAVE_ROOT, "斗破苍穹 第5季"));
    }

    @Test
    void completedStatesCoverSeedingPhases() {
        assertTrue(MovieSubscriptionService.isCompletedState("uploading"));
        assertTrue(MovieSubscriptionService.isCompletedState("stalledUP"));
        assertTrue(MovieSubscriptionService.isCompletedState("pausedUP"));
        assertFalse(MovieSubscriptionService.isCompletedState("downloading"));
        assertFalse(MovieSubscriptionService.isCompletedState("metaDL"));
        assertTrue(MovieSubscriptionService.isFailedState("error"));
        assertTrue(MovieSubscriptionService.isFailedState("missingFiles"));
        assertFalse(MovieSubscriptionService.isFailedState("downloading"));
    }
}
