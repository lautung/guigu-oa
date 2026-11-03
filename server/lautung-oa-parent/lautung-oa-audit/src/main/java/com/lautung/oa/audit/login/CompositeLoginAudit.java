package com.lautung.oa.audit.login;

import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.LoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 登录审计的组合（ADR 0054）：库是查询真相，应用日志是 DB 故障期的兜底轨迹。
 *
 * <p>写库失败只 WARN，然后补一条应用日志，绝不向上抛——审计是旁路，不能把一次合法登录
 * 变成 500。{@link #recordLogOnly(LoginEvent)} 直接走日志，被锁定挡下的尝试永不落库。
 */
public class CompositeLoginAudit implements LoginAudit {

    private static final Logger log = LoggerFactory.getLogger(CompositeLoginAudit.class);

    private final LoginAudit database;

    private final LoginAudit fallback;

    public CompositeLoginAudit(LoginAudit database, LoginAudit fallback) {
        this.database = database;
        this.fallback = fallback;
    }

    @Override
    public void record(LoginEvent event) {
        try {
            database.record(event);
        } catch (RuntimeException e) {
            log.warn("Login audit write failed for username={}; falling back to the application log",
                    event == null ? null : event.username(), e);
            fallback.record(event);
        }
    }

    @Override
    public void recordLogOnly(LoginEvent event) {
        fallback.record(event);
    }
}
