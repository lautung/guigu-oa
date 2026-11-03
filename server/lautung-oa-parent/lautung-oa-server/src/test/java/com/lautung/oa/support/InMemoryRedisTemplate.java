package com.lautung.oa.support;

import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * 合同测试共用的内存 Redis：值、hash、TTL、SET NX EX 与 SCAN 都能工作。
 *
 * <p>ADR 0060 之后每次认证请求都会走会话节流（{@code SET NX EX 60} + {@code HSET}），
 * 只有值操作的旧 fake 会抛 {@code UnsupportedOperationException}。这里把同一份实现给所有
 * {@code @SpringBootTest} 使用，避免每个套件各抄一份互不兼容的代理。
 */
public class InMemoryRedisTemplate extends RedisTemplate<String, String> {

    private final Map<String, String> values = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> hashes = new ConcurrentHashMap<>();
    private final Map<String, Long> expiresAt = new ConcurrentHashMap<>();

    public void clear() {
        values.clear();
        hashes.clear();
        expiresAt.clear();
    }

    /** 剩余 TTL；没有设置过期时间时返回 null，已过期返回 {@link Duration#ZERO}。 */
    public Duration ttl(String key) {
        if (!alive(key)) {
            return Duration.ZERO;
        }
        Long deadline = expiresAt.get(key);
        if (deadline == null) {
            return null;
        }
        return Duration.ofMillis(Math.max(0L, deadline - System.currentTimeMillis()));
    }

    public Set<String> keys(String pattern) {
        return matching(pattern);
    }

    @Override
    public ValueOperations<String, String> opsForValue() {
        return (ValueOperations<String, String>) Proxy.newProxyInstance(
                ValueOperations.class.getClassLoader(),
                new Class<?>[]{ValueOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (args == null || args.length == 0) {
                        if ("toString".equals(name)) {
                            return "InMemoryValueOperations";
                        }
                        throw new UnsupportedOperationException(name);
                    }
                    String key = String.valueOf(args[0]);
                    switch (name) {
                        case "set" -> {
                            values.put(key, String.valueOf(args[1]));
                            applyTtl(key, args.length > 2 ? args[2] : null);
                            return null;
                        }
                        case "setIfAbsent" -> {
                            if (alive(key)) {
                                return false;
                            }
                            values.put(key, String.valueOf(args[1]));
                            applyTtl(key, args.length > 2 ? args[2] : null);
                            return true;
                        }
                        case "get" -> {
                            return alive(key) ? values.get(key) : null;
                        }
                        case "increment" -> {
                            long next = Long.parseLong(values.getOrDefault(key, "0")) + 1;
                            values.put(key, String.valueOf(next));
                            return next;
                        }
                        case "toString" -> {
                            return "InMemoryValueOperations";
                        }
                        default -> throw new UnsupportedOperationException(name);
                    }
                });
    }

    @Override
    @SuppressWarnings("unchecked")
    public <HK, HV> HashOperations<String, HK, HV> opsForHash() {
        return (HashOperations<String, HK, HV>) Proxy.newProxyInstance(
                HashOperations.class.getClassLoader(),
                new Class<?>[]{HashOperations.class},
                (proxy, method, args) -> {
                    String name = method.getName();
                    if (args == null || args.length == 0) {
                        if ("toString".equals(name)) {
                            return "InMemoryHashOperations";
                        }
                        throw new UnsupportedOperationException(name);
                    }
                    String key = String.valueOf(args[0]);
                    switch (name) {
                        case "putAll" -> {
                            Map<String, String> target =
                                    hashes.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
                            ((Map<Object, Object>) args[1]).forEach(
                                    (field, value) -> target.put(String.valueOf(field), String.valueOf(value)));
                            return null;
                        }
                        case "put" -> {
                            hashes.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>())
                                    .put(String.valueOf(args[1]), String.valueOf(args[2]));
                            return null;
                        }
                        case "putIfAbsent" -> {
                            Map<String, String> target =
                                    hashes.computeIfAbsent(key, ignored -> new ConcurrentHashMap<>());
                            if (target.containsKey(String.valueOf(args[1]))) {
                                return false;
                            }
                            target.put(String.valueOf(args[1]), String.valueOf(args[2]));
                            return true;
                        }
                        case "get" -> {
                            Map<String, String> target = alive(key) ? hashes.get(key) : null;
                            return target == null ? null : target.get(String.valueOf(args[1]));
                        }
                        case "entries" -> {
                            Map<String, String> target = alive(key) ? hashes.get(key) : null;
                            return target == null ? Map.of() : new LinkedHashMap<>(target);
                        }
                        case "hasKey" -> {
                            Map<String, String> target = alive(key) ? hashes.get(key) : null;
                            return target != null && target.containsKey(String.valueOf(args[1]));
                        }
                        case "delete" -> {
                            Map<String, String> target = hashes.get(key);
                            if (target == null) {
                                return 0L;
                            }
                            long removed = args.length > 1 && args[1] instanceof Object[] fields
                                    ? deleteFields(target, fields)
                                    : target.size();
                            return removed;
                        }
                        case "size" -> {
                            Map<String, String> target = alive(key) ? hashes.get(key) : null;
                            return target == null ? 0L : (long) target.size();
                        }
                        case "keys" -> {
                            Map<String, String> target = alive(key) ? hashes.get(key) : null;
                            return target == null ? Set.of() : Set.copyOf(target.keySet());
                        }
                        case "values" -> {
                            Map<String, String> target = alive(key) ? hashes.get(key) : null;
                            return target == null ? List.of() : List.copyOf(target.values());
                        }
                        case "toString" -> {
                            return "InMemoryHashOperations";
                        }
                        default -> throw new UnsupportedOperationException(name);
                    }
                });
    }

    @Override
    public Boolean hasKey(String key) {
        return alive(key);
    }

    @Override
    public Boolean delete(String key) {
        boolean existed = alive(key);
        values.remove(key);
        hashes.remove(key);
        expiresAt.remove(key);
        return existed;
    }

    @Override
    public Long delete(Collection<String> keys) {
        long removed = 0;
        for (String key : keys) {
            if (Boolean.TRUE.equals(delete(key))) {
                removed += 1;
            }
        }
        return removed;
    }

    @Override
    public Boolean expire(String key, long timeout, TimeUnit unit) {
        if (!alive(key)) {
            return false;
        }
        expiresAt.put(key, System.currentTimeMillis() + unit.toMillis(timeout));
        return true;
    }

    @Override
    public Boolean expire(String key, Duration timeout) {
        if (!alive(key)) {
            return false;
        }
        expiresAt.put(key, System.currentTimeMillis() + timeout.toMillis());
        return true;
    }

    @Override
    public Cursor<String> scan(ScanOptions options) {
        String pattern = options == null ? "*" : options.getPattern();
        return cursor(new ArrayList<>(matching(pattern == null ? "*" : pattern)));
    }

    /** 直接删字段的 Redis 语义；本 fake 只支持 {@code delete(key, Object... fields)} 的常见形状。 */
    private static long deleteFields(Map<String, String> target, Object[] fields) {
        long removed = 0;
        for (Object field : fields) {
            if (target.remove(String.valueOf(field)) != null) {
                removed += 1;
            }
        }
        return removed;
    }

    private boolean alive(String key) {
        Long deadline = expiresAt.get(key);
        if (deadline != null && deadline <= System.currentTimeMillis()) {
            values.remove(key);
            hashes.remove(key);
            expiresAt.remove(key);
            return false;
        }
        return values.containsKey(key) || hashes.containsKey(key);
    }

    private Set<String> matching(String pattern) {
        Pattern glob = glob(pattern);
        Set<String> matched = new java.util.LinkedHashSet<>();
        for (String key : values.keySet()) {
            if (alive(key) && glob.matcher(key).matches()) {
                matched.add(key);
            }
        }
        for (String key : hashes.keySet()) {
            if (alive(key) && glob.matcher(key).matches()) {
                matched.add(key);
            }
        }
        return matched;
    }

    private void applyTtl(String key, Object ttl) {
        if (ttl instanceof Duration duration) {
            expiresAt.put(key, System.currentTimeMillis() + duration.toMillis());
        } else if (ttl instanceof Expiration expiration) {
            expiresAt.put(key, System.currentTimeMillis() + expiration.getExpirationTimeInMilliseconds());
        }
    }

    private static Pattern glob(String pattern) {
        StringBuilder regex = new StringBuilder("^");
        for (char character : pattern.toCharArray()) {
            switch (character) {
                case '*' -> regex.append(".*");
                case '?' -> regex.append('.');
                case '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                        regex.append('\\').append(character);
                default -> regex.append(character);
            }
        }
        return Pattern.compile(regex.append('$').toString());
    }

    private static <T> Cursor<T> cursor(List<T> items) {
        Iterator<T> iterator = items.iterator();
        return new Cursor<>() {
            private boolean closed;

            @Override
            public CursorId getId() {
                return null;
            }

            @Override
            public long getCursorId() {
                return 0;
            }

            @Override
            public boolean isClosed() {
                return closed;
            }

            @Override
            public long getPosition() {
                return 0;
            }

            @Override
            public boolean hasNext() {
                return iterator.hasNext();
            }

            @Override
            public T next() {
                return iterator.next();
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException("remove");
            }

            @Override
            public void close() {
                closed = true;
            }
        };
    }

    @Override
    public void afterPropertiesSet() {
    }
}
