package com.atguigu.auth;

import com.atguigu.common.jwt.JwtHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class JwtHelperTest {

    private static final JwtHelper jwtHelper = new JwtHelper(
            "test-jwt-secret-for-contract-tests-0000000000000000000000000000000000000000");

    @Test
    public void createAccessAndRefreshTokensRoundTripOnlyForTheirOwnPurpose() {
        JwtHelper.TokenPair pair = jwtHelper.createTokenPair(1L, "admin");

        assertEquals(1L, jwtHelper.getUserId(pair.token()));
        assertEquals("admin", jwtHelper.getUsername(pair.token()));
        assertNull(jwtHelper.getRefreshUserId(pair.token()));
        assertEquals(1L, jwtHelper.getRefreshUserId(pair.refreshToken()));
        assertEquals("admin", jwtHelper.getRefreshUsername(pair.refreshToken()));
        assertNull(jwtHelper.getUserId(pair.refreshToken()));
    }

    @Test
    public void accessAndRefreshTokensUseTheirSpecifiedLifetimes() {
        long beforeCreation = System.currentTimeMillis();
        JwtHelper.TokenPair pair = jwtHelper.createTokenPair(1L, "admin");
        long afterCreation = System.currentTimeMillis();

        long accessExpiration = jwtHelper.getExpiration(pair.token()).getTime();
        long refreshExpiration = jwtHelper.getExpiration(pair.refreshToken()).getTime();
        assertTrue(accessExpiration >= beforeCreation + JwtHelper.ACCESS_TOKEN_LIFETIME_MILLIS - 1000);
        assertTrue(accessExpiration <= afterCreation + JwtHelper.ACCESS_TOKEN_LIFETIME_MILLIS);
        assertTrue(refreshExpiration >= beforeCreation + JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS - 1000);
        assertTrue(refreshExpiration <= afterCreation + JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS);
    }

    @Test
    public void missingOrBlankTokenDoesNotAuthenticate() {
        assertNull(jwtHelper.getUserId(null));
        assertNull(jwtHelper.getUserId(""));
        assertEquals("", jwtHelper.getUsername(null));
        assertEquals("", jwtHelper.getUsername(""));
    }

    @Test
    public void tamperedTokenDoesNotAuthenticate() {
        String token = jwtHelper.createTokenPair(1L, "admin").token();
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertNull(jwtHelper.getUserId(tampered));
        assertNull(jwtHelper.getUsername(tampered));
    }

    @Test
    public void signingSecretMustBeAtLeast64Bytes() {
        assertThrows(IllegalArgumentException.class, () -> new JwtHelper("too-short"));
    }
}
