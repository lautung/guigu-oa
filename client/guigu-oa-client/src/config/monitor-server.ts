import type {
  ApiRequestConfig,
  HikariMetrics,
  MemorySegmentMetrics,
  ServerMetrics,
} from '@/types/domain'

/**
 * 服务监控页的快照投影（ADR 0060，#240）：把 `/admin/monitor/server` 的
 * `ServerMonitorVo` 各段独立转成卡片视图。各段独立降级——服务端读不到的段是
 * `null`，投影为 `available:false`，页面显示「不可用」，不整页报错。
 * 票面的「系统」卡在合同里只有 `jvm.uptimeSeconds`（VO 没有 hostname/OS/JDK
 * 字段，#236 落的读法是 JMX 直读），故该卡只报运行时长。
 */

export interface MetricLine {
  label: string
  value: string
}

export interface DiskUsageView {
  mountPoint: string
  total: string
  used: string
  percentLabel: string
  /** 0-100 的数字，供 el-progress 的 percentage。 */
  percent: number
}

export interface MetricSectionView {
  available: boolean
  lines: MetricLine[]
  /** 仅磁盘段使用；其余段恒为空数组。 */
  disks: DiskUsageView[]
}

export type ServerSectionKey = 'cpu' | 'runtime' | 'memory' | 'threads' | 'disks' | 'hikari'

export type ServerMetricsView = Record<ServerSectionKey, MetricSectionView>

/** 页面经请求作用域发的快照读；不代理 actuator（ADR 0060），api 层的执行包装也用它。 */
export function serverMetricsRequest(): ApiRequestConfig {
  return { method: 'get', url: '/admin/monitor/server' }
}

const BYTE_UNITS = ['B', 'KB', 'MB', 'GB', 'TB'] as const

/** 字节转可读文本；读不到（null/负值/非数值）显示「—」。 */
export function formatBytes(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value) || value < 0) return '—'
  let amount = value
  let unit: (typeof BYTE_UNITS)[number] = BYTE_UNITS[0]
  for (let index = 1; index < BYTE_UNITS.length && amount >= 1024; index++) {
    amount /= 1024
    unit = BYTE_UNITS[index]
  }
  // 不足 1 KB 的字节数不带小数（「100 B」而非「100.0 B」）。
  return unit === 'B' ? `${Math.round(amount)} B` : `${amount.toFixed(1)} ${unit}`
}

/** 百分比（0-100）保留一位小数；读不到显示「—」。 */
export function formatPercent(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—'
  return `${value.toFixed(1)}%`
}

function textOf(value: number | null | undefined, suffix = ''): string {
  return typeof value === 'number' && Number.isFinite(value) ? `${value}${suffix}` : '—'
}

/** JVM 运行时长：天/小时/分三档，不足一分钟的报秒。 */
export function formatUptime(seconds: number | null | undefined): string {
  if (typeof seconds !== 'number' || !Number.isFinite(seconds) || seconds < 0) return '—'
  const total = Math.floor(seconds)
  const days = Math.floor(total / 86400)
  const hours = Math.floor((total % 86400) / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  if (days > 0) return `${days} 天 ${hours} 小时 ${minutes} 分`
  if (hours > 0) return `${hours} 小时 ${minutes} 分`
  if (minutes > 0) return `${minutes} 分`
  return `${total} 秒`
}

function unavailableSection(): MetricSectionView {
  return { available: false, lines: [], disks: [] }
}

function segmentText(segment: MemorySegmentMetrics | null | undefined): string {
  const used = formatBytes(segment?.usedBytes)
  const committed = formatBytes(segment?.committedBytes)
  const max = typeof segment?.maxBytes === 'number' ? formatBytes(segment.maxBytes) : '无上限'
  return `已用 ${used} / 已分配 ${committed} / 上限 ${max}`
}

/** 连接池段：服务监控页与数据监控页共用同一份读法（ADR 0060）。 */
export function presentHikari(hikari: HikariMetrics | null | undefined): MetricSectionView {
  if (!hikari) return unavailableSection()
  return {
    available: true,
    disks: [],
    lines: [
      {
        label: '连接占用',
        value: `活跃 ${textOf(hikari.activeConnections)} / 空闲 ${textOf(
          hikari.idleConnections,
        )} / 总数 ${textOf(hikari.totalConnections)}`,
      },
      { label: '最大池大小', value: textOf(hikari.maxPoolSize) },
      { label: '等待线程', value: textOf(hikari.threadsAwaiting) },
      { label: '获取超时', value: textOf(hikari.connectionTimeoutSeconds, ' 秒') },
      { label: '累计获取', value: textOf(hikari.totalAcquired) },
      { label: '累计超时', value: textOf(hikari.totalTimeouts) },
    ],
  }
}

/** 各段独立投影；任一段 null 只影响自己那张卡。 */
export function presentServerMetrics(
  snapshot: ServerMetrics | null | undefined,
): ServerMetricsView {
  if (!snapshot) {
    return {
      cpu: unavailableSection(),
      runtime: unavailableSection(),
      memory: unavailableSection(),
      threads: unavailableSection(),
      disks: unavailableSection(),
      hikari: unavailableSection(),
    }
  }

  const cpu = snapshot.cpu
  const jvm = snapshot.jvm
  const memory = snapshot.memory

  const memoryLines: MetricLine[] = []
  if (memory) {
    memoryLines.push(
      {
        label: '物理内存',
        value: `共 ${formatBytes(memory.totalBytes)}，空闲 ${formatBytes(memory.freeBytes)}`,
      },
      { label: '物理使用率', value: formatPercent(memory.usagePercent) },
    )
  }
  if (jvm) {
    memoryLines.push(
      { label: '堆内存', value: segmentText(jvm.heap) },
      { label: '非堆内存', value: segmentText(jvm.nonHeap) },
    )
  }

  const threads = jvm?.threads

  return {
    cpu: cpu
      ? {
          available: true,
          disks: [],
          lines: [
            { label: '使用率', value: formatPercent(cpu.usagePercent) },
            { label: '系统负载', value: textOf(cpu.systemLoad) },
            { label: '核数', value: textOf(cpu.availableProcessors) },
          ],
        }
      : unavailableSection(),
    runtime: jvm
      ? {
          available: true,
          disks: [],
          lines: [{ label: '运行时长', value: formatUptime(jvm.uptimeSeconds) }],
        }
      : unavailableSection(),
    memory:
      memoryLines.length > 0
        ? { available: true, lines: memoryLines, disks: [] }
        : unavailableSection(),
    threads: threads
      ? {
          available: true,
          disks: [],
          lines: [
            { label: '活动线程', value: textOf(threads.threadCount) },
            { label: '峰值线程', value: textOf(threads.peakThreadCount) },
            { label: '守护线程', value: textOf(threads.daemonThreadCount) },
          ],
        }
      : unavailableSection(),
    disks: Array.isArray(snapshot.disks)
      ? {
          available: true,
          lines: [],
          disks: snapshot.disks.map((disk) => ({
            mountPoint: disk.mountPoint || '—',
            total: formatBytes(disk.totalBytes),
            used: formatBytes(disk.usedBytes),
            percentLabel: formatPercent(disk.usagePercent),
            percent:
              typeof disk.usagePercent === 'number' && Number.isFinite(disk.usagePercent)
                ? Math.min(100, Math.max(0, disk.usagePercent))
                : 0,
          })),
        }
      : unavailableSection(),
    hikari: presentHikari(snapshot.hikari),
  }
}
