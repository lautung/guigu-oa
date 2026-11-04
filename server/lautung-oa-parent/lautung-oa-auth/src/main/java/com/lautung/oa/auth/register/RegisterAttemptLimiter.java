package com.lautung.oa.auth.register;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

/**
 * 注册的按 IP 计数（#253，ADR 0062）：每分钟最多 5 次尝试，成功与失败都算。
 *
 * <p>键前缀刻意独立于 {@code LoginAttemptLimiter} 的 {@code login:attempt:}：注册失败不计入
 * 登录失败、不产生账号锁定，解锁登录账号也不会清掉注册计数。超限的请求在解析字段之前
 * 就被拒，只留应用日志、不落审计表（ADR 0054 的写放大边界）。
 */
@Component
public class RegisterAttemptLimiter {

    static final String KEY_PREFIX = "register:attempt:";
    static final int MAX_ATTEMPTS = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final RedisTemplate<String, String> redisTemplate;

    public RegisterAttemptLimiter(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * Counts one attempt from {@code sourceAddress}. Returns {@code false} when this attempt is
     * over the limit; the counter itself still moves so a burst cannot refresh the window forever.
     */
    public boolean tryAcquire(String sourceAddress) {
        String key = KEY_PREFIX + normalize(sourceAddress);
        Long attempts = redisTemplate.opsForValue().increment(key);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(key, WINDOW.toMillis(), TimeUnit.MILLISECONDS);
        }
        return attempts == null || attempts <= MAX_ATTEMPTS;
    }

    private static String normalize(String sourceAddress) {
        String value = sourceAddress == null ? "" : sourceAddress.trim();
        if (value.isEmpty()) {
            return "unknown";
        }
        return value.length() > 45 ? value.substring(0, 45) : value;
    }
}
