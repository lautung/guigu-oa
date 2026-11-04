package com.lautung.oa.security.session;

import com.lautung.oa.model.monitor.OnlineSession;

import java.util.List;

/**
 * 在线会话登记端口（ADR 0060）：登录写登记、核验按会话节流写最后活跃、列表可列举、
 * 退出与强退都走「吊销 + 注销」。登记与吊销同一个生活史，所以端口留在 security。
 *
 * <p>登记不是会话真相：真相仍是 refresh 令牌与吊销键。列表读侧只负责把仍有效的登记
 * 交给调用方，失效口径（用户删除/停用/tokenVersion 不一致）由 monitor 读侧按用户行现值判定。
 */
public interface OnlineSessionRegistry {

    /** 登录成功时登记这次登录；{@code loginTime}/{@code lastActive} 由实现按当前时间填写。 */
    void register(OnlineSessionLogin login);

    /** 核验通过后按会话节流（≥60 秒一次）刷新最后活跃；不续登记 TTL。 */
    void touch(String sessionId);

    /** 列出当前登记的全部会话，顺序不保证。 */
    List<OnlineSession> list();

    /** 注销登记（退出或强退之后调用）；同时清掉该会话的节流键。 */
    void remove(String sessionId);

    /**
     * 单会话强退：吊销该 sessionId 的令牌并删除登记，不改用户状态。
     *
     * @return 该会话曾经登记过时为 true；未知 sessionId 返回 false，由调用方命名拒绝
     */
    boolean forceLogout(String sessionId);
}
