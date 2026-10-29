package com.atguigu.auth;

import com.atguigu.common.jwt.JwtHelper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtHelperTest {

    private static final String SECRET =
            "test-jwt-secret-for-contract-tests-0000000000000000000000000000000000000000";
    private static final JwtHelper jwtHelper = new JwtHelper(SECRET);

    @Test
    public void bothTokensOfOneLoginShareTheSessionIdAndOnlyReadForTheirOwnPurpose() {
        JwtHelper.TokenPair pair = jwtHelper.createTokenPair(1L, "admin", 7L);

        JwtHelper.TokenClaims access = jwtHelper.readAccessToken(pair.token());
        JwtHelper.TokenClaims refresh = jwtHelper.readRefreshToken(pair.refreshToken());

        assertEquals(1L, access.userId());
        assertEquals("admin", access.username());
        assertNotNull(access.sessionId());
        assertEquals(access.sessionId(), refresh.sessionId());
        assertEquals(7L, access.tokenVersion());
        assertEquals(7L, refresh.tokenVersion());
        assertEquals(1L, refresh.userId());
        assertEquals("admin", refresh.username());
        assertNull(jwtHelper.readRefreshToken(pair.token()));
        assertNull(jwtHelper.readAccessToken(pair.refreshToken()));
    }

    @Test
    public void accessAndRefreshTokensUseTheirSpecifiedLifetimes() {
        long beforeCreation = System.currentTimeMillis();
        JwtHelper.TokenPair pair = jwtHelper.createTokenPair(1L, "admin", 0L);
        long afterCreation = System.currentTimeMillis();

        long accessExpiration = jwtHelper.readAccessToken(pair.token()).expiresAt().getTime();
        long refreshExpiration = jwtHelper.readRefreshToken(pair.refreshToken()).expiresAt().getTime();
        assertTrue(accessExpiration >= beforeCreation + JwtHelper.ACCESS_TOKEN_LIFETIME_MILLIS - 1000);
        assertTrue(accessExpiration <= afterCreation + JwtHelper.ACCESS_TOKEN_LIFETIME_MILLIS);
        assertTrue(refreshExpiration >= beforeCreation + JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS - 1000);
        assertTrue(refreshExpiration <= afterCreation + JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS);
    }

    @Test
    public void missingOrBlankTokenDoesNotAuthenticate() {
        assertNull(jwtHelper.readAccessToken(null));
        assertNull(jwtHelper.readAccessToken(""));
        assertNull(jwtHelper.readRefreshToken(null));
        assertNull(jwtHelper.readRefreshToken(""));
    }

    @Test
    public void tamperedTokenDoesNotAuthenticate() {
        String token = jwtHelper.createTokenPair(1L, "admin", 0L).token();
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertNull(jwtHelper.readAccessToken(tampered));
        assertNull(jwtHelper.readRefreshToken(tampered));
    }

    @Test
    public void signingSecretMustBeAtLeast64Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtHelper("too-short"));
    }

    @Test
    public void accessTokenMintedForARefreshKeepsThatSessionsIdentity() {
        JwtHelper.TokenPair pair = jwtHelper.createTokenPair(1L, "admin", 7L);
        JwtHelper.TokenClaims refresh = jwtHelper.readRefreshToken(pair.refreshToken());

        String refreshed = jwtHelper.createAccessToken(
                refresh.userId(), refresh.username(), refresh.sessionId(),
                refresh.tokenVersion());

        assertEquals(refresh.sessionId(), jwtHelper.readAccessToken(refreshed).sessionId());
        assertEquals(7L, jwtHelper.readAccessToken(refreshed).tokenVersion());
    }

    @Test
    public void tokenMintedBeforeCredentialGenerationsReadsAsGenerationZero() {
        byte[] secret = SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(secret);
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject("AUTH-USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .claim("userId", 1L)
                .claim("username", "admin")
                .claim("tokenType", "access")
                .claim("sessionId", "pre-generation")
                .signWith(key)
                .compact();

        JwtHelper.TokenClaims claims = jwtHelper.readAccessToken(token);

        assertEquals(0L, claims.tokenVersion());
        assertEquals("pre-generation", claims.sessionId());
    }

    @Test
    public void tokenWithoutASessionIdIsRejectedBecauseItCannotBeRevoked() {
        byte[] secret = SECRET.getBytes(StandardCharsets.UTF_8);
        SecretKey key = Keys.hmacShaKeyFor(secret);
        long now = System.currentTimeMillis();
        String token = Jwts.builder()
                .subject("AUTH-USER")
                .issuedAt(new Date(now))
                .expiration(new Date(now + 60_000))
                .claim("userId", 1L)
                .claim("username", "admin")
                .claim("tokenType", "access")
                .signWith(key)
                .compact();

        assertNull(jwtHelper.readAccessToken(token));
    }
}
