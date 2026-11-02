package com.atguigu.security;

import com.atguigu.security.login.LoginAttemptPolicy;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LoginAttemptPolicyTest {

    @Test
    public void accountFailuresBackOffExponentiallyFromTheFifthFailure() {
        LoginAttemptPolicy policy = LoginAttemptPolicy.account();

        assertEquals(Duration.ZERO, policy.backoffFor(0));
        assertEquals(Duration.ZERO, policy.backoffFor(4));
        assertEquals(Duration.ofSeconds(1), policy.backoffFor(5));
        assertEquals(Duration.ofSeconds(2), policy.backoffFor(6));
        assertEquals(Duration.ofSeconds(4), policy.backoffFor(7));
        assertEquals(Duration.ofSeconds(8), policy.backoffFor(8));
        assertEquals(Duration.ofSeconds(16), policy.backoffFor(9));
        assertEquals(Duration.ofSeconds(30), policy.backoffFor(10));
        assertEquals(Duration.ofSeconds(30), policy.backoffFor(1_000));
    }

    @Test
    public void accountFailuresLockForFifteenMinutesAfterTenFailures() {
        LoginAttemptPolicy policy = LoginAttemptPolicy.account();

        assertEquals(10, policy.lockAfter());
        assertEquals(Duration.ofMinutes(15), policy.lockDuration());
        assertEquals(Duration.ofMinutes(15), policy.window());
    }

    @Test
    public void theSourcePolicyOnlyLocksAndDoesNotBackOff() {
        LoginAttemptPolicy policy = LoginAttemptPolicy.source();

        assertFalse(policy.backoffEnabled());
        assertEquals(Duration.ZERO, policy.backoffFor(5));
        assertEquals(100, policy.lockAfter());
        assertEquals(Duration.ofMinutes(15), policy.lockDuration());
    }

    @Test
    public void anAccountLockThresholdAtOrBelowTheBackoffThresholdIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new LoginAttemptPolicy(
                5, Duration.ofSeconds(1), Duration.ofSeconds(30),
                5, Duration.ofMinutes(15), Duration.ofMinutes(15)));
    }

    @Test
    public void aDisabledBackoffNeedsNeitherBaseNorCap() {
        assertTrue(new LoginAttemptPolicy(
                0, Duration.ZERO, Duration.ZERO,
                3, Duration.ofMinutes(1), Duration.ofMinutes(1)).lockAfter() > 0);
    }
}
