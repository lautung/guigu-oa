package com.lautung.oa.security.session;

import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.security.login.UserAgentParser;
import com.lautung.oa.security.revocation.TokenRevocation;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Redis 会话登记（ADR 0060）：一次登录一个 hash，键 {@code login:session:<sessionId>}。
 *
 * <p>TTL 取 refresh 令牌寿命（7 天，与 {@code token:revoked:} 同寿）；最后活跃用
 * {@code login:session:active:<sessionId>} 的 {@code SET NX EX 60} 节流，命中才写字段，
 * 不延长 hash TTL。列举用 {@code SCAN}，不用 {@code KEYS}。
 */
public class RedisOnlineSessionRegistry implements OnlineSessionRegistry {

    static final String KEY_PREFIX = "login:session:";
    static final String ACTIVE_KEY_PREFIX = "login:session:active:";
    private static final Duration RETENTION = Duration.ofMillis(JwtHelper.REFRESH_TOKEN_LIFETIME_MILLIS);
    private static final Duration ACTIVE_THROTTLE = Duration.ofSeconds(60);
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_NAME = "name";
    private static final String FIELD_LOGIN_TIME = "loginTime";
    private static final String FIELD_IP = "ip";
    private static final String FIELD_BROWSER = "browser";
    private static final String FIELD_OS = "os";
    private static final String FIELD_LAST_ACTIVE = "lastActive";
    private static final String FIELD_TOKEN_VERSION = "tokenVersion";

    private final RedisTemplate<String, String> redisTemplate;
    private final TokenRevocation tokenRevocation;

    public RedisOnlineSessionRegistry(
            RedisTemplate<String, String> redisTemplate,
            TokenRevocation tokenRevocation) {
        this.redisTemplate = redisTemplate;
        this.tokenRevocation = tokenRevocation;
    }

    @Override
    public void register(OnlineSessionLogin login) {
        if (login == null || !StringUtils.hasText(login.sessionId())) {
            return;
        }
        String now = LocalDateTime.now().format(TIME_FORMAT);
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put(FIELD_USER_ID, String.valueOf(login.userId()));
        fields.put(FIELD_USERNAME, nullToEmpty(login.username()));
        fields.put(FIELD_NAME, nullToEmpty(login.name()));
        fields.put(FIELD_LOGIN_TIME, now);
        fields.put(FIELD_IP, nullToEmpty(login.ip()));
        fields.put(FIELD_BROWSER, UserAgentParser.browser(login.userAgent()));
        fields.put(FIELD_OS, UserAgentParser.os(login.userAgent()));
        fields.put(FIELD_LAST_ACTIVE, now);
        fields.put(FIELD_TOKEN_VERSION, String.valueOf(login.tokenVersion()));

        String key = key(login.sessionId());
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        hash.putAll(key, fields);
        try {
            redisTemplate.expire(key, RETENTION);
        } catch (RuntimeException e) {
            // 两条命令之间失败会留下没有 TTL 的孤儿 hash；失败路径自己收回（JVM 崩溃的极小窗口除外）。
            redisTemplate.delete(key);
            throw e;
        }
    }

    @Override
    public void touch(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        ValueOperations<String, String> values = redisTemplate.opsForValue();
        if (!Boolean.TRUE.equals(values.setIfAbsent(ACTIVE_KEY_PREFIX + sessionId, "1", ACTIVE_THROTTLE))) {
            return;
        }
        String key = key(sessionId);
        // 会话 hash 由 TTL 收尾；已过期的会话不能因为一次 touch 变成没有 TTL 的孤儿 hash。
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForHash().put(key, FIELD_LAST_ACTIVE, LocalDateTime.now().format(TIME_FORMAT));
        }
    }

    @Override
    public List<OnlineSession> list() {
        List<OnlineSession> sessions = new ArrayList<>();
        HashOperations<String, String, String> hash = redisTemplate.opsForHash();
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(200).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                // 节流键也落在 login:session: 前缀下，它不是会话行。
                if (key.startsWith(ACTIVE_KEY_PREFIX)) {
                    continue;
                }
                OnlineSession session = read(key, hash.entries(key));
                if (session != null) {
                    sessions.add(session);
                }
            }
        }
        return sessions;
    }

    @Override
    public void remove(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        redisTemplate.delete(key(sessionId));
        redisTemplate.delete(ACTIVE_KEY_PREFIX + sessionId);
    }

    @Override
    public boolean forceLogout(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return false;
        }
        String key = key(sessionId);
        if (!Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            return false;
        }
        tokenRevocation.revoke(sessionId);
        remove(sessionId);
        return true;
    }

    private static OnlineSession read(String key, Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return null;
        }
        Long userId = parseLong(fields.get(FIELD_USER_ID));
        Long tokenVersion = parseLong(fields.get(FIELD_TOKEN_VERSION));
        String sessionId = key.substring(KEY_PREFIX.length());
        if (userId == null || tokenVersion == null || !StringUtils.hasText(sessionId)) {
            return null;
        }
        return new OnlineSession(
                sessionId,
                userId,
                fields.get(FIELD_USERNAME),
                fields.get(FIELD_NAME),
                fields.get(FIELD_LOGIN_TIME),
                fields.get(FIELD_IP),
                fields.get(FIELD_BROWSER),
                fields.get(FIELD_OS),
                fields.get(FIELD_LAST_ACTIVE),
                tokenVersion);
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String key(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
