import axios from 'axios'
import { afterEach, describe, expect, it } from 'vitest'

import { fetchNoticeImage } from '@/notice/api'
import { createRecognition } from '@/recognition'
import { configureRequest } from '@/request/request'
import { installHttp } from '../support/harness'

const originalAdapter = axios.defaults.adapter

function harness(respond) {
  const notifications = []
  const recognition = createRecognition({
    credentials: () => ({ token: 'access-token', refreshToken: 'refresh-token' }),
    entryId: () => 'entry-1',
    writeTokens: () => {},
    clearUser: () => {},
    hasUser: () => true,
    writeUser: () => {},
    recovery: () => ({ status: 'idle', target: '' }),
    showRecovery: () => {},
    loadUser: async () => ({ name: 'Alice' }),
    currentPath: () => '/notice',
    goTo: () => {},
    goToLogin: () => {},
    notify: (message) => notifications.push(message),
  })
  const http = installHttp(respond)
  configureRequest(recognition)
  return { http, notifications }
}

afterEach(() => {
  axios.defaults.adapter = originalAdapter
})

describe('公告图片的二进制请求', () => {
  it('成功时直通 Blob，并带上认证头', async () => {
    const blob = new Blob(['png'], { type: 'image/png' })
    const h = harness(() => blob)

    await expect(fetchNoticeImage(6)).resolves.toBe(blob)

    expect(h.http.calls).toHaveLength(1)
    expect(h.http.calls[0]).toMatchObject({
      method: 'get',
      url: '/admin/notices/images/6',
      responseType: 'blob',
    })
    expect(h.http.calls[0].headers.get('token')).toBe('access-token')
  })

  it('失败时静默，不弹全局提示', async () => {
    const h = harness(() => {
      throw new Error('network down')
    })

    await expect(fetchNoticeImage(6)).rejects.toBeTruthy()
    expect(h.notifications).toEqual([])
  })
})
