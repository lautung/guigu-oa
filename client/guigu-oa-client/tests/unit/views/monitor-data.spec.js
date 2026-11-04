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
  return wrapper.findAll('.el-table__row')
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
