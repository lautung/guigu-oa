package com.lautung.oa.security.login;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes the audit trail to the application log as key=value lines. User-controlled fields are
 * stripped of control characters so a failed login cannot forge extra log lines.
 *
 * <p>This is the fallback path: {@code audit} wires a database writer ahead of it, so a line
 * reaching this class either belongs to an attempt that must not be persisted (locked-out
 * attempts) or is covering a database write failure (ADR 0054).
 */
public class Slf4jLoginAudit implements LoginAudit {

    private static final Logger log = LoggerFactory.getLogger(Slf4jLoginAudit.class);
    private static final int MAX_FIELD_LENGTH = 64;

    @Override
    public void record(LoginEvent event) {
        write(event);
    }

    @Override
    public void recordLogOnly(LoginEvent event) {
        write(event);
    }

    private void write(LoginEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        String message = "login audit event={} kind={} username={} source={} msg={}";
        Object[] fields = new Object[]{
                eventName(event.type()), event.kind().value(), field(event.username()),
                field(event.sourceAddress()), field(event.msg())};
        if (event.type() == LoginEventType.LOCKED) {
            log.warn(message, fields);
            return;
        }
        log.info(message, fields);
    }

    private static String eventName(LoginEventType type) {
        return switch (type) {
            case SUCCESS -> "success";
            case FAILURE -> "failed";
            case LOCKED -> "locked";
            case UNLOCKED -> "unlocked";
        };
    }

    private static String field(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        String printable = value.replaceAll("[\\p{Cntrl}\\s=]", "_");
        return printable.length() > MAX_FIELD_LENGTH ? printable.substring(0, MAX_FIELD_LENGTH) : printable;
    }
}
