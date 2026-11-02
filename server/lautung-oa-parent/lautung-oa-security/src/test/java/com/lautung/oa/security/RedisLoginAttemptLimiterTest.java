package com.lautung.oa.security;

import com.lautung.oa.security.login.LoginAttemptLimiter;
import com.lautung.oa.security.login.LoginAttemptPolicy;
import com.lautung.oa.security.login.LoginAttemptScope;
import com.lautung.oa.security.login.LoginAudit;
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

    private final MutableClock clock = new MutableClock(START);
    private final RecordingLoginAudit audit = new RecordingLoginAudit();
    private final InMemoryRedisTemplate redis = new InMemoryRedisTemplate();

    @Test
    public void fourFailuresAreAllowedAndTheFifthStartsTheWait() {
        LoginAttemptLimiter limiter = limiter();

        for (int failure = 1; failure <= 4; failure += 1) {
            assertFalse(limiter.isBlocked(USER, SOURCE), "failure " + failure + " is still below the backoff");
            limiter.recordFailure(USER, SOURCE);
        }

        assertFalse(limiter.isBlocked(USER, SOURCE), "four failures are still below the backoff");
        limiter.recordFailure(USER, SOURCE);

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
            limiter.recordFailure(USER, SOURCE);
        }

        clock.advance(Duration.ofSeconds(1));
        assertFalse(limiter.isBlocked(USER, SOURCE));
        limiter.recordFailure(USER, SOURCE);

        clock.advance(Duration.ofSeconds(1));
        assertTrue(limiter.isBlocked(USER, SOURCE), "the sixth failure waits two seconds");
        clock.advance(Duration.ofSeconds(1));
        assertFalse(limiter.isBlocked(USER, SOURCE));
    }

    @Test
    public void tenFailuresLockTheAccountForFifteenMinutesAndAuditBothEnds() {
        LoginAttemptLimiter limiter = limiter();
        for (int failure = 1; failure <= 5; failure += 1) {
            limiter.recordFailure(USER, SOURCE);
        }
        for (long waitSeconds : new long[]{1, 2, 4, 8, 16}) {
            clock.advance(Duration.ofSeconds(waitSeconds));
            assertFalse(limiter.isBlocked(USER, SOURCE));
            limiter.recordFailure(USER, SOURCE);
        }

        // The exponential wait would end after 30 seconds; the lock must outlast it by 15 minutes.
        clock.advance(Duration.ofSeconds(31));
        assertTrue(limiter.isBlocked(USER, SOURCE));
        assertEquals(1, audit.locked(LoginAttemptScope.ACCOUNT));

        clock.advance(Duration.ofMinutes(15).minusSeconds(31));
        assertFalse(limiter.isBlocked(USER, SOURCE));
        assertEquals(1, audit.unlocked(LoginAttemptScope.ACCOUNT));

        limiter.recordFailure(USER, SOURCE);
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
            limiter.recordFailure("alice", SOURCE);
        }
        limiter.recordSuccess("alice", SOURCE);
        assertFalse(limiter.isBlocked("alice", SOURCE));

        limiter.recordFailure("bob", SOURCE);
        assertTrue(limiter.isBlocked("alice", SOURCE), "the source still remembers the failures");
        assertFalse(limiter.isBlocked("alice", "203.0.113.9"));
    }

    @Test
    public void failuresAreCountedPerNormalizedUsername() {
        LoginAttemptPolicy account = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(15), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(account, LoginAttemptPolicy.source());

        limiter.recordFailure("Alice", SOURCE);
        limiter.recordFailure(" alice ", SOURCE);

        assertTrue(limiter.isBlocked("ALICE", SOURCE));
        assertFalse(limiter.isBlocked("bob", SOURCE));
    }

    @Test
    public void aSourceIsLockedAfterFailuresSpreadAcrossAccounts() {
        LoginAttemptPolicy source = new LoginAttemptPolicy(
                0, Duration.ofSeconds(1), Duration.ofSeconds(30),
                2, Duration.ofMinutes(5), Duration.ofMinutes(15));
        LoginAttemptLimiter limiter = limiter(LoginAttemptPolicy.account(), source);

        limiter.recordFailure("alice", SOURCE);
        limiter.recordFailure("bob", SOURCE);

        assertTrue(limiter.isBlocked("carol", SOURCE));
        assertFalse(limiter.isBlocked("carol", "203.0.113.9"));
    }

    @Test
    public void blankUsernamesAndMissingAddressesDoNotBreakThePolicy() {
        LoginAttemptLimiter limiter = limiter();

        assertFalse(limiter.isBlocked(null, null));
        limiter.recordFailure(null, null);
        limiter.recordSuccess(null, null);
        assertFalse(limiter.isBlocked("", ""));
    }

    @Test
    public void eachFailureIsAuditedWithTheAccountCount() {
        LoginAttemptLimiter limiter = limiter();

        limiter.recordFailure(USER, SOURCE);
        limiter.recordFailure(USER, SOURCE);

        assertEquals(List.of(1, 2), audit.failureCounts(USER, SOURCE));
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

        private final List<Failure> failures = new ArrayList<>();
        private final List<Object[]> locks = new ArrayList<>();
        private final List<Object[]> unlocks = new ArrayList<>();

        @Override
        public void loginFailed(String username, String source, int failures) {
            this.failures.add(new Failure(username, source, failures));
        }

        @Override
        public void loginLocked(String username, String source, LoginAttemptScope scope, Instant lockedUntil) {
            locks.add(new Object[]{username, source, scope, lockedUntil});
        }

        @Override
        public void loginUnlocked(String username, String source, LoginAttemptScope scope) {
            unlocks.add(new Object[]{username, source, scope});
        }

        private List<Integer> failureCounts(String username, String source) {
            return failures.stream()
                    .filter(failure -> failure.username().equals(username) && failure.source().equals(source))
                    .map(Failure::failures)
                    .toList();
        }

        private long locked(LoginAttemptScope scope) {
            return locks.stream().filter(lock -> lock[2] == scope).count();
        }

        private long unlocked(LoginAttemptScope scope) {
            return unlocks.stream().filter(unlock -> unlock[2] == scope).count();
        }
    }

    private record Failure(String username, String source, int failures) {
    }

    private static final class InMemoryRedisTemplate extends RedisTemplate<String, String> {

        private final Map<String, String> store = new ConcurrentHashMap<>();

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
