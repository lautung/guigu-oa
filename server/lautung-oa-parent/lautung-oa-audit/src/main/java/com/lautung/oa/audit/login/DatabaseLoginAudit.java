package com.lautung.oa.audit.login;

import com.lautung.oa.audit.service.LoginLogWriter;
import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.LoginEvent;

/**
 * 登录日志的数据库实现（ADR 0054）：{@link #record(LoginEvent)} 同步写 {@code sys_login_log}。
 *
 * <p>它没有「只写日志」的语义——那是组装层的决定；{@link #recordLogOnly(LoginEvent)} 空实现，
 * 防止某个调用点误把「不该落库」的事件交给数据库实现时反而写出一行。
 */
public class DatabaseLoginAudit implements LoginAudit {

    private final LoginLogWriter writer;

    public DatabaseLoginAudit(LoginLogWriter writer) {
        this.writer = writer;
    }

    @Override
    public void record(LoginEvent event) {
        writer.write(event);
    }

    @Override
    public void recordLogOnly(LoginEvent event) {
        // 数据库实现没有 log-only 路径；组合层负责把它交给应用日志实现。
    }
}
