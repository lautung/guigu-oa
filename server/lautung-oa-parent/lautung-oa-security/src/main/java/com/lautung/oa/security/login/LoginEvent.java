package com.lautung.oa.security.login;

/**
 * One audit event. The security module only knows the shape; the audit module decides where it
 * lands (database row, application log, or both).
 *
 * <p>{@code userAgent} and {@code msg} may be null. No password or token may ever be put here.
 * {@code kind} separates login attempts from self-registration (#253, ADR 0062); the five-argument
 * constructor keeps the login path on {@link LoginEventKind#LOGIN} without touching its call sites.
 */
public record LoginEvent(
        LoginEventType type,
        String username,
        String sourceAddress,
        String userAgent,
        String msg,
        LoginEventKind kind
) {

    public LoginEvent {
        if (kind == null) {
            kind = LoginEventKind.LOGIN;
        }
    }

    public LoginEvent(
            LoginEventType type,
            String username,
            String sourceAddress,
            String userAgent,
            String msg
    ) {
        this(type, username, sourceAddress, userAgent, msg, LoginEventKind.LOGIN);
    }
}
