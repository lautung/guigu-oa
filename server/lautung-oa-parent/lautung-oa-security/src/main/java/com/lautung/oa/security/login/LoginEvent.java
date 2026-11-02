package com.lautung.oa.security.login;

/**
 * One login audit event. The security module only knows the shape; the audit module decides
 * where it lands (database row, application log, or both).
 *
 * <p>{@code userAgent} and {@code msg} may be null. No password or token may ever be put here.
 */
public record LoginEvent(
        LoginEventType type,
        String username,
        String sourceAddress,
        String userAgent,
        String msg
) {
}
