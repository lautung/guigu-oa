package com.lautung.oa.monitor.service;

import com.lautung.oa.vo.monitor.HikariMonitorVo;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Hikari 直读（ADR 0060）：数据监控与服务监控共用的唯一读法。
 * unwrap {@code HikariDataSource} 后读快照（{@code HikariPoolMXBean}）与配置
 * （{@code HikariConfig}）；累计获取/超时用 Hikari 自带的 metrics SPI 装一个轻量
 * 计数器（不是 Micrometer），读第一次时装上，装不上（比如已绑定了别的指标工厂）
 * 就让位并把累计字段降级为 null。任何一段读不到都不抛——降级，不 500。
 */
@Component
public class HikariMonitorReader {

    private final DataSource dataSource;

    private final PoolCounters counters = new PoolCounters();

    private final AtomicBoolean installAttempted = new AtomicBoolean();

    public HikariMonitorReader(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public HikariMonitorVo read() {
        try {
            HikariDataSource hikari = dataSource.unwrap(HikariDataSource.class);
            if (hikari == null) {
                return null;
            }
            installCountersOnce(hikari);

            HikariMonitorVo vo = new HikariMonitorVo();
            vo.setMaxPoolSize(hikari.getMaximumPoolSize());
            vo.setConnectionTimeoutSeconds(hikari.getConnectionTimeout() / 1000.0);
            vo.setTotalAcquired(counters.usable() ? counters.acquired.get() : null);
            vo.setTotalTimeouts(counters.usable() ? counters.timeouts.get() : null);
            if (hikari.isRunning()) {
                HikariPoolMXBean pool = hikari.getHikariPoolMXBean();
                if (pool != null) {
                    vo.setActiveConnections(pool.getActiveConnections());
                    vo.setIdleConnections(pool.getIdleConnections());
                    vo.setTotalConnections(pool.getTotalConnections());
                    vo.setThreadsAwaiting(pool.getThreadsAwaitingConnection());
                }
            }
            return vo;
        } catch (Exception e) {
            // unwrap 抛 SQLException / 不支持 / 池读法异常：整段降级，监控页不 500。
            return null;
        }
    }

    private void installCountersOnce(HikariDataSource hikari) {
        if (!installAttempted.compareAndSet(false, true)) {
            return;
        }
        try {
            if (hikari.getMetricsTrackerFactory() == null) {
                hikari.setMetricsTrackerFactory(counters.factory());
            } else {
                counters.relinquish();
            }
        } catch (Exception e) {
            counters.relinquish();
        }
    }

    /** 累计获取与累计超时；让位或装不上后 {@link #usable()} 为 false，字段降级 null。 */
    private static final class PoolCounters {

        private final AtomicLong acquired = new AtomicLong();

        private final AtomicLong timeouts = new AtomicLong();

        private volatile boolean usable = true;

        MetricsTrackerFactory factory() {
            return new MetricsTrackerFactory() {
                @Override
                public IMetricsTracker create(String poolName, PoolStats poolStats) {
                    return new MetricsTracker() {
                        @Override
                        public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
                            acquired.incrementAndGet();
                        }

                        @Override
                        public void recordConnectionTimeout() {
                            timeouts.incrementAndGet();
                        }
                    };
                }
            };
        }

        void relinquish() {
            usable = false;
        }

        boolean usable() {
            return usable;
        }
    }
}
