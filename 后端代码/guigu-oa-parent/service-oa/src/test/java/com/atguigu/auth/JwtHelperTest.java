package com.atguigu.auth;

import com.atguigu.common.jwt.JwtHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JwtHelperTest {

    @Test
    public void createTokenRoundTripsUserIdAndUsername() {
        String token = JwtHelper.createToken(1L, "admin");

        assertEquals(1L, JwtHelper.getUserId(token));
        assertEquals("admin", JwtHelper.getUsername(token));
    }

    @Test
    public void missingOrBlankTokenDoesNotAuthenticate() {
        assertNull(JwtHelper.getUserId(null));
        assertNull(JwtHelper.getUserId(""));
        assertEquals("", JwtHelper.getUsername(null));
        assertEquals("", JwtHelper.getUsername(""));
    }

    @Test
    public void tamperedTokenDoesNotAuthenticate() {
        String token = JwtHelper.createToken(1L, "admin");
        String tampered = token.substring(0, token.length() - 4) + "xxxx";

        assertNull(JwtHelper.getUserId(tampered));
        assertNull(JwtHelper.getUsername(tampered));
    }
}
