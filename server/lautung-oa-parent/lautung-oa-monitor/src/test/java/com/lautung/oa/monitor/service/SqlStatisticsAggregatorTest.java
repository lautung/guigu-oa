package com.lautung.oa.monitor.service;

import com.lautung.oa.framework.observability.SlowSqlCollector.SlowSqlEntry;
import com.lautung.oa.vo.monitor.SqlStatementStatVo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 统计的聚合口径（#258）：按 statementId 聚合次数、平均、最大与最近一条时间；
 * 输入是慢 SQL 内存环的快照，窗口边界由环容量（200）决定，本类只钉聚合本身。
 */
class SqlStatisticsAggregatorTest {

    @Test
    void aggregatesCountAverageMaxAndLatestTimestampPerStatement() {
        List<SqlStatementStatVo> stats = SqlStatisticsAggregator.aggregate(List.of(
                entry(1000L, "stmt.a", 100L),
                entry(2000L, "stmt.a", 200L),
                entry(3000L, "stmt.a", 300L),
                entry(1500L, "stmt.b", 500L)));

        assertEquals(2, stats.size());
        // 最大耗时降序：先 b（500）后 a（300）。
        assertEquals("stmt.b", stats.get(0).getStatementId());

        SqlStatementStatVo a = stats.get(1);
        assertEquals("stmt.a", a.getStatementId());
        assertEquals(3L, a.getCount());
        assertEquals(200.0, a.getAvgElapsedMs(), 1e-9);
        assertEquals(300L, a.getMaxElapsedMs());
        assertEquals(3000L, a.getLastTimestamp(), "最近一条时间取组内最大 timestamp");
    }

    @Test
    void roundsTheAverageToOneDecimal() {
        List<SqlStatementStatVo> stats = SqlStatisticsAggregator.aggregate(List.of(
                entry(1L, "stmt.a", 10L),
                entry(2L, "stmt.a", 11L)));

        assertEquals(10.5, stats.get(0).getAvgElapsedMs(), 1e-9);
    }

    @Test
    void emptyEntriesYieldAnEmptyStatisticList() {
        assertTrue(SqlStatisticsAggregator.aggregate(List.of()).isEmpty());
    }

    @Test
    void equalMaximaBreakTiesByCountThenStatementId() {
        List<SqlStatementStatVo> stats = SqlStatisticsAggregator.aggregate(List.of(
                entry(1L, "stmt.b", 100L),
                entry(2L, "stmt.a", 100L),
                entry(3L, "stmt.z", 100L),
                entry(4L, "stmt.z", 100L)));

        assertEquals(List.of("stmt.z", "stmt.a", "stmt.b"),
                stats.stream().map(SqlStatementStatVo::getStatementId).toList());
    }

    @Test
    void aMissingStatementIdStillGroupsAsOneRow() {
        List<SqlStatementStatVo> stats = SqlStatisticsAggregator.aggregate(List.of(
                entry(1L, null, 10L),
                entry(2L, null, 20L)));

        assertEquals(1, stats.size());
        assertEquals(2L, stats.get(0).getCount());
        assertEquals(null, stats.get(0).getStatementId());
    }

    private static SlowSqlEntry entry(long timestamp, String statementId, long elapsedMs) {
        return new SlowSqlEntry(timestamp, statementId, elapsedMs, 1L, "SELECT 1");
    }
}
