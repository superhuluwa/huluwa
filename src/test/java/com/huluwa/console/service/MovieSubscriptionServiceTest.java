package com.huluwa.console.service;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

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
        assertEquals("208-209", first.episode());
        assertEquals("斗破苍穹 第5季208-209.2160p.HD国语中字无水印.mp4", first.title());

        // 同一 info hash 只保留首次出现（站点会重复挂同一集）
        long distinct = magnets.stream().map(MovieSearchService.MagnetItem::hash).distinct().count();
        assertEquals(magnets.size(), distinct, "磁力列表不应有重复 info hash");
    }

    /**
     * 「资源列表」要求按集数倒序：接口层排序后最新一集在最前，且不能改坏订阅巡检依赖的原始解析顺序。
     */
    @Test
    void compareEpisodeDescSortsLatestEpisodeFirst() throws Exception {
        List<MovieSearchService.MagnetItem> magnets = MovieSearchService.parseMagnets(detailFixture());
        List<MovieSearchService.MagnetItem> sorted = new ArrayList<>(magnets);
        sorted.sort(MovieSubscriptionService::compareEpisodeDesc);

        int latest = sorted.stream()
                .map(MovieSearchService.MagnetItem::episode)
                .filter(episode -> episode != null && !episode.isBlank())
                .mapToInt(episode -> Integer.parseInt(episode.substring(episode.lastIndexOf('-') + 1)))
                .max()
                .orElseThrow();
        assertEquals("214", String.valueOf(latest), "快照里的最新集数应稳定为 214");
        assertEquals("214", sorted.get(0).episode(), "最新一集必须排在第一行");

        int previous = Integer.MAX_VALUE;
        boolean sawUnknown = false;
        for (MovieSearchService.MagnetItem item : sorted) {
            String episode = item.episode();
            if (episode == null || episode.isBlank()) {
                sawUnknown = true;
                continue;
            }
            assertFalse(sawUnknown, "识别不出集号的条目必须排在最后，异常位置：" + item.title());
            int current = Integer.parseInt(episode.substring(episode.lastIndexOf('-') + 1));
            assertTrue(current <= previous, "集数必须按倒序排列，异常位置：" + episode);
            previous = current;
        }

        // 详情页解析顺序保持不变，订阅巡检仍按页面顺序处理基线
        assertEquals("208-209", magnets.get(0).episode(), "解析层不应被接口层的倒序排序影响");
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
                        "magnet:?xt=urn:btih:" + "c".repeat(40), "c".repeat(40), "1080P", "10"),
                new MovieSearchService.MagnetItem("剧 第1季01-14.1080p.mp4",
                        "magnet:?xt=urn:btih:" + "d".repeat(40), "d".repeat(40), "1080P", "1-14")));
        items.sort(MovieSubscriptionService::compareEpisodeDesc);

        assertEquals("1-14", items.get(0).episode(), "合并资源按结束集号排序");
        assertEquals("10", items.get(1).episode(), "两位数集号必须排在个位数之前");
        assertEquals("2", items.get(2).episode());
        assertEquals(null, items.get(3).episode(), "识别不出集号的条目排在最后");
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
        assertEquals("208-209", MovieSearchService.episodeOf("斗破苍穹 第5季208-209.2160p.mp4"));
        assertEquals("12", MovieSearchService.episodeOf("Some.Show.S05E12.1080p.mkv"));
        assertEquals("2", MovieSearchService.episodeOf("某剧 第02集 1080P"));
        assertEquals("2", MovieSearchService.episodeOf("剧名 第5季02.1080p.mp4"));
        assertEquals("1-14", MovieSearchService.episodeOf("玉簟秋.01-14.1080p.mp4"));
        assertEquals("1-14", MovieSearchService.episodeOf("玉簟秋 第01集-第14集 1080P"));
        assertEquals("1-14", MovieSearchService.episodeOf("玉簟秋 第01-14集 1080P"));
        assertEquals("1-14", MovieSearchService.episodeOf("玉簟秋 EP01-EP14 1080P"));
        assertEquals("1-14", MovieSearchService.episodeOf("Some.Show.S01E01-E14.1080p.mkv"));
        assertEquals("1", MovieSearchService.episodeOf("玉簟秋.01-01.1080p.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("全集打包 1080P"));
    }

    /**
     * 发布标记不是集号：扩展名（.mp4→4）、分辨率（1080p / 4k）、年份（2024）都不能被抽成集号，
     * 否则整季打包与电影正片会被误判成「第 4 集」。用真实详情页快照里的标题锁定。
     */
    @Test
    void episodeOfIgnoresReleaseTagsFromRealTitles() throws Exception {
        // 整季打包 / 合集：没有集号，不能因 .mp4 里的 4 判成第 4 集
        assertEquals(null, MovieSearchService.episodeOf("斗破苍穹 缘起.全集.1080p.HD国语中字无水印.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("斗破苍穹 缘起.全集.4k.HD国语中字无水印.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("三年之约全集.1080p.HD国语中字无水印.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("第四季全集打包.1080p.HD国语中字无水印.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("第一季全集打包.1080p.HD国语中字无水印.mp4"));
        // 电影正片（特殊篇 / 剧场版）：同样没有集号
        assertEquals(null, MovieSearchService.episodeOf("虚天战纪.导演剪辑版.2160p.HD国语中字无水印.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("凡人修仙传：星海飞驰篇.1080p.HD国语中字无水印.mp4"));
        assertEquals(null, MovieSearchService.episodeOf("吞噬星空 决战原始星.2160p.HD国语中字无水印.mp4"));
        // 真实年份也不是集号
        assertEquals(null, MovieSearchService.episodeOf("某电影.2024.1080p.BluRay.x264.mkv"));
        // 但真正的裸集号不受影响（站点常见写法）
        assertEquals("212", MovieSearchService.episodeOf("斗破苍穹 第5季212.2160p.HD国语中字无水印.mp4"));
        assertEquals("177-178", MovieSearchService.episodeOf("177-178.1080p.HD国语中字无水印.mkv"));
        assertEquals("4", MovieSearchService.episodeOf("重返天南04.2160p.HD国语中字无水印.mp4"));

        // 快照整页复核：整季打包与电影条目必须是「无集号」，且不再出现被误判的 4
        List<MovieSearchService.MagnetItem> magnets = MovieSearchService.parseMagnets(detailFixture());
        // 快照里的第 4 集只有真实的「第5季04」，全集 / 打包 / 电影条目不能再落进 4
        List<String> episodeFour = magnets.stream()
                .filter(item -> "4".equals(item.episode()))
                .map(MovieSearchService.MagnetItem::title)
                .toList();
        assertFalse(episodeFour.isEmpty(), "真实第 4 集仍应被识别");
        assertTrue(episodeFour.stream().allMatch(title -> title.contains("第5季04")),
                "第 4 集只能来自真实集号，实际：" + episodeFour);
        assertTrue(magnets.stream()
                        .filter(item -> item.title().contains("全集") || item.title().contains("打包"))
                        .allMatch(item -> item.episode() == null || item.episode().isBlank()),
                "整季打包条目必须识别不出集号");
        assertTrue(magnets.stream()
                        .filter(item -> item.title().contains("缘起") || item.title().contains("三年之约"))
                        .allMatch(item -> item.episode() == null || item.episode().isBlank()),
                "电影 / 特别篇条目必须识别不出集号");
    }

    /** 历史基线里的错误集号要能在启动时按新规则修正（不清记录、不重置基线）。 */
    @Test
    void refreshMovieMagnetEpisodesFixesHistoricalMisjudgement() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource("jdbc:sqlite::memory:", true);
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new ClassPathResource("schema.sql")).execute(dataSource);
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("""
                INSERT INTO movie_subscription
                    (id, res_id, title, detail_url, enabled, baseline_at, downloader, created_at, updated_at)
                VALUES (1, '48089', '斗破苍穹 第5季', 'https://www.dygangs.net/dmq/20211031/48089.htm',
                        1, '2026-10-01 00:00:00', 'qb', '2026-10-01 00:00:00', '2026-10-01 00:00:00')
                """);
        jdbc.update("""
                INSERT INTO movie_subscription_magnet
                    (subscription_id, title, magnet, magnet_key, quality, episode, baseline, first_seen_at)
                VALUES (1, '斗破苍穹 缘起.全集.1080p.HD国语中字无水印.mp4', 'magnet:?xt=urn:btih:%s', 'a', '1080P', '4', 1, '2026-10-01 00:00:00'),
                       (1, '斗破苍穹 第5季212.2160p.HD国语中字无水印.mp4', 'magnet:?xt=urn:btih:%s', 'b', '2160P', '212', 1, '2026-10-01 00:00:00')
                """.formatted("a".repeat(40), "b".repeat(40)));
        try {
            new com.huluwa.console.config.DatabaseInitializer(jdbc).run(null);
            assertEquals(null, jdbc.queryForObject(
                    "SELECT episode FROM movie_subscription_magnet WHERE magnet_key = 'a'", String.class),
                    "整季打包的错误集号应被清空");
            assertEquals("212", jdbc.queryForObject(
                    "SELECT episode FROM movie_subscription_magnet WHERE magnet_key = 'b'", String.class),
                    "正确集号必须保持不变");
            assertEquals("2026-10-01 00:00:00", jdbc.queryForObject(
                    "SELECT baseline_at FROM movie_subscription WHERE id = 1", String.class),
                    "修正集号不能重置基线时间");
        } finally {
            dataSource.destroy();
        }
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
        assertEquals("/vol1/1000/downloads/斗破苍穹 第5季", MovieSubscriptionService.savePathUnder(null, "斗破苍穹 第5季"));
        assertEquals("/vol1/1000/downloads/斗破苍穹 第5季", MovieSubscriptionService.savePathUnder("/vol1/1000/downloads/", "斗破苍穹 第5季"));
        // 目录名里的非法字符会被替换，不会串到别的目录层级
        assertEquals("/vol1/1000/downloads/剧名 A_B", MovieSubscriptionService.savePathUnder(null, "剧名 A/B"));
        assertEquals("/vol1/1000/downloads/剧名_第1季", MovieSubscriptionService.savePathUnder(null, "剧名:第1季"));
        // 已配置根目录（如 qB 的默认保存路径）时用它作为根
        assertEquals("/volume1/video/剧名", MovieSubscriptionService.savePathUnder("/volume1/video", "剧名"));
        // 空片名兜底成「未命名」，不会退化成根目录导致所有剧混在一起
        assertEquals("/vol1/1000/downloads/未命名", MovieSubscriptionService.savePathUnder(null, "  "));
        assertEquals("未命名", MovieSubscriptionService.sanitizeFolder(null));
        assertEquals("/vol1/1000/downloads/_", MovieSubscriptionService.savePathUnder(null, ".."));
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
        String folder = MovieSubscriptionService.savePathUnder("/vol1/1000/downloads", "斗破苍穹 第5季");
        assertEquals("/vol1/1000/downloads/斗破苍穹 第5季", folder);
        // 剧名里的斜杠不会造成多级目录穿透
        assertEquals("/vol1/1000/downloads/剧名 A_B", MovieSubscriptionService.savePathUnder("/vol1/1000/downloads", "剧名 A/B"));
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
