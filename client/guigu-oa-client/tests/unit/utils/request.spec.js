import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const service = vi.fn()
  service.interceptors = {
    request: { use: vi.fn() },
    response: { use: vi.fn() },
  }
  return {
    service,
    axiosPost: vi.fn(),
    resetToken: vi.fn(),
    routerReplace: vi.fn(),
  }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mocks.service),
    post: mocks.axiosPost,
  },
}))

vi.mock('element-plus', () => ({ ElMessage: vi.fn() }))
vi.mock('@/store/modules/user', () => ({
  useUserStore: () => ({ resetToken: mocks.resetToken }),
}))
vi.mock('@/router', () => ({
  default: {
    currentRoute: { value: { path: '/work', fullPath: '/work' } },
    replace: mocks.routerReplace,
  },
}))

import service from '@/utils/request'
import { getToken, getRefreshToken, setToken, setRefreshToken, removeToken } from '@/utils/auth'

describe('utils/request authentication refresh', () => {
  const responseHandler = mocks.service.interceptors.response.use.mock.calls[0][0]
  const requestHandler = mocks.service.interceptors.request.use.mock.calls[0][0]

  beforeEach(() => {
    mocks.service.mockReset()
    mocks.axiosPost.mockReset()
    mocks.resetToken.mockReset()
    mocks.routerReplace.mockReset()
    removeToken()
    setToken('old-access')
    setRefreshToken('old-refresh')
  })

  it('refreshes an expired access token and retries the original request', async () => {
    const config = { url: '/admin/process/show/12', headers: {} }
    mocks.axiosPost.mockResolvedValue({
      data: { code: 200, data: { token: 'new-access', refreshToken: 'new-refresh' } },
    })
    mocks.service.mockResolvedValue({ code: 200, data: 'retried' })

    const result = await responseHandler({
      data: { code: 208, message: '认证失败' },
      config,
    })

    expect(mocks.axiosPost).toHaveBeenCalledWith(
      '/dev-api/admin/system/index/refresh',
      { refreshToken: 'old-refresh' },
      { timeout: 15000 },
    )
    expect(config._authRefreshRetried).toBe(true)
    expect(config.headers.token).toBe('new-access')
    expect(getToken()).toBe('new-access')
    expect(getRefreshToken()).toBe('new-refresh')
    expect(mocks.service).toHaveBeenCalledWith(config)
    expect(result.data).toBe('retried')
    expect(service).toBe(mocks.service)
  })

  it('attaches the current access token to API requests', () => {
    const config = { headers: {} }

    expect(requestHandler(config).headers.token).toBe('old-access')
  })

  it('shares one refresh operation across concurrent expired requests', async () => {
    let resolveRefresh
    mocks.axiosPost.mockReturnValue(
      new Promise((resolve) => {
        resolveRefresh = resolve
      }),
    )
    mocks.service.mockResolvedValue({ code: 200, data: 'retried' })

    const firstConfig = requestHandler({ url: '/admin/process/show/1', headers: {} })
    const secondConfig = requestHandler({ url: '/admin/process/show/2', headers: {} })
    const first = responseHandler({ data: { code: 208 }, config: firstConfig })
    const second = responseHandler({ data: { code: 208 }, config: secondConfig })

    expect(mocks.axiosPost).toHaveBeenCalledTimes(1)
    resolveRefresh({
      data: { code: 200, data: { token: 'new-access', refreshToken: 'old-refresh' } },
    })

    await expect(Promise.all([first, second])).resolves.toHaveLength(2)
    expect(mocks.service).toHaveBeenCalledTimes(2)
    expect(firstConfig.headers.token).toBe('new-access')
    expect(secondConfig.headers.token).toBe('new-access')
  })

  it('reuses a refreshed access token when an older response arrives after refresh completes', async () => {
    const lateConfig = requestHandler({ url: '/admin/process/show/3', headers: {} })
    mocks.axiosPost.mockResolvedValue({
      data: { code: 200, data: { token: 'new-access', refreshToken: 'old-refresh' } },
    })
    mocks.service.mockResolvedValue({ code: 200, data: 'retried' })

    await responseHandler({
      data: { code: 208 },
      config: { url: '/admin/process/show/1', headers: {} },
    })
    const result = await responseHandler({ data: { code: 208 }, config: lateConfig })

    expect(mocks.axiosPost).toHaveBeenCalledTimes(1)
    expect(lateConfig._authRefreshRetried).toBe(true)
    expect(lateConfig.headers.token).toBe('new-access')
    expect(mocks.service).toHaveBeenCalledTimes(2)
    expect(result.data).toBe('retried')
  })

  it('clears both local tokens when refresh fails', async () => {
    mocks.axiosPost.mockRejectedValue(new Error('refresh expired'))
    const config = requestHandler({ url: '/admin/process/show/1', headers: {} })

    await expect(responseHandler({ data: { code: 208 }, config })).rejects.toThrow(
      'refresh expired',
    )
    await vi.waitFor(() => expect(mocks.resetToken).toHaveBeenCalledTimes(1))
    expect(getToken()).toBeUndefined()
    expect(getRefreshToken()).toBeUndefined()
    expect(mocks.routerReplace).toHaveBeenCalledWith('/login?redirect=%2Fwork')
  })

  it('does not refresh or retry a request more than once', async () => {
    const config = {
      url: '/admin/process/show/1',
      headers: { token: 'old-access' },
      _authRefreshRetried: true,
    }

    await expect(
      responseHandler({ data: { code: 208, message: '认证失败' }, config }),
    ).rejects.toThrow('认证失败')

    expect(mocks.axiosPost).not.toHaveBeenCalled()
    expect(mocks.service).not.toHaveBeenCalled()
    expect(getToken()).toBeUndefined()
    expect(getRefreshToken()).toBeUndefined()
  })

  it('does not let an old request clear or refresh a newer login session', async () => {
    const config = requestHandler({ url: '/admin/process/show/1', headers: {} })
    setToken('new-access')
    setRefreshToken('new-refresh')

    await expect(
      responseHandler({ data: { code: 208, message: '认证失败' }, config }),
    ).rejects.toThrow('请求所属的登录状态已变化')

    expect(mocks.axiosPost).not.toHaveBeenCalled()
    expect(getToken()).toBe('new-access')
    expect(getRefreshToken()).toBe('new-refresh')
    expect(mocks.resetToken).not.toHaveBeenCalled()
  })

  it('does not overwrite a newer login when an older refresh finishes late', async () => {
    let resolveRefresh
    mocks.axiosPost.mockReturnValue(
      new Promise((resolve) => {
        resolveRefresh = resolve
      }),
    )
    const config = requestHandler({ url: '/admin/process/show/1', headers: {} })
    const request = responseHandler({ data: { code: 208 }, config })

    setToken('new-access')
    setRefreshToken('new-refresh')
    resolveRefresh({
      data: { code: 200, data: { token: 'old-refreshed-access', refreshToken: 'old-refresh' } },
    })

    await expect(request).rejects.toThrow('请求所属的登录状态已变化')
    expect(getToken()).toBe('new-access')
    expect(getRefreshToken()).toBe('new-refresh')
    expect(mocks.service).not.toHaveBeenCalled()
    expect(mocks.resetToken).not.toHaveBeenCalled()
  })

  it('does not clear a newer login when an older refresh fails late', async () => {
    let rejectRefresh
    mocks.axiosPost.mockReturnValue(
      new Promise((_, reject) => {
        rejectRefresh = reject
      }),
    )
    const config = requestHandler({ url: '/admin/process/show/1', headers: {} })
    const request = responseHandler({ data: { code: 208 }, config })

    setToken('new-access')
    setRefreshToken('new-refresh')
    rejectRefresh(new Error('old refresh failed'))

    await expect(request).rejects.toThrow('old refresh failed')
    expect(getToken()).toBe('new-access')
    expect(getRefreshToken()).toBe('new-refresh')
    expect(mocks.resetToken).not.toHaveBeenCalled()
  })

  it('does not deliver a successful response from an older login session', async () => {
    const config = requestHandler({ url: '/admin/process/show/1', headers: {} })
    setToken('new-access')
    setRefreshToken('new-refresh')

    await expect(
      responseHandler({ data: { code: 200, data: 'old user data' }, config }),
    ).rejects.toThrow('请求所属的登录状态已变化')
    expect(getToken()).toBe('new-access')
    expect(mocks.resetToken).not.toHaveBeenCalled()
  })
})
