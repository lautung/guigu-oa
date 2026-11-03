package com.lautung.oa.security.session;

/**
 * 一个在线的登录会话（ADR 0060）：一行 = 一次登录，同一账号多端各占一行。
 *
 * <p>{@code loginTime}/{@code lastActive} 是 {@code yyyy-MM-dd HH:mm:ss} 形状的服务器本地
 * 时间；列表按同一格式的字符串过滤与排序，避免把时间格式泄漏给登记实现之外。
 */
public record OnlineSession(
        String sessionId,
        Long userId,
        String username,
        String name,
        String loginTime,
        String ip,
        String browser,
        String os,
        String lastActive,
        long tokenVersion
) {
}
