package com.lautung.oa.monitor.service;

import com.lautung.oa.vo.monitor.HikariMonitorVo;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hikari 读法（ADR 0060）：unwrap 不到 HikariDataSource 时整段降级 null；
 * 池未启动时配置照读、快照字段降级 null；计数器装不上时累计字段降级 null。不 500。
 */
class HikariMonitorReaderTest {

    @Test
    void nonHikariDataSourceDegradesToNullSection() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.unwrap(HikariDataSource.class)).thenThrow(new SQLException("not hikari"));
        HikariMonitorReader reader = new HikariMonitorReader(dataSource);

        assertNull(reader.read(), "非 Hikari 数据源整段降级为 null");
    }

    @Test
    void unwrapReturningNullDegradesToNullSection() throws SQLException {
        DataSource dataSource = mock(DataSource.class);
        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(null);
        HikariMonitorReader reader = new HikariMonitorReader(dataSource);

        assertNull(reader.read());
    }

    /** 懒启动的 HikariDataSource：配置可读，池快照为 null，累计计数从 0 开始。 */
    @Test
    void idleHikariStillCarriesConfigWithNullSnapshot() throws SQLException {
        HikariDataSource hikari = new HikariDataSource();
        try {
            hikari.setMaximumPoolSize(17);
            hikari.setConnectionTimeout(250);
            DataSource dataSource = mock(DataSource.class);
            when(dataSource.unwrap(HikariDataSource.class)).thenReturn(hikari);
            HikariMonitorReader reader = new HikariMonitorReader(dataSource);

            HikariMonitorVo vo = reader.read();

            assertNotNull(vo, "配置在池未启动时也应可读");
            assertEquals(17, vo.getMaxPoolSize());
            assertEquals(0.25, vo.getConnectionTimeoutSeconds(), 1e-9);
            assertNull(vo.getActiveConnections(), "池未启动时快照字段降级 null");
            assertNull(vo.getIdleConnections());
            assertNull(vo.getTotalConnections());
            assertNull(vo.getThreadsAwaiting());
            assertEquals(0L, vo.getTotalAcquired());
            assertEquals(0L, vo.getTotalTimeouts());
        } finally {
            hikari.close();
        }
    }
}
