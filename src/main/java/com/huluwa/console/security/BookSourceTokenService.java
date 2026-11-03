package com.huluwa.console.security;

import com.huluwa.console.service.ConfigService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * 书源访问令牌：给外部阅读器（如开源阅读）长期使用的只读令牌。
 *
 * <p>与登录令牌相互独立：使用单独签名密钥和独立版本号，可随时整体吊销，
 * 不需要改动登录状态；有效期刻意放长，避免书源配置频繁失效。</p>
 */
@Service
public class BookSourceTokenService {

    /** 令牌用途声明值，用于区分登录令牌。 */
    public static final String SCOPE = "book-source";
    /** 书源令牌有效期（天）。 */
    public static final int EXPIRE_DAYS = 365;

    private static final String CFG_SECRET = "source.token.secret";
    private static final String CFG_VERSION = "source.token.version";
    private static final String CLAIM_SCOPE = "scope";
    private static final String CLAIM_VERSION = "ver";

    private static final Logger log = LoggerFactory.getLogger(BookSourceTokenService.class);

    private final ConfigService configs;
    private volatile SecretKey key;

    public BookSourceTokenService(ConfigService configs) { this.configs = configs; }

    /** 签发指定书源的访问令牌（默认有效期）。 */
    public String issue(String sourceId) {
        return issue(sourceId, EXPIRE_DAYS);
    }

    /** 签发指定有效期的书源令牌，天数会被限制在 1 天到 {@link #EXPIRE_DAYS} 之间。 */
    public String issue(String sourceId, int days) {
        Instant now = Instant.now();
        int valid = Math.max(1, Math.min(days, EXPIRE_DAYS));
        return Jwts.builder()
                .subject(sourceId)
                .claim(CLAIM_SCOPE, SCOPE)
                .claim(CLAIM_VERSION, currentVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(Duration.ofDays(valid))))
                .signWith(key())
                .compact();
    }

    /** 校验令牌并返回所属书源 ID；无效、过期或已吊销时返回 null。 */
    public String verify(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key())
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();
            if (!SCOPE.equals(claims.get(CLAIM_SCOPE, String.class))) return null;
            Integer version = claims.get(CLAIM_VERSION, Integer.class);
            if (version == null || version != currentVersion()) return null;
            return claims.getSubject();
        } catch (JwtException | IllegalArgumentException error) {
            return null;
        }
    }

    /** 吊销全部已签发的书源令牌。 */
    public void revokeAll() {
        configs.put(CFG_VERSION, String.valueOf(currentVersion() + 1));
    }

    /** 惰性初始化签名密钥：构造阶段数据库尚未就绪，首次签发/校验时再读取。 */
    private SecretKey key() {
        SecretKey current = key;
        if (current != null) return current;
        synchronized (this) {
            if (key == null) {
                String secret = configs.get(CFG_SECRET);
                if (secret == null || secret.isBlank()) {
                    byte[] random = new byte[64];
                    new SecureRandom().nextBytes(random);
                    secret = Base64.getEncoder().encodeToString(random);
                    configs.put(CFG_SECRET, secret);
                    log.info("已生成书源访问令牌签名密钥并持久化到数据库");
                }
                key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            }
            return key;
        }
    }

    private int currentVersion() {
        String value = configs.get(CFG_VERSION);
        if (value == null || value.isBlank()) return 1;
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException error) {
            return 1;
        }
    }
}
