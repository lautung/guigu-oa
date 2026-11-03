package com.lautung.oa.monitor.service;

import com.lautung.oa.vo.monitor.HikariMonitorVo;
import com.lautung.oa.vo.monitor.ServerMonitorVo;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 服务监控读法（ADR 0060）：JMX 各段独立降级——磁盘枚举失败该段 null，
 * Hikari 读不到该段 null，其余段照常返回，不 500。
 */
class ServerMonitorServiceTest {

    @Test
    void diskEnumerationFailureDegradesOnlyTheDiskSection() {
        HikariMonitorReader hikariReader = hikariReaderDegradingToNull();
        ServerMonitorService service = new ServerMonitorService(
                hikariReader, () -> { throw new IllegalStateException("no /proc"); });

        ServerMonitorVo vo = service.read();

        assertNull(vo.getDisks(), "磁盘段枚举失败降级为 null");
        assertNotNull(vo.getCpu(), "其余段照常");
        assertNotNull(vo.getMemory());
        assertNotNull(vo.getJvm());
        assertNull(vo.getHikari());
    }

    @Test
    void snapshotCarriesJmxSectionsAndDisksOnTheHappyPath() {
        HikariMonitorReader hikariReader = hikariReaderDegradingToNull();
        ServerMonitorService service = new ServerMonitorService(hikariReader,
                FileSystems.getDefault()::getFileStores);

        ServerMonitorVo vo = service.read();

        assertNotNull(vo.getCpu());
        assertNotNull(vo.getCpu().getAvailableProcessors());
        assertTrue(vo.getCpu().getAvailableProcessors() > 0);
        // CPU 使用率/负载在无头环境可能返回负值（不可用），降级为 null 而不是 -100。
        if (vo.getCpu().getUsagePercent() != null) {
            assertTrue(vo.getCpu().getUsagePercent() >= 0 && vo.getCpu().getUsagePercent() <= 100);
        }
        assertNotNull(vo.getMemory());
        assertNotNull(vo.getMemory().getTotalBytes());
        assertTrue(vo.getMemory().getTotalBytes() > 0);
        assertNotNull(vo.getJvm());
        assertNotNull(vo.getJvm().getHeap());
        assertNotNull(vo.getJvm().getHeap().getUsedBytes());
        assertNotNull(vo.getJvm().getThreads());
        assertNotNull(vo.getJvm().getThreads().getThreadCount());
        assertNotNull(vo.getJvm().getUptimeSeconds());
        assertNotNull(vo.getDisks());
        assertTrue(vo.getDisks().size() > 0, "本机至少有一个挂载点");
        ServerMonitorVo.Disk disk = vo.getDisks().get(0);
        assertNotNull(disk.getMountPoint());
        assertNotNull(disk.getTotalBytes());
        assertNull(vo.getHikari());
    }

    private HikariMonitorReader hikariReaderDegradingToNull() {
        DataSource dataSource = mock(DataSource.class);
        try {
            when(dataSource.unwrap(com.zaxxer.hikari.HikariDataSource.class))
                    .thenThrow(new SQLException("not hikari"));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return new HikariMonitorReader(dataSource);
    }
}
