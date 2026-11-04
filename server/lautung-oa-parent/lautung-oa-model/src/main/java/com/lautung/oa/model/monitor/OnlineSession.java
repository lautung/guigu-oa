package com.lautung.oa.model.monitor;

/**
 * 一个在线的登录会话（ADR 0060）：一行 = 一次登录，同一账号多端各占一行。
 *
 * <p>{@code loginTime}/{@code lastActive} 是 {@link SessionTime}（{@code yyyy-MM-dd HH:mm:ss}
 * 的服务器本地时间）；列表按它过滤与排序，不再把格式细节泄漏给调用方。到列表 VO 的逐字段
 * 映射在 {@code OnlineSessionVo.of}，读侧只负责过滤与分页。
 */
public record OnlineSession(
        String sessionId,
        Long userId,
        String username,
        String name,
        SessionTime loginTime,
        String ip,
        String browser,
        String os,
        SessionTime lastActive,
        long tokenVersion
) {
}
