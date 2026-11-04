package com.lautung.oa.auth.register;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 注册限流窗口（PR #260 审查）：首次计数建 TTL；键已存在但 TTL=-1 时补 TTL，
 * 避免进程中断留下的计数永不过期。
 */
class RegisterAttemptLimiterTest {

    private static final String KEY = RegisterAttemptLimiter.KEY_PREFIX + "1.2.3.4";

    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);

    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);

    private final RegisterAttemptLimiter limiter = new RegisterAttemptLimiter(redisTemplate);

    @BeforeEach
    void injectCollaborators() {
        when(redisTemplate.opsForValue()).thenReturn(values);
    }

    @Test
    void firstAttemptStartsTheWindow() {
        when(values.increment(KEY)).thenReturn(1L);

        assertTrue(limiter.tryAcquire("1.2.3.4"));

        verify(redisTemplate).expire(
                KEY, RegisterAttemptLimiter.WINDOW.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void existingKeyWithoutTtlGetsOneBackfilled() {
        when(values.increment(KEY)).thenReturn(3L);
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(-1L);

        assertTrue(limiter.tryAcquire("1.2.3.4"));

        verify(redisTemplate).expire(
                KEY, RegisterAttemptLimiter.WINDOW.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void existingWindowIsKept() {
        when(values.increment(KEY)).thenReturn(3L);
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(42L);

        assertTrue(limiter.tryAcquire("1.2.3.4"));

        verify(redisTemplate, never()).expire(
                KEY, RegisterAttemptLimiter.WINDOW.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Test
    void overLimitAttemptStillMovesTheCounterWithoutRefreshingTheWindow() {
        when(values.increment(KEY)).thenReturn(6L);
        when(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).thenReturn(30L);

        assertFalse(limiter.tryAcquire("1.2.3.4"));

        verify(redisTemplate, never()).expire(
                KEY, RegisterAttemptLimiter.WINDOW.toMillis(), TimeUnit.MILLISECONDS);
    }
}
