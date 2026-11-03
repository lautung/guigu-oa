package com.lautung.oa.monitor.service;

import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.vo.monitor.MonitorDataVo;
import com.lautung.oa.vo.monitor.SlowSqlEntryVo;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 数据监控读侧（ADR 0060）：Hikari 段与服务监控共用 {@link HikariMonitorReader}；
 * 慢 SQL 段 {@code enabled} 直读 {@code lautung.oa.sql.slow-sql.enabled}，缓冲从
 * framework 的 {@link SlowSqlCollector} bean 取（开关关闭时 bean 不存在，entries 为空，
 * 客户端据此显示「采集未开启」）。不建表、不采样、无保留期、不引 Micrometer。
 */
@Service
public class DataMonitorService {

    private final HikariMonitorReader hikariReader;

    private final ObjectProvider<SlowSqlCollector> collectorProvider;

    private final boolean slowSqlEnabled;

    private final long slowSqlThresholdMs;

    public DataMonitorService(
            HikariMonitorReader hikariReader,
            ObjectProvider<SlowSqlCollector> collectorProvider,
            @Value("${lautung.oa.sql.slow-sql.enabled:false}") boolean slowSqlEnabled,
            @Value("${lautung.oa.sql.slow-sql.threshold-ms:500}") long slowSqlThresholdMs) {
        this.hikariReader = hikariReader;
        this.collectorProvider = collectorProvider;
        this.slowSqlEnabled = slowSqlEnabled;
        this.slowSqlThresholdMs = slowSqlThresholdMs;
    }

    public MonitorDataVo read() {
        MonitorDataVo vo = new MonitorDataVo();
        vo.setHikari(hikariReader.read());

        MonitorDataVo.SlowSql slowSql = new MonitorDataVo.SlowSql();
        slowSql.setEnabled(slowSqlEnabled);
        slowSql.setThresholdMs(slowSqlThresholdMs);
        slowSql.setEntries(collector());
        vo.setSlowSql(slowSql);
        return vo;
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
