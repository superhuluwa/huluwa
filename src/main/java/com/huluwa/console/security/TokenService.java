package com.huluwa.console.security;

import com.huluwa.console.config.AppProperties;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * JWT 签发与校验。
 * 密钥优先取 huluwa.token.secret；未配置时自动生成 64 字节随机值并持久化到 sys_config，
 * 保证重启后已签发的令牌仍然有效。
 */
@Service
public class TokenService {

    public static final String CFG_JWT_SECRET = "security.jwt.secret";
    public static final String CFG_TOKEN_VERSION = "security.token.version";
    /** 令牌版本：修改密码后自增，使历史令牌立即失效 */
    public static final String CLAIM_VERSION = "ver";

    private static final Logger log = LoggerFactory.getLogger(TokenService.class);

    private final ConfigService configService;
    private final AppProperties properties;
    private SecretKey key;

    public TokenService(ConfigService configService, AppProperties properties) {
        this.configService = configService;
        this.properties = properties;
    }

    /**
     * 密钥惰性初始化：schema.sql 由 Spring 在数据源就绪后执行，
     * 此处不能在 bean 构造阶段访问数据库，故推迟到首次签发/校验令牌时。
     */
    private SecretKey key() {
        SecretKey current = this.key;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (this.key == null) {
                String secret = properties.getToken().getSecret();
                if (secret == null || secret.isBlank()) {
                    secret = configService.get(CFG_JWT_SECRET);
                    if (secret == null || secret.isBlank()) {
                        secret = generateSecret();
                        configService.put(CFG_JWT_SECRET, secret);
                        log.info("已生成 JWT 签名密钥并持久化到数据库");
                    }
                }
                this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            }
            return this.key;
        }
    }

    public String issue(String username) {
        Instant now = Instant.now();
        Duration ttl = Duration.ofHours(Math.max(1, properties.getToken().getExpireHours()));
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_VERSION, currentVersion())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(ttl)))
                .signWith(key())
                .compact();
    }

    public String parseSubject(String token) {
        Claims claims = parse(token);
        return claims.getSubject();
    }

    public boolean isValid(String token) {
        try {
            Claims claims = parse(token);
            Integer ver = claims.get(CLAIM_VERSION, Integer.class);
            return ver != null && ver == currentVersion();
        } catch (JwtException | IllegalArgumentException ex) {
            return false;
        }
    }

    /** 修改密码时调用：历史令牌全部失效 */
    public void revokeAll() {
        configService.put(CFG_TOKEN_VERSION, String.valueOf(currentVersion() + 1));
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private int currentVersion() {
        String value = configService.get(CFG_TOKEN_VERSION);
        if (value == null || value.isBlank()) {
            return 1;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return 1;
        }
    }

    private String generateSecret() {
        byte[] random = new byte[64];
        new SecureRandom().nextBytes(random);
        return Base64.getEncoder().encodeToString(random);
    }

    /** 生成用于口令校验的等长哈希，避免空指针等低质量输入 */
    static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Base64.getEncoder().encodeToString(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}