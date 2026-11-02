package com.lautung.oa.security.revocation;

import com.lautung.oa.common.jwt.JwtHelper;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.StringUtils;

import java.time.Duration;

public class RedisTokenRevocation implements TokenRevocation {

    private static final String KEY_PREFIX = "token:revoked:";
    /** A refresh token lives at most this long, so the marks can expire with it. */
    private static final Duration RETENTION = Duration.ofMillis(JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS);

    private final RedisTemplate<String, String> redisTemplate;

    public RedisTokenRevocation(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean isRevoked(String sessionId) {
        return StringUtils.hasText(sessionId)
                && redisTemplate.opsForValue().get(KEY_PREFIX + sessionId) != null;
    }

    @Override
    public void revoke(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        redisTemplate.opsForValue().set(KEY_PREFIX + sessionId, "1", RETENTION);
    }
}
