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

import CacheMonitorPage from '@/views/monitor/cache/index.vue'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

const snapshot = {
  server: { redisVersion: '7.4.1', uptimeSeconds: 90061 },
  memory: {
    usedMemoryBytes: 536870912,
    usedMemoryHuman: '512.00M',
    maxMemoryBytes: 1073741824,
  },
  clients: { connectedClients: 5 },
  stats: { keyspaceHits: 80, keyspaceMisses: 20, expiredKeys: 10 },
  keyspace: [{ db: 'db0', keys: 4, expires: 1, avgTtlMs: 1234 }],
}

let responderOverride = null
let http

function responder(config) {
  if (responderOverride) return responderOverride(config)
  if ((config.url || '') === '/admin/monitor/cache') return ok(snapshot)
  throw new Error(`Unexpected request ${config.url}`)
}

function mountPage() {
  const { pinia } = setupSession()
  return mount(CacheMonitorPage, {
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

describe('缓存监控页', () => {
  it('reads /admin/monitor/cache once and renders the section cards', async () => {
    const wrapper = mountPage()
    await flushPromises()

    expect(http.calls.map((call) => call.url)).toEqual(['/admin/monitor/cache'])

    const server = cardOf(wrapper, 'Redis 信息')
    expect(server.text()).toContain('7.4.1')
    expect(server.text()).toContain('1 天 1 小时 1 分')

    expect(cardOf(wrapper, '内存').text()).toContain('512.0 MB')
    expect(cardOf(wrapper, '内存').text()).toContain('1.0 GB')

    const clients = cardOf(wrapper, '客户端与命中率')
    expect(clients.text()).toContain('5')
    expect(clients.text()).toContain('80.0%')

    const keyspace = cardOf(wrapper, '各 db 键数')
    expect(keyspace.text()).toContain('db0')
    expect(keyspace.text()).toContain('1234 ms')
    expect(wrapper.text()).not.toContain('不可用')
  })

  it('renders 不可用 on the degraded cards only and keeps the others readable', async () => {
    responderOverride = () => ok({ ...snapshot, memory: null, keyspace: null })
    const wrapper = mountPage()
    await flushPromises()

    expect(cardOf(wrapper, '内存').text()).toContain('不可用')
    expect(cardOf(wrapper, '各 db 键数').text()).toContain('不可用')
    expect(cardOf(wrapper, 'Redis 信息').text()).toContain('7.4.1')
    expect(cardOf(wrapper, '客户端与命中率').text()).toContain('80.0%')
  })

  it('renders an empty keyspace table without reading it as unavailable', async () => {
    responderOverride = () => ok({ ...snapshot, keyspace: [] })
    const wrapper = mountPage()
    await flushPromises()

    expect(cardOf(wrapper, '各 db 键数').text()).not.toContain('不可用')
    expect(cardOf(wrapper, '各 db 键数').text()).toContain('暂无数据')
  })

  it('renders the whole page as unavailable cards when the snapshot is null', async () => {
    responderOverride = () => ok(null)
    const wrapper = mountPage()
    await flushPromises()

    expect(wrapper.findAll('.metric-card').length).toBeGreaterThan(0)
    for (const title of ['Redis 信息', '内存', '客户端与命中率', '各 db 键数']) {
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
    expect(cardOf(wrapper, 'Redis 信息').text()).toContain('7.4.1')
    release(ok({ ...snapshot, server: null }))
    await flushPromises()

    expect(http.calls.filter((call) => call.url === '/admin/monitor/cache')).toHaveLength(2)
    expect(cardOf(wrapper, 'Redis 信息').text()).toContain('不可用')

    // 刷新撞上传输失败：保留卡片、给出可重试错误行。
    responderOverride = () => {
      throw new AxiosError('boom', 'ERR_NETWORK')
    }
    await wrapper.find('.refresh-button').trigger('click')
    await flushPromises()
    expect(http.calls.filter((call) => call.url === '/admin/monitor/cache')).toHaveLength(3)
    expect(wrapper.find('.list-load-error').exists()).toBe(true)
    expect(cardOf(wrapper, 'Redis 信息').text()).toContain('不可用')

    responderOverride = () => ok(snapshot)
    await wrapper.find('.list-load-error button').trigger('click')
    await flushPromises()
    expect(http.calls.filter((call) => call.url === '/admin/monitor/cache')).toHaveLength(4)
    expect(wrapper.find('.list-load-error').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('不可用')
  })
})
