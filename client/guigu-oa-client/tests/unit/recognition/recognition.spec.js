import axios, { AxiosError } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { createRecognition } from '@/recognition'
import { createRequestClient } from '@/utils/request'

const originalAdapter = axios.defaults.adapter
const infoUrl = '/admin/system/index/info'
const businessUrl = '/admin/process/findPending/1/10'
const ok = (data) => ({ code: 200, data })
const expired = { code: 208, message: '认证失败' }
function deferred() {
  let resolve, reject
  const promise = new Promise((yes, no) => {
    resolve = yes
    reject = no
  })
  return { promise, resolve, reject }
}
function harness(respond) {
  const state = {
    tokens: { token: 'old-access', refreshToken: 'refresh' },
    entryId: 'initial',
    user: null,
    recovery: { status: 'idle', target: '' },
  }
  const notifications = [],
    destinations = []
  let request
  let entrySequence = 0
  const recognition = createRecognition({
    credentials: () => state.tokens,
    entryId: () => state.entryId,
    writeTokens: (tokens) => {
      state.tokens = tokens
      state.entryId ||= `entry-${++entrySequence}`
    },
    clearUser: () => {
      state.tokens = { token: '' }
      state.entryId = ''
      state.user = null
    },
    hasUser: () => state.user !== null,
    writeUser: (user) => {
      state.user = user
    },
    recovery: () => state.recovery,
    showRecovery: (recovery) => {
      state.recovery = recovery
    },
    loadUser: async () => (await request({ method: 'get', url: infoUrl })).data,
    currentPath: () => '/work/pending?from=bookmark',
    goTo: (path) => destinations.push(path),
    goToLogin: (path) => destinations.push(`/login?redirect=${encodeURIComponent(path)}`),
    notify: (message) => notifications.push(message),
  })
  const calls = []
  axios.defaults.adapter = async (config) => {
    calls.push(config)
    try {
      return { data: await respond(config), status: 200, statusText: 'OK', headers: {}, config }
    } catch (error) {
      throw error instanceof AxiosError
        ? error
        : new AxiosError(error.message, 'ERR_NETWORK', config)
    }
  }
  request = createRequestClient(recognition)
  return { state, recognition, request, calls, notifications, destinations }
}

afterEach(() => {
  axios.defaults.adapter = originalAdapter
})

describe('recognition interface with real request transport', () => {
  it('retains credentials after first info failure and retries the original target', async () => {
    const respond = vi
      .fn()
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValue(ok({ name: 'Alice', configMenus: [] }))
    const h = harness(respond)
    await expect(h.recognition.recognize('/work/pending?source=bookmark')).rejects.toMatchObject({
      kind: 'retryable',
    })
    expect(h.state.recovery).toEqual({
      status: 'retryable',
      target: '/work/pending?source=bookmark',
    })
    expect(h.state.tokens.token).toBe('old-access')
    expect(h.notifications).toEqual([])
    await h.recognition.resume()
    expect(h.state.user.name).toBe('Alice')
    expect(h.destinations).toEqual(['/work/pending?source=bookmark'])
  })

  it.each(['success', 'network', 'invalid', 'disabled'])(
    'discards late old-user info %s after a new token-only entry',
    async (outcome) => {
      const old = deferred()
      const h = harness(
        vi
          .fn()
          .mockImplementationOnce(() => old.promise)
          .mockResolvedValue(ok({ name: 'Bob' })),
      )
      h.recognition.establish({ token: 'alice-only' })
      const waiting = h.recognition.recognize('/work/started')
      const rejected = expect(waiting).rejects.toMatchObject({ kind: 'superseded' })
      await vi.waitFor(() => expect(h.calls).toHaveLength(1))
      h.recognition.establish({ token: 'bob-only' })
      await h.recognition.recognize('/work/pending')
      if (outcome === 'network')
        old.reject(new AxiosError('old network', 'ERR_NETWORK', h.calls[0]))
      else
        old.resolve(
          outcome === 'success'
            ? ok({ name: 'Alice' })
            : { code: outcome === 'invalid' ? 208 : 209 },
        )
      await rejected
      expect(h.state.tokens).toEqual({ token: 'bob-only' })
      expect(h.state.user.name).toBe('Bob')
      expect(h.destinations).toEqual([])
      expect(h.notifications).toEqual([])
    },
  )

  it('does not restore user data after exit, even with identical new credentials', async () => {
    const old = deferred()
    const h = harness(() => old.promise)
    const waiting = h.recognition.recognize()
    const rejected = expect(waiting).rejects.toMatchObject({ kind: 'superseded' })
    await vi.waitFor(() => expect(h.calls).toHaveLength(1))
    h.recognition.exit()
    h.recognition.establish({ token: 'old-access', refreshToken: 'refresh' })
    old.resolve(ok({ name: 'Alice' }))
    await rejected
    expect(h.state.user).toBeNull()
    expect(h.destinations).toHaveLength(1)
  })

  it('keeps recognized user data on a background info failure', async () => {
    const h = harness(() => Promise.reject(new Error('timeout')))
    h.state.user = { name: 'Alice' }
    await expect(h.recognition.recognize()).rejects.toMatchObject({ kind: 'retryable' })
    expect(h.state.user.name).toBe('Alice')
    expect(h.state.tokens.token).toBe('old-access')
    expect(h.state.recovery.status).toBe('idle')
    expect(h.destinations).toEqual([])
    expect(h.notifications).toEqual(['timeout'])
  })

  it('shares one refresh and retries concurrent requests with current headers', async () => {
    const refresh = deferred()
    const h = harness((config) => {
      if (config.url.endsWith('/refresh')) return refresh.promise
      return config.headers.get('token') === 'new-access' ? ok('done') : expired
    })
    const a = h.request({ method: 'get', url: businessUrl })
    const b = h.request({ method: 'get', url: businessUrl })
    await vi.waitFor(() =>
      expect(h.calls.filter((c) => c.url.endsWith('/refresh'))).toHaveLength(1),
    )
    refresh.resolve(ok({ token: 'new-access', refreshToken: 'refresh' }))
    await expect(Promise.all([a, b])).resolves.toEqual([ok('done'), ok('done')])
    expect(h.state.tokens.token).toBe('new-access')
    expect(h.notifications).toEqual([])
  })

  it('reuses refreshed access for a late 208 without a second refresh', async () => {
    const late = deferred()
    let count = 0
    const h = harness((config) => {
      if (config.url.endsWith('/refresh'))
        return ok({ token: 'new-access', refreshToken: 'refresh' })
      if (config.headers.get('token') === 'new-access') return ok('done')
      return ++count === 1 ? late.promise : expired
    })
    const a = h.request({ method: 'get', url: businessUrl })
    await vi.waitFor(() => expect(count).toBe(1))
    await h.request({ method: 'get', url: businessUrl })
    late.resolve(expired)
    await expect(a).resolves.toEqual(ok('done'))
    expect(h.calls.filter((c) => c.url.endsWith('/refresh'))).toHaveLength(1)
  })

  it.each([403, 503])('preserves refreshed entry after retry returns %s', async (code) => {
    const h = harness((config) =>
      config.url.endsWith('/refresh')
        ? ok({ token: 'new-access', refreshToken: 'refresh' })
        : config.headers.get('token') === 'new-access'
          ? { code, message: '拒绝' }
          : expired,
    )
    await expect(h.request({ method: 'get', url: businessUrl })).rejects.toMatchObject({
      kind: code === 403 ? 'forbidden' : 'retryable',
    })
    expect(h.state.tokens.token).toBe('new-access')
    expect(h.destinations).toEqual([])
    expect(h.notifications).toEqual(['拒绝'])
  })

  it('retains both tokens when refresh transport times out', async () => {
    const h = harness((config) =>
      config.url.endsWith('/refresh')
        ? Promise.reject(new AxiosError('timeout', 'ECONNABORTED', config))
        : expired,
    )
    await expect(h.request({ method: 'get', url: businessUrl })).rejects.toMatchObject({
      kind: 'retryable',
    })
    expect(h.state.tokens).toEqual({ token: 'old-access', refreshToken: 'refresh' })
    expect(h.destinations).toEqual([])
    expect(h.notifications).toEqual(['timeout'])
  })

  it.each([208, 209])('clears confirmed refresh failure %s only once', async (code) => {
    const refresh = deferred()
    const h = harness((config) => (config.url.endsWith('/refresh') ? refresh.promise : expired))
    const requests = [
      h.request({ method: 'get', url: businessUrl }),
      h.request({ method: 'get', url: businessUrl }),
    ]
    const result = Promise.allSettled(requests)
    await vi.waitFor(() =>
      expect(h.calls.filter((c) => c.url.endsWith('/refresh'))).toHaveLength(1),
    )
    refresh.resolve({ code, message: '身份失效' })
    await result
    expect(h.state.tokens.token).toBe('')
    expect(h.destinations).toHaveLength(1)
    expect(h.notifications).toEqual(['身份失效'])
  })

  it('does not retry an original request more than once', async () => {
    const h = harness((config) =>
      config.url.endsWith('/refresh')
        ? ok({ token: 'new-access', refreshToken: 'refresh' })
        : expired,
    )
    await expect(h.request({ method: 'get', url: businessUrl })).rejects.toMatchObject({
      kind: 'invalid',
    })
    expect(h.calls).toHaveLength(3)
    expect(h.state.tokens.token).toBe('')
    expect(h.destinations).toHaveLength(1)
  })

  it.each(['success', 'failure'])('does not commit late old refresh %s', async (outcome) => {
    const old = deferred()
    const h = harness((config) => (config.url.endsWith('/refresh') ? old.promise : expired))
    const waiting = h.request({ method: 'get', url: businessUrl })
    const rejected = expect(waiting).rejects.toMatchObject({ kind: 'superseded' })
    await vi.waitFor(() => expect(h.calls).toHaveLength(2))
    h.recognition.establish({ token: 'bob', refreshToken: 'bob-refresh' })
    if (outcome === 'failure') old.reject(new Error('late failure'))
    else old.resolve(ok({ token: 'old-refreshed', refreshToken: 'refresh' }))
    await rejected
    expect(h.state.tokens.token).toBe('bob')
    expect(h.notifications).toEqual([])
    expect(h.destinations).toEqual([])
  })

  it('clears a token-only entry after confirmed 208 without attempting refresh', async () => {
    const h = harness(() => expired)
    h.recognition.establish({ token: 'token-only' })
    await expect(h.recognition.recognize('/work/started')).rejects.toMatchObject({
      kind: 'invalid',
    })
    expect(h.calls).toHaveLength(1)
    expect(h.state.tokens.token).toBe('')
    expect(h.destinations).toEqual(['/login?redirect=%2Fwork%2Fstarted'])
  })
  it.each([
    { configMenus: {} },
    { configMenus: [{ path: 12 }] },
    { configMenus: [{ path: '/system/sysUser', title: '用户管理', type: 'page' }] },
    { avatar: 42 },
  ])('keeps malformed user data behind the recovery seam: %j', async (bad) => {
    const h = harness(() => ok({ name: 'Alice', ...bad }))
    await expect(h.recognition.recognize()).rejects.toMatchObject({ kind: 'retryable' })
    expect(h.state.user).toBeNull()
    expect(h.state.recovery.status).toBe('retryable')
    expect(h.state.tokens.token).toBe('old-access')
  })

  it('does not resume role configuration after an old recognition rejects', async () => {
    const old = deferred()
    const h = harness(() => old.promise)
    h.state.user = { name: 'Alice' }
    const resume = h.recognition.resume('/system/sysRole')
    await vi.waitFor(() => expect(h.calls).toHaveLength(1))
    h.recognition.establish({ token: 'bob' })
    old.reject(new AxiosError('late failure', 'ERR_NETWORK', h.calls[0]))
    await resume
    expect(h.destinations).toEqual([])
    expect(h.notifications).toEqual([])
    expect(h.state.tokens.token).toBe('bob')
  })

  it('keeps the same entry generation when refreshed credentials are replaced', () => {
    const h = harness(() => ok({ name: 'Alice' }))
    const context = h.recognition.capture()
    h.recognition.commitRefresh(context, { token: 'new-access', refreshToken: 'rotated-refresh' })
    expect(() => h.recognition.assertCurrent(context)).not.toThrow()
  })

  it.each([
    { token: 123, refreshToken: 'refresh' },
    { token: 'new', refreshToken: 42 },
  ])('does not commit malformed refreshed tokens: %j', async (tokens) => {
    const h = harness((config) => (config.url.endsWith('/refresh') ? ok(tokens) : expired))
    await expect(h.request({ method: 'get', url: businessUrl })).rejects.toMatchObject({
      kind: 'retryable',
    })
    expect(h.state.tokens).toEqual({ token: 'old-access', refreshToken: 'refresh' })
    expect(h.destinations).toEqual([])
  })
})

describe('caller-owned normal errors preserve request evidence and recognition', () => {
  const start = { method: 'post', url: '/admin/process/startUp', normalErrorNotification: 'caller', data: {} }
  it('retains definitive business rejection evidence without a global ordinary toast', async () => {
    const h = harness(() => ({ code: 201, message: '不能发起' }))
    await expect(h.request(start)).rejects.toMatchObject({ kind: 'retryable', detail: { source: 'business', operation: 'request', businessCode: 201 } })
    expect(h.notifications).toEqual([])
    expect(h.state.tokens.token).toBe('old-access')
  })
  it('distinguishes an original request timeout from a business rejection', async () => {
    const h = harness(config => Promise.reject(new AxiosError('timeout', 'ECONNABORTED', config)))
    await expect(h.request(start)).rejects.toMatchObject({ detail: { source: 'timeout', operation: 'request' } })
    expect(h.notifications).toEqual([])
  })
  it('retains the original auth rejection when only the refresh request times out', async () => {
    const h = harness(config => config.url.endsWith('/refresh') ? Promise.reject(new AxiosError('timeout', 'ECONNABORTED', config)) : expired)
    await expect(h.request(start)).rejects.toMatchObject({ detail: { source: 'timeout', operation: 'refresh', originalBusinessCode: 208 } })
    expect(h.notifications).toEqual([])
    expect(h.state.tokens.token).toBe('old-access')
    expect(h.calls.filter(call => call.url === start.url)).toHaveLength(1)
  })
  it('still clears a disabled identity and presents recognition even when ordinary errors belong to the caller', async () => {
    const h = harness(() => ({ code: 209, message: '停用' }))
    await expect(h.request(start)).rejects.toMatchObject({ kind: 'disabled' })
    expect(h.state.tokens.token).toBe('')
    expect(h.destinations).toHaveLength(1)
    expect(h.notifications).toEqual(['停用'])
  })
  it('keeps malformed responses distinct from a definitive rejection', async () => {
    const h = harness(() => ({ message: 'invalid payload' }))
    await expect(h.request(start)).rejects.toMatchObject({ detail: { source: 'unexpected', operation: 'request' } })
    expect(h.notifications).toEqual([])
  })
})
