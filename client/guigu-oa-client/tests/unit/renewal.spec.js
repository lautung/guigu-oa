import axios, { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRecognition } from '@/recognition'
import { createRenewal } from '@/renewal'
import { BusinessRequestFailure } from '@/request-failure'
import { installHttp } from './support/harness'

const originalAdapter = axios.defaults.adapter
const refreshUrl = '/dev-api/admin/system/index/refresh'
const businessUrl = '/admin/process/findPending/1/10'
const refreshed = { token: 'new-access', refreshToken: 'refresh' }
/** #145/#155 修复后认证失败（401）带真实状态码：错误体仍是业务信封。 */
function httpError(status, data, config) {
  return new AxiosError('http ' + status, 'ERR_BAD_REQUEST', config, null, {
    status,
    data,
    headers: {},
    config,
  })
}
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
function requestConfig(overrides = {}) {
  return { url: businessUrl, headers: { token: 'old-access' }, ...overrides }
}

/**
 * 续期 module 直接打 interface：真 recognition（generation/entryId/forget 语义），
 * resend 是受控假件，adapter 只拦裸 axios 的 refresh POST。
 */
function renewalHarness(
  respond,
  { tokens = { token: 'old-access', refreshToken: 'refresh' } } = {},
) {
  const state = { tokens, entryId: 'entry-1' }
  const notifications = [],
    destinations = []
  let entrySequence = 1
  const recognition = createRecognition({
    credentials: () => state.tokens,
    entryId: () => state.entryId,
    writeTokens: (next) => {
      state.tokens = next
      state.entryId ||= `entry-${++entrySequence}`
    },
    clearUser: () => {
      state.tokens = { token: '' }
      state.entryId = ''
    },
    hasUser: () => true,
    writeUser: () => {},
    recovery: () => ({ status: 'idle', target: '' }),
    showRecovery: () => {},
    loadUser: async () => ({ name: 'Alice' }),
    currentPath: () => '/work/pending',
    goTo: () => {},
    goToLogin: (path) => destinations.push(path),
    notify: (message) => notifications.push(message),
  })
  const http = installHttp(respond)
  const resend = vi.fn(async (config) => ({ code: 200, data: null, config }))
  const renewal = createRenewal(recognition, resend)
  return {
    state,
    recognition,
    renewal,
    resend,
    refreshCalls: http.calls,
    notifications,
    destinations,
  }
}

afterEach(() => {
  axios.defaults.adapter = originalAdapter
})

describe('renewal module: single-flight refresh, header swap, replay', () => {
  it('shares one refresh for concurrent 208s and replays both with the new header', async () => {
    const refresh = deferred()
    const h = renewalHarness((config) => {
      expect(config.url).toBe(refreshUrl)
      return refresh.promise
    })
    const context = h.recognition.capture()
    const first = h.renewal.onExpired(requestConfig(), context)
    const second = h.renewal.onExpired(requestConfig(), context)
    refresh.resolve({ code: 200, data: refreshed })
    await Promise.all([first, second])
    expect(h.refreshCalls).toHaveLength(1)
    expect(h.resend).toHaveBeenCalledTimes(2)
    expect(h.resend.mock.calls.every(([config]) => config.headers.token === 'new-access')).toBe(
      true,
    )
    expect(h.state.tokens).toEqual(refreshed)
    expect(h.destinations).toEqual([])
  })

  it('replays a late 208 with the already refreshed header and no second refresh', async () => {
    const h = renewalHarness(() => ({ code: 200, data: refreshed }))
    const context = h.recognition.capture()
    await h.renewal.onExpired(requestConfig(), context)
    expect(h.refreshCalls).toHaveLength(1)
    const late = h.renewal.onExpired(requestConfig(), context)
    await late
    expect(h.refreshCalls).toHaveLength(1)
    expect(h.resend).toHaveBeenCalledTimes(2)
    expect(h.resend.mock.calls[1][0].headers.token).toBe('new-access')
  })

  it.each([
    ['authentication endpoint', requestConfig({ url: '/admin/system/index/login' })],
    ['already retried request', requestConfig({ _authRefreshRetried: true })],
    ['irrelevant request', requestConfig({ _isRelevant: () => false })],
  ])('declines renewal for a %s without any refresh', async (_name, config) => {
    const h = renewalHarness(() => ({ code: 200, data: refreshed }))
    const replay = h.renewal.onExpired(config, h.recognition.capture())
    expect(replay).toBeNull()
    expect(h.refreshCalls).toHaveLength(0)
    expect(h.resend).not.toHaveBeenCalled()
    expect(h.notifications).toEqual([])
    expect(h.destinations).toEqual([])
  })

  it('declines renewal without a refresh token and leaves rejection to the caller', async () => {
    const h = renewalHarness(() => ({ code: 200, data: refreshed }), {
      tokens: { token: 'old-access' },
    })
    expect(h.renewal.onExpired(requestConfig(), h.recognition.capture())).toBeNull()
    expect(h.refreshCalls).toHaveLength(0)
    expect(h.resend).not.toHaveBeenCalled()
  })

  it.each([208, 209])(
    'clears the session when refresh fails as an HTTP 401 envelope %s (#155)',
    async (code) => {
      const h = renewalHarness((config) =>
        Promise.reject(httpError(401, { code, message: '身份失效' }, config)),
      )
      const refused = h.renewal.onExpired(requestConfig(), h.recognition.capture())
      await expect(refused).rejects.toMatchObject({
        kind: code === 208 ? 'invalid' : 'disabled',
        businessCode: 208,
        detail: { source: 'business', operation: 'refresh', originalBusinessCode: 208 },
      })
      expect(h.state.tokens).toEqual({ token: '' })
      expect(h.notifications).toEqual(['身份失效'])
      expect(h.destinations).toHaveLength(1)
      expect(h.resend).not.toHaveBeenCalled()
    },
  )

  it('keeps credentials and the current entry on a refresh timeout', async () => {
    const h = renewalHarness((config) =>
      Promise.reject(new AxiosError('timeout', 'ECONNABORTED', config)),
    )
    const refused = h.renewal.onExpired(requestConfig(), h.recognition.capture())
    await expect(refused).rejects.toMatchObject({
      kind: 'retryable',
      detail: { source: 'timeout', operation: 'refresh', originalBusinessCode: 208 },
    })
    expect(h.state.tokens).toEqual({ token: 'old-access', refreshToken: 'refresh' })
    expect(h.destinations).toEqual([])
    expect(h.resend).not.toHaveBeenCalled()
  })

  it('rejects a refresh response without a usable token pair as retryable', async () => {
    const h = renewalHarness(() => ({ code: 200, data: { token: '', refreshToken: 'refresh' } }))
    await expect(
      h.renewal.onExpired(requestConfig(), h.recognition.capture()),
    ).rejects.toMatchObject({
      kind: 'retryable',
      detail: { source: 'unexpected', operation: 'refresh', originalBusinessCode: 208 },
    })
    expect(h.state.tokens).toEqual({ token: 'old-access', refreshToken: 'refresh' })
    expect(h.destinations).toEqual([])
  })

  it('flies a fresh refresh after a failed one settles (flight cleanup)', async () => {
    const first = deferred()
    const second = deferred()
    let flight = 0
    const h = renewalHarness(() => (++flight === 1 ? first.promise : second.promise))
    const context = h.recognition.capture()
    const refused = h.renewal.onExpired(requestConfig(), context)
    first.reject(new AxiosError('timeout', 'ECONNABORTED', h.refreshCalls[0]))
    await expect(refused).rejects.toMatchObject({ kind: 'retryable' })
    const retried = h.renewal.onExpired(requestConfig(), context)
    second.resolve({ code: 200, data: refreshed })
    await retried
    expect(h.refreshCalls).toHaveLength(2)
    expect(h.resend).toHaveBeenCalledTimes(1)
    expect(h.state.tokens).toEqual(refreshed)
  })

  it('discards a settled renewal that belongs to a superseded entry', async () => {
    const refresh = deferred()
    const h = renewalHarness(() => refresh.promise)
    const stale = h.recognition.capture()
    const waiting = h.renewal.onExpired(requestConfig(), stale)
    h.recognition.establish({ token: 'bob-only', refreshToken: 'refresh' })
    refresh.resolve({ code: 200, data: refreshed })
    await expect(waiting).rejects.toMatchObject({ kind: 'superseded' })
    expect(h.state.tokens).toEqual({ token: 'bob-only', refreshToken: 'refresh' })
    expect(h.destinations).toEqual([])
    expect(h.notifications).toEqual([])
  })

  it('marks the retried request so a second 208 falls back to business rejection', async () => {
    const h = renewalHarness(() => ({ code: 200, data: refreshed }))
    const config = requestConfig()
    await h.renewal.onExpired(config, h.recognition.capture())
    expect(config._authRefreshRetried).toBe(true)
    expect(h.renewal.onExpired(config, h.recognition.capture())).toBeNull()
    expect(h.resend).toHaveBeenCalledTimes(1)
  })

  it('wraps non-RequestFailure rejections with the explicit-refusal shape', async () => {
    const refresh = deferred()
    const h = renewalHarness(() => refresh.promise)
    const waiting = h.renewal.onExpired(requestConfig(), h.recognition.capture())
    refresh.reject(new Error('plain network loss'))
    await expect(waiting).rejects.toBeInstanceOf(BusinessRequestFailure)
    await expect(waiting).rejects.toMatchObject({
      kind: 'retryable',
      detail: { source: 'network', operation: 'refresh', originalBusinessCode: 208 },
    })
    expect(h.destinations).toEqual([])
  })
})
