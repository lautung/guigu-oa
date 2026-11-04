package com.lautung.oa.monitor.service;

import com.lautung.oa.vo.monitor.HikariMonitorVo;
import com.lautung.oa.vo.monitor.PoolHistoryPointVo;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 连接池历史采样（#258）：进程内环形缓冲保留最近 {@value #CAPACITY} 个点，每 30 秒
 * （{@code lautung.oa.monitor.pool-history.interval-ms}）经 {@link HikariMonitorReader}
 * 读一次活跃/空闲/总数/等待线程。只存内存，重启清零，不落库、不做时序存储。
 *
 * <p>采样失败（读不到池、池未启动、读法抛错）只跳过本点，不冒泡——监控页整页不因此报错。
 * 时钟与读法均可注入，单测用固定时钟推进 125 次验证环的淘汰顺序。
 */
@Component
public class PoolHistorySampler {

    private static final Logger log = LoggerFactory.getLogger(PoolHistorySampler.class);

    public static final int CAPACITY = 120;

    private final HikariMonitorReader reader;

    private final Clock clock;

    private final PoolHistoryPointVo[] ring = new PoolHistoryPointVo[CAPACITY];

    private long writes;

    @Autowired
    public PoolHistorySampler(HikariMonitorReader reader) {
        this(reader, Clock.systemUTC());
    }

    /** 单测入口：固定/可推进时钟。 */
    PoolHistorySampler(HikariMonitorReader reader, Clock clock) {
        this.reader = reader;
        this.clock = clock;
    }

    /** 启动先采一点，页面首次打开就有基线；此后由 {@code @Scheduled} 补齐窗口。 */
    @PostConstruct
    void sampleOnStartup() {
        sample();
    }

    @Scheduled(fixedRateString = "${lautung.oa.monitor.pool-history.interval-ms:30000}")
    public void sample() {
        HikariMonitorVo metrics;
        try {
            metrics = reader.read();
        } catch (RuntimeException e) {
            log.warn("连接池历史采样失败，跳过本点：{}", e.getMessage());
            return;
        }
        if (!complete(metrics)) {
            return;
        }
        PoolHistoryPointVo point = new PoolHistoryPointVo();
        point.setTimestamp(clock.millis());
        point.setActiveConnections(metrics.getActiveConnections());
        point.setIdleConnections(metrics.getIdleConnections());
        point.setTotalConnections(metrics.getTotalConnections());
        point.setThreadsAwaiting(metrics.getThreadsAwaiting());
        synchronized (this) {
            ring[(int) (writes % CAPACITY)] = point;
            writes++;
        }
    }

    /** 时间升序（最旧在前）；返回的列表与后续采样无关。 */
    public synchronized List<PoolHistoryPointVo> snapshot() {
        int count = (int) Math.min(writes, CAPACITY);
        List<PoolHistoryPointVo> points = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            points.add(ring[(int) ((writes - count + i) % CAPACITY)]);
        }
        return List.copyOf(points);
    }

    private static boolean complete(HikariMonitorVo metrics) {
        return metrics != null
                && metrics.getActiveConnections() != null
                && metrics.getIdleConnections() != null
                && metrics.getTotalConnections() != null
                && metrics.getThreadsAwaiting() != null;
    }
}
