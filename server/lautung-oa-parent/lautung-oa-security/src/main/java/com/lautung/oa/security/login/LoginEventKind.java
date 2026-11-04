package com.lautung.oa.security.login;

/**
 * The audit dimension a {@link LoginEvent} belongs to. {@code sys_login_log.event} stores this
 * string: login attempts are {@code login}, self-registration is {@code register} (#253, ADR 0062).
 *
 * <p>The login path keeps its old five-field constructor, which defaults to {@link #LOGIN}, so
 * every existing call site and stored row stays on the {@code login} dimension.
 */
public enum LoginEventKind {
    LOGIN("login"),
    REGISTER("register");

    private final String value;

    LoginEventKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
