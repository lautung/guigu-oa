package com.lautung.oa.audit.login;

import com.lautung.oa.audit.service.LoginLogWriter;
import com.lautung.oa.security.login.LoginAudit;
import com.lautung.oa.security.login.LoginEvent;
import com.lautung.oa.security.login.LoginEventType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * ADR 0054 的组合语义：库是查询真相，应用日志是 DB 故障期的兜底；被锁定挡下的尝试
 * 只走日志，永远不碰数据库。
 */
class CompositeLoginAuditTest {

    private static final LoginEvent EVENT = new LoginEvent(
            LoginEventType.FAILURE, "alice", "203.0.113.7", "curl/8.4.0", "登录失败");

    private final LoginAudit database = mock(LoginAudit.class);
    private final LoginAudit fallback = mock(LoginAudit.class);
    private final CompositeLoginAudit audit = new CompositeLoginAudit(database, fallback);

    @Test
    void aHealthyWriteGoesToTheDatabaseOnly() {
        audit.record(EVENT);

        verify(database).record(EVENT);
        verifyNoInteractions(fallback);
    }

    @Test
    void aDatabaseFailureIsSwallowedAndFallsBackToTheApplicationLog() {
        doThrow(new IllegalStateException("database is down")).when(database).record(EVENT);

        assertDoesNotThrow(() -> audit.record(EVENT));

        verify(fallback).record(EVENT);
    }

    @Test
    void logOnlyNeverTouchesTheDatabase() {
        audit.recordLogOnly(EVENT);

        verifyNoInteractions(database);
        verify(fallback).record(EVENT);
    }

    @Test
    void theDatabaseAuditNeverWritesALogOnlyRow() {
        LoginLogWriter writer = mock(LoginLogWriter.class);
        DatabaseLoginAudit databaseAudit = new DatabaseLoginAudit(writer);

        databaseAudit.record(EVENT);
        databaseAudit.recordLogOnly(EVENT);

        verify(writer).write(EVENT);
    }
}
