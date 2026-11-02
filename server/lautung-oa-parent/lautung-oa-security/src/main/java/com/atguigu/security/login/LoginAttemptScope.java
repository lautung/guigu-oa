package com.atguigu.security.login;

/** The two independent failure counters: the submitted account and the client address. */
public enum LoginAttemptScope {
    ACCOUNT,
    SOURCE
}
