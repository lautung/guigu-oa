package com.lautung.oa.security.login;

/** The two independent failure counters: the submitted account and the client address. */
public enum LoginAttemptScope {
    ACCOUNT,
    SOURCE
}
