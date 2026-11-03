package com.lautung.oa.audit.service;

import com.lautung.oa.audit.entity.SysLoginLog;
import com.lautung.oa.audit.mapper.LoginUserSnapshot;
import com.lautung.oa.audit.mapper.LoginUserSnapshotMapper;
import com.lautung.oa.audit.mapper.SysLoginLogMapper;
import com.lautung.oa.security.login.LoginEvent;
import com.lautung.oa.security.login.LoginEventType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginLogWriterTest {

    private static final String CHROME_WINDOWS =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/120.0.0.0 Safari/537.36";

    private final SysLoginLogMapper loginLogMapper = mock(SysLoginLogMapper.class);
    private final LoginUserSnapshotMapper snapshotMapper = mock(LoginUserSnapshotMapper.class);
    private final LoginLogWriter writer = new LoginLogWriter(loginLogMapper, snapshotMapper);

    @Test
    void writesOneRowWithTheUserSnapshotAndParsedClientColumns() {
        when(snapshotMapper.findLoginUserSnapshot("alice")).thenReturn(snapshot(7L, 77L));

        writer.write(new LoginEvent(
                LoginEventType.SUCCESS, "alice", "203.0.113.7", CHROME_WINDOWS, null));

        SysLoginLog row = insertedRow();
        assertEquals("alice", row.getUsername());
        assertEquals(7L, row.getUserId());
        assertEquals(77L, row.getDeptId());
        assertEquals("203.0.113.7", row.getIp());
        assertEquals("Chrome", row.getBrowser());
        assertEquals("Windows", row.getOs());
        assertEquals(0, row.getStatus(), "success maps to status 0");
        assertNull(row.getMsg());
        assertNotNull(row.getCreateTime(), "create_time must be present even without the handler");
    }

    @Test
    void unknownUsernamesKeepTheSnapshotColumnsEmpty() {
        when(snapshotMapper.findLoginUserSnapshot(any())).thenReturn(null);

        writer.write(new LoginEvent(
                LoginEventType.FAILURE, "no-such-user", "203.0.113.8", null, "登录失败"));

        SysLoginLog row = insertedRow();
        assertEquals("no-such-user", row.getUsername());
        assertNull(row.getUserId());
        assertNull(row.getDeptId());
        assertEquals(1, row.getStatus(), "failure maps to status 1");
        assertEquals("登录失败", row.getMsg());
        assertEquals("Unknown", row.getBrowser());
        assertEquals("Unknown", row.getOs());
    }

    @Test
    void lockedAndUnlockedEventsMapToStatusTwoAndThree() {
        when(snapshotMapper.findLoginUserSnapshot(any())).thenReturn(snapshot(1L, null));

        writer.write(new LoginEvent(
                LoginEventType.LOCKED, "alice", "203.0.113.9", CHROME_WINDOWS, "账号锁定"));
        writer.write(new LoginEvent(
                LoginEventType.UNLOCKED, "alice", "203.0.113.9", null, "手动解锁"));

        ArgumentCaptor<SysLoginLog> rows = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper, org.mockito.Mockito.times(2)).insert(rows.capture());
        assertEquals(2, rows.getAllValues().get(0).getStatus(), "locked maps to status 2");
        assertEquals(3, rows.getAllValues().get(1).getStatus(), "unlocked maps to status 3");
    }

    @Test
    void usernameIsTruncatedToTheColumnWidth() {
        when(snapshotMapper.findLoginUserSnapshot(any())).thenReturn(null);
        String username = "u".repeat(80);

        writer.write(new LoginEvent(
                LoginEventType.FAILURE, username, "203.0.113.10", CHROME_WINDOWS, "登录失败"));

        SysLoginLog row = insertedRow();
        assertEquals(64, row.getUsername().length());
        assertEquals(username.substring(0, 64), row.getUsername());
    }

    @Test
    void messageIsTruncatedToTheColumnWidth() {
        when(snapshotMapper.findLoginUserSnapshot(any())).thenReturn(null);
        String msg = "m".repeat(600);

        writer.write(new LoginEvent(
                LoginEventType.FAILURE, "alice", "203.0.113.11", CHROME_WINDOWS, msg));

        assertEquals(500, insertedRow().getMsg().length());
    }

    private SysLoginLog insertedRow() {
        ArgumentCaptor<SysLoginLog> row = ArgumentCaptor.forClass(SysLoginLog.class);
        verify(loginLogMapper).insert(row.capture());
        return row.getValue();
    }

    private static LoginUserSnapshot snapshot(Long userId, Long deptId) {
        LoginUserSnapshot snapshot = new LoginUserSnapshot();
        snapshot.setUserId(userId);
        snapshot.setDeptId(deptId);
        return snapshot;
    }
}
