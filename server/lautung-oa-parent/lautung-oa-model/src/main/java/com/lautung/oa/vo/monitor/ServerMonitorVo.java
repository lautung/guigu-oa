package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 服务监控页快照（ADR 0060）：控制器直读 JMX 与 Hikari，不代理 actuator、不外扩端点暴露。
 * 各段独立降级——任一段读不到就是 {@code null}，其余段照常返回，不 500。
 * 单位统一：字节、百分比（0-100）、秒。
 */
@Data
public class ServerMonitorVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Cpu cpu;

    /** 物理内存（com.sun.management.OperatingSystemMXBean）。 */
    private Memory memory;

    private Jvm jvm;

    private List<Disk> disks;

    private HikariMonitorVo hikari;

    @Data
    public static class Cpu implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 系统CPU使用率 0-100；JMX 返回负值（不可用）时为 null。 */
        private Double usagePercent;

        /** 系统负载；不可用（负值）时为 null。 */
        private Double systemLoad;

        private Integer availableProcessors;
    }

    @Data
    public static class Memory implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long totalBytes;

        private Long freeBytes;

        /** (total-free)/total，0-100。 */
        private Double usagePercent;
    }

    @Data
    public static class Jvm implements Serializable {

        private static final long serialVersionUID = 1L;

        /** JVM 启动以来的秒数。 */
        private Long uptimeSeconds;

        private MemorySegment heap;

        private MemorySegment nonHeap;

        private List<MemoryPool> pools;

        private Threads threads;
    }

    /** 一段内存用量的字节三件套；max 为 -1（无上限）时 {@code maxBytes} 为 null。 */
    @Data
    public static class MemorySegment implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long usedBytes;

        private Long committedBytes;

        private Long maxBytes;
    }

    @Data
    public static class MemoryPool implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;

        private MemorySegment usage;
    }

    @Data
    public static class Threads implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer threadCount;

        private Integer peakThreadCount;

        private Integer daemonThreadCount;
    }

    @Data
    public static class Disk implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 挂载点（FileStore 名称）。 */
        private String mountPoint;

        private Long totalBytes;

        private Long usableBytes;

        /** total - usable。 */
        private Long usedBytes;

        /** used/total，0-100。 */
        private Double usagePercent;
    }
}
