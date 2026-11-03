import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises } from '@vue/test-utils'

import { presentFeedItem } from '@/notice/config'
import { createUnreadPolling } from '@/notice/unread'

const ROW = presentFeedItem({
  id: 1,
  title: '系统维护',
  type: 1,
  publisherName: '管理员',
  publishTime: '2026-11-03 10:00:00',
  read: false,
})

function source(overrides = {}) {
  return {
    count: vi.fn(async () => 3),
    recent: vi.fn(async () => [ROW]),
    ...overrides,
  }
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  vi.useRealTimers()
})

describe('未读轮询', () => {
  it('立即拉一次，之后每 60 秒再拉', async () => {
    const unread = source()
    const polling = createUnreadPolling({ source: unread })
    polling.start()
    await flushPromises()

    expect(unread.count).toHaveBeenCalledTimes(1)
    expect(unread.recent).toHaveBeenCalledTimes(1)
    expect(polling.state.count).toBe(3)
    expect(polling.state.recent).toEqual([
      {
        id: 1,
        title: '系统维护',
        type: 1,
        typeLabel: '通知',
        typeTagType: 'info',
        publisherName: '管理员',
        publishTime: '2026-11-03 10:00:00',
        read: false,
        readLabel: '未读',
        readTagType: 'danger',
      },
    ])

    await vi.advanceTimersByTimeAsync(59_999)
    expect(unread.count).toHaveBeenCalledTimes(1)

    await vi.advanceTimersByTimeAsync(1)
    expect(unread.count).toHaveBeenCalledTimes(2)
    expect(unread.recent).toHaveBeenCalledTimes(2)
    polling.stop()
  })

  it('窗口重新聚焦时补拉', async () => {
    const unread = source()
    const polling = createUnreadPolling({ source: unread })
    polling.start()
    await flushPromises()

    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    expect(unread.count).toHaveBeenCalledTimes(2)

    polling.stop()
    window.dispatchEvent(new Event('focus'))
    await flushPromises()
    expect(unread.count).toHaveBeenCalledTimes(2)
  })

  it('停止后清表、清状态且不再拉取', async () => {
    const unread = source()
    const polling = createUnreadPolling({ source: unread })
    polling.start()
    await flushPromises()
    expect(polling.isRunning()).toBe(true)

    polling.stop()
    expect(polling.isRunning()).toBe(false)
    expect(polling.state.count).toBe(0)
    expect(polling.state.recent).toEqual([])

    await vi.advanceTimersByTimeAsync(120_000)
    expect(unread.count).toHaveBeenCalledTimes(1)
  })

  it('拉取失败静默，保留上一次成功结果', async () => {
    let fail = false
    const unread = source({
      count: async () => {
        if (fail) throw new Error('network')
        return 4
      },
      recent: async () => {
        if (fail) throw new Error('network')
        return [ROW]
      },
    })
    const polling = createUnreadPolling({ source: unread })
    polling.start()
    await flushPromises()
    expect(polling.state.count).toBe(4)

    fail = true
    await polling.refresh()
    expect(polling.state.count).toBe(4)
    expect(polling.state.recent).toHaveLength(1)
    expect(polling.isRunning()).toBe(true)

    polling.stop()
  })

  it('识别终态失败时停表', async () => {
    const unread = source({
      count: async () => {
        throw Object.assign(new Error('登录失效'), { kind: 'invalid' })
      },
    })
    const polling = createUnreadPolling({ source: unread })
    polling.start()
    await flushPromises()

    expect(polling.isRunning()).toBe(false)
  })

  it('迟到的旧结果不覆盖新结果', async () => {
    let resolveFirst
    let calls = 0
    const unread = source({
      count: async () => {
        calls += 1
        if (calls === 1) {
          return new Promise((resolve) => {
            resolveFirst = resolve
          })
        }
        return 9
      },
    })
    const polling = createUnreadPolling({ source: unread })
    polling.start()
    await flushPromises()

    await polling.refresh()
    await flushPromises()
    expect(polling.state.count).toBe(9)

    resolveFirst(1)
    await flushPromises()
    expect(polling.state.count).toBe(9)
    polling.stop()
  })
})
