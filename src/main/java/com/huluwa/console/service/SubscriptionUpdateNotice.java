package com.huluwa.console.service;

import java.util.List;

/** 两个采集来源共用更新通知格式，完整列出本轮新增资源。 */
final class SubscriptionUpdateNotice {
    private SubscriptionUpdateNotice() {
    }

    static String title(String source, String seriesTitle) {
        return source + "更新：" + seriesTitle;
    }

    static String content(String source, String seriesTitle, int foundCount, String downloadSummary,
                          List<String> details, String detailUrl) {
        StringBuilder content = new StringBuilder();
        content.append("【").append(seriesTitle).append("】").append(source).append("采集到 ")
                .append(foundCount).append(" 条新剧集，").append(downloadSummary).append("：\n");
        for (String line : details) {
            content.append("• ").append(line).append('\n');
        }
        if (detailUrl != null && !detailUrl.isBlank()) {
            content.append("\n详情：").append(detailUrl);
        }
        return content.toString().trim();
    }
}
