package com.lautung.oa.monitor.service;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.security.cache.PermissionCacheKey;
import com.lautung.oa.vo.monitor.CacheKeyPageVo;
import com.lautung.oa.vo.monitor.CacheKeyVo;
import com.lautung.oa.vo.monitor.CacheMonitorVo;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 缓存监控与缓存列表（#237，ADR 0060）：
 *
 * <ul>
 *   <li>INFO：一次 {@code INFO} 取回后只解析 server/memory/clients/stats/keyspace 五段为
 *       字段，不整段回传；读不到时各段为空，不 500。</li>
 *   <li>列表：{@code SCAN}（不用 {@code KEYS}）+ 前缀白名单四个；结果在内存里排序后按
 *       游标窗口切页（与在线列表同口径），单页上限 {@link #MAX_KEY_COUNT}（超界 201），
 *       每键附 TTL（秒，-1 无过期）。</li>
 *   <li>清理：单键删除只收 {@code perm:} 前缀，清空只收 {@code perm:cache:*}；
 *       其余前缀只读——删掉 {@code token:revoked:*} 等于把已退出的会话复活。</li>
 * </ul>
 */
@Service
public class CacheMonitorService {

    /** 缓存列表的浏览白名单（ADR 0060）：四个前缀，空值或白名单外一律 201。 */
    static final Set<String> LIST_PREFIXES = Set.of(
            "perm:", "login:session:", "login:attempt:", "token:revoked:");

    /** 单键删除的清理白名单：只允许权限缓存家族（perm:*），ADR 0060。 */
    static final String DELETE_PREFIX = "perm:";

    /**
     * 列表单页条数上限（超界 201，与 count&lt;1 同一条消息，对齐 #238 logs 上限先例）。
     * 取 1000 而非更小的轻量档：客户端删除键后会把 count 设成已累计行数、整段重读累计窗口，
     * 上限必须容纳窗口里的累计行数，太小会打断删除后重读路径；再大的浏览量走游标翻页。
     */
    static final long MAX_KEY_COUNT = 1000;

    private static final int SCAN_HINT = 200;

    private final RedisTemplate<String, String> redisTemplate;

    public CacheMonitorService(RedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public CacheMonitorVo info() {
        try {
            Properties info = redisTemplate.execute(
                    (RedisCallback<Properties>) connection -> connection.serverCommands().info());
            return parseInfo(info);
        } catch (RuntimeException e) {
            // INFO 读不到（Redis 抖动等）按分段降级处理：各段为空，不 500。
            return new CacheMonitorVo();
        }
    }

    public CacheKeyPageVo keys(String prefix, long cursor, long count) {
        if (!StringUtils.hasText(prefix) || !LIST_PREFIXES.contains(prefix)) {
            throw new BusinessException(201, "不支持的前缀");
        }
        if (cursor < 0) {
            throw new BusinessException(201, "游标不合法");
        }
        if (count < 1 || count > MAX_KEY_COUNT) {
            throw new BusinessException(201, "count 不合法");
        }
        List<String> matched = new ArrayList<>();
        try (Cursor<String> scanned = redisTemplate.scan(
                ScanOptions.scanOptions().match(prefix + "*").count(SCAN_HINT).build())) {
            while (scanned.hasNext()) {
                matched.add(scanned.next());
            }
        }
        Collections.sort(matched);

        int from = (int) Math.min(cursor, matched.size());
        int to = (int) Math.min(from + count, matched.size());
        List<CacheKeyVo> keys = new ArrayList<>();
        for (String key : matched.subList(from, to)) {
            keys.add(new CacheKeyVo(key, ttlOf(key)));
        }
        long nextCursor = to < matched.size() ? to : 0L;
        return new CacheKeyPageVo(keys, nextCursor);
    }

    public void deleteKey(String key) {
        if (!StringUtils.hasText(key) || !key.startsWith(DELETE_PREFIX)) {
            throw new BusinessException(201, "只允许删除 perm: 前缀的键");
        }
        if (!Boolean.TRUE.equals(redisTemplate.delete(key))) {
            throw new BusinessException(201, "键不存在");
        }
    }

    public long clearPermissionCache() {
        List<String> targets = new ArrayList<>();
        try (Cursor<String> scanned = redisTemplate.scan(
                ScanOptions.scanOptions().match(PermissionCacheKey.PREFIX + "*").count(SCAN_HINT).build())) {
            while (scanned.hasNext()) {
                targets.add(scanned.next());
            }
        }
        if (targets.isEmpty()) {
            return 0L;
        }
        Long deleted = redisTemplate.delete(targets);
        return deleted == null ? 0L : deleted;
    }

    /** 驱动已把 INFO 拆成 {@code key:value}；这里只挑五段的字段，不做整段透传。 */
    private CacheMonitorVo parseInfo(Properties info) {
        CacheMonitorVo vo = new CacheMonitorVo();
        if (info == null || info.isEmpty()) {
            return vo;
        }
        CacheMonitorVo.Server server = new CacheMonitorVo.Server();
        server.setRedisVersion(info.getProperty("redis_version"));
        server.setUptimeSeconds(parseLong(info.getProperty("uptime_in_seconds")));
        vo.setServer(server);

        CacheMonitorVo.Memory memory = new CacheMonitorVo.Memory();
        memory.setUsedMemoryBytes(parseLong(info.getProperty("used_memory")));
        memory.setUsedMemoryHuman(info.getProperty("used_memory_human"));
        memory.setMaxMemoryBytes(parseLong(info.getProperty("maxmemory")));
        vo.setMemory(memory);

        CacheMonitorVo.Clients clients = new CacheMonitorVo.Clients();
        Long connected = parseLong(info.getProperty("connected_clients"));
        clients.setConnectedClients(connected == null ? null : connected.intValue());
        vo.setClients(clients);

        CacheMonitorVo.Stats stats = new CacheMonitorVo.Stats();
        stats.setKeyspaceHits(parseLong(info.getProperty("keyspace_hits")));
        stats.setKeyspaceMisses(parseLong(info.getProperty("keyspace_misses")));
        stats.setExpiredKeys(parseLong(info.getProperty("expired_keys")));
        vo.setStats(stats);

        List<CacheMonitorVo.KeyspaceDb> keyspace = new ArrayList<>();
        Map<String, CacheMonitorVo.KeyspaceDb> dbs = new LinkedHashMap<>();
        for (String name : info.stringPropertyNames()) {
            if (!name.startsWith("db") || name.length() < 3) {
                continue;
            }
            CacheMonitorVo.KeyspaceDb db = parseKeyspaceRow(name, info.getProperty(name));
            if (db != null) {
                dbs.put(name, db);
            }
        }
        dbs.keySet().stream().sorted().forEachOrdered(name -> keyspace.add(dbs.get(name)));
        vo.setKeyspace(keyspace);
        return vo;
    }

    private CacheMonitorVo.KeyspaceDb parseKeyspaceRow(String name, String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        CacheMonitorVo.KeyspaceDb db = new CacheMonitorVo.KeyspaceDb();
        db.setDb(name);
        for (String pair : value.split(",")) {
            int equals = pair.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String field = pair.substring(0, equals);
            String raw = pair.substring(equals + 1);
            switch (field) {
                case "keys" -> db.setKeys(parseLong(raw));
                case "expires" -> db.setExpires(parseLong(raw));
                case "avg_ttl" -> db.setAvgTtlMs(parseLong(raw));
                default -> {
                    // 未知子字段忽略。
                }
            }
        }
        return db;
    }

    /** 逐键 TTL（秒）：无过期 -1；扫描与读取之间键消失（-2）也归 -1，不外漏哨兵值。 */
    private Long ttlOf(String key) {
        try {
            Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl == null || ttl < 0) {
                return -1L;
            }
            return ttl;
        } catch (RuntimeException e) {
            return -1L;
        }
    }

    private static Long parseLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
