package com.lautung.oa.security.login;

/**
 * Audit trail of login attempts; no password or token may ever pass through here.
 *
 * <p>{@link #record(LoginEvent)} is the durable path: a blocked attempt must never take it (one
 * attacker could otherwise turn the audit table into a write amplifier). {@link
 * #recordLogOnly(LoginEvent)} writes the application log without a database row and is what a
 * locked-out attempt uses.
 */
public interface LoginAudit {

    void record(LoginEvent event);

    void recordLogOnly(LoginEvent event);
}
