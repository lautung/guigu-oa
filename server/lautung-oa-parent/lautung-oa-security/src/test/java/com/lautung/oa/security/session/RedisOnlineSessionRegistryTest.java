package com.lautung.oa.security.session;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.model.monitor.OnlineSession;
import com.lautung.oa.model.monitor.SessionTime;
import com.lautung.oa.security.revocation.TokenRevocation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisOnlineSessionRegistryTest {

    private static final String SESSION_ID = "4b9dd1de-7c69-4fcc-9c2d-3fd2f6d8a000";
    private static final String HASH_KEY = "login:session:" + SESSION_ID;
    private static final String ACTIVE_KEY = "login:session:active:" + SESSION_ID;

    @SuppressWarnings("unchecked")
    private final RedisTemplate<String, String> redisTemplate = mock(RedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final HashOperations<String, String, String> hashOperations = mock(HashOperations.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final TokenRevocation tokenRevocation = mock(TokenRevocation.class);
    private final RedisOnlineSessionRegistry registry =
            new RedisOnlineSessionRegistry(redisTemplate, tokenRevocation);

    @BeforeEach
    void wireRedis() {
        when(redisTemplate.<String, String>opsForHash()).thenReturn(hashOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void registeringWritesOneHashWithTheLoginSnapshotAndTheRefreshLifetime() {
        registry.register(new OnlineSessionLogin(
                SESSION_ID, 7L, "alice", "爱丽丝", "203.0.113.9",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36", 3L));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, String>> fields = ArgumentCaptor.forClass(Map.class);
        verify(hashOperations).putAll(eq(HASH_KEY), fields.capture());
        Map<String, String> written = fields.getValue();
        assertEquals("7", written.get("userId"));
        assertEquals("alice", written.get("username"));
        assertEquals("爱丽丝", written.get("name"));
        assertEquals("203.0.113.9", written.get("ip"));
        assertEquals("Chrome", written.get("browser"));
        assertEquals("Windows", written.get("os"));
        assertEquals("3", written.get("tokenVersion"));
        assertTime(written.get("loginTime"));
        assertEquals(written.get("loginTime"), written.get("lastActive"));
        // 会话寿命只由 refresh 令牌决定（ADR 0060）：与吊销键同寿，7 天。
        verify(redisTemplate).expire(HASH_KEY, Duration.ofMillis(JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS));
    }

    @Test
    void touchingOnlyWritesLastActiveWhenTheSixtySecondThrottleKeyWasSet() {
        when(valueOperations.setIfAbsent(ACTIVE_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(redisTemplate.hasKey(HASH_KEY)).thenReturn(true);

        registry.touch(SESSION_ID);

        verify(valueOperations).setIfAbsent(ACTIVE_KEY, "1", Duration.ofSeconds(60));
        ArgumentCaptor<String> lastActive = ArgumentCaptor.forClass(String.class);
        verify(hashOperations).put(eq(HASH_KEY), eq("lastActive"), lastActive.capture());
        assertTime(lastActive.getValue());
        // 不续 hash TTL：touch 只写字段，不调用 expire。
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void aSecondTouchInsideTheWindowDoesNotWriteLastActiveAgain() {
        when(valueOperations.setIfAbsent(ACTIVE_KEY, "1", Duration.ofSeconds(60)))
                .thenReturn(true)
                .thenReturn(false);
        when(redisTemplate.hasKey(HASH_KEY)).thenReturn(true);

        registry.touch(SESSION_ID);
        registry.touch(SESSION_ID);

        verify(hashOperations, times(1)).put(eq(HASH_KEY), eq("lastActive"), anyString());
    }

    @Test
    void touchingASessionWhoseHashAlreadyExpiredDoesNotCreateAnOrphan() {
        when(valueOperations.setIfAbsent(ACTIVE_KEY, "1", Duration.ofSeconds(60))).thenReturn(true);
        when(redisTemplate.hasKey(HASH_KEY)).thenReturn(false);

        registry.touch(SESSION_ID);

        verify(hashOperations, never()).put(eq(HASH_KEY), eq("lastActive"), anyString());
    }

    @Test
    void listingReadsHashesUnderTheSessionPrefixAndSkipsThrottleKeys() {
        String secondSession = "second-session";
        String secondHash = "login:session:" + secondSession;
        String activeKey = "login:session:active:" + secondSession;

        @SuppressWarnings("unchecked")
        Cursor<String> cursor = mock(Cursor.class);
        when(cursor.hasNext()).thenReturn(true, true, true, false);
        when(cursor.next()).thenReturn(HASH_KEY, activeKey, secondHash);
        when(redisTemplate.scan(any(ScanOptions.class))).thenReturn(cursor);

        Map<String, String> first = new HashMap<>();
        first.put("userId", "7");
        first.put("username", "alice");
        first.put("name", "爱丽丝");
        first.put("loginTime", "2026-10-29 08:00:00");
        first.put("ip", "203.0.113.9");
        first.put("browser", "Chrome");
        first.put("os", "Windows");
        first.put("lastActive", "2026-10-29 09:00:00");
        first.put("tokenVersion", "3");
        when(hashOperations.entries(HASH_KEY)).thenReturn(first);

        Map<String, String> broken = new LinkedHashMap<>();
        broken.put("username", "missing-fields");
        when(hashOperations.entries(secondHash)).thenReturn(broken);

        List<OnlineSession> sessions = registry.list();

        assertEquals(1, sessions.size());
        OnlineSession session = sessions.get(0);
        assertEquals(SESSION_ID, session.sessionId());
        assertEquals(7L, session.userId());
        assertEquals("alice", session.username());
        assertEquals("爱丽丝", session.name());
        assertEquals("2026-10-29 08:00:00", session.loginTime().toString());
        assertEquals("203.0.113.9", session.ip());
        assertEquals("Chrome", session.browser());
        assertEquals("Windows", session.os());
        assertEquals("2026-10-29 09:00:00", session.lastActive().toString());
        assertEquals(3L, session.tokenVersion());
        // 节流键不是会话行；字段不全的坏 hash 直接跳过，不抛异常。
        verify(hashOperations, never()).entries(activeKey);
    }

    @Test
    void removingClearsBothTheSessionHashAndTheThrottleKey() {
        registry.remove(SESSION_ID);

        verify(redisTemplate).delete(HASH_KEY);
        verify(redisTemplate).delete(ACTIVE_KEY);
    }

    @Test
    void forceLogoutRevokesAndRemovesOnlyAKnownSession() {
        when(redisTemplate.hasKey(HASH_KEY)).thenReturn(true);

        assertTrue(registry.forceLogout(SESSION_ID));

        verify(tokenRevocation).revoke(SESSION_ID);
        verify(redisTemplate).delete(HASH_KEY);
        verify(redisTemplate).delete(ACTIVE_KEY);
    }

    @Test
    void forceLoggingOutAnUnknownSessionIsANoOp() {
        when(redisTemplate.hasKey(HASH_KEY)).thenReturn(false);

        assertFalse(registry.forceLogout(SESSION_ID));

        verify(tokenRevocation, never()).revoke(anyString());
        verify(redisTemplate, never()).delete(HASH_KEY);
    }

    @Test
    void blankSessionIdsAreIgnored() {
        registry.touch(" ");
        registry.remove(null);
        assertFalse(registry.forceLogout(""));

        verify(hashOperations, never()).put(anyString(), anyString(), anyString());
        verify(redisTemplate, never()).delete(anyString());
    }

    private static void assertTime(String value) {
        assertTrue(value != null && value.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}"),
                "expected yyyy-MM-dd HH:mm:ss but was " + value);
        // 能被同一个值类型解析，供列表按时间范围过滤与排序。
        assertNotNull(SessionTime.parse(value));
    }
}
