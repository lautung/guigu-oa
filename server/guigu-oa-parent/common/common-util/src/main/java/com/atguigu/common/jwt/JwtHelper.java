package com.atguigu.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtHelper {

    private static final long TOKEN_EXPIRATION = 365L * 24 * 60 * 60 * 1000;
    // HS512 要求密钥至少 64 字节
    private static final String TOKEN_SIGN_KEY =
            "guigu-oa-secret-key-for-hs512-must-be-at-least-64-bytes-long-padding!";

    private static SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(TOKEN_SIGN_KEY.getBytes(StandardCharsets.UTF_8));
    }

    public static String createToken(Long userId, String username) {
        return Jwts.builder()
                .subject("AUTH-USER")
                .expiration(new Date(System.currentTimeMillis() + TOKEN_EXPIRATION))
                .claim("userId", userId)
                .claim("username", username)
                .signWith(getSecretKey())
                .compact();
    }

    public static Long getUserId(String token) {
        try {
            if (!StringUtils.hasText(token)) return null;
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
            Number userId = (Number) jws.getPayload().get("userId");
            return userId == null ? null : userId.longValue();
        } catch (Exception e) {
            return null;
        }
    }

    public static String getUsername(String token) {
        try {
            if (!StringUtils.hasText(token)) return "";
            Jws<Claims> jws = Jwts.parser()
                    .verifyWith(getSecretKey())
                    .build()
                    .parseSignedClaims(token);
            return (String) jws.getPayload().get("username");
        } catch (Exception e) {
            return null;
        }
    }
}
