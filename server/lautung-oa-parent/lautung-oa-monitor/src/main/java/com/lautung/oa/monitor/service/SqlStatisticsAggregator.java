package com.lautung.oa.monitor.service;

import com.lautung.oa.framework.observability.SlowSqlCollector.SlowSqlEntry;
import com.lautung.oa.vo.monitor.SqlStatementStatVo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 慢 SQL 内存环的聚合（#258）：按 {@code statementId} 归组，算次数、平均、最大与
 * 最近一条时间。输入只有环内最近 200 条，所以统计口径是「慢 SQL 窗口」而不是全量 SQL；
 * 输出按最大耗时降序、次数降序、语句 id 升序，读侧稳定。
 */
public final class SqlStatisticsAggregator {

    /** 响应里的口径说明：客户端原样展示，避免把慢 SQL 窗口误读成全量统计。 */
    public static final String SLOW_SQL_ONLY_NOTE = "只覆盖慢 SQL，不是全量 SQL";

    private static final Comparator<SqlStatementStatVo> ORDER = Comparator
            .comparing(SqlStatementStatVo::getMaxElapsedMs, Comparator.reverseOrder())
            .thenComparing(SqlStatementStatVo::getCount, Comparator.reverseOrder())
            .thenComparing(stat -> stat.getStatementId() == null ? "" : stat.getStatementId());

    private SqlStatisticsAggregator() {
    }

    public static List<SqlStatementStatVo> aggregate(List<SlowSqlEntry> entries) {
        Map<String, Accumulator> groups = new LinkedHashMap<>();
        for (SlowSqlEntry entry : entries) {
            groups.computeIfAbsent(entry.statementId(), key -> new Accumulator(key)).add(entry);
        }
        List<SqlStatementStatVo> stats = new ArrayList<>(groups.size());
        for (Accumulator group : groups.values()) {
            stats.add(group.toVo());
        }
        stats.sort(ORDER);
        return List.copyOf(stats);
    }

    private static final class Accumulator {

        private final String statementId;

        private long count;

        private long totalElapsedMs;

        private long maxElapsedMs;

        private long lastTimestamp;

        private Accumulator(String statementId) {
            this.statementId = statementId;
        }

        void add(SlowSqlEntry entry) {
            count++;
            totalElapsedMs += entry.elapsedMs();
            maxElapsedMs = Math.max(maxElapsedMs, entry.elapsedMs());
            lastTimestamp = Math.max(lastTimestamp, entry.timestamp());
        }

        SqlStatementStatVo toVo() {
            SqlStatementStatVo vo = new SqlStatementStatVo();
            vo.setStatementId(statementId);
            vo.setCount(count);
            vo.setAvgElapsedMs(Math.round(totalElapsedMs * 10.0 / count) / 10.0);
            vo.setMaxElapsedMs(maxElapsedMs);
            vo.setLastTimestamp(lastTimestamp);
            return vo;
        }
    }
}
