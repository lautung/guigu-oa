package com.lautung.oa.security.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Locale;

/**
 * Writes the audit trail to the application log as key=value lines. User-controlled fields are
 * stripped of control characters so a failed login cannot forge extra log lines.
 */
public class Slf4jLoginAudit implements LoginAudit {

    private static final Logger log = LoggerFactory.getLogger(Slf4jLoginAudit.class);
    private static final int MAX_FIELD_LENGTH = 64;

    @Override
    public void loginFailed(String username, String sourceAddress, int accountFailures) {
        log.info("login audit event=failed username={} source={} failures={}",
                field(username), field(sourceAddress), accountFailures);
    }

    @Override
    public void loginLocked(String username, String sourceAddress, LoginAttemptScope scope, Instant lockedUntil) {
        log.warn("login audit event=locked username={} source={} scope={} lockedUntil={}",
                field(username), field(sourceAddress), scopeName(scope), lockedUntil);
    }

    @Override
    public void loginUnlocked(String username, String sourceAddress, LoginAttemptScope scope) {
        log.info("login audit event=unlocked username={} source={} scope={}",
                field(username), field(sourceAddress), scopeName(scope));
    }

    private static String scopeName(LoginAttemptScope scope) {
        return scope == null ? "-" : scope.name().toLowerCase(Locale.ROOT);
    }

    private static String field(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String printable = value.replaceAll("[\\p{Cntrl}\\s=]", "_");
        return printable.length() > MAX_FIELD_LENGTH ? printable.substring(0, MAX_FIELD_LENGTH) : printable;
    }
}
