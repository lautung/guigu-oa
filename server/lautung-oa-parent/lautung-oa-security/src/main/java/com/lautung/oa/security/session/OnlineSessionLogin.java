package com.lautung.oa.security.session;

/**
 * 一次登录的登记载荷（ADR 0060）：登录过滤器在签发令牌后立刻交给会话登记端口。
 *
 * <p>{@code name} 是登录时的用户姓名快照，列表读侧不再 join 用户表；{@code userAgent} 是
 * 原始 UA，端口实现负责解析成 browser/os，不把原串写进登记。
 */
public record OnlineSessionLogin(
        String sessionId,
        Long userId,
        String username,
        String name,
        String ip,
        String userAgent,
        long tokenVersion
) {
}
