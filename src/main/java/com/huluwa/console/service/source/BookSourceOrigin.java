package com.huluwa.console.service.source;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 解析书源接口的对外地址。
 *
 * <p>优先级：调用方显式传入 &gt; 配置项 huluwa.source.public-url &gt; 请求头推断。
 * 反向代理改写 Host 时，请求头推断会得到内网地址，需要在配置里显式指定公网地址。</p>
 */
@Component
public class BookSourceOrigin {

    private final String publicUrl;

    public BookSourceOrigin(@Value("${huluwa.source.public-url:}") String publicUrl) {
        String value = publicUrl == null ? "" : publicUrl.trim();
        this.publicUrl = value.startsWith("http://") || value.startsWith("https://") ? value : "";
    }

    /** 解析对外地址；request 可为 null，此时只能依赖传入值或配置项。 */
    public String resolve(String baseUrl, HttpServletRequest request) {
        String origin = baseUrl == null ? "" : baseUrl.trim();
        if (origin.isEmpty()) origin = publicUrl;
        if (origin.isEmpty() && request != null) {
            String proto = firstHeader(request, "X-Forwarded-Proto");
            String host = firstHeader(request, "X-Forwarded-Host");
            String scheme = proto.isEmpty() ? request.getScheme() : proto;
            String authority = host.isEmpty() ? request.getHeader("Host") : host;
            if (authority == null || authority.isBlank()) {
                authority = request.getServerName() + ":" + request.getServerPort();
            }
            origin = scheme + "://" + authority;
        }
        while (origin.endsWith("/")) origin = origin.substring(0, origin.length() - 1);
        return origin;
    }

    private static String firstHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        if (value == null || value.isBlank()) return "";
        int comma = value.indexOf(',');
        return (comma >= 0 ? value.substring(0, comma) : value).trim();
    }
}
