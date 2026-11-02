package com.lautung.oa.security;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.security.revocation.RedisTokenRevocation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class RedisTokenRevocationTest {

    @Test
    @SuppressWarnings("unchecked")
    public void revocationMarksTheSessionForAsLongAsARefreshTokenOfItCanLive() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);

        new RedisTokenRevocation(redisTemplate).revoke("session-1");

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> retention = ArgumentCaptor.forClass(Duration.class);
        verify(values).set(key.capture(), eq("1"), retention.capture());
        assertTrue(key.getValue().endsWith("session-1"));
        assertEquals(Duration.ofMillis(JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS), retention.getValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void onlyMarkedSessionsAreReportedAndBlankIdsTouchNothing() {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(anyString()))
                .thenAnswer(call -> ((String) call.getArgument(0)).endsWith("session-1") ? "1" : null);
        RedisTokenRevocation revocation = new RedisTokenRevocation(redisTemplate);

        assertTrue(revocation.isRevoked("session-1"));
        assertFalse(revocation.isRevoked("session-2"));
        assertFalse(revocation.isRevoked(null));
        assertFalse(revocation.isRevoked(""));

        revocation.revoke(null);
        revocation.revoke("");
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
