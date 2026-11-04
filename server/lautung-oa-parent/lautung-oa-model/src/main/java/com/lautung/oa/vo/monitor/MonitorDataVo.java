package com.lautung.oa.vo.monitor;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 数据监控页快照（ADR 0060）：Hikari 段与服务监控共用同一个读法；慢 SQL 段跟随
 * {@code lautung.oa.sql.slow-sql.enabled}——关闭时 {@code enabled=false} 且 entries 为空，
 * 客户端据此显示「采集未开启」。
 */
@Data
public class MonitorDataVo implements Serializable {

    private static final long serialVersionUID = 1L;

    private HikariMonitorVo hikari;

    private SlowSql slowSql;

    @Data
    public static class SlowSql implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean enabled;

        private Long thresholdMs;

        /** 最新在前的最近 200 条；采集关闭时为空列表。 */
        private List<SlowSqlEntryVo> entries;
    }
}
