package com.lautung.oa.config.cache;

/**
 * 参数缓存键的唯一构造点（#252，ADR 0062）：{@code config:<configKey>}。
 *
 * <p>读端口、写后失效与管理页手动刷新共用这个前缀；缓存列表页把它列入浏览白名单，
 * 但清理动作仍只允许 {@code perm:}（ADR 0060 的边界不放宽）。
 */
public final class ConfigCacheKey {

    public static final String PREFIX = "config:";

    private ConfigCacheKey() {
    }

    public static String of(String configKey) {
        return PREFIX + configKey;
    }

    public static boolean isConfigCacheKey(String key) {
        return key != null && key.startsWith(PREFIX);
    }
}
