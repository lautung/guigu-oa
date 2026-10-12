package com.atguigu.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

public class JwtHelper {

    public static final long ACCESS_TOKEN_LIFETIME_MILLIS = Duration.ofHours(2).toMillis();
    public static final long REFRESH_TOKEN_LIFETIME_MILLIS = Duration.ofDays(7).toMillis();

    private static final String ACCESS_TOKEN_TYPE = "access";
    private static final String REFRESH_TOKEN_TYPE = "refresh";
    private static final String SUBJECT = "AUTH-USER";

    private final SecretKey secretKey;

    public JwtHelper(String secret) {
        byte[] keyBytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 64) {
            throw new IllegalArgumentException("guigu.jwt.secret must contain at least 64 UTF-8 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    public TokenPair createTokenPair(Long userId, String username) {
        return new TokenPair(
                createAccessToken(userId, username),
                createToken(userId, username, REFRESH_TOKEN_TYPE, REFRESH_TOKEN_LIFETIME_MILLIS));
    }

    public String createAccessToken(Long userId, String username) {
        return createToken(userId, username, ACCESS_TOKEN_TYPE, ACCESS_TOKEN_LIFETIME_MILLIS);
    }

    public Long getUserId(String token) {
        return getLongClaim(token, ACCESS_TOKEN_TYPE, "userId");
    }

    public String getUsername(String token) {
        if (!StringUtils.hasText(token)) {
            return "";
        }
        return getStringClaim(token, ACCESS_TOKEN_TYPE, "username");
    }

    public Long getRefreshUserId(String token) {
        return getLongClaim(token, REFRESH_TOKEN_TYPE, "userId");
    }

    public String getRefreshUsername(String token) {
        return getStringClaim(token, REFRESH_TOKEN_TYPE, "username");
    }

    public Date getExpiration(String token) {
        Claims claims = readClaims(token);
        return claims == null ? null : claims.getExpiration();
    }

    private String createToken(Long userId, String username, String type, long lifetimeMillis) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(SUBJECT)
                .issuedAt(new Date(now))
                .expiration(new Date(now + lifetimeMillis))
                .claims(Map.of("userId", userId, "username", username, "tokenType", type))
                .signWith(secretKey)
                .compact();
    }

    private Long getLongClaim(String token, String expectedType, String name) {
        Claims claims = readTypedClaims(token, expectedType);
        if (claims == null) {
            return null;
        }
        Object value = claims.get(name);
        return value instanceof Number number ? number.longValue() : null;
    }

    private String getStringClaim(String token, String expectedType, String name) {
        Claims claims = readTypedClaims(token, expectedType);
        if (claims == null) {
            return null;
        }
        Object value = claims.get(name);
        return value instanceof String string ? string : null;
    }

    private Claims readTypedClaims(String token, String expectedType) {
        Claims claims = readClaims(token);
        if (claims == null || !SUBJECT.equals(claims.getSubject())
                || !expectedType.equals(claims.get("tokenType", String.class))) {
            return null;
        }
        return claims;
    }

    private Claims readClaims(String token) {
        if (!StringUtils.hasText(token)) {
            return null;
        }
        try {
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token);
            return jws.getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public record TokenPair(String token, String refreshToken) {
    }
}
