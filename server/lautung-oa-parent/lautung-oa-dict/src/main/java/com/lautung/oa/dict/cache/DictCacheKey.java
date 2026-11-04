package com.lautung.oa.dict.cache;

/**
 * 字典缓存键的唯一构造点（#251，ADR 0062）：{@code dict:<dictType>}。
 *
 * <p>读路径、写后失效与管理页手动刷新共用这个前缀；缓存列表页把它列入浏览白名单，
 * 但清理动作仍只允许 {@code perm:}（ADR 0060 的边界不放宽）。
 */
public final class DictCacheKey {

    public static final String PREFIX = "dict:";

    private DictCacheKey() {
    }

    public static String of(String dictType) {
        return PREFIX + dictType;
    }

    public static boolean isDictCacheKey(String key) {
        return key != null && key.startsWith(PREFIX);
    }
}
