package com.lautung.oa.security.login;

import java.time.Duration;

/**
 * How one dimension of login failures escalates.
 *
 * <p>After {@code backoffAfter} failures every further attempt must wait an exponentially
 * growing interval, and at {@code lockAfter} failures the dimension is locked for
 * {@code lockDuration}. Counters expire after {@code window} without a failure.</p>
 */
public record LoginAttemptPolicy(
        int backoffAfter,
        Duration backoffBase,
        Duration backoffCap,
        int lockAfter,
        Duration lockDuration,
        Duration window
) {

    /** The account dimension: a handful of failures slow down, ten failures lock. */
    public static LoginAttemptPolicy account() {
        return new LoginAttemptPolicy(
                5, Duration.ofSeconds(1), Duration.ofSeconds(30),
                10, Duration.ofMinutes(15), Duration.ofMinutes(15));
    }

    /**
     * The client-address dimension: many accounts may be tried from one address before it is
     * refused, because one office or carrier NAT shares an address.
     */
    public static LoginAttemptPolicy source() {
        return new LoginAttemptPolicy(
                0, Duration.ZERO, Duration.ZERO,
                100, Duration.ofMinutes(15), Duration.ofMinutes(15));
    }

    public LoginAttemptPolicy {
        if (lockAfter < 1) {
            throw new IllegalArgumentException("lockAfter must be positive");
        }
        if (backoffAfter < 0) {
            throw new IllegalArgumentException("backoffAfter must not be negative");
        }
        if (backoffAfter > 0 && backoffAfter >= lockAfter) {
            throw new IllegalArgumentException("backoffAfter must stay below lockAfter");
        }
        if (window.isNegative() || window.isZero()) {
            throw new IllegalArgumentException("window must be positive");
        }
        if (lockDuration.isNegative() || lockDuration.isZero()) {
            throw new IllegalArgumentException("lockDuration must be positive");
        }
        if (backoffAfter > 0 && (backoffBase.isNegative() || backoffBase.isZero())) {
            throw new IllegalArgumentException("backoffBase must be positive when backoff is enabled");
        }
    }

    public boolean backoffEnabled() {
        return backoffAfter > 0;
    }

    /** The wait required after {@code failures} recorded failures. */
    public Duration backoffFor(int failures) {
        if (!backoffEnabled() || failures < backoffAfter) {
            return Duration.ZERO;
        }
        long millis = backoffBase.toMillis() * (1L << Math.min(failures - backoffAfter, 20));
        return Duration.ofMillis(Math.min(millis, backoffCap.toMillis()));
    }
}
