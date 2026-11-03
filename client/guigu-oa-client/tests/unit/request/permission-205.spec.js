import axios from 'axios'
import { afterEach, describe, expect, it } from 'vitest'
import { createListPage } from '@/list/list-page'
import { createRecognition } from '@/recognition'
import { configureRequest } from '@/request/request'
import { openRequestScope } from '@/request/request-scope'
import { PermissionRequestFailure } from '@/request/request-failure'
import { installHttp } from '../support/harness'

const originalAdapter = axios.defaults.adapter

function harness(respond) {
  const notifications = []
  const state = { tokens: { token: 'token', refreshToken: 'refresh' } }
  const recognition = createRecognition({
    credentials: () => state.tokens,
    entryId: () => 'entry-1',
    writeTokens: () => {},
    clearUser: () => {},
    hasUser: () => true,
    writeUser: () => {},
    recovery: () => ({ status: 'idle', target: '' }),
    showRecovery: () => {},
    loadUser: async () => ({ name: 'Alice' }),
    currentPath: () => '/system/sysUser',
    goTo: () => {},
    goToLogin: () => {},
    notify: (message) => notifications.push(message),
  })
  const http = installHttp(respond)
  configureRequest(recognition)
  return { recognition, notifications, http }
}

afterEach(() => {
  axios.defaults.adapter = originalAdapter
})

describe('205 没有操作权限', () => {
  it('notifies the server message for a scoped read even when normal errors are quiet', async () => {
    const h = harness(() => ({ code: 205, message: '没有操作权限' }))
    const scope = openRequestScope(h.recognition, { notifyNormalErrors: false })

    await expect(
      scope.request({ method: 'get', url: '/admin/system/sysUser/1/10' }),
    ).rejects.toBeInstanceOf(PermissionRequestFailure)

    expect(h.notifications).toEqual(['没有操作权限'])
    expect(h.http.calls).toHaveLength(1)
  })

  it('notifies once for a quiet list read and keeps the failure on the page', async () => {
    const h = harness(() => ({ code: 205, message: '没有操作权限' }))
    const list = createListPage({
      openScope: () => openRequestScope(h.recognition, { notifyNormalErrors: false }),
      toRequest: ({ page, size }) => ({
        method: 'get',
        url: `/admin/system/sysUser/${page}/${size}`,
      }),
      notify: () => {},
      confirm: async () => true,
    })

    await list.ready

    expect(h.notifications).toEqual(['没有操作权限'])
    expect(list.view.status).toBe('failed')
    expect(list.view.error).toBe('没有操作权限')
  })

  it('falls back to the canonical 205 message when the response omits one', async () => {
    const h = harness(() => ({ code: 205 }))
    const scope = openRequestScope(h.recognition, { notifyNormalErrors: false })

    await expect(
      scope.request({ method: 'get', url: '/admin/system/sysUser/1/10' }),
    ).rejects.toBeInstanceOf(PermissionRequestFailure)

    expect(h.notifications).toEqual(['没有操作权限'])
  })

  it('keeps other quiet failures quiet', async () => {
    const h = harness(() => ({ code: 500, message: '服务器错误' }))
    const scope = openRequestScope(h.recognition, { notifyNormalErrors: false })

    await expect(
      scope.request({ method: 'get', url: '/admin/system/sysUser/1/10' }),
    ).rejects.toMatchObject({ message: '服务器错误' })

    expect(h.notifications).toEqual([])
  })
})
