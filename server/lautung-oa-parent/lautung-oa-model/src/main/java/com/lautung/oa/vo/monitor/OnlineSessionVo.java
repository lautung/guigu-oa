package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;

/**
 * 在线用户列表的一行（ADR 0060）：一行 = 一次登录，同一账号多端各占一行。
 *
 * <p>{@code name} 是登录时的用户姓名快照，读侧不 join 用户表；{@code current} 标出请求者
 * 自己那台，客户端据此禁用该行的强退按钮。
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
}
