import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  }
})

import ElementPlus from 'element-plus'
import { AxiosError } from 'axios'

import DataMonitorPage from '@/views/monitor/data/index.vue'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

const longSql = 'SELECT id FROM oa_process WHERE status = 1 ORDER BY create_time DESC'.padEnd(
  300,
  ' x',
)

const snapshot = {
  hikari: {
    activeConnections: 2,
    idleConnections: 6,
    totalConnections: 8,
    threadsAwaiting: 1,
    maxPoolSize: 10,
    connectionTimeoutSeconds: 30,
    totalAcquired: 1234,
    totalTimeouts: 3,
  },
  sqlStatistics: {
    enabled: true,
    note: '只覆盖慢 SQL，不是全量 SQL',
    statements: [
      {
        statementId: 'SysUserMapper.selectPage',
        count: 3,
        avgElapsedMs: 200.4,
        maxElapsedMs: 300,
        lastTimestamp: 1730000000000,
      },
      {
        statementId: 'OaProcessMapper.selectPending',
        count: 1,
        avgElapsedMs: 120,
        maxElapsedMs: 120,
        lastTimestamp: 1729990000000,
      },
    ],
  },
  poolHistory: {
    capacity: 120,
    intervalMs: 30000,
    points: [
      {
        timestamp: 1730000000000,
        activeConnections: 1,
        idleConnections: 6,
        totalConnections: 8,
        threadsAwaiting: 0,
      },
      {
        timestamp: 1730000030000,
        activeConnections: 2,
        idleConnections: 6,
        totalConnections: 8,
        threadsAwaiting: 1,
      },
    ],
  },
  slowSql: {
    enabled: true,
    thresholdMs: 1000,
    entries: [
      {
        timestamp: 1730000000000,
        statementId: 'SysUserMapper.selectPage',
        elapsedMs: 1523,
        rows: 120,
        sql: 'SELECT * FROM sys_user WHERE dept_id = 100 LIMIT 10',
      },
      {
        timestamp: 1729990000000,
        statementId: 'OaProcessMapper.selectPending',
        elapsedMs: 2048,
        rows: 0,
        sql: longSql,
      },
    ],
  },
}

let responderOverride = null
let http

function responder(config) {
  if (responderOverride) return responderOverride(config)
  if ((config.url || '') === '/admin/monitor/data') return ok(snapshot)
  throw new Error(`Unexpected request ${config.url}`)
}

function mountPage() {
  const { pinia } = setupSession()
  return mount(DataMonitorPage, {
    global: { plugins: [pinia, ElementPlus] },
  })
}

function cardOf(wrapper, title) {
  return wrapper.findAll('.metric-card').find((node) => node.find('.metric-title').text() === title)
}

function rowsOf(wrapper) {
  return wrapper.find('.slow-sql-card').findAll('.el-table__row')
}

function statsRowsOf(wrapper) {
  return wrapper.find('.sql-statistics-card').findAll('.el-table__row')
}

beforeEach(() => {
  vi.clearAllMocks()
  responderOverride = null
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('数据监控页', () => {
  it('reads /admin/monitor/data once and renders the pool card and the slow sql table', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(http.calls.map((call) => call.url)).toEqual(['/admin/monitor/data'])
    expect(cardOf(wrapper, 'Hikari').text()).toContain('活跃 2 / 空闲 6 / 总数 8')
    expect(cardOf(wrapper, 'Hikari').text()).toContain('累计超时')
    expect(wrapper.find('.page-header').text()).toContain('慢 SQL 阈值 1000 ms')

    const rows = rowsOf(wrapper)
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('SysUserMapper.selectPage')
    expect(rows[0].text()).toContain('1523 ms')
    expect(rows[0].text()).toContain('120')
    expect(rows[0].text()).toMatch(/^\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}/)
    // 最新在前。
    expect(rows[1].text()).toContain('OaProcessMapper.selectPending')
    // 折叠态只显示单行预览，不整段铺出长 SQL。
    expect(rows[1].text()).not.toContain(longSql)
  })

  it('expands a row to reveal the full sql statement', async () => {
    const wrapper = mountPage()
    await flushPromises()

    await rowsOf(wrapper)[1].find('.el-table__expand-icon').trigger('click')
    await flushPromises()

    const expanded = wrapper.find('.el-table__expanded-cell')
    expect(expanded.exists()).toBe(true)
    expect(expanded.text()).toContain(longSql)
  })

  it('renders 采集未开启 empty state instead of the table when the collector is off', async () => {
    responderOverride = () =>
      ok({ ...snapshot, slowSql: { enabled: false, thresholdMs: 1000, entries: [] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.el-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('采集未开启')
    expect(rowsOf(wrapper)).toHaveLength(0)
    expect(cardOf(wrapper, 'Hikari').text()).toContain('活跃 2')
  })

  it('keeps the empty table readable when the ring is enabled but has no entries', async () => {
    responderOverride = () =>
      ok({ ...snapshot, slowSql: { enabled: true, thresholdMs: 1000, entries: [] } })
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.el-empty').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('采集未开启')
    expect(wrapper.find('.el-table__empty-text').text()).toBeTruthy()
  })

  it('renders the pool card as unavailable while the slow sql ring keeps working', async () => {
    responderOverride = () => ok({ ...snapshot, hikari: null })
    const wrapper = mountPage()
    await flushPromises()

    expect(cardOf(wrapper, 'Hikari').text()).toContain('不可用')
    expect(rowsOf(wrapper)).toHaveLength(2)
  })

  it('shows the load error state with retry on transport failure', async () => {
    responderOverride = () => {
      throw new AxiosError('boom', 'ERR_NETWORK')
    }
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.list-load-error').exists()).toBe(true)
    expect(wrapper.findAll('.metric-card')).toHaveLength(0)
  })

  it('re-reads the snapshot from the refresh button', async () => {
    const wrapper = mountPage()
    await flushPromises()

    let release
    responderOverride = () => new Promise((resolve) => (release = resolve))
    await wrapper.find('.refresh-button').trigger('click')
    expect(wrapper.find('.refresh-button').classes()).toContain('is-loading')
    release(ok(snapshot))
    await flushPromises()

    expect(http.calls.filter((call) => call.url === '/admin/monitor/data')).toHaveLength(2)
    expect(rowsOf(wrapper)).toHaveLength(2)
  })
})

describe('数据监控页的 SQL 统计段', () => {
  it('renders the coverage note and one row per statement with count, average, max and time', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.sql-statistics-card')
    expect(card.text()).toContain('只覆盖慢 SQL，不是全量 SQL')

    const rows = statsRowsOf(wrapper)
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('SysUserMapper.selectPage')
    expect(rows[0].text()).toContain('3')
    expect(rows[0].text()).toContain('200.4 ms')
    expect(rows[0].text()).toContain('300 ms')
    expect(rows[1].text()).toContain('OaProcessMapper.selectPending')
  })

  it('keeps the table with an explicit empty line when the ring has no statements', async () => {
    responderOverride = () =>
      ok({
        ...snapshot,
        sqlStatistics: { enabled: true, note: '只覆盖慢 SQL，不是全量 SQL', statements: [] },
      })
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.sql-statistics-card')
    expect(statsRowsOf(wrapper)).toHaveLength(0)
    expect(card.find('.el-table__empty-text').text()).toContain('窗口内暂无慢 SQL')
  })

  it('renders 采集未开启 when the collector is off', async () => {
    responderOverride = () =>
      ok({
        ...snapshot,
        sqlStatistics: { enabled: false, note: '只覆盖慢 SQL，不是全量 SQL', statements: [] },
      })
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.sql-statistics-card')
    expect(card.find('.el-empty').exists()).toBe(true)
    expect(card.text()).toContain('采集未开启')
    expect(statsRowsOf(wrapper)).toHaveLength(0)
  })

  it('renders 不可用 when the statistics section is missing', async () => {
    responderOverride = () => ok({ ...snapshot, sqlStatistics: null })
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.sql-statistics-card')
    expect(card.find('.metric-unavailable').text()).toBe('不可用')
    expect(statsRowsOf(wrapper)).toHaveLength(0)
  })
})

describe('数据监控页的连接池历史段', () => {
  it('renders the interval label, four polylines and the latest sample', async () => {
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.pool-history-card')
    expect(card.text()).toContain('每 30 秒采样，最多 120 点')
    expect(card.findAll('svg.pool-history-chart polyline')).toHaveLength(4)
    expect(card.find('.pool-history-latest').text()).toContain('活跃 2')
    expect(card.find('.pool-history-latest').text()).toContain('空闲 6')
    expect(card.find('.pool-history-latest').text()).toContain('总数 8')
    expect(card.find('.pool-history-latest').text()).toContain('等待 1')
  })

  it('renders 暂无采样 before the first point', async () => {
    responderOverride = () =>
      ok({ ...snapshot, poolHistory: { capacity: 120, intervalMs: 30000, points: [] } })
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.pool-history-card')
    expect(card.find('.el-empty').exists()).toBe(true)
    expect(card.text()).toContain('暂无采样')
    expect(card.find('svg.pool-history-chart').exists()).toBe(false)
  })

  it('renders 不可用 when the history section is missing', async () => {
    responderOverride = () => ok({ ...snapshot, poolHistory: null })
    const wrapper = mountPage()
    await flushPromises()

    const card = wrapper.find('.pool-history-card')
    expect(card.find('.metric-unavailable').text()).toBe('不可用')
    expect(card.find('svg.pool-history-chart').exists()).toBe(false)
  })
})
