package com.lautung.oa.common.jwt;

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
import java.util.UUID;

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
            throw new IllegalArgumentException("lautung.jwt.secret must contain at least 64 UTF-8 bytes");
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Both tokens of one login share a session id so 退出 can revoke exactly that login, and carry
     * the user's token version so a password change retires every login.
     */
    public TokenPair createTokenPair(Long userId, String username, long tokenVersion) {
        String sessionId = newSessionId();
        return new TokenPair(
                createAccessToken(userId, username, sessionId, tokenVersion),
                createToken(userId, username, REFRESH_TOKEN_TYPE, REFRESH_TOKEN_LIFETIME_MILLIS,
                        sessionId, tokenVersion),
                sessionId);
    }

    public String createAccessToken(
            Long userId, String username, String sessionId, long tokenVersion) {
        return createToken(userId, username, ACCESS_TOKEN_TYPE, ACCESS_TOKEN_LIFETIME_MILLIS,
                sessionId, tokenVersion);
    }

    public TokenClaims readAccessToken(String token) {
        return readClaims(token, ACCESS_TOKEN_TYPE);
    }

    public TokenClaims readRefreshToken(String token) {
        return readClaims(token, REFRESH_TOKEN_TYPE);
    }

    private String newSessionId() {
        return UUID.randomUUID().toString();
    }

    private String createToken(
            Long userId, String username, String type, long lifetimeMillis,
            String sessionId, long tokenVersion) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(SUBJECT)
                .issuedAt(new Date(now))
                .expiration(new Date(now + lifetimeMillis))
                .claims(Map.of(
                        "userId", userId,
                        "username", username,
                        "tokenType", type,
                        "sessionId", sessionId,
                        "tokenVersion", tokenVersion))
                .signWith(secretKey)
                .compact();
    }

    private TokenClaims readClaims(String token, String expectedType) {
        Claims claims = readTypedClaims(token, expectedType);
        if (claims == null) {
            return null;
        }
        Object userId = claims.get("userId");
        Object username = claims.get("username");
        String sessionId = claims.get("sessionId", String.class);
        Object tokenVersion = claims.get("tokenVersion");
        if (!(userId instanceof Number number) || !(username instanceof String name)) {
            return null;
        }
        // Tokens minted before token versions exist authenticate as version zero.
        long version = tokenVersion instanceof Number value ? value.longValue() : 0L;
        return new TokenClaims(
                number.longValue(), name, sessionId, version, claims.getExpiration());
    }

    private Claims readTypedClaims(String token, String expectedType) {
        Claims claims = readSignedClaims(token);
        if (claims == null || !SUBJECT.equals(claims.getSubject())
                || !expectedType.equals(claims.get("tokenType", String.class))
                // Tokens without a session id predate revocation and must not authenticate.
                || !StringUtils.hasText(claims.get("sessionId", String.class))) {
            return null;
        }
        return claims;
    }

    private Claims readSignedClaims(String token) {
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

    public record TokenClaims(
            Long userId, String username, String sessionId,
            long tokenVersion, Date expiresAt) {
    }

    /** {@code sessionId} 与两张令牌里的声明一致，登录登记直接用它，不再反解令牌。 */
    public record TokenPair(String token, String refreshToken, String sessionId) {
    }
}
