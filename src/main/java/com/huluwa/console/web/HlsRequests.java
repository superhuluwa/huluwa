package com.huluwa.console.web;

/** HLS 管理接口载荷；Cookie 仅写入，不通过查询接口读取。 */
public final class HlsRequests {
    private HlsRequests() { }
    public record QuickSubscription(String apiUrl, String resId) { }
    public record Subscription(String apiUrl, String resId, String title, String year, Integer season,
                               String sourceName, Integer intervalMinutes, Integer fromEpisode,
                               Boolean downloadExisting, String referer, String userAgent, String cookie,
                               Boolean clearCookie) { }
    public record Download(String apiUrl, String resId, String title, String year, Integer season,
                           Integer episode, String episodeTitle, String url, String sourceName,
                           String referer, String userAgent, String cookie) { }
    public record Settings(Integer threadCount, Integer maxAttempts, Integer timeoutMinutes,
                           String format, String maxSpeed) { }
    public record Enabled(boolean enabled) { }
}
