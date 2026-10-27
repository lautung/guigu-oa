import axios, { AxiosError } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import ElementPlus, { ElMessage } from 'element-plus'
import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, describe, expect, it, vi } from 'vitest'
import App from '@/App.vue'
import { useUserStore } from '@/store/modules/user'
import { setupRecognition } from '@/recognition/browser-adapter'
import { setupNavigation } from '@/permission'
import { getToken, getRefreshToken, getEntryId, removeToken, setToken } from '@/utils/auth'

vi.mock('element-plus', async (importOriginal) => ({
  ...(await importOriginal()),
  ElMessage: vi.fn(),
}))
const originalAdapter = axios.defaults.adapter
let wrapper
const ok = (data) => ({ code: 200, data })
function setup(respond, initial = '/login') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const history = createMemoryHistory()
  history.push(initial)
  const router = createRouter({
    history,
    routes: [
      { path: '/login', component: { template: '<div>登录表面</div>' } },
      { path: '/work/pending', component: { template: '<div>办事内容</div>' } },
    ],
  })
  axios.defaults.adapter = async (config) => {
    try {
      return { data: await respond(config), status: 200, statusText: 'OK', headers: {}, config }
    } catch (error) {
      throw error instanceof AxiosError
        ? error
        : new AxiosError(error.message, 'ERR_NETWORK', config)
    }
  }
  const recognition = setupRecognition(pinia, router)
  setupNavigation(router, pinia, recognition)
  const store = useUserStore(pinia)
  return { pinia, router, recognition, store }
}
afterEach(() => {
  wrapper?.unmount()
  wrapper = undefined
  removeToken()
  axios.defaults.adapter = originalAdapter
  vi.clearAllMocks()
})

describe('browser adapter with real Pinia and navigation', () => {
  it('keeps login, recognition and local exit distinct', async () => {
    const calls = []
    const h = setup((config) => {
      calls.push(config.url)
      return config.url.endsWith('/login')
        ? ok({ token: 'jwt-alice', refreshToken: 'refresh-alice' })
        : ok({
            name: 'Alice',
            configMenus: [{ path: '/system/sysUser', title: '用户管理', type: 1 }],
            buttons: ['ignored'],
          })
    })
    await h.router.push('/login')
    await h.store.login({ username: ' alice ', password: '111111' })
    expect(getToken()).toBe('jwt-alice')
    expect(h.store.name).toBe('')
    await h.router.push('/work/pending')
    expect(h.store.name).toBe('Alice')
    expect(h.store.menus).toEqual([{ path: '/system/sysUser', title: '用户管理', type: 1 }])
    expect(h.store.buttons).toBeUndefined()
    await h.store.logout()
    await flushPromises()
    expect(h.store.name).toBe('')
    expect(h.store.recognized).toBe(false)
    expect(getToken()).toBeUndefined()
    expect(getRefreshToken()).toBeUndefined()
    expect(h.router.currentRoute.value.path).toBe('/login')
    expect(calls).toEqual(['/admin/system/index/login', '/admin/system/index/info'])
  })

  it('renders first-failure recovery and resumes the bookmarked target', async () => {
    setToken('only-token')
    const respond = vi
      .fn()
      .mockRejectedValueOnce(new Error('temporary'))
      .mockResolvedValue(ok({ name: 'Alice' }))
    const h = setup(respond, '/work/pending?from=bookmark')
    wrapper = mount(App, { global: { plugins: [h.pinia, h.router, ElementPlus] } })
    await vi.waitFor(() => expect(wrapper.text()).toContain('暂时无法获取用户信息'))
    expect(wrapper.text()).not.toContain('办事内容')
    expect(getToken()).toBe('only-token')
    await wrapper.get('button').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('办事内容'))
    expect(h.router.currentRoute.value.fullPath).toBe('/work/pending?from=bookmark')
    expect(h.store.name).toBe('Alice')
    expect(ElMessage).not.toHaveBeenCalled()
  })

  it('does not let the old navigation catch clear the newly recognized user', async () => {
    let rejectOld, oldConfig
    const old = new Promise((_, reject) => {
      rejectOld = reject
    })
    let infoCount = 0
    const h = setup((config) => {
      if (config.url.endsWith('/login')) return ok({ token: 'bob-only' })
      if (++infoCount === 1) {
        oldConfig = config
        return old
      }
      return ok({ name: 'Bob' })
    })
    await h.router.push('/login')
    h.recognition.establish({ token: 'alice-only' })
    const oldNavigation = h.router.push('/work/pending')
    await vi.waitFor(() => expect(infoCount).toBe(1))
    await h.store.logout()
    await h.store.login({ username: 'bob', password: '111111' })
    await h.router.push('/work/pending')
    rejectOld(new AxiosError('late network', 'ERR_NETWORK', oldConfig))
    await oldNavigation
    await flushPromises()
    expect(h.store.name).toBe('Bob')
    expect(getToken()).toBe('bob-only')
    expect(h.router.currentRoute.value.path).toBe('/work/pending')
    expect(ElMessage).not.toHaveBeenCalled()
  })

  it('synchronizes refreshed credentials in cookies and the existing store', async () => {
    const h = setup((config) => {
      if (config.url.endsWith('/refresh'))
        return ok({ token: 'new-access', refreshToken: 'refresh' })
      return config.headers.get('token') === 'new-access' ? ok({ name: 'Alice' }) : { code: 208 }
    })
    h.recognition.establish({ token: 'old-access', refreshToken: 'refresh' })
    await h.store.getInfo()
    expect(h.store.token).toBe('new-access')
    expect(getToken()).toBe('new-access')
    expect(h.store.refreshToken).toBe(getRefreshToken())
  })
  it('shows recovery for an invalid menu response and allows a valid retry', async () => {
    setToken('only-token')
    const respond = vi
      .fn()
      .mockResolvedValueOnce(ok({ name: 'Alice', configMenus: {} }))
      .mockResolvedValue(ok({ name: 'Alice', configMenus: [] }))
    const h = setup(respond, '/work/pending')
    wrapper = mount(App, { global: { plugins: [h.pinia, h.router, ElementPlus] } })
    await vi.waitFor(() => expect(wrapper.text()).toContain('暂时无法获取用户信息'))
    expect(h.store.recognized).toBe(false)
    expect(h.store.menus).toEqual([])
    expect(getToken()).toBe('only-token')
    await wrapper.get('button').trigger('click')
    await vi.waitFor(() => expect(wrapper.text()).toContain('办事内容'))
  })
  it('does not let a different tab old refresh overwrite the shared new entry', async () => {
    let finishRefresh
    let refreshCalls = 0
    const refresh = new Promise((resolve) => {
      finishRefresh = resolve
    })
    const respond = (config) => {
      if (config.url.endsWith('/refresh')) {
        refreshCalls += 1
        return refresh
      }
      if (config.url.endsWith('/login')) return ok({ token: 'bob-only' })
      return { code: 208 }
    }
    const alice = setup(respond)
    alice.recognition.establish({ token: 'alice', refreshToken: 'alice-refresh' })
    const aliceEntry = getEntryId()
    const waiting = alice.recognition.recognize('/work/pending')
    const rejected = expect(waiting).rejects.toMatchObject({ kind: 'superseded' })
    await vi.waitFor(() => expect(refreshCalls).toBe(1))
    // Allow the info request to enter the deferred refresh before replacing the cookies.
    await flushPromises()
    const bob = setup(respond)
    await bob.store.login({ username: 'bob', password: '111111' })
    expect(getEntryId()).not.toBe(aliceEntry)
    finishRefresh(ok({ token: 'alice-refreshed', refreshToken: 'alice-refresh' }))
    await rejected
    expect(getToken()).toBe('bob-only')
    expect(bob.store.token).toBe('bob-only')
    expect(ElMessage).not.toHaveBeenCalled()
  })
})
