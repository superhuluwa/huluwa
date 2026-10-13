package com.huluwa.console.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/** 剧集编号、稳定去重键、请求头与文件名的共用规则。 */
public final class HlsSupport {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern SPECIAL = Pattern.compile("预告|花絮|番外|彩蛋|特辑|特别篇|片花|幕后|先导|解说|试看");
    private static final Pattern MULTI_EPISODE = Pattern.compile("(?i)S\\d+E\\d+(?:[ ._-]*E|[-~])\\d+|第\\s*\\d+\\s*[-~至]\\s*\\d+\\s*集");
    private static final Pattern EPISODE = Pattern.compile("(?i)(?:S\\d{1,3}E|(?:^|[ ._-])EP?)(\\d{1,4})(?!\\d)");
    private static final Pattern CHINESE_EPISODE = Pattern.compile("第\\s*([0-9零〇一二两三四五六七八九十百千]+)\\s*[集话期]");
    private static final Pattern NUMBER = Pattern.compile("^(\\d{1,4})(?:集|话|期)?$");
    private HlsSupport() { }

    public static String now() { return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(TIME); }
    public static String afterMinutes(long value) {
        return LocalDateTime.now(ZoneId.of("Asia/Shanghai")).plusMinutes(value).format(TIME);
    }
    public static String text(Object value) { return value == null ? "" : value.toString(); }
    public static long number(Object value) { return value instanceof Number n ? n.longValue() : 0; }
    public static String required(String value, String label) {
        String result = value == null ? "" : value.trim();
        if (result.isEmpty() || result.length() > 500) throw new BusinessException(400, "请填写有效的" + label);
        return result;
    }
    public static int range(Integer value, int fallback, int min, int max, String label) {
        int result = value == null ? fallback : value;
        if (result < min || result > max) throw new BusinessException(400, label + "应在 " + min + " 到 " + max + " 之间");
        return result;
    }
    public static String httpUrl(String value, String label) {
        String result = value == null ? "" : value.trim();
        try {
            URI uri = URI.create(result);
            if (result.length() > 16000 || uri.getHost() == null || uri.getUserInfo() != null
                    || !("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (Exception e) { throw new BusinessException(400, label + "必须是有效的 HTTP/HTTPS 地址"); }
    }
    public static String providerUrl(String value) {
        String result = httpUrl(value == null || value.isBlank()
                ? DyttSearchService.BASE_URL + "/api.php/provide/vod/" : value, "采集接口");
        URI uri = URI.create(result);
        if (uri.getQuery() != null || uri.getFragment() != null) {
            throw new BusinessException(400, "采集接口只填写接口路径，请勿附带查询参数");
        }
        return result.replaceAll("/+$", "") + "/";
    }
    public static String resourceId(String value) {
        String result = required(value, "作品 ID");
        if (!result.matches("[0-9]{1,18}")) throw new BusinessException(400, "作品 ID 应为采集站的数字 ID");
        return result;
    }
    public static Integer episodeNumber(String title) {
        String value = text(title).trim();
        if (SPECIAL.matcher(value).find() || MULTI_EPISODE.matcher(value).find()) return null;
        var chinese = CHINESE_EPISODE.matcher(value);
        if (chinese.find()) return positive(chineseNumber(chinese.group(1)));
        var episode = EPISODE.matcher(value);
        if (episode.find()) return positive(Integer.parseInt(episode.group(1)));
        var plain = NUMBER.matcher(value);
        return plain.matches() ? positive(Integer.parseInt(plain.group(1))) : null;
    }
    /** 标题含显式季号时必须与用户选择一致；没有季号时使用订阅季号。 */
    public static boolean matchesSeason(String title, int season) {
        var match = Pattern.compile("(?i)S(\\d{1,3})E\\d").matcher(text(title));
        if (match.find()) return Integer.parseInt(match.group(1)) == season;
        var chinese = Pattern.compile("第\\s*([0-9零〇一二两三四五六七八九十百千]+)\\s*季").matcher(text(title));
        return !chinese.find() || chineseNumber(chinese.group(1)) == season;
    }
    private static Integer positive(int value) { return value > 0 && value <= 9999 ? value : null; }
    private static int chineseNumber(String value) {
        if (value.matches("[0-9]+")) {
            try { return Integer.parseInt(value); } catch (NumberFormatException e) { return 0; }
        }
        if (value.matches("[零〇一二两三四五六七八九]+")) {
            StringBuilder digits = new StringBuilder();
            for (char character : value.toCharArray()) {
                digits.append("零一二三四五六七八九".indexOf(character == '两' ? '二' : character == '〇' ? '零' : character));
            }
            try { return Integer.parseInt(digits.toString()); } catch (NumberFormatException e) { return 0; }
        }
        int result = 0, digit = 0;
        for (char character : value.toCharArray()) {
            int unit = switch (character) { case '十' -> 10; case '百' -> 100; case '千' -> 1000; default -> 0; };
            if (unit > 0) { result += (digit == 0 ? 1 : digit) * unit; digit = 0; }
            else { digit = "零一二三四五六七八九".indexOf(character == '两' ? '二' : character == '〇' ? '零' : character); }
        }
        return result + Math.max(digit, 0);
    }
    public static String safeName(String value) {
        String result = text(value).replaceAll("[\\p{Cntrl}\\\\/:*?\"<>|]", "_")
                .replaceAll("\\s+", " ").replaceAll("^[. ]+|[. ]+$", "");
        if (result.isBlank()) throw new BusinessException(400, "剧名不能只包含特殊字符");
        // 中文文件名按 UTF-8 字节限制，给季集后缀和扩展名留足文件系统空间。
        StringBuilder limited = new StringBuilder();
        int bytes = 0;
        for (int point : result.codePoints().toArray()) {
            String character = new String(Character.toChars(point));
            int size = character.getBytes(StandardCharsets.UTF_8).length;
            if (bytes + size > 180) break;
            limited.append(character); bytes += size;
        }
        return limited.toString();
    }
    public static String filename(String title, int season, int episode) {
        return safeName(title) + String.format(" S%02dE%02d", season, episode);
    }
    public static String seriesKey(String provider, String resourceId, String title, String year) {
        String identity = resourceId != null && !resourceId.isBlank()
                ? "maccms:" + providerUrl(provider) + "#" + resourceId(resourceId)
                : "manual:" + safeName(title) + ":" + text(year);
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
    public static String headers(String referer, String userAgent, String cookie, String previous) {
        Map<String, String> result = new LinkedHashMap<>();
        if (referer != null && !referer.isBlank()) result.put("Referer", httpUrl(referer, "来源页面"));
        if (userAgent != null && !userAgent.isBlank()) result.put("User-Agent", headerValue(userAgent));
        else result.put("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 Chrome/131.0.0.0 Safari/537.36");
        if (cookie != null && !cookie.isBlank()) result.put("Cookie", headerValue(cookie));
        else if (previous != null && headerMap(previous).containsKey("Cookie")) {
            result.put("Cookie", headerMap(previous).get("Cookie"));
        }
        try { return JSON.writeValueAsString(result); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    private static String headerValue(String value) {
        if (value.length() > 16000 || value.contains("\r") || value.contains("\n")) {
            throw new BusinessException(400, "请求头格式不正确");
        }
        return value.trim();
    }
    public static Map<String, String> headerMap(String value) {
        try { return value == null || value.isBlank() ? Map.of()
                : JSON.readValue(value, new TypeReference<Map<String, String>>() { }); }
        catch (Exception e) { throw new IllegalStateException("已保存的请求头格式不正确", e); }
    }
    public static String redact(String value) {
        return text(value).replaceAll("(?i)https?://[^\\s\"'<>]+", "[地址已隐藏]")
                .replaceAll("(?i)(cookie|authorization|token|sign|password)\\s*[:=]\\s*[^\\r\\n]+", "$1=[已隐藏]");
    }
    public static String error(Exception e) {
        String result = redact(e.getMessage());
        return result.isBlank() ? "操作失败，请查看任务日志" : result.substring(0, Math.min(result.length(), 500));
    }
}
