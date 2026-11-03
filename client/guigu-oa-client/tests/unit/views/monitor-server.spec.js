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

import ServerMonitorPage from '@/views/monitor/server/index.vue'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

const snapshot = {
  cpu: { usagePercent: 12.34, systemLoad: 0.85, availableProcessors: 8 },
  memory: { totalBytes: 17179869184, freeBytes: 8589934592, usagePercent: 50 },
  jvm: {
    uptimeSeconds: 90061,
    heap: { usedBytes: 536870912, committedBytes: 1073741824, maxBytes: 4294967296 },
    nonHeap: { usedBytes: 73400320, committedBytes: 104857600, maxBytes: null },
    threads: { threadCount: 42, peakThreadCount: 50, daemonThreadCount: 38 },
  },
  disks: [{ mountPoint: '/', totalBytes: 100, usableBytes: 25, usedBytes: 75, usagePercent: 75 }],
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

let responderOverride = null
let http

function responder(config) {
  if (responderOverride) return responderOverride(config)
  if ((config.url || '') === '/admin/monitor/server') return ok(snapshot)
  throw new Error(`Unexpected request ${config.url}`)
}

function mountPage() {
  const { pinia } = setupSession()
  return mount(ServerMonitorPage, {
    global: { plugins: [pinia, ElementPlus] },
  })
}

function cardOf(wrapper, title) {
  return wrapper.findAll('.metric-card').find((node) => node.find('.metric-title').text() === title)
}

beforeEach(() => {
  vi.clearAllMocks()
  responderOverride = null
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('服务监控页', () => {
  it('reads /admin/monitor/server once and renders the section cards', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(http.calls.map((call) => call.url)).toEqual(['/admin/monitor/server'])

    const cpu = cardOf(wrapper, 'CPU')
    expect(cpu.text()).toContain('12.3%')
    expect(cpu.text()).toContain('0.85')
    expect(cpu.text()).toContain('8')

    expect(cardOf(wrapper, '系统').text()).toContain('运行时长')
    expect(cardOf(wrapper, '内存').text()).toContain('共 16.0 GB')
    expect(cardOf(wrapper, '线程').text()).toContain('42')
    expect(cardOf(wrapper, '磁盘').text()).toContain('/')
    expect(wrapper.text()).not.toContain('不可用')
  })

  it('renders 不可用 on the degraded card only and keeps the others readable', async () => {
    responderOverride = () => ok({ ...snapshot, cpu: null, disks: null })
    const wrapper = mountPage()
    await flushPromises()

    expect(cardOf(wrapper, 'CPU').text()).toContain('不可用')
    expect(cardOf(wrapper, '磁盘').text()).toContain('不可用')
    expect(cardOf(wrapper, '内存').text()).toContain('共 16.0 GB')
    expect(cardOf(wrapper, 'Hikari').text()).toContain('活跃 2')
  })

  it('renders the whole page as unavailable cards when the snapshot is null', async () => {
    responderOverride = () => ok(null)
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.findAll('.metric-card').length).toBeGreaterThan(0)
    for (const title of ['CPU', '系统', '内存', '线程', '磁盘', 'Hikari']) {
      expect(cardOf(wrapper, title).text()).toContain('不可用')
    }
  })

  it('shows the load error state with retry instead of blank cards on transport failure', async () => {
    responderOverride = () => {
      throw new AxiosError('boom', 'ERR_NETWORK')
    }
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.find('.list-load-error').exists()).toBe(true)
    expect(wrapper.findAll('.metric-card')).toHaveLength(0)
  })

  it('re-reads the snapshot from the refresh button and retry link', async () => {
    const wrapper = mountPage()
    await flushPromises()

    // 受控 promise：卡住响应以观察刷新按钮的加载态。
    let release
    responderOverride = () => new Promise((resolve) => (release = resolve))
    const click = wrapper.find('.refresh-button').trigger('click')
    await click
    expect(wrapper.find('.refresh-button').classes()).toContain('is-loading')
    expect(cardOf(wrapper, 'CPU').text()).toContain('12.3%')
    release(ok({ ...snapshot, cpu: null }))
    await flushPromises()

    expect(http.calls.filter((call) => call.url === '/admin/monitor/server')).toHaveLength(2)
    expect(cardOf(wrapper, 'CPU').text()).toContain('不可用')

    // 刷新撞上传输失败：保留卡片、给出可重试错误行。
    responderOverride = () => {
      throw new AxiosError('boom', 'ERR_NETWORK')
    }
    await wrapper.find('.refresh-button').trigger('click')
    await flushPromises()
    expect(http.calls.filter((call) => call.url === '/admin/monitor/server')).toHaveLength(3)
    expect(wrapper.find('.list-load-error').exists()).toBe(true)
    expect(cardOf(wrapper, 'CPU').text()).toContain('不可用')

    responderOverride = () => ok(snapshot)
    await wrapper.find('.list-load-error button').trigger('click')
    await flushPromises()
    expect(http.calls.filter((call) => call.url === '/admin/monitor/server')).toHaveLength(4)
    expect(wrapper.find('.list-load-error').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('不可用')
  })
})
