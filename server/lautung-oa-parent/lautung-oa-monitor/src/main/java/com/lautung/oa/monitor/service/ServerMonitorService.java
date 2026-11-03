package com.lautung.oa.monitor.service;

import com.lautung.oa.vo.monitor.ServerMonitorVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.nio.file.FileStore;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * 服务监控的 JMX 直读（ADR 0060）：不代理 actuator、不外扩端点暴露，自研薄读一份稳定形状。
 * 各段独立 try/catch——读不到就降级 {@code null}，其余段照常返回，监控页不 500。
 * 单位统一：字节、百分比（0-100）、秒。
 */
@Component
public class ServerMonitorService {

    private final HikariMonitorReader hikariReader;

    /** 挂载点来源做成可注入：磁盘枚举失败（无 /proc 等）只降级磁盘段，测试可夹逼。 */
    private final Supplier<Iterable<FileStore>> fileStores;

    @Autowired
    public ServerMonitorService(HikariMonitorReader hikariReader) {
        this(hikariReader, () -> FileSystems.getDefault().getFileStores());
    }

    ServerMonitorService(HikariMonitorReader hikariReader, Supplier<Iterable<FileStore>> fileStores) {
        this.hikariReader = hikariReader;
        this.fileStores = fileStores;
    }

    public ServerMonitorVo read() {
        ServerMonitorVo vo = new ServerMonitorVo();
        vo.setCpu(readCpu());
        vo.setMemory(readPhysicalMemory());
        vo.setJvm(readJvm());
        vo.setDisks(readDisks());
        vo.setHikari(hikariReader.read());
        return vo;
    }

    private ServerMonitorVo.Cpu readCpu() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (os == null) {
                return null;
            }
            ServerMonitorVo.Cpu cpu = new ServerMonitorVo.Cpu();
            double cpuLoad = os.getCpuLoad();
            cpu.setUsagePercent(cpuLoad >= 0 ? cpuLoad * 100.0 : null);
            double systemLoad = os.getSystemLoadAverage();
            cpu.setSystemLoad(systemLoad >= 0 ? systemLoad : null);
            cpu.setAvailableProcessors(os.getAvailableProcessors());
            return cpu;
        } catch (Exception e) {
            return null;
        }
    }

    private ServerMonitorVo.Memory readPhysicalMemory() {
        try {
            com.sun.management.OperatingSystemMXBean os =
                    ManagementFactory.getPlatformMXBean(com.sun.management.OperatingSystemMXBean.class);
            if (os == null) {
                return null;
            }
            long total = os.getTotalMemorySize();
            long free = os.getFreeMemorySize();
            ServerMonitorVo.Memory memory = new ServerMonitorVo.Memory();
            memory.setTotalBytes(total);
            memory.setFreeBytes(free);
            memory.setUsagePercent(total > 0 ? (total - free) * 100.0 / total : null);
            return memory;
        } catch (Exception e) {
            return null;
        }
    }

    private ServerMonitorVo.Jvm readJvm() {
        try {
            java.lang.management.MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

            ServerMonitorVo.Jvm jvm = new ServerMonitorVo.Jvm();
            jvm.setUptimeSeconds(ManagementFactory.getRuntimeMXBean().getUptime() / 1000);
            jvm.setHeap(segment(memoryBean.getHeapMemoryUsage()));
            jvm.setNonHeap(segment(memoryBean.getNonHeapMemoryUsage()));
            List<ServerMonitorVo.MemoryPool> pools = new ArrayList<>();
            for (java.lang.management.MemoryPoolMXBean poolBean : ManagementFactory.getMemoryPoolMXBeans()) {
                ServerMonitorVo.MemoryPool pool = new ServerMonitorVo.MemoryPool();
                pool.setName(poolBean.getName());
                pool.setUsage(segment(poolBean.getUsage()));
                pools.add(pool);
            }
            jvm.setPools(pools);
            ServerMonitorVo.Threads threads = new ServerMonitorVo.Threads();
            threads.setThreadCount(threadBean.getThreadCount());
            threads.setPeakThreadCount(threadBean.getPeakThreadCount());
            threads.setDaemonThreadCount(threadBean.getDaemonThreadCount());
            jvm.setThreads(threads);
            return jvm;
        } catch (Exception e) {
            return null;
        }
    }

    private List<ServerMonitorVo.Disk> readDisks() {
        Iterable<FileStore> stores;
        try {
            stores = fileStores.get();
        } catch (Exception e) {
            return null;
        }
        List<ServerMonitorVo.Disk> disks = new ArrayList<>();
        for (FileStore store : stores) {
            // 单个挂载点读不到（权限、热拔）就跳过，不拖垮整段。
            try {
                long total = store.getTotalSpace();
                long usable = store.getUsableSpace();
                ServerMonitorVo.Disk disk = new ServerMonitorVo.Disk();
                disk.setMountPoint(store.name());
                disk.setTotalBytes(total);
                disk.setUsableBytes(usable);
                disk.setUsedBytes(total - usable);
                disk.setUsagePercent(total > 0 ? (total - usable) * 100.0 / total : null);
                disks.add(disk);
            } catch (Exception e) {
                // skip
            }
        }
        return disks;
    }

    private static ServerMonitorVo.MemorySegment segment(MemoryUsage usage) {
        if (usage == null) {
            return null;
        }
        ServerMonitorVo.MemorySegment segment = new ServerMonitorVo.MemorySegment();
        segment.setUsedBytes(usage.getUsed());
        segment.setCommittedBytes(usage.getCommitted());
        segment.setMaxBytes(usage.getMax() < 0 ? null : usage.getMax());
        return segment;
    }
}
