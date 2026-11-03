package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 缓存监控页快照（#237，ADR 0060）：只取 Redis {@code INFO} 的
 * {@code server / memory / clients / stats / keyspace} 五段解析成字段，不整段回传。
 *
 * <p>Redis 不可用时各段为空，读面照常 200（与 {@link ServerMonitorVo} 同一口径）。
 * 单位：字节、秒、毫秒（avgTtlMs）。
 */
@Data
public class CacheMonitorVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Server server;

    private Memory memory;

    private Clients clients;

    private Stats stats;

    private List<KeyspaceDb> keyspace;

    @Data
    public static class Server implements Serializable {

        private static final long serialVersionUID = 1L;

        private String redisVersion;

        private Long uptimeSeconds;
    }

    @Data
    public static class Memory implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long usedMemoryBytes;

        private String usedMemoryHuman;

        /** 0 表示未设上限（Redis 原值）。 */
        private Long maxMemoryBytes;
    }

    @Data
    public static class Clients implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer connectedClients;
    }

    @Data
    public static class Stats implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long keyspaceHits;

        private Long keyspaceMisses;

        private Long expiredKeys;
    }

    /** INFO keyspace 段的一行 {@code dbN:keys=..,expires=..,avg_ttl=..}。 */
    @Data
    public static class KeyspaceDb implements Serializable {

        private static final long serialVersionUID = 1L;

        private String db;

        private Long keys;

        private Long expires;

        private Long avgTtlMs;
    }
}
