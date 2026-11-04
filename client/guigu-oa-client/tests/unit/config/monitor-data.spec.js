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

const statistics = [
  {
    statementId: 'com.lautung.oa.mapper.SysUserMapper.selectPage',
    count: 3,
    avgElapsedMs: 200.4,
    maxElapsedMs: 300,
    lastTimestamp: 1730000000000,
  },
  {
    statementId: null,
    count: 1,
    avgElapsedMs: null,
    maxElapsedMs: null,
    lastTimestamp: null,
  },
]

const poolPoints = [
  {
    timestamp: 1730000000000,
    activeConnections: 1,
    idleConnections: 2,
    totalConnections: 3,
    threadsAwaiting: 0,
  },
  {
    timestamp: 1730000030000,
    activeConnections: 2,
    idleConnections: 1,
    totalConnections: 3,
    threadsAwaiting: 4,
  },
]

function snapshotWith({ sqlStatistics, poolHistory } = {}) {
  return {
    ...enabledSnapshot,
    ...(sqlStatistics === undefined ? {} : { sqlStatistics }),
    ...(poolHistory === undefined ? {} : { poolHistory }),
  }
}

describe('SQL 统计段投影', () => {
  it('projects count, average, max and latest time per statement', () => {
    const view = presentMonitorData(
      snapshotWith({
        sqlStatistics: {
          enabled: true,
          note: '只覆盖慢 SQL，不是全量 SQL',
          statements: statistics,
        },
      }),
    )

    expect(view.sqlStatistics.available).toBe(true)
    expect(view.sqlStatistics.enabled).toBe(true)
    expect(view.sqlStatistics.note).toBe('只覆盖慢 SQL，不是全量 SQL')
    expect(view.sqlStatistics.rows).toHaveLength(2)

    const [first, second] = view.sqlStatistics.rows
    expect(first.statementId).toBe('com.lautung.oa.mapper.SysUserMapper.selectPage')
    expect(first.countText).toBe('3')
    expect(first.avgText).toBe('200.4 ms')
    expect(first.maxText).toBe('300 ms')
    expect(first.lastTime).toBe(formatEpochMs(1730000000000))

    // 缺失的语句 id、平均、最大与时间都降级为「—」。
    expect(second.statementId).toBe('—')
    expect(second.countText).toBe('1')
    expect(second.avgText).toBe('—')
    expect(second.maxText).toBe('—')
    expect(second.lastTime).toBe('—')
  })

  it('keeps the enabled state with zero rows for an empty ring', () => {
    const view = presentMonitorData(
      snapshotWith({
        sqlStatistics: { enabled: true, note: '只覆盖慢 SQL，不是全量 SQL', statements: [] },
      }),
    )

    expect(view.sqlStatistics.available).toBe(true)
    expect(view.sqlStatistics.enabled).toBe(true)
    expect(view.sqlStatistics.rows).toEqual([])
  })

  it('marks the section disabled when collection is off', () => {
    const view = presentMonitorData(
      snapshotWith({
        sqlStatistics: { enabled: false, note: '只覆盖慢 SQL，不是全量 SQL', statements: [] },
      }),
    )

    expect(view.sqlStatistics.available).toBe(true)
    expect(view.sqlStatistics.enabled).toBe(false)
    expect(view.sqlStatistics.rows).toEqual([])
  })

  it('marks the section unavailable when the snapshot has no statistics field', () => {
    const view = presentMonitorData(enabledSnapshot)

    expect(view.sqlStatistics.available).toBe(false)
    expect(view.sqlStatistics.enabled).toBe(false)
    expect(view.sqlStatistics.rows).toEqual([])
  })
})

describe('连接池历史段投影', () => {
  it('projects points oldest first and builds the four polylines', () => {
    const view = presentMonitorData(
      snapshotWith({ poolHistory: { capacity: 120, intervalMs: 30000, points: poolPoints } }),
    )

    expect(view.poolHistory.available).toBe(true)
    expect(view.poolHistory.intervalLabel).toBe('每 30 秒采样，最多 120 点')
    expect(view.poolHistory.points).toHaveLength(2)

    const [first, second] = view.poolHistory.points
    expect(first.time).toBe(formatEpochMs(1730000000000))
    expect(first.activeText).toBe('1')
    expect(first.threadsAwaitingText).toBe('0')
    expect(second.totalText).toBe('3')

    expect(view.poolHistory.latest?.timestamp).toBe(1730000030000)
    expect(view.poolHistory.latest?.activeText).toBe('2')

    // 600x120、内边距 8，两点贴左右边；纵轴取四序列最大值（4）。
    expect(view.poolHistory.chart.width).toBe(600)
    expect(view.poolHistory.chart.height).toBe(120)
    expect(view.poolHistory.chart.maxValue).toBe(4)
    expect(view.poolHistory.chart.total).toBe('8,34 592,34')
    expect(view.poolHistory.chart.active).toBe('8,86 592,60')
    expect(view.poolHistory.chart.idle).toBe('8,60 592,86')
    expect(view.poolHistory.chart.waiting).toBe('8,112 592,8')
  })

  it('keeps an empty section available before the first sample', () => {
    const view = presentMonitorData(
      snapshotWith({ poolHistory: { capacity: 120, intervalMs: 30000, points: [] } }),
    )

    expect(view.poolHistory.available).toBe(true)
    expect(view.poolHistory.points).toEqual([])
    expect(view.poolHistory.latest).toBeNull()
    expect(view.poolHistory.chart.total).toBe('')
    expect(view.poolHistory.chart.maxValue).toBe(1)
  })

  it('marks the section unavailable when the snapshot has no history field', () => {
    const view = presentMonitorData(enabledSnapshot)

    expect(view.poolHistory.available).toBe(false)
    expect(view.poolHistory.points).toEqual([])
    expect(view.poolHistory.chart.total).toBe('')
  })

  it('places a single point at the horizontal centre', () => {
    const view = presentMonitorData(
      snapshotWith({
        poolHistory: {
          capacity: 120,
          intervalMs: 30000,
          points: [poolPoints[0]],
        },
      }),
    )

    expect(view.poolHistory.chart.total).toBe('300,8')
    expect(view.poolHistory.chart.maxValue).toBe(3)
  })

  it('omits the interval label when the server sends no interval', () => {
    const view = presentMonitorData(snapshotWith({ poolHistory: { capacity: 120, points: [] } }))

    expect(view.poolHistory.intervalLabel).toBe('')
  })
})
