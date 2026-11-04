import { presentHikari, type MetricSectionView } from '@/config/monitor-server'
import type { ApiRequestConfig, MonitorDataSnapshot, SlowSqlEntry } from '@/types/domain'

/**
 * 数据监控页的快照投影（ADR 0060，#240）：Hikari 段与服务监控共用
 * `presentHikari`（同一个服务端读法），慢 SQL 段消费内存环的最近 200 条。
 * 采集关闭（`enabled=false`，含快照缺失）由页面渲染 `el-empty`「采集未开启」；
 * 时间戳由客户端格式化（VO 合同如此），SQL 的 1024 字符截断在服务端缓冲侧完成。
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

export interface MonitorDataView {
  hikari: MetricSectionView
  slowSqlEnabled: boolean
  thresholdLabel: string
  rows: SlowSqlRowView[]
}

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

/** 表格列里的单行预览：一行放不下长 SQL，全文留给展开行。 */
function sqlPreviewOf(sql: string): string {
  const flat = sql.replace(/\s+/g, ' ').trim()
  return flat.length > 120 ? `${flat.slice(0, 120)}…` : flat
}

function rowOf(entry: SlowSqlEntry, index: number): SlowSqlRowView {
  const sql = entry.sql || ''
  const timestamp = typeof entry.timestamp === 'number' ? entry.timestamp : null
  return {
    key: `${timestamp ?? 'na'}-${index}`,
    timestamp,
    time: formatEpochMs(entry.timestamp),
    statementId: entry.statementId || '—',
    elapsed:
      typeof entry.elapsedMs === 'number' && Number.isFinite(entry.elapsedMs)
        ? `${entry.elapsedMs} ms`
        : '—',
    rowsText:
      typeof entry.rows === 'number' && Number.isFinite(entry.rows) ? String(entry.rows) : '—',
    sql,
    sqlPreview: sqlPreviewOf(sql),
  }
}

/** 数据监控快照投影：池段可整段不可用，慢 SQL 段只有开/关两态。 */
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
  }
}
