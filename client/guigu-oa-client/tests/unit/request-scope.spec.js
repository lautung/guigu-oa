import { describe, expect, it, vi } from 'vitest'
import { createRequestScope } from '@/request-scope'

function fakeRecognition() {
  let generation = 1
  let entryId = 'entry-1'
  return {
    capture: vi.fn(() => ({ generation, entryId, silent: false })),
    assertCurrent: vi.fn((context) => {
      if (context.generation !== generation || context.entryId !== entryId)
        throw new Error('请求所属的进入结果已变化')
    }),
    replaceIdentity(next) {
      generation = next.generation ?? generation + 1
      entryId = next.entryId ?? entryId
    },
  }
}

function harness(options = {}) {
  const recognition = fakeRecognition()
  const sends = []
  const scope = createRequestScope(
    recognition,
    (config) => {
      sends.push(config)
      return Promise.resolve({ code: 200, data: { ok: true } })
    },
    options,
  )
  return { recognition, sends, scope }
}

describe('请求作用域', () => {
  it('打开时捕获一次身份，每次请求带上上下文与错误策略，调用方配置不被改写', async () => {
    const h = harness({ notifyNormalErrors: false })
    const config = { method: 'get', url: '/admin/process/findPending/1/10', params: { page: 1 } }

    await h.scope.request(config)
    await h.scope.request({ method: 'post', url: '/admin/process/startUp', data: { id: 1 } })

    expect(h.recognition.capture).toHaveBeenCalledTimes(1)
    const context = h.recognition.capture.mock.results[0].value
    expect(h.sends[0]).toEqual({
      ...config,
      _recognition: context,
      _notifyNormalErrors: false,
    })
    expect(h.sends[1]).toMatchObject({
      method: 'post',
      url: '/admin/process/startUp',
      data: { id: 1 },
      _recognition: context,
      _notifyNormalErrors: false,
    })
    expect(h.sends[0]._isRelevant).toBeUndefined()
    expect(config).not.toHaveProperty('_recognition')
  })

  it('默认提示普通错误，按次相关性原样交给传输层', async () => {
    const h = harness()
    let relevant = true
    await h.scope.request({ method: 'get', url: '/list' }, () => relevant)

    expect(h.sends[0]._notifyNormalErrors).toBe(true)
    expect(h.sends[0]._isRelevant()).toBe(true)
    relevant = false
    expect(h.sends[0]._isRelevant()).toBe(false)
  })

  it('current 反映这次打开是否仍属于当前身份', () => {
    const h = harness()
    expect(h.scope.current()).toBe(true)
    h.recognition.replaceIdentity({ entryId: 'entry-2' })
    expect(h.scope.current()).toBe(false)
    expect(h.recognition.assertCurrent).toHaveBeenCalledWith(
      h.recognition.capture.mock.results[0].value,
    )
  })

  it('身份变化后新开的作用域捕获新身份', async () => {
    const recognition = fakeRecognition()
    const sends = []
    const send = (config) => {
      sends.push(config)
      return Promise.resolve({ code: 200, data: null })
    }
    const first = createRequestScope(recognition, send)
    recognition.replaceIdentity({ entryId: 'entry-2' })
    const second = createRequestScope(recognition, send)

    await first.request({ method: 'get', url: '/list' })
    await second.request({ method: 'get', url: '/list' })

    expect(sends[0]._recognition.entryId).toBe('entry-1')
    expect(sends[1]._recognition.entryId).toBe('entry-2')
    expect(first.current()).toBe(false)
    expect(second.current()).toBe(true)
  })
})
