package com.lautung.oa.security.cache;

/**
 * 权限缓存键的唯一构造点（#237，ADR 0060）：{@code perm:cache:<username>}。
 *
 * <p>写（{@code IndexController}）、读（{@code TokenAuthenticationFilter}）、失效
 * （{@code PermissionCacheInvalidator}）三处共用，缓存列表页也按这个前缀浏览与清理；
 * 迁移前的裸 username 键由写入新键时顺手删除，不另做全局迁移。
 */
public final class PermissionCacheKey {

    public static final String PREFIX = "perm:cache:";

    private PermissionCacheKey() {
    }

    public static String of(String username) {
        return PREFIX + username;
    }

    /** 缓存列表页的浏览/清理白名单以这个前缀判断「是不是权限缓存键」。 */
    public static boolean isPermissionCacheKey(String key) {
        return key != null && key.startsWith(PREFIX);
    }
}
