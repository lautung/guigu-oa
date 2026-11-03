import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'

const { messageMock, confirmMock } = vi.hoisted(() => ({
  messageMock: Object.assign(vi.fn(), { error: vi.fn(), success: vi.fn(), close: vi.fn() }),
  confirmMock: vi.fn(),
}))

vi.mock('element-plus', async (importOriginal) => {
  const actual = await importOriginal()
  return {
    ...actual,
    ElMessage: messageMock,
    ElMessageBox: { confirm: confirmMock, close: vi.fn() },
  }
})

const monitorApi = vi.hoisted(() => ({
  forceLogout: vi.fn(),
}))

vi.mock('@/api/monitor', () => monitorApi)

import ElementPlus from 'element-plus'
import MonitorOnlinePage from '@/views/monitor/online/index.vue'
import { hasPermi } from '@/permission'
import {
  ensureDomApis,
  fakeRecognition,
  installHttp,
  ok,
  paged,
  setupSession,
} from '../support/harness'

const currentSession = {
  sessionId: 'sess-current',
  username: 'admin',
  name: '管理员',
  loginTime: '2026-01-01 08:00:00',
  ip: '127.0.0.1',
  browser: 'Chrome',
  os: 'macOS',
  lastActive: '2026-01-01 09:00:00',
  current: true,
}

const otherSession = {
  sessionId: 'sess-other',
  username: 'alice',
  name: '爱丽丝',
  loginTime: '2025-12-31 20:00:00',
  ip: '10.0.0.8',
  browser: 'Firefox',
  os: 'Windows',
  lastActive: '2025-12-31 21:30:00',
  current: false,
}

const FORCE_LOGOUT_PERMISSION = 'monitor:online:forceLogout'

let rows = []
let listResponderOverride = null
let http

function responder(config) {
  const url = config.url || ''
  // Override 接管全部请求（含续期端点），供 208 等终态场景构造完整响应面。
  if (listResponderOverride) return listResponderOverride(config)
  if (/^\/admin\/monitor\/online\/\d+\/\d+$/.test(url)) return paged(rows)
  throw new Error(`Unexpected request ${url}`)
}

function realOptions(pinia) {
  return {
    global: {
      plugins: [pinia, ElementPlus],
      directives: { hasPermi },
    },
  }
}

function rowOf(wrapper, text) {
  return wrapper.findAll('.el-table__row').find((node) => node.text().includes(text))
}

function forceButtonOf(row) {
  return row.findAll('button').find((node) => node.text() === '强退')
}

ensureDomApis()

beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  monitorApi.forceLogout.mockResolvedValue(ok(null))
  rows = [{ ...currentSession }, { ...otherSession }]
  listResponderOverride = null
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('在线用户页', () => {
  it('loads sessions, renders the columns and the header total', async () => {
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.vm.rows).toEqual([
      expect.objectContaining({
        sessionId: 'sess-current',
        username: 'admin',
        name: '管理员',
        loginTime: '2026-01-01 08:00:00',
        ip: '127.0.0.1',
        browser: 'Chrome',
        os: 'macOS',
        lastActive: '2026-01-01 09:00:00',
        current: true,
      }),
      expect.objectContaining({ username: 'alice', current: false }),
    ])
    expect(wrapper.vm.view.total).toBe(2)
    expect(wrapper.find('.page-header').text()).toContain('共 2 条')
    expect(http.calls.at(-1).url).toBe('/admin/monitor/online/1/10')
  })

  it('sends the username filter and the login time range, then paginates', async () => {
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    wrapper.vm.query.username = 'ali'
    wrapper.vm.query.loginTimes = ['2025-12-01 00:00:00', '2025-12-31 23:59:59']
    wrapper.vm.search()
    await flushPromises()

    expect(http.calls.at(-1).url).toBe('/admin/monitor/online/1/10')
    expect(http.calls.at(-1).params).toEqual({
      username: 'ali',
      beginTime: '2025-12-01 00:00:00',
      endTime: '2025-12-31 23:59:59',
    })

    wrapper.vm.list.handleSizeChange(20)
    await flushPromises()
    expect(http.calls.at(-1).url).toBe('/admin/monitor/online/1/20')
  })

  it('marks the current session, disables its force logout, keeps others enabled', async () => {
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    const currentRow = rowOf(wrapper, 'admin')
    const otherRow = rowOf(wrapper, 'alice')

    expect(currentRow.find('.el-tag').text()).toBe('当前会话')
    expect(forceButtonOf(currentRow).attributes('disabled')).toBeDefined()
    expect(otherRow.find('.el-tag').exists()).toBe(false)
    expect(forceButtonOf(otherRow).attributes('disabled')).toBeUndefined()
  })

  it('confirms with username, login time and ip before forcing logout, then refreshes', async () => {
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    await forceButtonOf(rowOf(wrapper, 'alice')).trigger('click')
    await flushPromises()

    const message = confirmMock.mock.calls[0][0]
    expect(message).toContain('alice')
    expect(message).toContain('2025-12-31 20:00:00')
    expect(message).toContain('10.0.0.8')
    expect(monitorApi.forceLogout).toHaveBeenCalledTimes(1)
    expect(monitorApi.forceLogout).toHaveBeenCalledWith('sess-other')

    const readsAfterForce = http.calls.filter((call) => /monitor\/online\/\d+\/\d+/.test(call.url))
    expect(readsAfterForce.length).toBeGreaterThanOrEqual(2)
    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: expect.any(String) }),
    )
  })

  it('keeps the session online when the confirmation is cancelled', async () => {
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await forceButtonOf(rowOf(wrapper, 'alice')).trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(monitorApi.forceLogout).not.toHaveBeenCalled()
  })

  it('renders the empty state when no session is online', async () => {
    rows = []
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.text()).toContain('暂无数据')
    expect(wrapper.find('.page-header').text()).toContain('共 0 条')
  })

  it('removes the force logout action from the DOM without the button permission', async () => {
    const { pinia } = setupSession({ permissions: [] })
    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.findAll('button').map((node) => node.text())).not.toContain('强退')
  })

  it('hands a 208 session expiry to the recognition layer without a fake error row', async () => {
    const rejectSpy = vi.fn((_context, error) => {
      throw error
    })
    const recognition = fakeRecognition({ reject: rejectSpy })
    const { pinia } = setupSession({ permissions: [FORCE_LOGOUT_PERMISSION], recognition })
    listResponderOverride = () => ({ code: 208, message: '认证失败', data: null })

    const wrapper = mount(MonitorOnlinePage, realOptions(pinia))
    await flushPromises()

    expect(rejectSpy).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.view.status).not.toBe('failed')
    expect(wrapper.find('.list-load-error').exists()).toBe(false)
  })
})
