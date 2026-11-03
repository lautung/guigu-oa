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

const cacheApi = vi.hoisted(() => ({
  deleteCacheKey: vi.fn(),
  clearPermissionCache: vi.fn(),
}))

vi.mock('@/api/monitor', () => cacheApi)

import ElementPlus from 'element-plus'
import { AxiosError } from 'axios'

import CacheListPage from '@/views/monitor/cacheList/index.vue'
import { hasPermi } from '@/permission'
import { ensureDomApis, installHttp, ok, setupSession } from '../support/harness'

ensureDomApis()

const CLEAN_PERMISSION = 'monitor:cache:clean'

let keysPage = { keys: [], nextCursor: 0 }
let responderOverride = null
let http

/** 每个白名单前缀一页数据，供前缀切换断言各自的窗口内容。 */
function pageForPrefix(prefix) {
  if (prefix === 'login:session:')
    return { keys: [{ key: 'login:session:s1', ttl: -1 }], nextCursor: 0 }
  if (prefix === 'token:revoked:')
    return { keys: [{ key: 'token:revoked:t1', ttl: 3600 }], nextCursor: 0 }
  return keysPage
}

function responder(config, callIndex) {
  const url = config.url || ''
  // Override 接管全部请求（含调用序号），供迟到窗口等场景构造完整响应面。
  if (responderOverride) return responderOverride(config, callIndex)
  if (url === '/admin/monitor/cache/keys') return ok(pageForPrefix(config.params?.prefix))
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

function keysRequests() {
  return http.calls.filter((call) => call.url === '/admin/monitor/cache/keys')
}

function rowOf(wrapper, text) {
  return wrapper.findAll('.el-table__row').find((node) => node.text().includes(text))
}

function deleteButtonOf(row) {
  return row.findAll('button').find((node) => node.text() === '删除')
}

beforeEach(() => {
  vi.clearAllMocks()
  messageMock.mockReset()
  confirmMock.mockReset().mockResolvedValue('confirm')
  cacheApi.deleteCacheKey.mockResolvedValue(ok(null))
  cacheApi.clearPermissionCache.mockResolvedValue(ok(0))
  keysPage = {
    keys: [
      { key: 'perm:cache:admin', ttl: -1 },
      { key: 'perm:cache:alice', ttl: 120 },
    ],
    nextCursor: 0,
  }
  responderOverride = null
  http = installHttp(responder)
})

afterEach(() => http.restore())

describe('缓存列表页', () => {
  it('loads the perm: prefix first page and marks only perm: rows deletable', async () => {
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    expect(keysRequests()).toHaveLength(1)
    expect(keysRequests()[0].params).toEqual({ prefix: 'perm:', cursor: 0, count: 100 })

    const adminRow = rowOf(wrapper, 'perm:cache:admin')
    const aliceRow = rowOf(wrapper, 'perm:cache:alice')
    expect(adminRow.text()).toContain('无过期')
    expect(aliceRow.text()).toContain('120 秒')
    expect(deleteButtonOf(adminRow)).toBeDefined()
    expect(wrapper.find('.clear-cache-button').exists()).toBe(true)
  })

  it('resets cursor and rows when the prefix changes, hiding the delete entries', async () => {
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.switchPrefix('login:session:')
    await flushPromises()

    expect(keysRequests()).toHaveLength(2)
    expect(keysRequests()[1].params).toEqual({
      prefix: 'login:session:',
      cursor: 0,
      count: 100,
    })
    expect(wrapper.text()).not.toContain('perm:cache:admin')
    expect(wrapper.text()).toContain('login:session:s1')
    expect(wrapper.findAll('.el-table__row button').map((node) => node.text())).not.toContain(
      '删除',
    )
  })

  it('appends the next window on 加载更多 and hides the button when the scan ends', async () => {
    keysPage = {
      keys: [{ key: 'perm:cache:admin', ttl: -1 }],
      nextCursor: 1,
    }
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    const moreButton = wrapper.find('.load-more-button')
    expect(moreButton.exists()).toBe(true)

    keysPage = {
      keys: [{ key: 'perm:cache:alice', ttl: 60 }],
      nextCursor: 0,
    }
    await wrapper.vm.loadMore()
    await flushPromises()

    expect(keysRequests()[1].params).toEqual({ prefix: 'perm:', cursor: 1, count: 100 })
    expect(rowOf(wrapper, 'perm:cache:admin')).toBeDefined()
    expect(rowOf(wrapper, 'perm:cache:alice')).toBeDefined()
    expect(wrapper.find('.load-more-button').exists()).toBe(false)
  })

  it('confirms with the key name before deleting, then refreshes the current window', async () => {
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.removeRow({ key: 'perm:cache:alice', deletable: true })
    await flushPromises()

    expect(confirmMock.mock.calls[0][0]).toContain('perm:cache:alice')
    expect(cacheApi.deleteCacheKey).toHaveBeenCalledTimes(1)
    expect(cacheApi.deleteCacheKey).toHaveBeenCalledWith('perm:cache:alice')

    // 写后刷新：整段重读已累计的窗口（2 行），不是回到翻页第一页。
    expect(keysRequests()[1].params).toEqual({ prefix: 'perm:', cursor: 0, count: 2 })
    expect(messageMock).toHaveBeenCalledWith(
      expect.objectContaining({ type: 'success', message: expect.any(String) }),
    )
  })

  it('keeps the key when the confirmation is cancelled', async () => {
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    confirmMock.mockRejectedValueOnce(new Error('cancel'))
    await wrapper.vm.removeRow({ key: 'perm:cache:alice', deletable: true })
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(cacheApi.deleteCacheKey).not.toHaveBeenCalled()
    expect(keysRequests()).toHaveLength(1)
  })

  it('clears every permission cache key after confirmation and reports the count', async () => {
    cacheApi.clearPermissionCache.mockResolvedValue(ok(3))
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    await wrapper.vm.clearAll()
    await flushPromises()

    expect(confirmMock).toHaveBeenCalledTimes(1)
    expect(cacheApi.clearPermissionCache).toHaveBeenCalledTimes(1)
    expect(keysRequests()[1].params).toEqual({ prefix: 'perm:', cursor: 0, count: 2 })
    const notify = messageMock.mock.calls.find(
      (call) => call[0] && typeof call[0] === 'object' && call[0].type === 'success',
    )
    expect(notify[0].message).toContain('3')
  })

  it('removes both clean entries from the DOM without the clean permission', async () => {
    const { pinia } = setupSession({ permissions: [] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.find('.clear-cache-button').exists()).toBe(false)
    expect(wrapper.findAll('.el-table__row button').map((node) => node.text())).not.toContain(
      '删除',
    )
  })

  it('renders the empty state when the prefix has no keys', async () => {
    keysPage = { keys: [], nextCursor: 0 }
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.text()).toContain('暂无数据')
  })

  it('shows the load error state with retry on transport failure', async () => {
    responderOverride = () => {
      throw new AxiosError('boom', 'ERR_NETWORK')
    }
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    expect(wrapper.find('.list-load-error').exists()).toBe(true)

    responderOverride = null
    await wrapper.find('.list-load-error button').trigger('click')
    await flushPromises()
    expect(keysRequests()).toHaveLength(2)
    expect(wrapper.find('.list-load-error').exists()).toBe(false)
    expect(rowOf(wrapper, 'perm:cache:admin')).toBeDefined()
  })

  it('drops a late window when the prefix switches again', async () => {
    const { pinia } = setupSession({ permissions: [CLEAN_PERMISSION] })
    const wrapper = mount(CacheListPage, realOptions(pinia))
    await flushPromises()

    // 切到 login:session: 的窗口被卡住；随后切到 token:revoked: 先返回并生效。
    let release
    const stuck = new Promise((resolve) => (release = resolve))
    responderOverride = (config, callIndex) => {
      if (callIndex === 2) return stuck
      if (callIndex === 3)
        return ok({ keys: [{ key: 'token:revoked:t1', ttl: 3600 }], nextCursor: 0 })
      return ok(keysPage)
    }
    const late = wrapper.vm.switchPrefix('login:session:')
    const latest = wrapper.vm.switchPrefix('token:revoked:')
    await late
    await latest
    await flushPromises()

    release(ok({ keys: [{ key: 'login:session:s1', ttl: -1 }], nextCursor: 0 }))
    await flushPromises()

    expect(wrapper.text()).toContain('token:revoked:t1')
    expect(wrapper.text()).not.toContain('login:session:s1')
    expect(keysRequests()[2].params.prefix).toBe('token:revoked:')
  })
})
