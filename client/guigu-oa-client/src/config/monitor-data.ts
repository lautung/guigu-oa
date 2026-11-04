import { presentHikari, type MetricSectionView } from '@/config/monitor-server'
import type {
  ApiRequestConfig,
  MonitorDataSnapshot,
  PoolHistoryPoint,
  SlowSqlEntry,
  SqlStatementStat,
} from '@/types/domain'

/**
 * 数据监控页的快照投影（ADR 0060，#240/#258）：Hikari 段与服务监控共用
 * `presentHikari`（同一个服务端读法），慢 SQL 段消费内存环的最近 200 条；
 * SQL 统计段按 `statementId` 聚合同一内存环（**只覆盖慢 SQL，不是全量 SQL**），
 * 连接池历史段消费进程内采样环（最多 120 点）。各段独立降级：段缺失或不可用
 * 只影响自己那张卡，不整页报错。时间戳由客户端格式化（VO 合同如此），
 * SQL 的 1024 字符截断在服务端缓冲侧完成。
 */

export interface SlowSqlRowView {
  key: string
  /** 原始 epoch 毫秒；服务端保证最新在前，这里原样保留顺序。 */
  timestamp: number | null
  time: string
  statementId: string
  elapsed: string
  rowsText: string
  /** 完整 SQL（服务端已截断到 1024），展开行显示。 */
  sql: string
  /** 表格里折叠显示的单行预览。 */
  sqlPreview: string
}

export interface SqlStatisticsRowView {
  key: string
  statementId: string
  countText: string
  avgText: string
  maxText: string
  lastTime: string
}

export interface SqlStatisticsView {
  /** 段本身是否可读；缺失即「不可用」，与采集开关是两件事。 */
  available: boolean
  enabled: boolean
  /** 服务端下发的口径说明：「只覆盖慢 SQL，不是全量 SQL」。 */
  note: string
  rows: SqlStatisticsRowView[]
}

export interface PoolHistoryPointView {
  key: string
  timestamp: number | null
  time: string
  active: number | null
  idle: number | null
  total: number | null
  threadsAwaiting: number | null
  activeText: string
  idleText: string
  totalText: string
  threadsAwaitingText: string
}

/** 迷你折线的几何在投影里算好，页面只负责画（不引图表库）。 */
export interface PoolHistoryChartView {
  width: number
  height: number
  /** 四条序列共用的纵轴上限，至少为 1。 */
  maxValue: number
  total: string
  active: string
  idle: string
  waiting: string
}

export interface PoolHistoryView {
  available: boolean
  intervalLabel: string
  /** 时间升序（最旧在前），与折线从左到右一致。 */
  points: PoolHistoryPointView[]
  chart: PoolHistoryChartView
  latest: PoolHistoryPointView | null
}

export interface MonitorDataView {
  hikari: MetricSectionView
  slowSqlEnabled: boolean
  thresholdLabel: string
  rows: SlowSqlRowView[]
  sqlStatistics: SqlStatisticsView
  poolHistory: PoolHistoryView
}

/** 折线坐标系：600×120、内边距 8，与页面 SVG 的 viewBox 一致。 */
export const POOL_HISTORY_CHART_WIDTH = 600

export const POOL_HISTORY_CHART_HEIGHT = 120

const POOL_HISTORY_CHART_PADDING = 8

/** 页面经请求作用域发的快照读；api 层的执行包装也用它。 */
export function dataMetricsRequest(): ApiRequestConfig {
  return { method: 'get', url: '/admin/monitor/data' }
}

/** 慢 SQL 时间戳：本地时区 YYYY-MM-DD HH:mm:ss；缺失显示「—」。 */
export function formatEpochMs(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—'
  const stamp = new Date(value)
  const pad = (part: number) => String(part).padStart(2, '0')
  return (
    `${stamp.getFullYear()}-${pad(stamp.getMonth() + 1)}-${pad(stamp.getDate())} ` +
    `${pad(stamp.getHours())}:${pad(stamp.getMinutes())}:${pad(stamp.getSeconds())}`
  )
}

/** 耗时列：整数不带小数，平均值保留服务端给的一位小数；缺失显示「—」。 */
export function formatDurationMs(value: number | null | undefined): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return '—'
  return `${Number.isInteger(value) ? value : value.toFixed(1)} ms`
}

function numberOrNull(value: number | null | undefined): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null
}

function textOrDash(value: number | null): string {
  return value === null ? '—' : String(value)
}

/** 表格列里的单行预览：一行放不下长 SQL，全文留给展开行。 */
function sqlPreviewOf(sql: string): string {
  const flat = sql.replace(/\s+/g, ' ').trim()
  return flat.length > 120 ? `${flat.slice(0, 120)}…` : flat
}

function rowOf(entry: SlowSqlEntry, index: number): SlowSqlRowView {
  const sql = entry.sql || ''
  const timestamp = numberOrNull(entry.timestamp)
  return {
    key: `${timestamp ?? 'na'}-${index}`,
    timestamp,
    time: formatEpochMs(timestamp),
    statementId: entry.statementId || '—',
    elapsed: formatDurationMs(entry.elapsedMs),
    rowsText: textOrDash(numberOrNull(entry.rows)),
    sql,
    sqlPreview: sqlPreviewOf(sql),
  }
}

function statisticsRowOf(stat: SqlStatementStat, index: number): SqlStatisticsRowView {
  const count = numberOrNull(stat.count)
  return {
    key: `${stat.statementId ?? 'na'}-${index}`,
    statementId: stat.statementId || '—',
    countText: textOrDash(count),
    avgText: formatDurationMs(stat.avgElapsedMs),
    maxText: formatDurationMs(stat.maxElapsedMs),
    lastTime: formatEpochMs(stat.lastTimestamp),
  }
}

function presentSqlStatistics(statistics: MonitorDataSnapshot['sqlStatistics']): SqlStatisticsView {
  if (!statistics) {
    return { available: false, enabled: false, note: '', rows: [] }
  }
  const enabled = statistics.enabled === true
  return {
    available: true,
    enabled,
    note: statistics.note || '',
    rows: enabled ? (statistics.statements || []).map(statisticsRowOf) : [],
  }
}

function poolPointOf(point: PoolHistoryPoint, index: number): PoolHistoryPointView {
  const timestamp = numberOrNull(point.timestamp)
  const active = numberOrNull(point.activeConnections)
  const idle = numberOrNull(point.idleConnections)
  const total = numberOrNull(point.totalConnections)
  const threadsAwaiting = numberOrNull(point.threadsAwaiting)
  return {
    key: `${timestamp ?? 'na'}-${index}`,
    timestamp,
    time: formatEpochMs(timestamp),
    active,
    idle,
    total,
    threadsAwaiting,
    activeText: textOrDash(active),
    idleText: textOrDash(idle),
    totalText: textOrDash(total),
    threadsAwaitingText: textOrDash(threadsAwaiting),
  }
}

function roundCoordinate(value: number): number {
  return Math.round(value * 10) / 10
}

/** 单条序列 → SVG polyline 的 points；空序列给空串（页面据此不画线）。 */
function polylineOf(values: (number | null)[], maxValue: number): string {
  if (values.length === 0) return ''
  const padding = POOL_HISTORY_CHART_PADDING
  const innerWidth = POOL_HISTORY_CHART_WIDTH - padding * 2
  const innerHeight = POOL_HISTORY_CHART_HEIGHT - padding * 2
  return values
    .map((value, index) => {
      const x =
        values.length === 1
          ? POOL_HISTORY_CHART_WIDTH / 2
          : padding + (innerWidth * index) / (values.length - 1)
      const y = POOL_HISTORY_CHART_HEIGHT - padding - ((value ?? 0) / maxValue) * innerHeight
      return `${roundCoordinate(x)},${roundCoordinate(y)}`
    })
    .join(' ')
}

function emptyChart(): PoolHistoryChartView {
  return {
    width: POOL_HISTORY_CHART_WIDTH,
    height: POOL_HISTORY_CHART_HEIGHT,
    maxValue: 1,
    total: '',
    active: '',
    idle: '',
    waiting: '',
  }
}

function intervalLabelOf(
  intervalMs: number | null | undefined,
  capacity: number | null | undefined,
): string {
  const amount = numberOrNull(intervalMs)
  if (amount === null || amount <= 0) return ''
  const seconds = amount / 1000
  const secondsText = Number.isInteger(seconds) ? String(seconds) : seconds.toFixed(1)
  const capacityValue = numberOrNull(capacity)
  return `每 ${secondsText} 秒采样，最多 ${capacityValue === null ? '—' : capacityValue} 点`
}

function presentPoolHistory(history: MonitorDataSnapshot['poolHistory']): PoolHistoryView {
  if (!history) {
    return {
      available: false,
      intervalLabel: '',
      points: [],
      chart: emptyChart(),
      latest: null,
    }
  }
  const points = (history.points || []).map(poolPointOf)
  const magnitudes = points
    .flatMap((point) => [point.active, point.idle, point.total, point.threadsAwaiting])
    .filter((value): value is number => value !== null)
  const maxValue = magnitudes.length > 0 ? Math.max(1, ...magnitudes) : 1
  return {
    available: true,
    intervalLabel: intervalLabelOf(history.intervalMs, history.capacity),
    points,
    chart: {
      width: POOL_HISTORY_CHART_WIDTH,
      height: POOL_HISTORY_CHART_HEIGHT,
      maxValue,
      total: polylineOf(
        points.map((point) => point.total),
        maxValue,
      ),
      active: polylineOf(
        points.map((point) => point.active),
        maxValue,
      ),
      idle: polylineOf(
        points.map((point) => point.idle),
        maxValue,
      ),
      waiting: polylineOf(
        points.map((point) => point.threadsAwaiting),
        maxValue,
      ),
    },
    latest: points.length > 0 ? points[points.length - 1] : null,
  }
}

/** 数据监控快照投影：池段与统计/历史段可整段不可用，慢 SQL 段只有开/关两态。 */
export function presentMonitorData(
  snapshot: MonitorDataSnapshot | null | undefined,
): MonitorDataView {
  const slowSql = snapshot?.slowSql
  const enabled = slowSql?.enabled === true
  const thresholdMs = slowSql?.thresholdMs

  return {
    hikari: presentHikari(snapshot?.hikari),
    slowSqlEnabled: enabled,
    thresholdLabel:
      typeof thresholdMs === 'number' && Number.isFinite(thresholdMs)
        ? `慢 SQL 阈值 ${thresholdMs} ms`
        : '',
    rows: enabled ? (slowSql?.entries || []).map(rowOf) : [],
    sqlStatistics: presentSqlStatistics(snapshot?.sqlStatistics),
    poolHistory: presentPoolHistory(snapshot?.poolHistory),
  }
}
