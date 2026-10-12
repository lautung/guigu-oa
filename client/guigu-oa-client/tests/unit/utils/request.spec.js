import { beforeEach, describe, expect, it, vi } from 'vitest'

const mocks = vi.hoisted(() => {
  const service = vi.fn()
  service.interceptors = {
    request: { use: vi.fn() },
    response: { use: vi.fn() }
  }
  return { service, axiosPost: vi.fn() }
})

vi.mock('axios', () => ({
  default: {
    create: vi.fn(() => mocks.service),
    post: mocks.axiosPost
  }
}))

vi.mock('element-plus', () => ({ ElMessage: vi.fn() }))

import service from '@/utils/request'
import { getToken, getRefreshToken, setToken, setRefreshToken, removeToken } from '@/utils/auth'

describe('utils/request authentication refresh', () => {
  const responseHandler = mocks.service.interceptors.response.use.mock.calls[0][0]
  const requestHandler = mocks.service.interceptors.request.use.mock.calls[0][0]

  beforeEach(() => {
    mocks.service.mockReset()
    mocks.axiosPost.mockReset()
    removeToken()
    setToken('old-access')
    setRefreshToken('old-refresh')
  })

  it('refreshes an expired access token and retries the original request', async () => {
    const config = { url: '/admin/process/show/12', headers: {} }
    mocks.axiosPost.mockResolvedValue({
      data: { code: 200, data: { token: 'new-access', refreshToken: 'new-refresh' } }
    })
    mocks.service.mockResolvedValue({ code: 200, data: 'retried' })

    const result = await responseHandler({
      data: { code: 208, message: '认证失败' },
      config
    })

    expect(mocks.axiosPost).toHaveBeenCalledWith(
      '/dev-api/admin/system/index/refresh',
      { refreshToken: 'old-refresh' },
      { timeout: 15000 }
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
})
