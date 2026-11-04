package com.lautung.oa.vo.monitor;

import com.lautung.oa.model.monitor.OnlineSession;
import com.lautung.oa.model.monitor.SessionTime;
import lombok.Data;

import java.io.Serializable;

/**
 * 在线用户列表的一行（ADR 0060）：一行 = 一次登录，同一账号多端各占一行。
 *
 * <p>{@code name} 是登录时的用户姓名快照，读侧不 join 用户表；{@code current} 标出请求者
 * 自己那台，客户端据此禁用该行的强退按钮。登记行到本 VO 的逐字段映射只在 {@link #of} 一处。
 */
@Data
public class OnlineSessionVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private String sessionId;

    private String username;

    private String name;

    private String loginTime;

    private String ip;

    private String browser;

    private String os;

    private String lastActive;

    private boolean current;

    /** 列表行：{@code currentSessionId} 是请求者自己的会话，用于标出并禁用的那一行。 */
    public static OnlineSessionVo of(OnlineSession session, String currentSessionId) {
        OnlineSessionVo row = new OnlineSessionVo();
        row.setSessionId(session.sessionId());
        row.setUsername(session.username());
        row.setName(session.name());
        row.setLoginTime(text(session.loginTime()));
        row.setIp(session.ip());
        row.setBrowser(session.browser());
        row.setOs(session.os());
        row.setLastActive(text(session.lastActive()));
        row.setCurrent(session.sessionId() != null && session.sessionId().equals(currentSessionId));
        return row;
    }

    private static String text(SessionTime time) {
        return time == null ? null : time.toString();
    }
}

