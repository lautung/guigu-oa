package com.lautung.oa.audit.service;

import com.lautung.oa.audit.entity.SysLoginLog;
import com.lautung.oa.audit.mapper.LoginUserSnapshot;
import com.lautung.oa.audit.mapper.LoginUserSnapshotMapper;
import com.lautung.oa.audit.mapper.SysLoginLogMapper;
import com.lautung.oa.security.login.UserAgentParser;
import com.lautung.oa.security.login.LoginEvent;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 登录日志的同步落库（ADR 0054）：库是查询真相，写失败由 composite 捕获后转写应用日志。
 *
 * <p>列宽是第二道闸，写入前先按列宽截断：{@code username} 64、{@code ip} 45、
 * {@code msg} 500。{@code browser}/{@code os} 只存解析结果，UA 原串不落库。
 */
@Component
public class LoginLogWriter {

    private static final int USERNAME_WIDTH = 64;
    private static final int IP_WIDTH = 45;
    private static final int MSG_WIDTH = 500;

    private final SysLoginLogMapper loginLogMapper;

    private final LoginUserSnapshotMapper snapshotMapper;

    public LoginLogWriter(SysLoginLogMapper loginLogMapper, LoginUserSnapshotMapper snapshotMapper) {
        this.loginLogMapper = loginLogMapper;
        this.snapshotMapper = snapshotMapper;
    }

    /**
     * Writes the row; a database failure propagates so the composite can fall back to the
     * application log. The login path itself never sees the exception.
     */
    public void write(LoginEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        SysLoginLog row = new SysLoginLog();
        row.setUsername(clip(event.username(), USERNAME_WIDTH));
        row.setIp(clip(event.sourceAddress(), IP_WIDTH));
        row.setBrowser(UserAgentParser.browser(event.userAgent()));
        row.setOs(UserAgentParser.os(event.userAgent()));
        row.setStatus(event.type().status());
        row.setEvent(event.kind().value());
        row.setMsg(clip(event.msg(), MSG_WIDTH));
        snapshotUser(row);
        // 兜底只防 handler 未装配/未生效，时间取应用时钟（ADR 0031）。
        if (row.getCreateTime() == null) {
            row.setCreateTime(new Date());
        }
        loginLogMapper.insert(row);
    }

    private void snapshotUser(SysLoginLog row) {
        if (!StringUtils.hasText(row.getUsername())) {
            return;
        }
        LoginUserSnapshot snapshot = snapshotMapper.findLoginUserSnapshot(row.getUsername());
        if (snapshot == null) {
            return;
        }
        row.setUserId(snapshot.getUserId());
        row.setDeptId(snapshot.getDeptId());
    }

    private static String clip(String value, int width) {
        if (value == null || value.length() <= width) {
            return value;
        }
        return value.substring(0, width);
    }
}
