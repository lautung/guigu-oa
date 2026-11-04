package com.lautung.oa.monitor.service;

import com.lautung.oa.framework.observability.SlowSqlCollector;
import com.lautung.oa.vo.monitor.MonitorDataVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 数据监控读侧的分段降级（#258）：采集关闭时统计段只有口径说明、没有行；
 * 采样器异常时历史段整段 null，Hikari/慢 SQL/统计段照常返回，不整页报错。
 */
class DataMonitorServiceTest {

    @Test
    void disabledCollectionKeepsTheStatisticsSectionEmptyWithTheScopeNote() {
        SlowSqlCollector collector = new SlowSqlCollector();
        collector.record("stmt.a", 100L, 1L, "SELECT 1");
        DataMonitorService service = service(providerOf(collector), mock(PoolHistorySampler.class), false);

        MonitorDataVo.SqlStatistics statistics = service.read().getSqlStatistics();

        assertFalse(statistics.getEnabled());
        assertEquals(SqlStatisticsAggregator.SLOW_SQL_ONLY_NOTE, statistics.getNote());
        assertTrue(statistics.getStatements().isEmpty(), "关闭态不暴露统计行");
    }

    @Test
    void enabledCollectionAggregatesTheRingIntoSortedRows() {
        SlowSqlCollector collector = new SlowSqlCollector();
        collector.record("stmt.slow", 300L, 1L, "SELECT 1");
        collector.record("stmt.fast", 10L, 1L, "SELECT 2");
        DataMonitorService service = service(providerOf(collector), mock(PoolHistorySampler.class), true);

        MonitorDataVo.SqlStatistics statistics = service.read().getSqlStatistics();

        assertTrue(statistics.getEnabled());
        assertEquals(2, statistics.getStatements().size());
        assertEquals("stmt.slow", statistics.getStatements().get(0).getStatementId(),
                "最大耗时降序");
    }

    @Test
    void aFailingSamplerDegradesOnlyTheHistorySectionToNull() {
        PoolHistorySampler sampler = mock(PoolHistorySampler.class);
        when(sampler.snapshot()).thenThrow(new IllegalStateException("sampler broken"));
        DataMonitorService service = service(providerOf(null), sampler, false);

        MonitorDataVo vo = service.read();

        assertNull(vo.getPoolHistory(), "历史段读不到整段降级 null");
        assertFalse(vo.getSqlStatistics().getEnabled());
        assertEquals(SqlStatisticsAggregator.SLOW_SQL_ONLY_NOTE, vo.getSqlStatistics().getNote());
        assertTrue(vo.getSlowSql().getEntries().isEmpty());
    }

    private static DataMonitorService service(
            ObjectProvider<SlowSqlCollector> provider,
            PoolHistorySampler sampler,
            boolean slowSqlEnabled) {
        return new DataMonitorService(
                mock(HikariMonitorReader.class), provider, sampler, slowSqlEnabled, 500L, 30000L);
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<SlowSqlCollector> providerOf(SlowSqlCollector collector) {
        ObjectProvider<SlowSqlCollector> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(collector);
        return provider;
    }
}
