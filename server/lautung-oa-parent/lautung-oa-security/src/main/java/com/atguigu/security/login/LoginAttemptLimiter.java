package com.atguigu.security.login;

/** Remembers failed logins so a password cannot be guessed without limit. */
public interface LoginAttemptLimiter {

    /**
     * Whether this attempt must be refused before its password is even checked. Counting is
     * done on the submitted username, so an unknown account is treated exactly like a known one.
     */
    boolean isBlocked(String username, String sourceAddress);

    /** Records a checked password that did not authenticate. */
    void recordFailure(String username, String sourceAddress);

    /** Clears the account's failures; the address keeps its own count. */
    void recordSuccess(String username, String sourceAddress);
}
