package com.lautung.oa.security.login;

/**
 * The four things worth remembering about a login attempt. The numeric {@code status} is what
 * {@code sys_login_log.status} stores: 0 success / 1 failure / 2 locked / 3 unlocked (ADR 0054).
 */
public enum LoginEventType {
    SUCCESS(0),
    FAILURE(1),
    LOCKED(2),
    UNLOCKED(3);

    private final int status;

    LoginEventType(int status) {
        this.status = status;
    }

    public int status() {
        return status;
    }
}
