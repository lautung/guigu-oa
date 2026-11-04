package com.lautung.oa.monitor.service;

import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.vo.monitor.MonitorDataVo;
import com.lautung.oa.vo.monitor.SlowSqlEntryVo;
import com.lautung.oa.vo.monitor.SqlStatementStatVo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据监控读侧（ADR 0060）：Hikari 段与服务监控共用 {@link HikariMonitorReader}；
 * 慢 SQL 段 {@code enabled} 直读 {@code lautung.oa.sql.slow-sql.enabled}，缓冲从
 * framework 的 {@link SlowSqlCollector} bean 取（开关关闭时 bean 不存在，entries 为空，
 * 客户端据此显示「采集未开启」）。SQL 统计只聚合该环（不是全量 SQL），连接池历史由
 * {@link PoolHistorySampler} 在进程内采样。不建表、无保留期、不引 Micrometer。
 */
@Service
public class DataMonitorService {

    private final HikariMonitorReader hikariReader;

    private final ObjectProvider<SlowSqlCollector> collectorProvider;

    private final PoolHistorySampler poolHistorySampler;

    private final boolean slowSqlEnabled;

    private final long slowSqlThresholdMs;

    private final long poolHistoryIntervalMs;

    public DataMonitorService(
            HikariMonitorReader hikariReader,
            ObjectProvider<SlowSqlCollector> collectorProvider,
            PoolHistorySampler poolHistorySampler,
            @Value("${lautung.oa.sql.slow-sql.enabled:false}") boolean slowSqlEnabled,
            @Value("${lautung.oa.sql.slow-sql.threshold-ms:500}") long slowSqlThresholdMs,
            @Value("${lautung.oa.monitor.pool-history.interval-ms:30000}") long poolHistoryIntervalMs) {
        this.hikariReader = hikariReader;
        this.collectorProvider = collectorProvider;
        this.poolHistorySampler = poolHistorySampler;
        this.slowSqlEnabled = slowSqlEnabled;
        this.slowSqlThresholdMs = slowSqlThresholdMs;
        this.poolHistoryIntervalMs = poolHistoryIntervalMs;
    }

    public MonitorDataVo read() {
        MonitorDataVo vo = new MonitorDataVo();
        vo.setHikari(hikariReader.read());

        MonitorDataVo.SlowSql slowSql = new MonitorDataVo.SlowSql();
        slowSql.setEnabled(slowSqlEnabled);
        slowSql.setThresholdMs(slowSqlThresholdMs);
        slowSql.setEntries(collector());
        vo.setSlowSql(slowSql);
        vo.setSqlStatistics(sqlStatistics());
        vo.setPoolHistory(poolHistory());
        return vo;
    }

    /**
     * 统计段只从慢 SQL 环聚合：关闭态 {@code enabled=false} 且 statements 为空，
     * 客户端据此显示「采集未开启」；note 固定明示「不是全量 SQL」。
     */
    private MonitorDataVo.SqlStatistics sqlStatistics() {
        MonitorDataVo.SqlStatistics statistics = new MonitorDataVo.SqlStatistics();
        statistics.setEnabled(slowSqlEnabled);
        statistics.setNote(SqlStatisticsAggregator.SLOW_SQL_ONLY_NOTE);
        statistics.setStatements(slowSqlEnabled ? aggregate() : List.of());
        return statistics;
    }

    private List<SqlStatementStatVo> aggregate() {
        SlowSqlCollector collector = collectorProvider.getIfAvailable();
        if (collector == null) {
            return List.of();
        }
        return SqlStatisticsAggregator.aggregate(collector.snapshot());
    }

    /** 历史段读不到（采样器异常）整段 null，页面显示「不可用」而不是整页报错。 */
    private MonitorDataVo.PoolHistory poolHistory() {
        try {
            MonitorDataVo.PoolHistory history = new MonitorDataVo.PoolHistory();
            history.setCapacity(PoolHistorySampler.CAPACITY);
            history.setIntervalMs(poolHistoryIntervalMs);
            history.setPoints(poolHistorySampler.snapshot());
            return history;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private List<SlowSqlEntryVo> collector() {
        SlowSqlCollector collector = collectorProvider.getIfAvailable();
        if (collector == null) {
            return List.of();
        }
        return collector.snapshot().stream()
                .map(entry -> {
                    SlowSqlEntryVo row = new SlowSqlEntryVo();
                    row.setTimestamp(entry.timestamp());
                    row.setStatementId(entry.statementId());
                    row.setElapsedMs(entry.elapsedMs());
                    row.setRows(entry.rows());
                    row.setSql(entry.sql());
                    return row;
                })
                .toList();
    }
}
