package com.atguigu.security.login;

import java.time.Instant;

/** Audit trail of repeated login failures; no password or token may ever pass through here. */
public interface LoginAudit {

    void loginFailed(String username, String sourceAddress, int accountFailures);

    void loginLocked(String username, String sourceAddress, LoginAttemptScope scope, Instant lockedUntil);

    void loginUnlocked(String username, String sourceAddress, LoginAttemptScope scope);
}
