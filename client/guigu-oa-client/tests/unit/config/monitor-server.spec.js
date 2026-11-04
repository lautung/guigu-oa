import { describe, expect, it } from 'vitest'

import { presentHikari, presentServerMetrics, serverMetricsRequest } from '@/config/monitor-server'

/** 完整快照（与服务端 ServerMonitorVo 字段一一对应，单位：字节/百分比/秒）。 */
const fullSnapshot = {
  cpu: { usagePercent: 12.34, systemLoad: 0.85, availableProcessors: 8 },
  memory: { totalBytes: 17179869184, freeBytes: 8589934592, usagePercent: 50 },
  jvm: {
    uptimeSeconds: 90061,
    heap: { usedBytes: 536870912, committedBytes: 1073741824, maxBytes: 4294967296 },
    nonHeap: { usedBytes: 73400320, committedBytes: 104857600, maxBytes: null },
    pools: [{ name: 'G1 Eden Space', usage: { usedBytes: 1, committedBytes: 2, maxBytes: null } }],
    threads: { threadCount: 42, peakThreadCount: 50, daemonThreadCount: 38 },
  },
  disks: [
    {
      mountPoint: '/',
      totalBytes: 100,
      usableBytes: 25,
      usedBytes: 75,
      usagePercent: 75,
    },
  ],
  hikari: {
    activeConnections: 2,
    idleConnections: 6,
    totalConnections: 8,
    threadsAwaiting: 0,
    maxPoolSize: 10,
    connectionTimeoutSeconds: 30,
    totalAcquired: 1234,
    totalTimeouts: 3,
  },
}

function linesOf(section, label) {
  return section.lines.find((line) => line.label === label)?.value
}

describe('服务监控快照投影', () => {
  it('projects cpu usage, load and processors from the cpu section', () => {
    const view = presentServerMetrics(fullSnapshot)

    expect(view.cpu.available).toBe(true)
    expect(linesOf(view.cpu, '使用率')).toBe('12.3%')
    expect(linesOf(view.cpu, '系统负载')).toBe('0.85')
    expect(linesOf(view.cpu, '核数')).toBe('8')
  })

  it('marks a null section unavailable and keeps the other sections readable', () => {
    const view = presentServerMetrics({ ...fullSnapshot, cpu: null })

    expect(view.cpu.available).toBe(false)
    expect(view.cpu.lines).toEqual([])
    expect(view.memory.available).toBe(true)
    expect(view.disks.available).toBe(true)
  })

  it('shows 不可用 for every section when the whole snapshot is missing', () => {
    const view = presentServerMetrics(null)

    for (const key of ['cpu', 'runtime', 'memory', 'threads', 'disks', 'hikari']) {
      expect(view[key].available).toBe(false)
    }
  })

  it('projects physical, heap and non-heap memory in bytes', () => {
    const view = presentServerMetrics(fullSnapshot)

    expect(view.memory.available).toBe(true)
    expect(linesOf(view.memory, '物理内存')).toBe('共 16.0 GB，空闲 8.0 GB')
    expect(linesOf(view.memory, '物理使用率')).toBe('50.0%')
    expect(linesOf(view.memory, '堆内存')).toBe('已用 512.0 MB / 已分配 1.0 GB / 上限 4.0 GB')
    expect(linesOf(view.memory, '非堆内存')).toBe('已用 70.0 MB / 已分配 100.0 MB / 上限 无上限')
  })

  it('still shows heap segments when the physical memory section is null', () => {
    const view = presentServerMetrics({ ...fullSnapshot, memory: null })

    expect(view.memory.available).toBe(true)
    expect(linesOf(view.memory, '堆内存')).toContain('512.0 MB')
  })

  it('projects jvm uptime on the runtime section', () => {
    const view = presentServerMetrics(fullSnapshot)

    expect(view.runtime.available).toBe(true)
    // 90061 秒 = 1 天 1 小时 1 分 1 秒。
    expect(linesOf(view.runtime, '运行时长')).toBe('1 天 1 小时 1 分')
  })

  it('marks the runtime section unavailable when the jvm section is null', () => {
    const view = presentServerMetrics({ ...fullSnapshot, jvm: null })

    expect(view.runtime.available).toBe(false)
    expect(view.threads.available).toBe(false)
    // 物理内存段独立于 jvm，照常可读。
    expect(view.memory.available).toBe(true)
    expect(linesOf(view.memory, '物理内存')).toContain('16.0 GB')
    expect(linesOf(view.memory, '堆内存')).toBeUndefined()
  })

  it('projects thread counts', () => {
    const view = presentServerMetrics(fullSnapshot)

    expect(view.threads.available).toBe(true)
    expect(linesOf(view.threads, '活动线程')).toBe('42')
    expect(linesOf(view.threads, '峰值线程')).toBe('50')
    expect(linesOf(view.threads, '守护线程')).toBe('38')
  })

  it('projects one usage bar per disk mount point', () => {
    const view = presentServerMetrics(fullSnapshot)

    expect(view.disks.available).toBe(true)
    expect(view.disks.disks).toEqual([
      { mountPoint: '/', total: '100 B', used: '75 B', percentLabel: '75.0%', percent: 75 },
    ])
  })

  it('renders an available disks section with no bars for an empty list', () => {
    const view = presentServerMetrics({ ...fullSnapshot, disks: [] })

    expect(view.disks.available).toBe(true)
    expect(view.disks.disks).toEqual([])
  })
})

describe('Hikari 段投影（服务监控与数据监控共用）', () => {
  it('projects pool usage, waiting, config and cumulative counters', () => {
    const view = presentHikari(fullSnapshot.hikari)

    expect(view.available).toBe(true)
    expect(linesOf(view, '连接占用')).toBe('活跃 2 / 空闲 6 / 总数 8')
    expect(linesOf(view, '最大池大小')).toBe('10')
    expect(linesOf(view, '等待线程')).toBe('0')
    expect(linesOf(view, '获取超时')).toBe('30 秒')
    expect(linesOf(view, '累计获取')).toBe('1234')
    expect(linesOf(view, '累计超时')).toBe('3')
  })

  it('shows dashes for counters the pool cannot report', () => {
    const view = presentHikari({
      activeConnections: null,
      idleConnections: null,
      totalConnections: null,
      threadsAwaiting: null,
      maxPoolSize: 10,
      connectionTimeoutSeconds: 30,
      totalAcquired: null,
      totalTimeouts: null,
    })

    expect(view.available).toBe(true)
    expect(linesOf(view, '连接占用')).toBe('活跃 — / 空闲 — / 总数 —')
    expect(linesOf(view, '等待线程')).toBe('—')
    expect(linesOf(view, '累计获取')).toBe('—')
    expect(linesOf(view, '累计超时')).toBe('—')
  })

  it('marks the whole pool unavailable when the section is null', () => {
    const view = presentHikari(null)

    expect(view.available).toBe(false)
    expect(view.lines).toEqual([])
  })
})

describe('快照读请求形状', () => {
  it('reads the backoffice snapshot endpoint, not actuator', () => {
    expect(serverMetricsRequest()).toEqual({ method: 'get', url: '/admin/monitor/server' })
  })
})
