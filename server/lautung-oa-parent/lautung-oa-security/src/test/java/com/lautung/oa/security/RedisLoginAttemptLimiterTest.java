package com.lautung.oa.security;

import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.security.login.LoginAttemptPolicy;
import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.LoginEvent;
import com.lautung.oa.security.login.LoginEventType;
import com.lautung.oa.security.login.RedisLoginAttemptLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class RedisLoginAttemptLimiterTest {

    private static final Instant START = Instant.parse("2026-10-29T00:00:00Z");
    private static final String USER = "alice";
    private static final String SOURCE = "198.51.100.7";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0";

    private final MutableClock clock = new MutableClock(START);
    private final RecordingLoginAudit audit = new RecordingLoginAudit();
    private final InMemoryRedisTemplate redis = new InMemoryRedisTemplate();

    @Test
    public void fourFailuresAreAllowedAndTheFifthStartsTheWait() {
        LoginAttemptLimiter limiter = limiter();

        for (int failure = 1; failure <= 4; failure += 1) {
            assertFalse(limiter.isBlocked(USER, SOURCE), "failure " + failure + " is still below the backoff");
            limiter.recordFailure(USER, SOURCE, USER_AGENT);
        }

        assertFalse(limiter.isBlocked(USER, SOURCE), "four failures are still below the backoff");
        limiter.recordFailure(USER, SOURCE, USER_AGENT);

        assertTrue(limiter.isBlocked(USER, SOURCE), "the fifth failure starts a one second wait");
        clock.advance(Duration.ofMillis(999));
        assertTrue(limiter.isBlocked(USER, SOURCE));
        clock.advance(Duration.ofMillis(1));
        assertFalse(limiter.isBlocked(USER, SOURCE));
    }

    @Test
    public void eachFurtherFailureDoublesTheWait() {
        LoginAttemptLimiter limiter = limiter();
        for (int failure = 1; failure <= 5; failure += 1) {
            limiter.recordFailure(USER, SOURCE, USER_AGENT);
        }

        clock.advance(Duration.ofSeconds(1));
        assertFalse(limiter.isBlocked(USER, SOURCE));
        limiter.recordFailure(USER, SOURCE, USER_AGENT);

        clock.advance(Duration.ofSeconds(1));
        assertTrue(limiter.isBlocked(USER, SOURCE), "the sixth failure waits two seconds");
        clock.advance(Duration.ofSeconds(1));
        assertFalse(limiter.isBlocked(USER, SOURCE));
    }

    @Test
    public void tenFailuresLockTheAccountForFifteenMinutesAndAuditBothEnds() {
        LoginAttemptLimiter limiter = limiter();
        for (int failure = 1; failure <= 5; failure += 1) {
            limiter.recordFailure(USER, SOURCE, USER_AGENT);
        }
        for (long waitSeconds : new long[]{1, 2, 4, 8, 16}) {
            clock.advance(Duration.ofSeconds(waitSeconds));
            assertFalse(limiter.isBlocked(USER, SOURCE));
            limiter.recordFailure(USER, SOURCE, USER_AGENT);
        }

        // The exponential wait would end after 30 seconds; the lock must outlast it by 15 minutes.
        clock.advance(Duration.ofSeconds(31));
        assertTrue(limiter.isBlocked(USER, SOURCE));
        assertEquals(1, audit.count(LoginEventType.LOCKED, "账号锁定"));

        clock.advance(Duration.ofMinutes(15).minusSeconds(31));
        assertFalse(limiter.isBlocked(USER, SOURCE));
        assertEquals(1, audit.count(LoginEventType.UNLOCKED, "账号解锁"));

        limiter.recordFailure(USER, SOURCE, USER_AGENT);
        assertFalse(limiter.isBlocked(USER, SOURCE), "failures restart from zero after the lock");
    }

    @Test
    public void aSuccessfulLoginClearsTheAccountButNotTheSource() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                10, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptPolicy source = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                5, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, source);

        for (int failure = 1; failure <= 4; failure += 1) {
            limiter.recordFailure("alice", SOURCE, USER_AGENT);
        }
        limiter.recordSuccess("alice", SOURCE);
        assertFalse(limiter.isBlocked("alice", SOURCE));

        limiter.recordFailure("bob", SOURCE, USER_AGENT);
        assertTrue(limiter.isBlocked("alice", SOURCE), "the source still remembers the failures");
        assertFalse(limiter.isBlocked("alice", "203.0.113.9"));
    }

    @Test
    public void failuresAreCountedPerNormalizedUsername() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, LoginAttemptPolicy.source());

        limiter.recordFailure("Alice", SOURCE, USER_AGENT);
        limiter.recordFailure(" alice ", SOURCE, USER_AGENT);

        assertTrue(limiter.isBlocked("ALICE", SOURCE));
        assertFalse(limiter.isBlocked("bob", SOURCE));
    }

    @Test
    public void aSourceIsLockedAfterFailuresSpreadAcrossAccounts() {
        LoginAttemptPolicy source = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(5), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(LoginAttemptPolicy.account(), source);

        limiter.recordFailure("alice", SOURCE, USER_AGENT);
        limiter.recordFailure("bob", SOURCE, USER_AGENT);

        assertTrue(limiter.isBlocked("carol", SOURCE));
        assertFalse(limiter.isBlocked("carol", "203.0.113.9"));
    }

    @Test
    public void blankUsernamesAndMissingAddressesDoNotBreakThePolicy() {
        LoginAttemptLimiter limiter = limiter();

        assertFalse(limiter.isBlocked(null, null));
        limiter.recordFailure(null, null, null);
        limiter.recordSuccess(null, null);
        limiter.unlockAccount(null);
        assertFalse(limiter.isBlocked("", ""));
    }

    @Test
    public void eachFailureIsAuditedOnceWithTheSubmittedFields() {
        LoginAttemptLimiter limiter = limiter();

        limiter.recordFailure(USER, SOURCE, USER_AGENT);
        limiter.recordFailure(USER, SOURCE, USER_AGENT);

        List<LoginEvent> failures = audit.events(LoginEventType.FAILURE);
        assertEquals(2, failures.size());
        assertEquals(USER, failures.get(0).username());
        assertEquals(SOURCE, failures.get(0).sourceAddress());
        assertEquals(USER_AGENT, failures.get(0).userAgent());
    }

    @Test
    public void aFailureThatLocksAuditsOneLockedEventPerScope() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptPolicy source = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, source);

        limiter.recordFailure(USER, SOURCE, USER_AGENT);
        limiter.recordFailure(USER, SOURCE, USER_AGENT);

        assertEquals(1, audit.count(LoginEventType.LOCKED, "账号锁定"));
        assertEquals(1, audit.count(LoginEventType.LOCKED, "来源锁定"));
    }

    @Test
    public void expiredLocksAreClearedAndAuditedAsLazyUnlocks() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                1, Duration.ofMinutes(1), Duration.ofMinutes(15));
        LoginAttemptPolicy source = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                1, Duration.ofMinutes(1), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, source);

        limiter.recordFailure(USER, SOURCE, USER_AGENT);
        assertTrue(limiter.isBlocked(USER, SOURCE));
        assertEquals(1, audit.count(LoginEventType.LOCKED, "账号锁定"));
        assertEquals(1, audit.count(LoginEventType.LOCKED, "来源锁定"));

        clock.advance(Duration.ofMinutes(2));
        assertFalse(limiter.isBlocked(USER, SOURCE));

        assertEquals(1, audit.count(LoginEventType.UNLOCKED, "账号解锁"));
        assertEquals(1, audit.count(LoginEventType.UNLOCKED, "来源解锁"));
    }

    @Test
    public void aBlockedCheckItselfWritesNoAuditEvent() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                1, Duration.ofMinutes(1), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, LoginAttemptPolicy.source());

        limiter.recordFailure(USER, SOURCE, USER_AGENT);
        int recorded = audit.recorded.size();

        assertTrue(limiter.isBlocked(USER, SOURCE));

        assertEquals(recorded, audit.recorded.size(), "a blocked check must not add a row");
        assertEquals(0, audit.logOnly.size(), "the limiter has no log-only path of its own");
    }

    @Test
    public void manualUnlockClearsOnlyTheAccountDimensionAndAuditsIt() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptPolicy source = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, source);
        limiter.recordFailure(USER, SOURCE, USER_AGENT);
        limiter.recordFailure(USER, SOURCE, USER_AGENT);

        limiter.unlockAccount(USER);

        assertFalse(redis.contains("login:attempt:account:alice:failures"));
        assertFalse(redis.contains("login:attempt:account:alice:last-failure"));
        assertFalse(redis.contains("login:attempt:account:alice:lock"));
        assertTrue(redis.contains("login:attempt:source:198.51.100.7:lock"), "the source stays locked");
        assertTrue(limiter.isBlocked(USER, SOURCE), "the source dimension still blocks");
        assertFalse(limiter.isBlocked(USER, "203.0.113.9"), "the account dimension is clear");
        assertEquals(1, audit.count(LoginEventType.UNLOCKED, "手动解锁"));
    }

    private RedisLoginAttemptLimiter limiter() {
        return limiter(LoginAttemptPolicy.account(), LoginAttemptPolicy.source());
    }

    private RedisLoginAttemptLimiter limiter(LoginAttemptPolicy account, LoginAttemptPolicy source) {
        return new RedisLoginAttemptLimiter(redis, account, source, audit, clock);
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    private static final class RecordingLoginAudit implements LoginAudit {

        private final List<LoginEvent> recorded = new ArrayList<>();
        private final List<LoginEvent> logOnly = new ArrayList<>();

        @Override
        public void record(LoginEvent event) {
            recorded.add(event);
        }

        @Override
        public void recordLogOnly(LoginEvent event) {
            logOnly.add(event);
        }

        private List<LoginEvent> events(LoginEventType type) {
            return recorded.stream().filter(event -> event.type() == type).toList();
        }

        private long count(LoginEventType type, String msg) {
            return recorded.stream()
                    .filter(event -> event.type() == type && msg.equals(event.msg()))
                    .count();
        }
    }

    private static final class InMemoryRedisTemplate extends RedisTemplate<String, String> {

        private final Map<String, String> store = new ConcurrentHashMap<>();

        private boolean contains(String key) {
            return store.containsKey(key);
        }

        @Override
        @SuppressWarnings("unchecked")
        public ValueOperations<String, String> opsForValue() {
            return (ValueOperations<String, String>) Proxy.newProxyInstance(
                    ValueOperations.class.getClassLoader(),
                    new Class<?>[]{ValueOperations.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if ("get".equals(name)) {
                            return store.get(String.valueOf(args[0]));
                        }
                        if ("set".equals(name) && args.length >= 2) {
                            store.put(String.valueOf(args[0]), String.valueOf(args[1]));
                            return null;
                        }
                        if ("increment".equals(name)) {
                            String key = String.valueOf(args[0]);
                            return Long.valueOf(store.merge(
                                    key, "1", (current, ignored) -> String.valueOf(Long.parseLong(current) + 1)));
                        }
                        if ("toString".equals(name)) {
                            return "InMemoryValueOperations";
                        }
                        throw new UnsupportedOperationException(name);
                    });
        }

        @Override
        public Boolean delete(String key) {
            return store.remove(key) != null;
        }

        @Override
        public Boolean expire(String key, long timeout, java.util.concurrent.TimeUnit unit) {
            return store.containsKey(key);
        }

        @Override
        public void afterPropertiesSet() {
        }
    }
}
