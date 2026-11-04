import { describe, expect, it } from 'vitest'

import { dataMetricsRequest, formatEpochMs, presentMonitorData } from '@/config/monitor-data'

const entries = [
  {
    timestamp: 1730000000000,
    statementId: 'com.lautung.oa.mapper.SysUserMapper.selectPage',
    elapsedMs: 1523,
    rows: 120,
    sql: 'SELECT * FROM sys_user WHERE dept_id = 100 ORDER BY id DESC LIMIT 10',
  },
  {
    timestamp: 1729990000000,
    statementId: 'com.lautung.oa.mapper.OaProcessMapper.selectPending',
    elapsedMs: 2048,
    rows: 0,
    sql: 'SELECT id FROM oa_process WHERE status = 1 AND assignee = ? ORDER BY create_time DESC'.padEnd(
      300,
      ' x',
    ),
  },
]

const enabledSnapshot = {
  hikari: {
    activeConnections: 1,
    idleConnections: 9,
    totalConnections: 10,
    threadsAwaiting: 0,
    maxPoolSize: 10,
    connectionTimeoutSeconds: 30,
    totalAcquired: 99,
    totalTimeouts: 1,
  },
  slowSql: { enabled: true, thresholdMs: 1000, entries },
}

describe('慢 SQL 环投影', () => {
  it('reports the collector state with the threshold label when enabled', () => {
    const view = presentMonitorData(enabledSnapshot)

    expect(view.slowSqlEnabled).toBe(true)
    expect(view.thresholdLabel).toBe('慢 SQL 阈值 1000 ms')
    expect(view.rows).toHaveLength(2)
  })

  it('formats each entry with time, statement id, elapsed, rows and a preview sql', () => {
    const view = presentMonitorData(enabledSnapshot)
    const [first, second] = view.rows

    expect(first.time).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}$/)
    expect(first.time).toBe(formatEpochMs(1730000000000))
    expect(first.statementId).toBe('com.lautung.oa.mapper.SysUserMapper.selectPage')
    expect(first.elapsed).toBe('1523 ms')
    expect(first.rowsText).toBe('120')
    expect(first.sql).toBe(entries[0].sql)
    expect(first.sqlPreview).toBe(entries[0].sql)
    // 最新在前。
    expect(view.rows[0].timestamp).toBe(1730000000000)
    expect(second.rowsText).toBe('0')

    // 300+ 字符的 SQL 折叠成一行预览，全文仍在 sql 里。
    expect(second.sqlPreview.length).toBeLessThanOrEqual(121)
    expect(second.sqlPreview.endsWith('…')).toBe(true)
    expect(second.sql).toBe(entries[1].sql)
  })

  it('shows dashes for entries without elapsed or rows', () => {
    const view = presentMonitorData({
      ...enabledSnapshot,
      slowSql: {
        enabled: true,
        thresholdMs: 1000,
        entries: [
          { timestamp: 1730000000000, statementId: 'stmt', elapsedMs: null, rows: null, sql: '' },
        ],
      },
    })

    expect(view.rows[0].elapsed).toBe('—')
    expect(view.rows[0].rowsText).toBe('—')
    expect(view.rows[0].sqlPreview).toBe('')
  })

  it('renders the empty ring as zero rows while still enabled', () => {
    const view = presentMonitorData({
      ...enabledSnapshot,
      slowSql: { enabled: true, thresholdMs: 1000, entries: [] },
    })

    expect(view.slowSqlEnabled).toBe(true)
    expect(view.rows).toEqual([])
  })

  it('flags a disabled collector as the page-level empty state', () => {
    const view = presentMonitorData({
      ...enabledSnapshot,
      slowSql: { enabled: false, thresholdMs: 1000, entries: [] },
    })

    expect(view.slowSqlEnabled).toBe(false)
    expect(view.rows).toEqual([])
  })

  it('treats a missing snapshot as a disabled collector with an unavailable pool', () => {
    const view = presentMonitorData(null)

    expect(view.slowSqlEnabled).toBe(false)
    expect(view.thresholdLabel).toBe('')
    expect(view.rows).toEqual([])
    expect(view.hikari.available).toBe(false)
  })
})

describe('慢 SQL 时间戳格式化', () => {
  it('formats epoch milliseconds as local YYYY-MM-DD HH:mm:ss', () => {
    const stamp = new Date(1730000000000)
    const pad = (value) => String(value).padStart(2, '0')
    const expected =
      `${stamp.getFullYear()}-${pad(stamp.getMonth() + 1)}-${pad(stamp.getDate())} ` +
      `${pad(stamp.getHours())}:${pad(stamp.getMinutes())}:${pad(stamp.getSeconds())}`

    expect(formatEpochMs(1730000000000)).toBe(expected)
  })

  it('shows a dash when the timestamp is missing', () => {
    expect(formatEpochMs(null)).toBe('—')
  })
})

describe('数据监控读请求形状', () => {
  it('reads the backoffice snapshot endpoint, not actuator', () => {
    expect(dataMetricsRequest()).toEqual({ method: 'get', url: '/admin/monitor/data' })
  })
})
