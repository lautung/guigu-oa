package com.lautung.oa.security.login;

/** Remembers failed logins so a password cannot be guessed without limit. */
public interface LoginAttemptLimiter {

    /**
     * Whether this attempt must be refused before its password is even checked. Counting is
     * done on the submitted username, so an unknown account is treated exactly like a known one.
     */
    boolean isBlocked(String username, String sourceAddress);

    /** Records a checked password that did not authenticate; {@code userAgent} is audit metadata only. */
    void recordFailure(String username, String sourceAddress, String userAgent);

    /** Clears the account's failures; the address keeps its own count. */
    void recordSuccess(String username, String sourceAddress);

    /**
     * Clears the account dimension's failure count, last-failure time and lock marker, and audits
     * the action. The source dimension is a brute-force threshold and has no manual unlock; it
     * only expires (ADR 0054).
     */
    void unlockAccount(String username);
}
