package com.lautung.oa.monitor.service;

import com.lautung.oa.vo.monitor.HikariMonitorVo;
import com.lautung.oa.vo.monitor.PoolHistoryPointVo;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 连接池历史采样器（#258）：进程内环 120 点、旧点被挤掉后仍按时间升序读；
 * 时钟可注入，采样失败（读不到池或读法抛错）只跳过本点，不冒泡、不整页报错。
 */
class PoolHistorySamplerTest {

    private static final Instant START = Instant.parse("2026-11-04T08:00:00Z");

    private final HikariMonitorReader reader = mock(HikariMonitorReader.class);

    private final MutableClock clock = new MutableClock(START);

    @Test
    void keepsTheMostRecent120PointsOldestFirst() {
        when(reader.read()).thenReturn(metrics(1, 2, 3, 4));
        PoolHistorySampler sampler = new PoolHistorySampler(reader, clock);

        for (int i = 0; i < 125; i++) {
            sampler.sample();
            clock.advance(Duration.ofSeconds(30));
        }

        List<PoolHistoryPointVo> points = sampler.snapshot();
        assertEquals(PoolHistorySampler.CAPACITY, points.size());
        assertEquals(120, points.size());
        // 前 5 点被挤掉：最早留下的是第 6 次采样。
        assertEquals(START.plusSeconds(5 * 30L).toEpochMilli(), points.get(0).getTimestamp());
        assertEquals(START.plusSeconds(124 * 30L).toEpochMilli(),
                points.get(points.size() - 1).getTimestamp());
        assertEquals(1, points.get(0).getActiveConnections());
        assertEquals(2, points.get(0).getIdleConnections());
        assertEquals(3, points.get(0).getTotalConnections());
        assertEquals(4, points.get(0).getThreadsAwaiting());
    }

    @Test
    void skipsThePointWhenThePoolSnapshotIsIncomplete() {
        when(reader.read()).thenReturn(metrics(null, null, null, null));
        PoolHistorySampler sampler = new PoolHistorySampler(reader, clock);

        sampler.sample();

        assertTrue(sampler.snapshot().isEmpty(), "池未启动时没有可画的点，不补 null 点");
    }

    @Test
    void skipsThePointWhenTheReaderReturnsNull() {
        when(reader.read()).thenReturn(null);
        PoolHistorySampler sampler = new PoolHistorySampler(reader, clock);

        sampler.sample();

        assertTrue(sampler.snapshot().isEmpty());
    }

    @Test
    void aThrowingReaderDoesNotEscalateOrAppend() {
        when(reader.read()).thenThrow(new IllegalStateException("pool gone"));
        PoolHistorySampler sampler = new PoolHistorySampler(reader, clock);

        assertDoesNotThrow(sampler::sample);
        assertTrue(sampler.snapshot().isEmpty());
    }

    @Test
    void snapshotIsIndependentOfLaterSamples() {
        when(reader.read()).thenReturn(metrics(1, 2, 3, 4));
        PoolHistorySampler sampler = new PoolHistorySampler(reader, clock);
        sampler.sample();

        List<PoolHistoryPointVo> first = sampler.snapshot();
        assertEquals(1, first.size());
        clock.advance(Duration.ofSeconds(30));
        sampler.sample();

        assertEquals(1, first.size(), "已返回的快照不随后续采样变化");
        assertEquals(2, sampler.snapshot().size());
    }

    private static HikariMonitorVo metrics(Integer active, Integer idle, Integer total, Integer waiting) {
        HikariMonitorVo vo = new HikariMonitorVo();
        vo.setActiveConnections(active);
        vo.setIdleConnections(idle);
        vo.setTotalConnections(total);
        vo.setThreadsAwaiting(waiting);
        return vo;
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
