package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;

/**
 * 数据监控页的连接池段（ADR 0060）：快照读 {@code HikariPoolMXBean}，配置读 {@code HikariConfig}，
 * 累计获取/超时由 monitor 侧的轻量计数器（Hikari 自带 SPI，非 Micrometer）持有。
 * 池未启动或读不到的段为 {@code null}，整个连接池不可用时整段为 {@code null}，不 500。
 */
@Data
public class HikariMonitorVo implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 活跃连接数；池未启动为 null。 */
    private Integer activeConnections;

    /** 空闲连接数；池未启动为 null。 */
    private Integer idleConnections;

    /** 总连接数；池未启动为 null。 */
    private Integer totalConnections;

    /** 等待连接的线程数；池未启动为 null。 */
    private Integer threadsAwaiting;

    /** 最大池大小（配置）。 */
    private Integer maxPoolSize;

    /** 获取连接超时（配置，统一为秒）。 */
    private Double connectionTimeoutSeconds;

    /** 累计获取连接次数（计数器安装起）；装不上计数器时为 null。 */
    private Long totalAcquired;

    /** 累计获取超时次数（计数器安装起）；装不上计数器时为 null。 */
    private Long totalTimeouts;
}
