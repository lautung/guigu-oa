import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

vi.mock('@/api/user', () => ({
  login: vi.fn(),
  getInfo: vi.fn()
}))

import { login, getInfo } from '@/api/user'
import { getToken, getRefreshToken, removeToken } from '@/utils/auth'
import { useUserStore } from '@/store/modules/user'

describe('store/user', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    removeToken()
  })

  afterEach(() => {
    vi.clearAllMocks()
    removeToken()
  })

  it('login stores the access and refresh tokens for later requests', async () => {
    login.mockResolvedValue({ data: { token: 'jwt-admin', refreshToken: 'refresh-admin' } })

    const store = useUserStore()
    await store.login({ username: 'admin', password: '111111' })

    expect(login).toHaveBeenCalledWith({ username: 'admin', password: '111111' })
    expect(store.token).toBe('jwt-admin')
    expect(store.refreshToken).toBe('refresh-admin')
    expect(getToken()).toBe('jwt-admin')
    expect(getRefreshToken()).toBe('refresh-admin')
  })

  it('getInfo fills name, avatar and menus from the info contract', async () => {
    getInfo.mockResolvedValue({
      data: {
        name: '管理员',
        avatar: 'https://example.com/avatar.png',
        routers: [{ path: '/system/sysUser', name: '用户管理' }],
        buttons: ['bnt.sysUser.add']
      }
    })

    const store = useUserStore()
    await store.getInfo()

    expect(store.name).toBe('管理员')
    expect(store.avatar).toBe('https://example.com/avatar.png')
    expect(store.menus).toEqual([{ path: '/system/sysUser', name: '用户管理' }])
    expect(store.buttons).toBeUndefined()
  })

  it('logout clears this client’s access and refresh tokens without a server call', async () => {
    login.mockResolvedValue({ data: { token: 'jwt-admin', refreshToken: 'refresh-admin' } })

    const store = useUserStore()
    await store.login({ username: 'admin', password: '111111' })
    store.name = '管理员'
    store.menus = [{ path: '/system/sysUser' }]

    await store.logout()

    expect(store.token).toBe('')
    expect(store.refreshToken).toBe('')
    expect(store.name).toBe('')
    expect(store.avatar).toBe('')
    expect(store.menus).toEqual([])
    expect(getToken()).toBeUndefined()
    expect(getRefreshToken()).toBeUndefined()
    expect(login).toHaveBeenCalledTimes(1)
  })
})
