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

    private SqlStatistics sqlStatistics;

    private PoolHistory poolHistory;

    @Data
    public static class SlowSql implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean enabled;

        private Long thresholdMs;

        /** 最新在前的最近 200 条；采集关闭时为空列表。 */
        private List<SlowSqlEntryVo> entries;
    }

    /** 按 statementId 聚合的内存环统计（#258）：只覆盖慢 SQL，不是全量 SQL。 */
    @Data
    public static class SqlStatistics implements Serializable {

        private static final long serialVersionUID = 1L;

        private Boolean enabled;

        /** 口径说明固定为「只覆盖慢 SQL，不是全量 SQL」，客户端原样展示。 */
        private String note;

        private List<SqlStatementStatVo> statements;
    }

    /** 连接池历史（#258）：进程内每 30 秒采样、保留 120 点，重启清零。 */
    @Data
    public static class PoolHistory implements Serializable {

        private static final long serialVersionUID = 1L;

        private Integer capacity;

        private Long intervalMs;

        /** 时间升序（最旧在前）；尚无采样时为空列表。 */
        private List<PoolHistoryPointVo> points;
    }
}
