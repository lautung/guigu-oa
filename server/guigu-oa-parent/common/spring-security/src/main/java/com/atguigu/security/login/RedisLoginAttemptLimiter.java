package com.atguigu.security.login;

import org.springframework.data.redis.core.RedisTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Redis-backed counters for the two login dimensions. Each dimension keeps its own failure
 * count, last failure time and lock marker, all expiring with the policy window; a lock marker
 * outlives its lock so the next attempt can audit the unlock.
 */
public class RedisLoginAttemptLimiter implements LoginAttemptLimiter {

    private static final String KEY_PREFIX = "login:attempt:";
    private static final Duration LOCK_MARKER_RETENTION = Duration.ofMinutes(5);
    private static final int MAX_DIMENSION_LENGTH = 64;
    private static final String BLANK_DIMENSION = "-";
    private static final String UNKNOWN_SOURCE = "unknown";

    private final RedisTemplate<String, String> redisTemplate;
    private final LoginAttemptPolicy accountPolicy;
    private final LoginAttemptPolicy sourcePolicy;
    private final LoginAudit audit;
    private final Clock clock;

    public RedisLoginAttemptLimiter(
            RedisTemplate<String, String> redisTemplate,
            LoginAttemptPolicy accountPolicy,
            LoginAttemptPolicy sourcePolicy,
            LoginAudit audit,
            Clock clock
    ) {
        this.redisTemplate = redisTemplate;
        this.accountPolicy = accountPolicy;
        this.sourcePolicy = sourcePolicy;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public boolean isBlocked(String username, String sourceAddress) {
        Instant now = clock.instant();
        Dimension account = Dimension.account(normalize(username, BLANK_DIMENSION));
        Dimension source = Dimension.source(normalize(sourceAddress, UNKNOWN_SOURCE));

        boolean accountBlocked = evaluate(account, accountPolicy, now, username, sourceAddress);
        boolean sourceBlocked = evaluate(source, sourcePolicy, now, username, sourceAddress);
        return accountBlocked || sourceBlocked;
    }

    @Override
    public void recordFailure(String username, String sourceAddress) {
        Instant now = clock.instant();
        Count account = countFailure(
                Dimension.account(normalize(username, BLANK_DIMENSION)), accountPolicy, now);
        Count source = countFailure(
                Dimension.source(normalize(sourceAddress, UNKNOWN_SOURCE)), sourcePolicy, now);
        audit.loginFailed(username, sourceAddress, account.failures());
        if (account.lockedUntil() != null) {
            audit.loginLocked(username, sourceAddress, LoginAttemptScope.ACCOUNT, account.lockedUntil());
        }
        if (source.lockedUntil() != null) {
            audit.loginLocked(username, sourceAddress, LoginAttemptScope.SOURCE, source.lockedUntil());
        }
    }

    @Override
    public void recordSuccess(String username, String sourceAddress) {
        Dimension account = Dimension.account(normalize(username, BLANK_DIMENSION));
        redisTemplate.delete(account.countKey());
        redisTemplate.delete(account.lastFailureKey());
    }

    private boolean evaluate(
            Dimension dimension,
            LoginAttemptPolicy policy,
            Instant now,
            String username,
            String sourceAddress
    ) {
        Long lockedUntil = readLong(dimension.lockKey());
        if (lockedUntil != null) {
            if (now.toEpochMilli() < lockedUntil) {
                return true;
            }
            clear(dimension);
            audit.loginUnlocked(username, sourceAddress, dimension.scope());
            return false;
        }
        if (!policy.backoffEnabled()) {
            return false;
        }
        int failures = readCount(dimension.countKey());
        Long lastFailureAt = readLong(dimension.lastFailureKey());
        if (failures < policy.backoffAfter() || lastFailureAt == null) {
            return false;
        }
        return now.toEpochMilli() - lastFailureAt < policy.backoffFor(failures).toMillis();
    }

    private Count countFailure(Dimension dimension, LoginAttemptPolicy policy, Instant now) {
        Long incremented = redisTemplate.opsForValue().increment(dimension.countKey());
        long failures = incremented == null ? 1 : incremented;
        // Only extend the window: overwriting the value here would let a slower thread undo an INCR.
        redisTemplate.expire(dimension.countKey(), policy.window().toMillis(), TimeUnit.MILLISECONDS);
        redisTemplate.opsForValue().set(
                dimension.lastFailureKey(), String.valueOf(now.toEpochMilli()), policy.window());

        Instant lockedUntil = null;
        if (failures >= policy.lockAfter()) {
            lockedUntil = now.plus(policy.lockDuration());
            redisTemplate.opsForValue().set(
                    dimension.lockKey(),
                    String.valueOf(lockedUntil.toEpochMilli()),
                    policy.lockDuration().plus(LOCK_MARKER_RETENTION));
        }
        return new Count((int) Math.min(failures, Integer.MAX_VALUE), lockedUntil);
    }

    private record Count(int failures, Instant lockedUntil) {
    }

    private void clear(Dimension dimension) {
        redisTemplate.delete(dimension.countKey());
        redisTemplate.delete(dimension.lastFailureKey());
        redisTemplate.delete(dimension.lockKey());
    }

    private Long readLong(String key) {
        String value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int readCount(String key) {
        Long value = readLong(key);
        return value == null ? 0 : (int) Math.min(value, Integer.MAX_VALUE);
    }

    private static String normalize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return normalized.length() > MAX_DIMENSION_LENGTH
                ? normalized.substring(0, MAX_DIMENSION_LENGTH)
                : normalized;
    }

    private record Dimension(LoginAttemptScope scope, String value) {

        private static Dimension account(String value) {
            return new Dimension(LoginAttemptScope.ACCOUNT, value);
        }

        private static Dimension source(String value) {
            return new Dimension(LoginAttemptScope.SOURCE, value);
        }

        private String prefix() {
            return KEY_PREFIX + scope.name().toLowerCase(Locale.ROOT) + ":" + value;
        }

        private String countKey() {
            return prefix() + ":failures";
        }

        private String lastFailureKey() {
            return prefix() + ":last-failure";
        }

        private String lockKey() {
            return prefix() + ":lock";
        }
    }
}
